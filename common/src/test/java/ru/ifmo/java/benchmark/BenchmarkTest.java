package ru.ifmo.java.benchmark;

import org.junit.Assert;
import org.junit.Test;
import ru.ifmo.java.benchmark.server.Server;
import ru.ifmo.java.benchmark.server.blocking.NaiveBlockingServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BenchmarkTest {
    final static int PORT = 12345;
    final static String HOST = "127.0.0.1";

    private Server makeServer() throws IOException {
        return new NaiveBlockingServer(HOST, PORT);
    }


    @Test
    public void runBenchmark() throws IOException {
        Server server = makeServer();
        CompletableFuture<Void> serverWorker = CompletableFuture.runAsync(server::run);

        Benchmark benchmark = new Benchmark(HOST, PORT);

        List<Benchmark.Point> evaluate = benchmark.evaluate(10, Arrays.asList(1000, 2000, 3000), Arrays.asList(10, 20, 30), Arrays.asList(10, 20, 30));

        Assert.assertEquals(3, evaluate.size());

        server.close();
        serverWorker.join();
    }
}