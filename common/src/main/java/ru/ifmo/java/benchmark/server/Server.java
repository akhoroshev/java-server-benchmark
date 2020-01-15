package ru.ifmo.java.benchmark.server;

import ru.ifmo.java.benchmark.protocol.Protocol;
import ru.ifmo.java.benchmark.server.async.AsyncServer;
import ru.ifmo.java.benchmark.server.blocking.BlockingServer;
import ru.ifmo.java.benchmark.server.blocking.NaiveBlockingServer;
import ru.ifmo.java.benchmark.server.nonblocking.NonBlockingServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Server {
    protected final ServerType serverType;
    protected final int serverPort;
    protected final String serverHost;

    protected final Logger logger;

    protected Server(ServerType serverType, String serverHost, int serverPort) {
        this.serverType = serverType;
        this.serverPort = serverPort;
        this.serverHost = serverHost;
        logger = Logger.getLogger(serverType.toString());
    }

    static public Server create(String serverHost, int serverPort, int threads, ServerType type) throws IOException {
        switch (type) {
            case NAIVE_BLOCKING:
                return new NaiveBlockingServer(serverHost, serverPort);
            case BLOCKING:
                return new BlockingServer(serverHost, serverPort, threads);
            case NON_BLOCKING:
                return new NonBlockingServer(serverHost, serverPort, threads);
            case ASYNC:
                return new AsyncServer(serverHost, serverPort, threads);
        }
        return null;
    }

    static public Server create(String serverHost, int serverPort, ServerType type) throws IOException {
        return create(serverHost, serverPort, 4, type);
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

    protected ClientContext beginProcessClient(Protocol.Request request) {
        ClientContext clientContext = new ClientContext(request);
        clientContext.contextProcessBeginTime = System.nanoTime();
        logger.log(Level.INFO, "ClientContext created " + clientContext.hashCode());
        return clientContext;
    }

    protected void processClientRequest(ClientContext clientContext) {
        logger.log(Level.INFO, "Begin process request " + clientContext.hashCode());
        clientContext.requestProcessBeginTime = System.nanoTime();
        if (clientContext.request.hasSortArrayRequest()) {
            clientContext.responseBuilder.setSortArrayResponse(processSortArrayRequest(clientContext.request.getSortArrayRequest()));
        } else {
            throw new IllegalStateException("Unexpected request type");
        }
        clientContext.requestProcessEndTime = System.nanoTime();
        logger.log(Level.INFO, "End process request " + clientContext.hashCode());
    }

    protected Protocol.Response endProcessClient(ClientContext clientContext) {
        logger.log(Level.INFO, "End process client " + clientContext.hashCode());
        clientContext.contextProcessEndTime = System.nanoTime();
        clientContext.responseBuilder.setProcessTimeClient((clientContext.contextProcessEndTime - clientContext.contextProcessBeginTime) / 1000000.f);
        clientContext.responseBuilder.setProcessTimeRequest((clientContext.requestProcessEndTime - clientContext.requestProcessBeginTime) / 1000000.f);
        return clientContext.responseBuilder.build();
    }

    public abstract void run();

    public abstract void close() throws IOException;

    public enum ServerType {
        NAIVE_BLOCKING,
        BLOCKING,
        NON_BLOCKING,
        ASYNC
    }

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
