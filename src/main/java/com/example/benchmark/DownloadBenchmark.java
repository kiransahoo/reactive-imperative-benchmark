package com.example.benchmark;

import com.sun.management.OperatingSystemMXBean;
import org.openjdk.jmh.annotations.*;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 2, time = 5)
@Fork(1)
@Threads(3)  // or @Threads(5) to highlight the difference
@State(Scope.Benchmark)
public class DownloadBenchmark {

    private static final String SLOW_URL = "http://localhost:8080/slow";

    private BlockingDownloadService blockingService;
    private ReactiveDownloadService reactiveService;

    // OS/JVM beans for approximate resource usage logging
    private OperatingSystemMXBean osBean;
    private MemoryMXBean memoryBean;
    private ThreadMXBean threadBean;

    @Setup(Level.Trial)
    public void setup() {
        this.blockingService = new BlockingDownloadService();
        this.reactiveService = new ReactiveDownloadService();

        osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        memoryBean = ManagementFactory.getMemoryMXBean();
        threadBean = ManagementFactory.getThreadMXBean();
    }

    @Setup(Level.Iteration)
    public void beforeIteration() {
        printSystemUsage("Before iteration");
    }

    @TearDown(Level.Iteration)
    public void afterIteration() {
        printSystemUsage("After iteration");
        System.out.println("------------------------------------");
    }

    /**
     * 1) Do a blocking call to the slow server (2s delay)
     * 2) Then do some CPU-bound work (like summing squares up to N).
     */
    @Benchmark
    public void blockingDownload() {
        // Slow I/O (blocking)
        blockingService.callSlowEndpoint(SLOW_URL);

        // CPU-bound work
        long sum = doCpuWork(500000); // e.g. sum squares
        // blackhole usage if needed, or you can store sum to a field
    }

    /**
     * 1) Fire a non-blocking call to the slow server
     * 2) In parallel, do CPU-bound work while I/O is in-flight
     *    (We can simulate it with a quick approach).
     */
    @Benchmark
    public void reactiveDownload() {
        // Kick off the slow request (non-blocking)
        Mono<String> slowCall = reactiveService.callSlowEndpoint(SLOW_URL);

        // Meanwhile, do CPU work right away
        long sum = doCpuWork(500000);

        // Then block on the slow response *after* CPU is done
        // (Alternatively, you can do a zip or parallel. This is just a demonstration.)
        slowCall.block();
    }

    /**
     * Summation of squares to simulate CPU-bound work.
     * The bigger N, the more CPU time we spend.
     */
    private long doCpuWork(int n) {
        long sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += (long) i * i;
        }
        return sum;
    }

    // Logging approximate usage
    private void printSystemUsage(String label) {
        double cpuLoad = osBean.getProcessCpuLoad() * 100.0;
        long used = memoryBean.getHeapMemoryUsage().getUsed();
        long max = memoryBean.getHeapMemoryUsage().getMax();
        int threads = threadBean.getThreadCount();

        System.out.printf("%s: CPU=%.2f%%, UsedHeap=%d MB, MaxHeap=%d MB, Threads=%d%n",
                label, cpuLoad, toMB(used), toMB(max), threads);
    }

    private long toMB(long bytes) {
        return bytes / (1024 * 1024);
    }
}
