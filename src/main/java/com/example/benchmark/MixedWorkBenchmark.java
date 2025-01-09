package com.example.benchmark;

import org.openjdk.jmh.annotations.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark comparing blocking vs reactive approaches for mixed I/O and CPU workloads.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(1)
public class MixedWorkBenchmark {
    private static final int NUM_TASKS = 12;
    private static final int THREAD_POOL_SIZE = 3;
    private static final String SLOW_URL = "http://localhost:8080/slow";

    private WebClient webClient;
    private RestTemplate restTemplate;
    private ExecutorService executorService;
    private AtomicInteger activeOperations;
    private AtomicInteger completedTasks;
    private Instant benchmarkStartTime;
    private static boolean finalSummaryPrinted = false;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";

    private static class BenchmarkResults {
        final String name;
        final long duration;
        final int tasksCompleted;

        BenchmarkResults(String name, long duration, int tasksCompleted) {
            this.name = name;
            this.duration = duration;
            this.tasksCompleted = tasksCompleted;
        }
    }

    private static final ConcurrentHashMap<String, BenchmarkResults> results = new ConcurrentHashMap<>();

    @Setup
    public void setup() {
        webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        restTemplate = new RestTemplate();

        executorService = new ThreadPoolExecutor(
                THREAD_POOL_SIZE,
                THREAD_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(NUM_TASKS),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger();
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("worker-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                }
        );

        activeOperations = new AtomicInteger(0);
        completedTasks = new AtomicInteger(0);

        printHeader("BENCHMARK CONFIGURATION");
    }

    private void recordResult(String name, long duration, int tasksCompleted) {
        results.put(name, new BenchmarkResults(name, duration, tasksCompleted));

        // Print the final comparison only after both scenarios are complete
        BenchmarkResults blockingResult = results.get("Blocking");
        BenchmarkResults reactiveResult = results.get("Reactive");

        if (blockingResult != null && reactiveResult != null) {
            String dashes = "-".repeat(100);

            // Print the metrics table
            System.out.println(dashes);
            System.out.printf("%-25s %-20s %-20s %-20s%n",
                    "Metric", "Blocking", "Reactive", "Difference");
            System.out.println(dashes);

            // Total Duration
            System.out.printf("%-25s %-20s %-20s %s%n",
                    "Total Duration:",
                    blockingResult.duration + " ms",
                    reactiveResult.duration + " ms",
                    String.format("%+d ms (%+.1f%%)",
                            blockingResult.duration - reactiveResult.duration,
                            ((blockingResult.duration - reactiveResult.duration) * 100.0 / blockingResult.duration)));

            // Average per task
            double blockingAvg = blockingResult.duration / (double)NUM_TASKS;
            double reactiveAvg = reactiveResult.duration / (double)NUM_TASKS;
            System.out.printf("%-25s %-20.1f %-20.1f %s%n",
                    "Avg Time per Task:",
                    blockingAvg,
                    reactiveAvg,
                    String.format("%+.1f ms (%+.1f%%)",
                            blockingAvg - reactiveAvg,
                            ((blockingAvg - reactiveAvg) * 100.0 / blockingAvg)));

            // Tasks Completed
            System.out.printf("%-25s %-20d %s%n",
                    "Tasks Completed:",
                    NUM_TASKS,
                    "Target: " + NUM_TASKS);

            System.out.println(dashes);
        }
    }
    private void printHeader(String title) {
        String border = "=".repeat(100);
        System.out.println("\n" + border);
        System.out.println(title);
        System.out.println(border);
        System.out.printf("Thread Pool Size: %d threads%n", THREAD_POOL_SIZE);
        System.out.printf("Number of Tasks: %d tasks%n", NUM_TASKS);
        System.out.printf("Operations per Task: 2 I/O (2s each) + CPU work%n");
        System.out.println(border + "\n");
    }

    private void logOperation(String taskId, String operation, String details) {
        long elapsedMs = Duration.between(benchmarkStartTime, Instant.now()).toMillis();
        String timestamp = String.format("%6d ms", elapsedMs);
        String status = String.format("Active: %2d, Completed: %2d",
                activeOperations.get(), completedTasks.get());

        String color = switch (operation) {
            case "START" -> ANSI_BLUE;
            case "COMPLETE" -> ANSI_GREEN;
            case "CPU" -> ANSI_CYAN;
            default -> ANSI_YELLOW;
        };

        System.out.printf("%s[%s] %-12s %-20s %-8s %s%s%n",
                color, timestamp, taskId, operation, details, status, ANSI_RESET);
    }

    private String formatDifference(BenchmarkResults blocking, BenchmarkResults reactive,
                                    java.util.function.Function<BenchmarkResults, Double> extractor,
                                    String unit) {
        if (blocking == null || reactive == null) return "N/A";
        double diff = extractor.apply(blocking) - extractor.apply(reactive);
        double percentage = (diff / extractor.apply(blocking)) * 100;
        return String.format("%+.1f %s (%+.1f%%)", diff, unit, percentage);
    }

    private void printBenchmarkSummary(String currentScenario) {
        BenchmarkResults currentResult = results.get(currentScenario);
        if (currentResult == null) return;

        String border = "=".repeat(100);
        String separator = "-".repeat(100);

        // Always print individual scenario results
        System.out.println("\n" + border);
        System.out.printf("BENCHMARK RESULTS - %s Scenario%n", currentScenario.toUpperCase());
        System.out.println(border);

        // Print configuration
        System.out.println("Configuration:");
        System.out.printf("Thread Pool Size:    %d threads%n", THREAD_POOL_SIZE);
        System.out.printf("Total Tasks:         %d tasks%n", NUM_TASKS);
        System.out.printf("I/O Operations:      2 per task (2s each)%n");
        System.out.printf("CPU Operations:      Matrix multiplication%n");
        System.out.println(separator);

        // Print current scenario metrics
        System.out.printf("Total Duration:      %d ms%n", currentResult.duration);
        System.out.printf("Avg Time per Task:   %.1f ms%n",
                (double) currentResult.duration / NUM_TASKS);
        System.out.printf("Performance Ratio:   %.2fx theoretical minimum%n",
                (double) currentResult.duration / 4000);
        System.out.println(border + "\n");

        // Only print comparison when both scenarios are complete
        BenchmarkResults blockingResult = results.get("Blocking");
        BenchmarkResults reactiveResult = results.get("Reactive");

        if (blockingResult != null && reactiveResult != null && !finalSummaryPrinted) {
            finalSummaryPrinted = true;
            printFinalComparison(blockingResult, reactiveResult);
        }
    }

    private Mono<Void> simulateCpuWork(String taskId) {
        return Mono.fromRunnable(() -> {
            logOperation(taskId, "CPU", "Started");
            int size = 100;
            double[][] result = new double[size][size];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    double sum = 0;
                    for (int k = 0; k < size; k++) {
                        sum += Math.random() * Math.random();
                    }
                    result[i][j] = sum;
                }
            }
            logOperation(taskId, "CPU", "Completed");
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Benchmark
    public List<String> blockingScenario() throws InterruptedException {
        benchmarkStartTime = Instant.now();
        printHeader("BLOCKING SCENARIO START");

        CountDownLatch latch = new CountDownLatch(NUM_TASKS);
        List<String> taskResults = new CopyOnWriteArrayList<>();

        for (int i = 0; i < NUM_TASKS; i++) {
            final String taskId = String.format("Block-%02d", i);
            executorService.submit(() -> {
                try {
                    activeOperations.incrementAndGet();
                    logOperation(taskId, "START", "Task");

                    logOperation(taskId, "I/O-1", "Started");
                    String result1 = restTemplate.getForObject(SLOW_URL, String.class);
                    logOperation(taskId, "I/O-1", "Completed");

                    simulateCpuWork(taskId).block();

                    logOperation(taskId, "I/O-2", "Started");
                    String result2 = restTemplate.getForObject(SLOW_URL, String.class);
                    logOperation(taskId, "I/O-2", "Completed");

                    taskResults.add(result1 + result2);
                    activeOperations.decrementAndGet();
                    completedTasks.incrementAndGet();
                    logOperation(taskId, "COMPLETE", "Task");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long duration = Duration.between(benchmarkStartTime, Instant.now()).toMillis();
        recordResult("Blocking", duration, completedTasks.get());
        printBenchmarkSummary("Blocking");
        return taskResults;
    }

    @Benchmark
    public List<String> reactiveScenario() {
        benchmarkStartTime = Instant.now();
        printHeader("REACTIVE SCENARIO START");

        List<String> taskResults = Flux.range(0, NUM_TASKS)
                .map(i -> String.format("React-%02d", i))
                .flatMap(taskId -> {
                    activeOperations.incrementAndGet();
                    logOperation(taskId, "START", "Task");

                    return webClient.get()
                            .uri(SLOW_URL)
                            .retrieve()
                            .bodyToMono(String.class)
                            .doOnSuccess(r -> logOperation(taskId, "I/O-1", "Completed"))
                            .flatMap(result1 ->
                                    simulateCpuWork(taskId)
                                            .then(Mono.just(result1))
                            )
                            .flatMap(result1 ->
                                    webClient.get()
                                            .uri(SLOW_URL)
                                            .retrieve()
                                            .bodyToMono(String.class)
                                            .doOnSuccess(r -> logOperation(taskId, "I/O-2", "Completed"))
                                            .map(result2 -> {
                                                activeOperations.decrementAndGet();
                                                completedTasks.incrementAndGet();
                                                logOperation(taskId, "COMPLETE", "Task");
                                                return result1 + result2;
                                            })
                            );
                }, 6)
                .collectList()
                .block(Duration.ofSeconds(60));

        long duration = Duration.between(benchmarkStartTime, Instant.now()).toMillis();
        recordResult("Reactive", duration, completedTasks.get());
        printBenchmarkSummary("Reactive");
        return taskResults;
    }

    private void printFinalComparison() {
        BenchmarkResults blockingResult = results.get("Blocking");
        BenchmarkResults reactiveResult = results.get("Reactive");

        if (blockingResult == null || reactiveResult == null) return;

        String dashes = "-".repeat(100);

        // Print the metrics table
        System.out.println(dashes);
        System.out.printf("%-25s %-20s %-20s %-20s%n",
                "Metric", "Blocking", "Reactive", "Difference");
        System.out.println(dashes);

        // Total Duration
        long blockingDuration = blockingResult.duration;
        long reactiveDuration = reactiveResult.duration;
        System.out.printf("%-25s %-20s %-20s %s%n",
                "Total Duration:",
                blockingDuration + " ms",
                reactiveDuration + " ms",
                String.format("%+d ms (%+.1f%%)",
                        blockingDuration - reactiveDuration,
                        ((blockingDuration - reactiveDuration) * 100.0 / blockingDuration)));

        // Average per task
        double blockingAvg = blockingDuration / (double)NUM_TASKS;
        double reactiveAvg = reactiveDuration / (double)NUM_TASKS;
        System.out.printf("%-25s %-20.1f %-20.1f %s%n",
                "Avg Time per Task:",
                blockingAvg,
                reactiveAvg,
                String.format("%+.1f ms (%+.1f%%)",
                        blockingAvg - reactiveAvg,
                        ((blockingAvg - reactiveAvg) * 100.0 / blockingAvg)));

        // Tasks Completed
        System.out.printf("%-25s %-20d %s%n",
                "Tasks Completed:",
                NUM_TASKS,
                "Target: " + NUM_TASKS);

        // Theoretical Analysis
        System.out.println(dashes);
        System.out.println();
        System.out.println("Theoretical Analysis:");
        System.out.printf("Minimum possible time:     4000ms (2 I/O operations * 2000ms)%n");
        System.out.printf("Blocking overhead:         %.1f%% above minimum%n",
                ((double)blockingDuration/4000 - 1) * 100);
        System.out.printf("Reactive overhead:         %.1f%% above minimum%n",
                ((double)reactiveDuration/4000 - 1) * 100);
        System.out.printf("Overall improvement:       %.1f%%%n",
                ((double)(blockingDuration - reactiveDuration) / blockingDuration * 100));
        System.out.println(dashes);
    }


    private void printFinalComparison(BenchmarkResults blocking, BenchmarkResults reactive) {
        String dashes = "-".repeat(100);

        // Print the header
        System.out.println(dashes);
        System.out.printf("%-25s %-20s %-20s %-20s%n",
                "Metric", "Blocking", "Reactive", "Difference");
        System.out.println(dashes);

        // Total Duration
        System.out.printf("%-25s %-20s %-20s %-20s%n",
                "Total Duration:",
                blocking.duration + " ms",
                reactive.duration + " ms",
                formatDifference(blocking.duration, reactive.duration, "ms"));

        // Average Time per Task
        double blockingAvg = (double) blocking.duration / NUM_TASKS;
        double reactiveAvg = (double) reactive.duration / NUM_TASKS;
        System.out.printf("%-25s %-20.1f %-20.1f %-20s%n",
                "Avg Time per Task:",
                blockingAvg,
                reactiveAvg,
                formatDifference(blockingAvg, reactiveAvg, "ms"));

        // Tasks Completed
        System.out.printf("%-25s %-20d %-20s%n",
                "Tasks Completed:",
                NUM_TASKS,
                "Target: " + NUM_TASKS);

        System.out.println(dashes);

        // Theoretical Analysis
        System.out.println("\nTheoretical Analysis:");
        System.out.printf("Minimum Time:           4000ms (2 I/O operations * 2000ms)%n");
        System.out.printf("Blocking Overhead:      %.1f%% above minimum%n",
                ((double)blocking.duration/4000 - 1) * 100);
        System.out.printf("Reactive Overhead:      %.1f%% above minimum%n",
                ((double)reactive.duration/4000 - 1) * 100);
        System.out.printf("Overall Improvement:    %.1f%%%n",
                ((double)(blocking.duration - reactive.duration) / blocking.duration * 100));
    }


    private String formatDifference(double blocking, double reactive, String unit) {
        double diff = blocking - reactive;
        double percentage = (diff / blocking) * 100;
        if (unit.equals("ms")) {
            return String.format("%+.0f ms (%+.1f%%)", diff, percentage);
        } else {
            return String.format("%+.2f%s (%+.1f%%)", diff, unit, percentage);
        }
    }

    @TearDown
    public void tearDown() {
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("Thread pool did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}