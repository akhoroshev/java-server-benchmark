package ru.ifmo.java.benchmark.cli.server;

import org.apache.commons.cli.*;
import ru.ifmo.java.benchmark.server.async.AsyncServer;
import ru.ifmo.java.benchmark.server.blocking.BlockingServer;
import ru.ifmo.java.benchmark.server.blocking.NaiveBlockingServer;
import ru.ifmo.java.benchmark.server.nonblocking.NonBlockingServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_NAIVE_BLOCKING_PORT = 12345;
    public static final int DEFAULT_BLOCKING_PORT = 12346;
    public static final int DEFAULT_ASYNC_PORT = 12347;
    public static final int DEFAULT_NON_BLOCKING_PORT = 12348;
    public static final int DEFAULT_THREADS_NUMBER = 4;

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(null, "host", true, "Host");
        options.addOption(null, "naive-blocking", true, "Port for naive blocking server");
        options.addOption(null, "blocking", true, "Port for blocking server");
        options.addOption(null, "async", true, "Port for async server");
        options.addOption(null, "non-blocking", true, "Port for nonblocking server");
        options.addOption(null, "threads", true, "Number of threads");


        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args, false);

            int naiveBlockingPort = Integer.parseInt(cmd.getOptionValue("naive-blocking", String.valueOf(DEFAULT_NAIVE_BLOCKING_PORT)));
            int blockingPort = Integer.parseInt(cmd.getOptionValue("blocking", String.valueOf(DEFAULT_BLOCKING_PORT)));
            int asyncPort = Integer.parseInt(cmd.getOptionValue("async", String.valueOf(DEFAULT_ASYNC_PORT)));
            int nonBlockingPort = Integer.parseInt(cmd.getOptionValue("non-blocking", String.valueOf(DEFAULT_NON_BLOCKING_PORT)));

            int threadsNumber = Integer.parseInt(cmd.getOptionValue("threads", String.valueOf(DEFAULT_THREADS_NUMBER)));

            String host = cmd.getOptionValue("host", DEFAULT_HOST);

            List<ru.ifmo.java.benchmark.server.Server> servers = Arrays.asList(
                    new AsyncServer(host, asyncPort, threadsNumber),
                    new BlockingServer(host, blockingPort, threadsNumber),
                    new NaiveBlockingServer(host, naiveBlockingPort),
                    new NonBlockingServer(host, nonBlockingPort, threadsNumber));

            ExecutorService executor = Executors.newCachedThreadPool();
            servers.forEach(server -> executor.submit(server::run));
        } catch (ParseException e) {
            new HelpFormatter().printHelp("server-cli-application", options);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
