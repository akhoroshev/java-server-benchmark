package ru.ifmo.java.benchmark;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ru.ifmo.java.benchmark.server.Server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RunWith(value = Parameterized.class)
public class BenchmarkTest {
    final static int PORT = 12345;
    final static String HOST = "127.0.0.1";
    final Server.ServerType serverType;

    public BenchmarkTest(Server.ServerType serverType) {
        this.serverType = serverType;
    }

    @Parameterized.Parameters
    public static Collection<Server.ServerType> data() {
        return EnumSet.allOf(Server.ServerType.class);
    }

    private Server makeServer() throws IOException {
        return Server.create(HOST, PORT, serverType);
    }

    @Test
    public void runBenchmark() throws IOException {
        Server server = makeServer();
        CompletableFuture<Void> serverWorker = CompletableFuture.runAsync(server::run);

        Benchmark benchmark = new Benchmark(HOST, PORT);

        List<Benchmark.Point> evaluate = benchmark.evaluate(20, Arrays.asList(100, 200, 300, 400, 500), Arrays.asList(10, 10, 10, 10, 10), Arrays.asList(10, 10, 10, 10, 10));

        evaluate.forEach(point -> {
            Assert.assertTrue(point.avgClientWaitingTime > 0);
            Assert.assertTrue(point.clientProcessTime > 0);
            Assert.assertTrue(point.requestProcessTime > 0);
        });

        Assert.assertEquals(5, evaluate.size());

        server.close();
        serverWorker.join();
    }
}