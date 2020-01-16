package ru.ifmo.java.benchmark.gui.client;

import org.apache.commons.lang3.tuple.Triple;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import ru.ifmo.java.benchmark.Benchmark;
import ru.ifmo.java.benchmark.server.Server;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ru.ifmo.java.benchmark.Benchmark.*;

public class Client {
    private final ExecutorService uiThreadPool = Executors.newSingleThreadExecutor();
    private final ExecutorService benchmarkThreadPool = Executors.newSingleThreadExecutor();
    private String hostAddress;
    private int naiveBlockingPort;
    private int blockingPort;
    private int asyncPort;
    private int nonBlockingPort;
    private Server.ServerType selectedType;
    private int requestCount;
    private ChangeParameter changeParameter;
    private Triple<Integer, Integer, Integer> changeRange; // from to delta
    private int valueN;
    private int valueM;
    private int valueDELTA;

    public Client() {
        setDefaults();
    }

    public static void main(String[] args) {
        new Client().run();
    }

    private static List<Integer> range(int from, int to, int step) {
        List<Integer> array = new ArrayList<>();
        for (int i = from; i <= to; i += step) {
            array.add(i);
        }
        return array;
    }

    private static List<Integer> values(int value, int count) {
        List<Integer> array = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            array.add(value);
        }
        return array;
    }

    private void run() {
        runInUiThread(() -> {
            final JFrame mainFrame = new JFrame();

            mainFrame.setBounds(100, 100, 1280, 720);
            mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            final JMenuBar menuBar = new JMenuBar();
            menuBar.add(createSettingsMenu());
            mainFrame.setJMenuBar(menuBar);


            JPanel resultsPanel = new JPanel();
            resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
            JScrollPane scrollPane = new JScrollPane(resultsPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

            mainFrame.add(scrollPane, BorderLayout.CENTER);

            JPanel benchProp = new JPanel(new GridLayout(1, 0));
            benchProp.add(createEnumSelector(Server.ServerType.class, () -> selectedType, in -> selectedType = in));
            benchProp.add(createEnumSelector(ChangeParameter.class, () -> changeParameter, in -> changeParameter = in));
            benchProp.add(createRangeInputField(() -> changeRange, in -> changeRange = in));
            benchProp.add(createInputNumberField("Delta value, ms", () -> valueDELTA, in -> valueDELTA = in));
            benchProp.add(createInputNumberField("Array size", () -> valueN, in -> valueN = in));
            benchProp.add(createInputNumberField("Client count", () -> valueM, in -> valueM = in));
            benchProp.add(createInputNumberField("Request count", () -> requestCount, in -> requestCount = in));
            benchProp.add(createRunBenchButton(() -> CompletableFuture.supplyAsync(() -> {
                try {
                    return runBench();
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, benchmarkThreadPool).thenAcceptAsync(jPanel -> {
                resultsPanel.add(jPanel);
                resultsPanel.updateUI();
            }, uiThreadPool)));

            mainFrame.add(benchProp, BorderLayout.SOUTH);
            mainFrame.setVisible(true);
        });
    }

    private JPanel createRunBenchButton(Runnable action) {
        JPanel panel = new JPanel(new BorderLayout());
        JButton button = new JButton("Run benchmark");
        button.addActionListener(e -> runInUiThread(action));
        panel.add(button, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createRangeInputField(Supplier<Triple<Integer, Integer, Integer>> getter, Consumer<Triple<Integer, Integer, Integer>> setter) {
        JPanel layout = new JPanel(new BorderLayout());
        JPanel labelPane = new JPanel(new GridLayout(0, 1));
        JPanel fieldPane = new JPanel(new GridLayout(0, 1));

        JFormattedTextField fromField = new JFormattedTextField(NumberFormat.getNumberInstance());
        fromField.setValue(getter.get().getLeft());
        fromField.setColumns(10);

        JFormattedTextField toField = new JFormattedTextField(NumberFormat.getNumberInstance());
        toField.setValue(getter.get().getMiddle());
        toField.setColumns(10);

        JFormattedTextField stepField = new JFormattedTextField(NumberFormat.getNumberInstance());
        stepField.setValue(getter.get().getRight());
        stepField.setColumns(10);

        Runnable callback = () -> setter.accept(Triple.of(
                ((Number) fromField.getValue()).intValue(),
                ((Number) toField.getValue()).intValue(),
                ((Number) stepField.getValue()).intValue()));

        fromField.addPropertyChangeListener("value", evt -> runInUiThread(callback));
        toField.addPropertyChangeListener("value", evt -> runInUiThread(callback));
        stepField.addPropertyChangeListener("value", evt -> runInUiThread(callback));

        labelPane.add(new JLabel("From"));
        labelPane.add(new JLabel("To"));
        labelPane.add(new JLabel("Step"));

        fieldPane.add(fromField);
        fieldPane.add(toField);
        fieldPane.add(stepField);

        layout.add(labelPane, BorderLayout.WEST);
        layout.add(fieldPane, BorderLayout.CENTER);


        return layout;
    }

    private JPanel createInputNumberField(String message, Supplier<Integer> getter, Consumer<Integer> setter) {
        JPanel layout = new JPanel(new BorderLayout());
        JPanel labelPane = new JPanel(new GridLayout(0, 1));
        JPanel fieldPane = new JPanel(new GridLayout(0, 1));

        JFormattedTextField amountField = new JFormattedTextField(NumberFormat.getNumberInstance());
        amountField.setValue(getter.get());
        amountField.setColumns(10);
        amountField.addPropertyChangeListener("value", evt -> runInUiThread(() -> setter.accept(((Number) evt.getNewValue()).intValue())));

        labelPane.add(new JLabel(message));
        fieldPane.add(amountField);

        layout.add(labelPane, BorderLayout.CENTER);
        layout.add(fieldPane, BorderLayout.SOUTH);

        return layout;
    }

    private ChartPanel createChart(String name, List<Double> x, List<Double> y) {
        XYSeries series = new XYSeries(name);
        for (int i = 0; i < x.size(); i++) {
            series.add(x.get(i), y.get(i));
        }

        XYSeriesCollection collection = new XYSeriesCollection(series);

        return new ChartPanel(ChartFactory.createScatterPlot(
                name,
                changeParameter.toString(),
                "time, ms",
                collection,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        ));
    }

    private JButton createSaveResultsButton(List<Benchmark.Point> points) {
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> runInUiThread(() -> {
            final JFileChooser SaveAs = new JFileChooser();
            SaveAs.setApproveButtonText("Save");
            int actionDialog = SaveAs.showOpenDialog(null);
            if (actionDialog != JFileChooser.APPROVE_OPTION) {
                return;
            }

            try (BufferedWriter outFile = new BufferedWriter(new FileWriter(new File(SaveAs.getSelectedFile() + ".csv")))) {
                String file = points.stream()
                        .map(p -> String.format("%d,%d,%d,%f,%f,%f", p.clients, p.elements, p.interval, p.requestProcessTime, p.clientProcessTime, p.avgClientWaitingTime))
                        .collect(Collectors.joining("\n", "clients,elements,interval,requestProcessTime,clientProcessTime,responseTime\n", ""));
                outFile.write(file);
            } catch (IOException err) {
                JOptionPane.showMessageDialog(null, err.getMessage());
            }
        }));

        return saveButton;
    }

    private JPanel runBench() throws IOException {
        StringBuilder benchInfo = new StringBuilder();

        benchInfo.append("<html>");
        benchInfo.append("Architecture: ").append(selectedType.toString()).append("<br>");
        benchInfo.append("Request count: ").append(requestCount).append("<br>");


        Benchmark benchmark = new Benchmark(hostAddress, getPort(selectedType));
        benchmark.warmUp();
        List<Integer> range = range(changeRange.getLeft(), changeRange.getMiddle(), changeRange.getRight());

        List<Benchmark.Point> evaluate = null;
        switch (changeParameter) {
            case N:
                evaluate = benchmark.evaluate(requestCount, range, values(valueM, range.size()), values(valueDELTA, range.size()));
                benchInfo.append("Client count: ").append(valueM).append("<br>").append("Delta: ").append(valueDELTA).append("<br>");
                break;
            case M:
                evaluate = benchmark.evaluate(requestCount, values(valueN, range.size()), range, values(valueDELTA, range.size()));
                benchInfo.append("Array size: ").append(valueN).append("<br>").append("Delta: ").append(valueDELTA).append("<br>");
                break;
            case DELTA:
                evaluate = benchmark.evaluate(requestCount, values(valueN, range.size()), values(valueM, range.size()), range);
                benchInfo.append("Array size: ").append(valueN).append("<br>").append("Client count: ").append(valueM).append("<br>");
                break;
        }

        ChartPanel requestProcessTimeOnServerDataChart = createChart(
                "Request process time on server",
                range.stream().map(Double::new).collect(Collectors.toList()),
                evaluate.stream().map(Benchmark.Point::getRequestProcessTime).collect(Collectors.toList()));

        ChartPanel clientProcessTimeOnServerChart = createChart(
                "Client process time on server",
                range.stream().map(Double::new).collect(Collectors.toList()),
                evaluate.stream().map(Benchmark.Point::getClientProcessTime).collect(Collectors.toList()));

        ChartPanel clientAvgResponseTimeDataChart = createChart(
                "Server response time",
                range.stream().map(Double::new).collect(Collectors.toList()),
                evaluate.stream().map(Benchmark.Point::getAvgClientWaitingTime).collect(Collectors.toList()));

        JPanel combined = new JPanel(new GridLayout(2, 2));
        combined.setPreferredSize(new Dimension(800, 800));

        JLabel label = new JLabel(benchInfo.toString(), SwingConstants.CENTER);
        label.setFont(new Font("", Font.PLAIN, 20));

        JPanel infoPanel = new JPanel();
        infoPanel.add(label);
        infoPanel.add(createSaveResultsButton(evaluate));

        combined.add(infoPanel);
        combined.add(requestProcessTimeOnServerDataChart);
        combined.add(clientProcessTimeOnServerChart);
        combined.add(clientAvgResponseTimeDataChart);

        combined.setBorder(BorderFactory.createLineBorder(Color.black));

        return combined;
    }

    private <E extends Enum<E>> JPanel createEnumSelector(Class<E> cls, Supplier<E> getter, Consumer<E> setter) {
        JPanel radioPanel = new JPanel(new GridLayout(0, 1));
        ButtonGroup group = new ButtonGroup();

        Set<JRadioButton> radioButtons = EnumSet.allOf(cls).stream().map(enumField -> {
            JRadioButton aButton = new JRadioButton(enumField.name());
            aButton.setActionCommand(enumField.name());
            aButton.addActionListener(e -> runInUiThread(() -> setter.accept(enumField)));
            if (getter.get() == enumField) {
                aButton.setSelected(true);
            }
            return aButton;
        }).collect(Collectors.toSet());

        radioButtons.forEach(group::add);
        radioButtons.forEach(radioPanel::add);

        return radioPanel;
    }

    private void setDefaults() {
        hostAddress = DEFAULT_HOST;
        naiveBlockingPort = DEFAULT_NAIVE_BLOCKING_PORT;
        blockingPort = DEFAULT_BLOCKING_PORT;
        asyncPort = DEFAULT_ASYNC_PORT;
        nonBlockingPort = DEFAULT_NON_BLOCKING_PORT;

        selectedType = Server.ServerType.NON_BLOCKING;
        requestCount = 10;
        changeParameter = ChangeParameter.N;
        changeRange = Triple.of(100, 500, 200);
        valueN = 1000;
        valueM = 10;
        valueDELTA = 10;
    }

    private int getPort(Server.ServerType type) {
        switch (type) {
            case NAIVE_BLOCKING:
                return naiveBlockingPort;
            case BLOCKING:
                return blockingPort;
            case NON_BLOCKING:
                return nonBlockingPort;
            case ASYNC:
                return asyncPort;
        }
        return -1;
    }

    private JMenu createSettingsMenu() {
        JMenu settings = new JMenu("Settings");

        List<Triple<String, Consumer<String>, Supplier<Object>>> actions = Arrays.asList(
                Triple.of("Hostname", s -> {
                            if (s != null) {
                                hostAddress = s;
                            }
                        },
                        () -> hostAddress),
                Triple.of("Naive blocking server port", s -> {
                            try {
                                naiveBlockingPort = Integer.parseInt(s);
                            } catch (Throwable ignored) {
                            }
                        },
                        () -> naiveBlockingPort),
                Triple.of("Blocking server port", s -> {
                            try {
                                blockingPort = Integer.parseInt(s);
                            } catch (Throwable ignored) {
                            }
                        },
                        () -> blockingPort),
                Triple.of("Async server port", s -> {
                            try {
                                asyncPort = Integer.parseInt(s);
                            } catch (Throwable ignored) {
                            }
                        },
                        () -> asyncPort),
                Triple.of("Non blocking server port", s -> {
                            try {
                                nonBlockingPort = Integer.parseInt(s);
                            } catch (Throwable ignored) {
                            }
                        },
                        () -> nonBlockingPort));

        actions.forEach(params -> {
            JMenuItem setter = new JMenuItem(params.getLeft());
            setter.addActionListener(e -> {
                String input = JOptionPane.showInputDialog("Specify parameter", params.getRight().get());
                params.getMiddle().accept(input);
            });
            settings.add(setter);
        });

        return settings;
    }

    private void runInUiThread(Runnable task) {
        uiThreadPool.submit(task);
    }

    private enum ChangeParameter {
        N,
        M,
        DELTA
    }
}
