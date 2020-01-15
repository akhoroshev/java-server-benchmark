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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class NaiveBlockingServer extends Server {
    final private ServerSocket serverSocket;
    final private ExecutorService pool = Executors.newCachedThreadPool();

    public NaiveBlockingServer(String serverHost, int serverPort) throws IOException {
        super(ServerType.NAIVE_BLOCKING, serverHost, serverPort);
        serverSocket = new ServerSocket(serverPort);
    }

    @Override
    public void run() {
        List<Socket> connections = new ArrayList<>();
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                connections.add(socket);
                pool.submit(new Worker(socket));
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
        pool.shutdown();
        serverSocket.close();
    }

    private class Worker implements Runnable {
        final InputStream inputStream;
        final OutputStream outputStream;
        final Socket socket;

        public Worker(Socket socket) throws IOException {
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
                    processClientRequest(clientContext);
                    Protocol.Response response = endProcessClient(clientContext);

                    byte[] responseBytes = response.toByteArray();
                    byte[] responseSizeBytes = ProtocolUtils.intToBytes(responseBytes.length);

                    outputStream.write(responseSizeBytes);
                    outputStream.write(responseBytes);
                }
            } catch (IOException ignored) {
            }
        }
    }
}
