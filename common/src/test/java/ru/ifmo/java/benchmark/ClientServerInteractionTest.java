package ru.ifmo.java.benchmark;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ru.ifmo.java.benchmark.client.Client;
import ru.ifmo.java.benchmark.server.Server;
import ru.ifmo.java.benchmark.server.async.AsyncServer;
import ru.ifmo.java.benchmark.server.blocking.BlockingServer;
import ru.ifmo.java.benchmark.server.blocking.NaiveBlockingServer;
import ru.ifmo.java.benchmark.server.nonblocking.NonBlockingServer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RunWith(value = Parameterized.class)
public class ClientServerInteractionTest {
    final static int PORT = 12345;
    final static String HOST = "127.0.0.1";
    final static Random random = new Random();
    final Class<? extends Server> clazz;

    public ClientServerInteractionTest(Class<? extends Server> clazz) {
        this.clazz = clazz;
    }

    @Parameterized.Parameters
    public static Collection<Class<? extends Server>> data() {
        return Arrays.asList(AsyncServer.class, BlockingServer.class, NaiveBlockingServer.class, NonBlockingServer.class);
    }

    static private List<Integer> makeRandomArray(int length) {
        ArrayList<Integer> arrayList = new ArrayList<>(length);

        for (int i = 0; i < length; i++) {
            arrayList.add(random.nextInt());
        }

        return arrayList;
    }

    private Server makeServer() {
        try {
            return clazz.getConstructor(String.class, int.class).newInstance(ClientServerInteractionTest.HOST, ClientServerInteractionTest.PORT);
        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Client makeClient() throws IOException {
        return new Client(ClientServerInteractionTest.HOST, ClientServerInteractionTest.PORT);
    }

    @Test
    public void testConnectionUpDown() throws IOException {
        Server server = makeServer();

        CompletableFuture<Void> serverWorker = CompletableFuture.runAsync(server::run);

        List<Client> clients = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            clients.add(makeClient());
        }

        server.close();

        for (Client client : clients) {
            client.close();
        }

        serverWorker.join();
    }

    @Test
    public void testSort() throws IOException {
        Server server = makeServer();

        CompletableFuture<Void> serverWorker = CompletableFuture.runAsync(server::run);

        List<Client> clients = new ArrayList<>();
        List<CompletableFuture<?>> completableFutures = new ArrayList<>();


        for (int i = 0; i < 10; i++) {
            Client client = makeClient();
            clients.add(client);
            List<Integer> in = makeRandomArray(1000000);
            completableFutures.add(client.sortArray(in).thenAcceptAsync(listResponse -> {
//                Collections.sort(in);
                Assert.assertEquals(in.size(), listResponse.getBody().size());
                Assert.assertEquals(in, listResponse.getBody());
            }));
        }

        completableFutures.forEach(CompletableFuture::join);


        for (Client client : clients) {
            client.close();
        }
        server.close();
        serverWorker.join();
    }
}