package ru.ifmo.java.benchmark.server.async;

import ru.ifmo.java.benchmark.protocol.Protocol;
import ru.ifmo.java.benchmark.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncServer extends Server {
    final private ExecutorService workingPool;
    AsynchronousServerSocketChannel asynchronousServerSocketChannel;

    public AsyncServer(String serverHost, int serverPort, int threads) throws IOException {
        super(ServerType.ASYNC, serverHost, serverPort);
        asynchronousServerSocketChannel = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(serverHost, serverPort));
        workingPool = Executors.newFixedThreadPool(threads);
    }

    public AsyncServer(String serverHost, int serverPort) throws IOException {
        this(serverHost, serverPort, 4);
    }

    @Override
    public void run() {
        asynchronousServerSocketChannel.accept(asynchronousServerSocketChannel, new CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel>() {
            @Override
            public void completed(AsynchronousSocketChannel result, AsynchronousServerSocketChannel attachment) {
                asynchronousServerSocketChannel.accept(asynchronousServerSocketChannel, this);
                startListenSize(result);
            }

            @Override
            public void failed(Throwable exc, AsynchronousServerSocketChannel attachment) {
                try {
                    attachment.close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void startListenSize(AsynchronousSocketChannel channel) {
        final ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
        channel.read(sizeBuffer, channel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel attachment) {
                if (result != 4) {
                    try {
                        attachment.close();
                    } catch (IOException ignored) {
                    }
                    return;
                }
                sizeBuffer.flip();
                int messageSize = sizeBuffer.getInt();
                startListenMessage(attachment, ByteBuffer.allocate(messageSize));
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                try {
                    attachment.close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void startListenMessage(AsynchronousSocketChannel channel, ByteBuffer messageBuffer) {
        channel.read(messageBuffer, channel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel attachment) {
                if (messageBuffer.remaining() > 0) {
                    startListenMessage(channel, messageBuffer);
                } else {
                    messageBuffer.flip();
                    try {
                        ClientContext clientContext = beginProcessClient(Protocol.Request.parseFrom(messageBuffer));
                        CompletableFuture.runAsync(() -> {
                            processClientRequest(clientContext);

                            byte[] response = endProcessClient(clientContext).toByteArray();
                            int responseSize = 4 + response.length;
                            ByteBuffer byteBuffer = ByteBuffer.allocate(responseSize);
                            byteBuffer.putInt(response.length);
                            byteBuffer.put(response);
                            byteBuffer.flip();

                            startWritingMessage(attachment, byteBuffer);
                        }, workingPool);
                        startListenSize(channel);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                try {
                    attachment.close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void startWritingMessage(AsynchronousSocketChannel channel, ByteBuffer messageBuffer) {
        channel.write(messageBuffer, channel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel attachment) {
                if (messageBuffer.remaining() > 0) {
                    startWritingMessage(channel, messageBuffer);
                }
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                try {
                    attachment.close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    @Override
    public void close() throws IOException {
        asynchronousServerSocketChannel.close();
        workingPool.shutdown();
    }
}
