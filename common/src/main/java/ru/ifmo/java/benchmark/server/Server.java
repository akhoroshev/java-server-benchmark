package ru.ifmo.java.benchmark.server;

import ru.ifmo.java.benchmark.protocol.Protocol;

import java.io.IOException;

public abstract class Server {
    protected final int serverPort;
    protected final String serverHost;

    protected Server(String serverHost, int serverPort) {
        this.serverPort = serverPort;
        this.serverHost = serverHost;
    }

    static protected ClientContext beginProcessClient(Protocol.Request request) {
        ClientContext clientContext = new ClientContext(request);
        clientContext.contextProcessBeginTime = System.nanoTime();
        return clientContext;
    }

    static protected void processClientRequest(ClientContext clientContext) {
        clientContext.requestProcessBeginTime = System.nanoTime();
        if (clientContext.request.hasSortArrayRequest()) {
            clientContext.responseBuilder.setSortArrayResponse(processSortArrayRequest(clientContext.request.getSortArrayRequest()));
        } else {
            throw new IllegalStateException("Unexpected request type");
        }
        clientContext.requestProcessEndTime = System.nanoTime();
    }

    static protected Protocol.Response endProcessClient(ClientContext clientContext) {
        clientContext.contextProcessEndTime = System.nanoTime();
        clientContext.responseBuilder.setProcessTimeClient((clientContext.contextProcessEndTime - clientContext.contextProcessBeginTime) / 1000000.f);
        clientContext.responseBuilder.setProcessTimeRequest((clientContext.requestProcessEndTime - clientContext.requestProcessBeginTime) / 1000000.f);
        return clientContext.responseBuilder.build();
    }

    static protected Protocol.SortArrayResponse processSortArrayRequest(Protocol.SortArrayRequest request) {
        return Protocol.SortArrayResponse.newBuilder().setData(request.getData()).build();
    }

    public abstract void run();

    public abstract void close() throws IOException;

    protected static class ClientContext {
        final Protocol.Response.Builder responseBuilder;
        final Protocol.Request request;
        float contextProcessBeginTime;
        float contextProcessEndTime;
        float requestProcessBeginTime;
        float requestProcessEndTime;

        public ClientContext(Protocol.Request request) {
            this.request = request;
            responseBuilder = Protocol.Response.newBuilder();
        }
    }
}
