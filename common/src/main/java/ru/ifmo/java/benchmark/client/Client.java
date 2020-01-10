package ru.ifmo.java.benchmark.client;

import ru.ifmo.java.benchmark.protocol.Protocol;
import ru.ifmo.java.benchmark.protocol.ProtocolUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    final private Socket socket;
    final private InputStream inputStream;
    final private OutputStream outputStream;

    final private ExecutorService singleExecutorService = Executors.newSingleThreadExecutor();

    public Client(String serverHost, int serverPort) throws IOException {
        socket = new Socket(serverHost, serverPort);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    public void close() throws IOException {
        singleExecutorService.shutdown();
        socket.close();
    }

    public CompletableFuture<Response<List<Integer>>> sortArray(List<Integer> array) {
        Protocol.Request request = Protocol.Request.newBuilder()
                .setSortArrayRequest(Protocol.SortArrayRequest.newBuilder()
                        .setData(Protocol.Array.newBuilder()
                                .addAllItem(array)
                                .build())
                        .build())
                .build();

        return CompletableFuture.supplyAsync(() -> {
            try {
                sendRequest(request);
                Protocol.Response response = receiveResponse();
                return new Response<>(response.getSortArrayResponse().getData().getItemList(), response.getProcessTimeRequest(), response.getProcessTimeClient());
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, singleExecutorService);
    }

    private void sendRequest(Protocol.Request request) throws IOException {
        byte[] requestBytes = request.toByteArray();
        byte[] requestSizeBytes = ProtocolUtils.intToBytes(requestBytes.length);

        outputStream.write(requestSizeBytes);
        outputStream.write(requestBytes);
    }

    private Protocol.Response receiveResponse() throws IOException {
        byte[] responseSizeBytes = new byte[4];
        if (inputStream.read(responseSizeBytes) != 4) {
            throw new IOException("Invalid length of prefix");
        }
        int responseSize = ProtocolUtils.bytesToInt(responseSizeBytes);
        byte[] responseBytes = new byte[responseSize];
        if (ProtocolUtils.read(inputStream, responseBytes) != responseSize) {
            throw new IOException("Invalid length of response");
        }
        return Protocol.Response.parseFrom(responseBytes);
    }

    public static class Response<T> {
        private final T body;
        private final float processTimeRequest;
        private final float processTimeClient;

        public Response(T body, float processTimeRequest, float processTimeClient) {
            this.body = body;
            this.processTimeRequest = processTimeRequest;
            this.processTimeClient = processTimeClient;
        }

        public float getProcessTimeClient() {
            return processTimeClient;
        }

        public float getProcessTimeRequest() {
            return processTimeRequest;
        }

        public T getBody() {
            return body;
        }
    }
}
