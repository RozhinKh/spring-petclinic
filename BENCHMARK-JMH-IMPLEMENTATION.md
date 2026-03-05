# JMH Benchmarks Implementation Guide

## Overview

This document describes the Java Microbenchmark Harness (JMH) benchmark implementation for PetClinic. The benchmarks measure startup time, latency, throughput, and memory footprint across three code variants:

- **Java 17 Baseline**: Spring Boot 4.0.1 compiled for Java 17
- **Java 21 Traditional**: Spring Boot 4.0.1 compiled for Java 21 with traditional threading
- **Java 21 Virtual Threads**: Spring Boot 4.0.1 compiled for Java 21 with virtual threads enabled

## Architecture

### Components

1. **StartupBenchmark** - Measures application startup time
2. **LatencyBenchmark** - Measures single-request latency with percentiles
3. **ThroughputBenchmark** - Measures requests per second throughput
4. **MemoryBenchmark** - Measures heap footprint and memory usage
5. **BenchmarkRunner** - Multi-variant orchestrator and result processor

### Data Flow

```
┌──────────────────────────────────┐
│   BenchmarkRunner.main()         │
│   (Multi-variant orchestrator)   │
└──────────────┬───────────────────┘
               │
               ├─→ Build all variants with Maven
               │
               └─→ For each variant:
                   ├─→ ApplicationStarter builds & starts application
                   ├─→ ApplicationStarter runs warm-up requests
                   ├─→ JMH executes benchmarks:
                   │   ├─→ StartupBenchmark (cold/warm startup)
                   │   ├─→ LatencyBenchmark (endpoint latencies)
                   │   ├─→ ThroughputBenchmark (req/sec)
                   │   └─→ MemoryBenchmark (heap footprint)
                   ├─→ JMH outputs JSON results (jmh-results.json)
                   └─→ BenchmarkRunner parses & stores results
                   
               └─→ Export unified JSON (benchmark-results.json)
                   └─→ Export CSV summary (benchmark-results.csv)
```

## Benchmark Details

### 1. StartupBenchmark

**Location**: `src/main/java/org/springframework/samples/petclinic/benchmark/StartupBenchmark.java`

**Purpose**: Measures application startup time from process launch to serving HTTP requests.

**Benchmarks**:
- `coldStartup`: Fresh JVM, no compilation cache
- `warmStartup`: Reuses JIT-compiled bytecode from previous run

**Configuration**:
- Fork count: 5 (independent JVM per run for isolation)
- Warmup iterations: 0 (measuring startup, not warmup)
- Measurement iterations: 5
- Output unit: Milliseconds
- Mode: SingleShotTime (measure only once per fork)

**Measurement Approach**:
- Records system time at JVM launch
- Polls `/actuator/health` endpoint until 200 response
- Calculates elapsed time from launch to health check success

### 2. LatencyBenchmark

**Location**: `src/main/java/org/springframework/samples/petclinic/benchmark/LatencyBenchmark.java`

**Purpose**: Measures single-request latency for key endpoints.

**Benchmarks**:
- `getOwners`: GET /owners (list owners)
- `getVets`: GET /vets (list vets)
- `getOwnerFind`: GET /owners/find (search form)
- `getOwnerById`: GET /owners/1 (retrieve specific owner)
- `postNewOwner`: POST /owners/new (create owner)

**Configuration**:
- Fork count: 5
- Warmup iterations: 10 (1 second each)
- Measurement iterations: 20 (1 second each)
- Output unit: Milliseconds
- Mode: AverageTime

**Measurement Approach**:
- Measures time from request start to response receipt
- Includes network round-trip time
- JMH automatically calculates percentiles (P50, P75, P90, P95, P99)

### 3. ThroughputBenchmark

**Location**: `src/main/java/org/springframework/samples/petclinic/benchmark/ThroughputBenchmark.java`

**Purpose**: Measures request throughput (requests per second).

**Benchmarks**:
- `getOwnersThroughput`: GET /owners throughput
- `getVetsThroughput`: GET /vets throughput
- `getOwnerByIdThroughput`: GET /owners/{id} throughput
- `mixedWorkloadThroughput`: Mixed GET /owners and GET /vets

**Configuration**:
- Fork count: 5
- Warmup iterations: 5 (2 seconds each)
- Measurement iterations: 10 (3 seconds each)
- Output unit: Requests per second
- Mode: Throughput

**Measurement Approach**:
- Runs as many requests as possible in measurement period
- JMH calculates requests per second automatically
- Measures steady-state performance after JIT compilation

### 4. MemoryBenchmark

**Location**: `src/main/java/org/springframework/samples/petclinic/benchmark/MemoryBenchmark.java`

**Purpose**: Measures heap memory usage under different conditions.

**Benchmarks**:
- `measureIdleHeap`: Heap at idle state (after GC)
- `measureHeapAfterLoad`: Heap footprint after 1000 sequential requests
- `measurePeakHeap`: Peak heap usage during 5-second load
- `measureEndpointHeapVariation`: Heap variation across different endpoints

**Configuration**:
- Fork count: 3 (less load on system for memory measurement)
- Warmup iterations: 1
- Measurement iterations: 3
- Output unit: Milliseconds (time for operations)
- Mode: SingleShotTime

**Measurement Approach**:
- Uses `ManagementFactory.getMemoryMXBean()` for heap monitoring
- Captures heap usage at key points (idle, during load, peak)
- Forces garbage collection before measurements for clean state
- Calculates average and peak heap consumption

## JSON Output Schema

### Unified Results File: benchmark-results.json

```json
{
  "timestamp": "2024-12-15T10:30:00Z",
  "variantCount": 3,
  "variants": [
    {
      "variant": "java17-baseline",
      "timestamp": "2024-12-15T10:30:00Z",
      "benchmarks": [
        {
          "name": "coldStartup",
          "benchmark_type": "startup",
          "full_name": "org.springframework.samples.petclinic.benchmark.StartupBenchmark.coldStartup",
          "mode": "SingleShotTime",
          "unit": "ms",
          "value": 2345.67,
          "score": 2345.67,
          "std_dev": 120.45,
          "min": 2100.23,
          "max": 2650.34,
          "variant": "java17-baseline"
        },
        {
          "name": "getOwners",
          "benchmark_type": "latency",
          "full_name": "org.springframework.samples.petclinic.benchmark.LatencyBenchmark.getOwners",
          "mode": "AverageTime",
          "unit": "ms",
          "value": 45.23,
          "score": 45.23,
          "std_dev": 5.12,
          "min": 38.90,
          "max": 67.45,
          "variant": "java17-baseline"
        },
        {
          "name": "getOwnersThroughput",
          "benchmark_type": "throughput",
          "full_name": "org.springframework.samples.petclinic.benchmark.ThroughputBenchmark.getOwnersThroughput",
          "mode": "Throughput",
          "unit": "ops/s",
          "value": 234.56,
          "score": 234.56,
          "std_dev": 15.23,
          "min": 210.23,
          "max": 256.78,
          "variant": "java17-baseline"
        },
        {
          "name": "measureIdleHeap",
          "benchmark_type": "memory",
          "full_name": "org.springframework.samples.petclinic.benchmark.MemoryBenchmark.measureIdleHeap",
          "mode": "SingleShotTime",
          "unit": "ms",
          "value": 456789000,
          "score": 456789000,
          "std_dev": 50000000,
          "min": 400000000,
          "max": 520000000,
          "variant": "java17-baseline"
        }
      ]
    },
    {
      "variant": "java21-traditional",
      "timestamp": "2024-12-15T10:45:00Z",
      "benchmarks": [...]
    },
    {
      "variant": "java21-virtual",
      "timestamp": "2024-12-15T11:00:00Z",
      "benchmarks": [...]
    }
  ]
}
```

### CSV Summary File: benchmark-results.csv

```csv
variant,benchmark_name,score,unit,timestamp
java17-baseline,coldStartup,2345.67,ms,2024-12-15T10:30:00Z
java17-baseline,warmStartup,1567.89,ms,2024-12-15T10:30:00Z
java17-baseline,getOwners,45.23,ms,2024-12-15T10:30:00Z
java17-baseline,getVets,52.45,ms,2024-12-15T10:30:00Z
java17-baseline,getOwnerFind,38.90,ms,2024-12-15T10:30:00Z
java17-baseline,getOwnerById,41.23,ms,2024-12-15T10:30:00Z
java17-baseline,postNewOwner,127.34,ms,2024-12-15T10:30:00Z
java17-baseline,getOwnersThroughput,234.56,ops/s,2024-12-15T10:30:00Z
java17-baseline,getVetsThroughput,198.34,ops/s,2024-12-15T10:30:00Z
java17-baseline,getOwnerByIdThroughput,256.78,ops/s,2024-12-15T10:30:00Z
java17-baseline,mixedWorkloadThroughput,215.67,ops/s,2024-12-15T10:30:00Z
java17-baseline,measureIdleHeap,456789000,bytes,2024-12-15T10:30:00Z
java17-baseline,measureHeapAfterLoad,512345000,bytes,2024-12-15T10:30:00Z
java17-baseline,measurePeakHeap,678901000,bytes,2024-12-15T10:30:00Z
java17-baseline,measureEndpointHeapVariation,567890000,bytes,2024-12-15T10:30:00Z
```

## Running the Benchmarks

### Option 1: Run All Variants

```bash
# Builds all variants and executes benchmarks
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner

# Output:
# - benchmark-results.json (unified results)
# - benchmark-results.csv (summary)
# - jmh-results.json (raw JMH output per variant)
```

### Option 2: Build Benchmarks JAR

```bash
# Build the shaded JAR for standalone execution
mvn clean package

# Produces: target/benchmarks.jar (executable JMH benchmarks)

# Run directly
java -jar target/benchmarks.jar
```

### Option 3: Run Individual Benchmarks

```bash
# Run only latency benchmarks
java -jar target/benchmarks.jar LatencyBenchmark

# Run specific variant
java -jar target/benchmarks.jar -p java.version=17 StartupBenchmark

# Run with custom fork count
java -jar target/benchmarks.jar -f 10 LatencyBenchmark
```

## JMH Configuration Parameters

### Fork Count
- **Purpose**: Run each benchmark in isolated JVM to prevent interference
- **Value**: 5-10 (higher = more stable but slower)
- **Tradeoff**: More forks = more accurate but takes longer

### Warmup Iterations
- **Purpose**: Allow JIT compilation to stabilize before measurement
- **Value**: 5-10 iterations
- **Duration**: 1-2 seconds per iteration
- **Effect**: Eliminates artificial startup overhead

### Measurement Iterations
- **Purpose**: Actual data collection after JIT stabilization
- **Value**: 10-20 iterations for stable statistics
- **Duration**: 1-3 seconds per iteration
- **Effect**: Sufficient data for percentile calculation

### Measurement Time
- **Purpose**: Duration of each measurement iteration
- **Value**: 1-3 seconds (longer = more stable)
- **Effect**: More requests per iteration = better percentile accuracy

## Expected Results

### Startup Time
```
Java 17 Baseline:     ~2.3 seconds (cold start)
Java 21 Traditional:  ~2.2 seconds (cold start) - ~5% improvement
Java 21 Virtual:      ~2.1 seconds (cold start) - ~10% improvement
```

### Single Request Latency (GET /owners)
```
Java 17 Baseline:     ~45ms (P95: 80ms)
Java 21 Traditional:  ~42ms (P95: 75ms)
Java 21 Virtual:      ~40ms (P95: 70ms)
```

### Throughput (requests/second)
```
Java 17 Baseline:     ~235 req/s
Java 21 Traditional:  ~250 req/s (+6%)
Java 21 Virtual:      ~280 req/s (+19%)
```

### Heap Memory (idle)
```
Java 17 Baseline:     ~450 MB
Java 21 Traditional:  ~460 MB
Java 21 Virtual:      ~440 MB (more efficient)
```

## Files Modified

1. **pom.xml**
   - Added JMH dependency (jmh-core, jmh-generator-annprocess)
   - Added maven-shade-plugin for creating benchmarks JAR
   - Added maven-compiler-plugin with JMH annotation processor

2. **Created Benchmark Classes**
   - `src/main/java/org/springframework/samples/petclinic/benchmark/StartupBenchmark.java`
   - `src/main/java/org/springframework/samples/petclinic/benchmark/LatencyBenchmark.java`
   - `src/main/java/org/springframework/samples/petclinic/benchmark/ThroughputBenchmark.java`
   - `src/main/java/org/springframework/samples/petclinic/benchmark/MemoryBenchmark.java`

3. **Created Orchestrator**
   - `src/main/java/org/springframework/samples/petclinic/benchmark/BenchmarkRunner.java`

## Integration with Multi-Version Execution

The BenchmarkRunner integrates with the existing load-test harness:

1. **ApplicationStarter** (existing): Manages application lifecycle
   - Builds variant with Maven
   - Starts application on specified port
   - Verifies health check
   - Runs warm-up requests
   - Gracefully shuts down

2. **BenchmarkRunner** (new): Orchestrates multi-variant execution
   - Iterates through all variants
   - Builds each variant once
   - Starts application for each variant
   - Executes JMH benchmarks against running application
   - Collects and aggregates results
   - Exports unified JSON/CSV output

## Variance and Accuracy

### Acceptable Variance (< 10% of mean)

The benchmarks are configured to achieve <10% coefficient of variation (std_dev/mean):

- **Latency**: Multiple iterations + warmup eliminates GC variance
- **Throughput**: Long measurement periods (3s) smooth out transients
- **Startup**: 5 forks in isolated JVMs prevents interference
- **Memory**: Multiple samples across load profile

### JIT Compilation Stabilization

JIT compilation variance is minimized through:
- 10+ warmup iterations allow C1 and C2 compilation
- Measurement iterations run after warmup completes
- Latency benchmarks warm each endpoint specifically
- Throughput benchmarks run steady-state

## Troubleshooting

### Application Won't Start During Benchmark

**Symptom**: "Application failed to start within timeout"

**Causes**:
- Port already in use (change in ApplicationStarter)
- Insufficient heap memory (reduce -Xmx)
- Database connection issues (verify H2/MySQL/PostgreSQL)

**Fix**:
```java
// In BenchmarkRunner.executeVariantBenchmarks()
starter = new ApplicationStarter(variant, "8080", ..., "30", "60", ...);
// Increase timeouts if needed: 30s health check, 60s startup
```

### JMH Benchmark Timeout

**Symptom**: "JMH benchmark timeout"

**Causes**:
- Application is slow/unresponsive
- Network connectivity issues
- Insufficient system resources

**Fix**: Increase timeout in `runJmhBenchmarks()`:
```java
if (!process.waitFor(90, TimeUnit.MINUTES)) { // Was 60
```

### Memory Results Show Negative Values

**Symptom**: Heap measurements are very large or negative

**Causes**:
- GC not completing before measurement
- Heap not being freed properly

**Fix**: Ensure proper GC in MemoryBenchmark:
```java
System.gc();
Thread.sleep(1000); // Increase from 500ms
```

## Performance Analysis

### Interpreting Results

1. **Startup Time**: Measure time to application ready
   - Lower is better (faster startup = better user experience)
   - Virtual threads may improve if thread pool contention exists

2. **Latency**: P95/P99 more important than average
   - User perception driven by slow requests
   - Target: <100ms P95 for web requests

3. **Throughput**: Requests per second under steady load
   - Virtual threads benefit from high concurrency
   - Baseline may be sufficient for current load

4. **Memory**: Heap footprint and growth
   - Virtual threads: lower memory per thread
   - Traditional: higher memory but familiar behavior

### Comparing Variants

Use the CSV output to compare:

```bash
# Extract latency for specific endpoint
grep "getOwners," benchmark-results.csv | cut -d, -f1,2,3

# Compare cold startup across variants
grep "coldStartup" benchmark-results.csv

# Calculate improvement
# java21-virtual latency = 40ms
# java17-baseline latency = 45ms
# Improvement = (45-40)/45 = 11.1%
```

## Next Steps

1. **JFR Integration** (Task 10)
   - Collect JFR events during benchmark runs
   - Correlate JFR with benchmark results
   - Identify hotspots and contentions

2. **Load Test Integration** (Task 11)
   - Use benchmark results as baseline
   - Run sustained load tests
   - Measure long-term stability

3. **Continuous Benchmarking**
   - Integrate with CI/CD pipeline
   - Track performance over time
   - Alert on regressions
