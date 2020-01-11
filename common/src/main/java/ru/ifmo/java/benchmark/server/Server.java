package ru.ifmo.java.benchmark.server;

import ru.ifmo.java.benchmark.protocol.Protocol;

import java.io.IOException;
import java.util.ArrayList;

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
        ArrayList<Integer> arrayList = new ArrayList<>(request.getData().getItemList());

        // Gnome sort
        {
            int i = 1;
            int tmp;
            while (i < arrayList.size()) {
                if (i == 0 || arrayList.get(i - 1) <= arrayList.get(i)) {
                    i++;
                } else {
                    tmp = arrayList.get(i);
                    arrayList.set(i, arrayList.get(i - 1));
                    arrayList.set(i - 1, tmp);
                    i--;
                }
            }
        }

        return Protocol.SortArrayResponse.newBuilder().setData(Protocol.Array.newBuilder().addAllItem(arrayList).build()).build();
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
