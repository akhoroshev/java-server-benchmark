package ru.ifmo.java.benchmark;

import org.apache.commons.lang3.tuple.Triple;
import ru.ifmo.java.benchmark.client.Client;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Benchmark {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_NAIVE_BLOCKING_PORT = 12345;
    public static final int DEFAULT_BLOCKING_PORT = 12346;
    public static final int DEFAULT_ASYNC_PORT = 12347;
    public static final int DEFAULT_NON_BLOCKING_PORT = 12348;

    final private String host;
    final private int port;

    public Benchmark(String host, int port) {
        this.host = host;
        this.port = port;
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

            for (Client client : clients) {
                CompletableFuture<Triple<List<TimeSpent>, Long, Long>> task = CompletableFuture.completedFuture(System.nanoTime())
                        .thenCompose(startTime -> client.sortArray(items)
                                .thenApply(response -> new ArrayList<>(Collections.singletonList(TimeSpent.from(response))))
                                .thenApply(timeSpents -> Triple.of(timeSpents, startTime, System.nanoTime())));

                for (int j = 1; j < requestCount; j++) {
                    task = task.thenApply(result ->
                    {
                        try {
                            Thread.sleep(currentTimeIntervalMs);
                        } catch (InterruptedException ignored) {
                        }
                        return result;
                    }).thenCompose(result -> client.sortArray(items).thenApply(listResponse -> {
                        result.getLeft().add(TimeSpent.from(listResponse));
                        return Triple.of(result.getLeft(), result.getMiddle(), System.nanoTime());
                    }));
                }
                tasks.add(task);
            }

            List<Triple<List<TimeSpent>, Long, Long>> triples = tasks.stream().map(CompletableFuture::join).collect(Collectors.toList());

            OptionalDouble averageProcessTimeClient = triples.stream().map(Triple::getLeft).flatMap(List::stream).map(TimeSpent::getProcessTimeClient).mapToDouble(value -> value).average();
            OptionalDouble averageProcessTimeRequest = triples.stream().map(Triple::getLeft).flatMap(List::stream).map(TimeSpent::getProcessTimeRequest).mapToDouble(value -> value).average();
            OptionalDouble averageTimeOnClientSide = triples.stream().map(listLongLongTriple -> listLongLongTriple.getRight() - listLongLongTriple.getMiddle())
                    .map(value -> value / 1000000.f - (requestCount - 1) * currentTimeIntervalMs)
                    .map(aFloat -> aFloat / requestCount)
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
