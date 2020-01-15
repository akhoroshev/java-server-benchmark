package ru.ifmo.java.benchmark.server.nonblocking;

import ru.ifmo.java.benchmark.protocol.Protocol;
import ru.ifmo.java.benchmark.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NonBlockingServer extends Server {
    final private ServerSocketChannel serverSocketChannel;
    final private Selector inputSelector = Selector.open();
    final private Selector outputSelector = Selector.open();
    final private ExecutorService inputSelectorExecutor = Executors.newSingleThreadExecutor();
    final private ExecutorService outputSelectorExecutor = Executors.newSingleThreadExecutor();
    final private ExecutorService workingPool;

    Lock inputSelectorLock = new ReentrantLock();
    Lock outputSelectorLock = new ReentrantLock();

    public NonBlockingServer(String serverHost, int serverPort, int threads) throws IOException {
        super(ServerType.NON_BLOCKING, serverHost, serverPort);
        serverSocketChannel = ServerSocketChannel.open().bind(new InetSocketAddress(serverHost, serverPort));
        workingPool = Executors.newFixedThreadPool(threads);
    }

    public NonBlockingServer(String serverHost, int serverPort) throws IOException {
        this(serverHost, serverPort, 4);
    }

    @Override
    public void run() {
        inputSelectorExecutor.submit(this::inputSelectorReader);
        outputSelectorExecutor.submit(this::outputSelectorWriter);
        List<SocketChannel> connections = new ArrayList<>();

        try {
            while (true) {
                SocketChannel socketChannel = serverSocketChannel.accept();

                connections.add(socketChannel);
                socketChannel.configureBlocking(false);
                inputSelectorLock.lock();
                try {
                    inputSelector.wakeup();
                    socketChannel.register(inputSelector, SelectionKey.OP_READ, new ChannelInputContext());
                } finally {
                    inputSelectorLock.unlock();
                }
            }
        } catch (IOException | ClosedSelectorException ignored) {
        } finally {
            connections.forEach(socket -> {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            });
        }
    }

    @Override
    public void close() throws IOException {
        serverSocketChannel.close();
        inputSelectorExecutor.shutdown();
        workingPool.shutdown();
        outputSelectorExecutor.shutdown();
        inputSelector.close();
        outputSelector.close();
    }

    private void inputSelectorReader() {
        try {
            while (!Thread.interrupted()) {
                inputSelectorLock.lock();
                inputSelectorLock.unlock();
                if (inputSelector.select() == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = inputSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    SocketChannel channel = (SocketChannel) key.channel();
                    ChannelInputContext context = (ChannelInputContext) key.attachment();

                    if (key.isReadable()) {
                        if (channel.read(context.buffer) == -1) {
                            key.cancel();
                        }
                    }

                    if (!context.buffer.hasRemaining()) {
                        context.buffer.flip();
                        switch (context.currentMessagePart) {
                            case HEAD:
                                context.currentMessagePart = ChannelInputContext.MessagePart.BODY;
                                context.buffer = ByteBuffer.allocate(context.buffer.getInt());
                                break;
                            case BODY:
                                ClientContext clientContext = beginProcessClient(Protocol.Request.parseFrom(context.buffer));
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        processClientRequest(clientContext);
                                        ChannelOutputContext channelOutputContext = new ChannelOutputContext();
                                        channelOutputContext.processedClientContext.set(clientContext);
                                        outputSelectorLock.lock();
                                        try {
                                            outputSelector.wakeup();
                                            channel.register(outputSelector, SelectionKey.OP_WRITE, channelOutputContext);
                                        } finally {
                                            outputSelectorLock.unlock();
                                        }
                                    } catch (ClosedChannelException ignored) {
                                    }
                                }, workingPool);
                                context.currentMessagePart = ChannelInputContext.MessagePart.HEAD;
                                context.buffer = ByteBuffer.allocate(4);
                                break;
                        }
                    }

                    keyIterator.remove();
                }
            }
        } catch (IOException | ClosedSelectorException ignored) {
        }
    }

    private void outputSelectorWriter() {
        try {
            while (!Thread.interrupted()) {
                outputSelectorLock.lock();
                outputSelectorLock.unlock();
                if (outputSelector.select() == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = outputSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    SocketChannel channel = (SocketChannel) key.channel();
                    ChannelOutputContext context = (ChannelOutputContext) key.attachment();

                    if (context.responseBytes == null) {
                        byte[] response = endProcessClient(context.processedClientContext.get()).toByteArray();
                        ByteBuffer responseBytes = ByteBuffer.allocate(4 + response.length);
                        responseBytes.putInt(response.length);
                        responseBytes.put(response);
                        responseBytes.flip();
                        context.responseBytes = responseBytes;
                    }

                    if (key.isWritable()) {
                        channel.write(context.responseBytes);
                    }

                    if (!context.responseBytes.hasRemaining()) {
                        context.responseBytes = null;
                        key.cancel();
                    }

                    keyIterator.remove();
                }
            }
        } catch (IOException | ClosedSelectorException ignored) {
        }
    }

    private static class ChannelInputContext {
        MessagePart currentMessagePart;
        ByteBuffer buffer;

        ChannelInputContext() {
            currentMessagePart = MessagePart.HEAD;
            buffer = ByteBuffer.allocate(4);
        }

        enum MessagePart {
            HEAD,
            BODY
        }
    }

    private static class ChannelOutputContext {
        final AtomicReference<ClientContext> processedClientContext = new AtomicReference<>();
        ByteBuffer responseBytes = null;
    }
}
