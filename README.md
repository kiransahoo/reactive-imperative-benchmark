# Blocking vs Reactive Performance Benchmark

This project demonstrates the performance difference between blocking and reactive approaches in handling I/O-bound operations with limited threads. It simulates a real-world scenario where each task requires:
- Two HTTP calls (2 seconds each)
- CPU-intensive computation between calls

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── example/
│   │           ├── benchmark/
│   │           │   ├── MixedWorkBenchmark.java    # Main benchmark code
│   │           │   ├── BlockingDownloadService.java
│   │           │   └── ReactiveDownloadService.java
│   │           └── slowserver/
│   │               └── SlowServerApplication.java  # Test server
└── pom.xml
```

## Prerequisites

- Java 17+
- Maven 3.6+
- Free port 8080 for the test server

## Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/io-benchmark.git
cd io-benchmark
```

2. Build the project:
```bash
mvn clean package
```

## Running the Benchmark

1. First, start the test server:
```bash
mvn spring-boot:run -pl slow-server
```

2. In a new terminal, run the benchmark:
```bash
java -jar target/benchmarks.jar MixedWorkBenchmark
```

## Understanding the Results

The benchmark shows a side-by-side comparison:

```
----------------------------------------------------------------------------------------------------
Metric                    Blocking             Reactive             Difference          
----------------------------------------------------------------------------------------------------
Total Duration:           17010 ms             10742 ms             -6268 ms (-36.8%)
Avg Time per Task:        1417.5               895.2                -522.3 ms (-36.8%)
Tasks Completed:          12                   Target: 12
----------------------------------------------------------------------------------------------------
```

### Key Metrics

- **Total Duration**: Total time to complete all tasks
- **Avg Time per Task**: Average time per task (Total Duration / Number of Tasks)
- **Tasks Completed**: Number of tasks successfully processed

### Understanding the Scenarios

1. **Blocking Scenario**:
   - Uses a fixed thread pool (3 threads)
   - Each thread handles one task at a time
   - Thread remains blocked during I/O operations

2. **Reactive Scenario**:
   - Uses the same number of threads
   - Non-blocking I/O operations
   - Threads can handle other tasks during I/O waits

### Performance Analysis

- **Theoretical Minimum**: 4000ms (2 I/O operations × 2000ms)
- **Blocking Overhead**: How much slower than theoretical minimum
- **Reactive Overhead**: How much slower than theoretical minimum
- **Overall Improvement**: Percentage improvement of reactive over blocking

## Configuration

You can modify these parameters in `MixedWorkBenchmark.java`:

```java
private static final int NUM_TASKS = 12;           // Number of tasks to process
private static final int THREAD_POOL_SIZE = 3;     // Size of thread pool
private static final String SLOW_URL = "http://localhost:8080/slow";
```

## Troubleshooting

1. **Server Connection Issues**:
   - Ensure the server is running on port 8080
   - Check server logs for any errors

2. **Benchmark Timing Out**:
   - Increase timeout in `reactiveScenario` method
   - Check for system resource constraints

3. **Inconsistent Results**:
   - Run benchmark multiple times
   - Ensure no other heavy processes are running

## Dependencies

- Spring WebFlux
- Project Reactor
- JMH (Java Microbenchmark Harness)
- Spring Boot (for test server)

## Contributing

Feel free to submit issues and enhancement requests!