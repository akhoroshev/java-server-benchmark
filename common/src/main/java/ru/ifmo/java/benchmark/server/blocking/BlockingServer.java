package ru.ifmo.java.benchmark.server.blocking;

import ru.ifmo.java.benchmark.protocol.Protocol;
import ru.ifmo.java.benchmark.protocol.ProtocolUtils;
import ru.ifmo.java.benchmark.server.Server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class BlockingServer extends Server {
    final private ServerSocket serverSocket;
    final private ExecutorService listenerPool = Executors.newCachedThreadPool();
    final private ExecutorService workingPool;

    public BlockingServer(String serverHost, int serverPort, int threads) throws IOException {
        super(ServerType.BLOCKING, serverHost, serverPort);
        serverSocket = new ServerSocket(serverPort);
        workingPool = Executors.newFixedThreadPool(threads);
    }

    public BlockingServer(String serverHost, int serverPort) throws IOException {
        this(serverHost, serverPort, 4);
    }

    @Override
    public void run() {
        List<Socket> connections = new ArrayList<>();
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                connections.add(socket);
                listenerPool.submit(new Listener(socket));
            }
        } catch (IOException | RejectedExecutionException ignored) {
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
        listenerPool.shutdown();
        serverSocket.close();
    }

    private class Listener implements Runnable {
        final InputStream inputStream;
        final OutputStream outputStream;
        final Socket socket;

        final ExecutorService singleExecutor = Executors.newSingleThreadExecutor();

        public Listener(Socket socket) throws IOException {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    byte[] sizeBuffer = new byte[4];

                    int count = inputStream.read(sizeBuffer);

                    if (count != 4) {
                        socket.close();
                        break;
                    }

                    int messageSize = ProtocolUtils.bytesToInt(sizeBuffer);
                    byte[] messageBuffer = new byte[messageSize];

                    count = ProtocolUtils.read(inputStream, messageBuffer);
                    if (count != messageSize) {
                        socket.close();
                        break;
                    }

                    ClientContext clientContext = beginProcessClient(Protocol.Request.parseFrom(messageBuffer));

                    CompletableFuture.runAsync(() -> processClientRequest(clientContext), workingPool).thenRunAsync(() -> {
                        Protocol.Response response = endProcessClient(clientContext);
                        try {
                            byte[] responseBytes = response.toByteArray();
                            byte[] responseSizeBytes = ProtocolUtils.intToBytes(responseBytes.length);

                            outputStream.write(responseSizeBytes);
                            outputStream.write(responseBytes);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }, singleExecutor);
                }
            } catch (IOException ignored) {
            }
        }
    }
}
