package ru.ifmo.java.benchmark;

import org.apache.commons.lang3.tuple.Triple;
import ru.ifmo.java.benchmark.client.Client;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Benchmark {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_NAIVE_BLOCKING_PORT = 12345;
    public static final int DEFAULT_BLOCKING_PORT = 12346;
    public static final int DEFAULT_ASYNC_PORT = 12347;
    public static final int DEFAULT_NON_BLOCKING_PORT = 12348;
    protected final Logger logger = Logger.getLogger(Benchmark.class.getName());
    final private String host;
    final private int port;

    public Benchmark(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void warmUp() throws IOException {
        Client client = new Client(host, port);
        ArrayList<Integer> items = getItems(2000);
        Collections.shuffle(items);

        client.sortArray(items).thenRun(() -> {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }).join();
    }

    public List<Point> evaluate(int requestCount, List<Integer> elementCounts, List<Integer> concurrencyClientCounts, List<Integer> timeIntervalsMs) throws IOException {
        int pointsCount = Math.min(Math.min(elementCounts.size(), concurrencyClientCounts.size()), timeIntervalsMs.size());

        List<Point> results = new ArrayList<>();

        for (int i = 0; i < pointsCount; i++) {
            final int currentTimeIntervalMs = timeIntervalsMs.get(i);
            final int currentConcurrencyClient = concurrencyClientCounts.get(i);
            final int currentElementCount = elementCounts.get(i);

            ArrayList<Integer> items = getItems(currentElementCount);
            Collections.shuffle(items);

            List<Client> clients = new ArrayList<>(currentConcurrencyClient);
            for (int j = 0; j < currentConcurrencyClient; j++) {
                clients.add(new Client(host, port));
            }

            List<CompletableFuture<Triple<List<TimeSpent>, Long, Long>>> tasks = new ArrayList<>();

            final AtomicBoolean firstFinished = new AtomicBoolean(false);
            final AtomicInteger countOfStartedClient = new AtomicInteger(0);

            for (Client client : clients) {
                CompletableFuture<Triple<List<TimeSpent>, Long, Long>> task = CompletableFuture.completedFuture(Triple.of(new ArrayList<>(), -1L, -1L));

                for (int j = 0; j < requestCount; j++) {
                    final int currentRequest = j;
                    task = task.thenApply(result ->
                    {
                        try {
                            if (currentRequest != 0)
                                Thread.sleep(currentTimeIntervalMs);
                        } catch (InterruptedException ignored) {
                        }
                        return result;
                    }).thenCompose(result -> {
                        // Some client receives all responses
                        if (firstFinished.get()) {
                            return CompletableFuture.completedFuture(result);
                        }

                        final long maybeStartPoint = System.nanoTime();

                        return client.sortArray(items).thenApply(listResponse -> {
                            if (currentRequest == 0) {
                                countOfStartedClient.incrementAndGet();
                            }
                            if (currentRequest == requestCount - 1) {
                                firstFinished.set(true);
                            }

                            long startPoint = result.getMiddle();

                            if (startPoint == -1 && countOfStartedClient.get() == currentConcurrencyClient) {
                                startPoint = maybeStartPoint;
                            }

                            if (startPoint == -1) {
                                // All clients not starts yet
                                return result;
                            }

                            result.getLeft().add(TimeSpent.from(listResponse));
                            return Triple.of(result.getLeft(), startPoint, System.nanoTime());
                        });
                    });
                }

                task = task.thenApply(listLongLongTriple -> {
                    try {
                        client.close();
                    } catch (IOException ignored) {
                    }
                    return listLongLongTriple;
                });

                tasks.add(task);
            }

            List<Triple<List<TimeSpent>, Long, Long>> triples = tasks.stream().map(CompletableFuture::join).collect(Collectors.toList());
            Optional<Triple<List<TimeSpent>, Long, Long>> incorrect = triples.stream().filter(t -> t.getMiddle() == -1 && t.getRight() == -1).findFirst();

            if (incorrect.isPresent()) {
                logger.log(Level.WARNING, "Incorrect result, skip point");
                continue;
            }

            OptionalDouble averageProcessTimeClient = triples.stream().map(Triple::getLeft).flatMap(List::stream).map(TimeSpent::getProcessTimeClient).mapToDouble(value -> value).average();
            OptionalDouble averageProcessTimeRequest = triples.stream().map(Triple::getLeft).flatMap(List::stream).map(TimeSpent::getProcessTimeRequest).mapToDouble(value -> value).average();
            OptionalDouble averageTimeOnClientSide = triples.stream().map(
                    t -> ((t.getRight() - t.getMiddle()) / 1000000.f - (t.getLeft().size() - 1) * currentTimeIntervalMs) / t.getLeft().size())
                    .mapToDouble(value -> value).average();

            results.add(Point.of(currentElementCount, currentConcurrencyClient, currentTimeIntervalMs, averageProcessTimeRequest.orElse(-1), averageProcessTimeClient.orElse(-1), averageTimeOnClientSide.orElse(-1)));
        }

        return results;
    }

    private ArrayList<Integer> getItems(int length) {
        ArrayList<Integer> arrayList = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            arrayList.add(i * 7 - 2020);
        }
        return arrayList;
    }

    public static class Point {
        final public int elements;
        final public int clients;
        final public int interval;
        final public double requestProcessTime;
        final public double clientProcessTime;
        final public double avgClientWaitingTime;

        private Point(int elements, int clients, int interval, double requestProcessTime, double clientProcessTime, double avgClientWaitingTime) {
            this.elements = elements;
            this.clients = clients;
            this.interval = interval;
            this.requestProcessTime = requestProcessTime;
            this.clientProcessTime = clientProcessTime;
            this.avgClientWaitingTime = avgClientWaitingTime;
        }

        static public Point of(int elements, int clients, int interval, double requestProcessTime, double clientProcessTime, double avgClientWaitingTime) {
            return new Point(elements, clients, interval, requestProcessTime, clientProcessTime, avgClientWaitingTime);
        }

        public double getRequestProcessTime() {
            return requestProcessTime;
        }

        public double getClientProcessTime() {
            return clientProcessTime;
        }

        public double getAvgClientWaitingTime() {
            return avgClientWaitingTime;
        }
    }

    private static class TimeSpent {
        final float processTimeRequest;
        final float processTimeClient;

        TimeSpent(float processTimeRequest, float processTimeClient) {
            this.processTimeRequest = processTimeRequest;
            this.processTimeClient = processTimeClient;
        }

        static <T> TimeSpent from(Client.Response<T> response) {
            return new TimeSpent(response.getProcessTimeRequest(), response.getProcessTimeClient());
        }

        public float getProcessTimeRequest() {
            return processTimeRequest;
        }

        public float getProcessTimeClient() {
            return processTimeClient;
        }
    }
}
