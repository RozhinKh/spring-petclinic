# Java Flight Recorder (JFR) Integration with JMH Benchmarks

## Overview

This document describes the integration of Java Flight Recorder (JFR) event collection with JMH benchmarks. JFR captures low-level runtime metrics (GC, threading, memory allocation, blocking events) that are correlated with high-level benchmark measurements to provide deep insights into performance characteristics.

## Architecture

### Components

#### 1. **JFRHarness** (`src/main/java/org/springframework/samples/petclinic/benchmark/JFRHarness.java`)

Manages the lifecycle of JFR recordings:

- **startRecording()**: Initiates JFR recording with configured event types before benchmark execution
- **stopRecording()**: Stops recording and saves the binary JFR file to disk
- **Configuration**: Enables specific event types for GC, threading, memory, and blocking analysis
- **Timing tracking**: Records precise start/stop times in both milliseconds and nanoseconds for correlation

**Key Features:**
```java
- Records start/stop times in multiple time units (ms, ns)
- Saves JFR files to `jfr-recordings/` directory
- Configures 10+ event types for comprehensive monitoring
- Returns recording metadata for correlation analysis
```

**Events Configured:**
- `jdk.GarbageCollection`, `jdk.GCPauseLevel` - GC pause tracking
- `jdk.GCHeapSummary`, `jdk.GCHeapMemoryUsage` - Heap memory state
- `jdk.ThreadStart`, `jdk.ThreadEnd`, `jdk.ThreadSleep`, `jdk.ThreadPark` - Thread lifecycle
- `jdk.JavaMonitorEnter`, `jdk.JavaMonitorWait` - Lock contention
- `jdk.ObjectAllocationInNewTLAB`, `jdk.ObjectAllocationOutsideTLAB` - Memory allocation
- `jdk.ExecutionSample` - Call stack sampling (10ms period)

#### 2. **JFREventParser** (`src/main/java/org/springframework/samples/petclinic/benchmark/JFREventParser.java`)

Parses JFR binary files and extracts metrics:

- **parseJFRFile()**: Main entry point, orchestrates extraction of all metric categories
- **extractGCMetrics()**: Collects GC pause counts, durations, types, and timing
- **extractThreadMetrics()**: Tracks thread creation/destruction, park events, net thread count
- **extractMemoryMetrics()**: Measures allocation counts, bytes allocated, TLAB vs off-heap ratios
- **extractBlockingMetrics()**: Records monitor enters, waits, durations, and contention patterns

**Output Format:**
```json
{
  "gc_metrics": {
    "pause_count": 5,
    "total_pause_duration_ms": 120,
    "avg_pause_duration_ms": 24.0,
    "min_pause_duration_ms": 15,
    "max_pause_duration_ms": 35,
    "gc_type_counts": {"G1 Old Generation": 2, "G1 Young Generation": 3},
    "pause_details": [
      {"gc_type": "G1 Young Generation", "duration_ms": 20, "timestamp": "2024-01-15T10:30:45Z", "event_time_nanos": 1234567890000}
    ]
  },
  "thread_metrics": {
    "thread_start_count": 45,
    "thread_end_count": 40,
    "thread_park_count": 120,
    "net_thread_creation": 5
  },
  "memory_metrics": {
    "tlab_allocations": 50000,
    "outside_tlab_allocations": 500,
    "total_allocation_bytes": 104857600,
    "total_allocation_mb": 100.0
  },
  "blocking_metrics": {
    "monitor_enter_count": 300,
    "monitor_wait_count": 25,
    "total_wait_duration_ms": 150,
    "avg_wait_duration_ms": 6.0,
    "wait_details": [
      {"wait_duration_ms": 5, "timeout_ms": 10000, "timestamp": "2024-01-15T10:30:50Z"}
    ]
  },
  "total_events_processed": 15234
}
```

#### 3. **JFRCorrelator** (`src/main/java/org/springframework/samples/petclinic/benchmark/JFRCorrelator.java`)

Correlates JFR metrics with JMH benchmark execution windows:

- **correlate()**: Main analysis orchestrator
- **analyzeTimingAlignment()**: Calculates overlap between JFR recording and benchmark execution
- **analyzeGcLatencyCorrelation()**: Identifies GC pauses during benchmark and estimates impact
- **analyzeThreadMetrics()**: Analyzes thread pool saturation during benchmark
- **analyzeMemoryPressure()**: Computes memory allocation pressure and TLAB contention
- **analyzeBlockingImpact()**: Detects lock contention frequency and severity

**Output Format:**
```json
{
  "timing": {
    "benchmark_start_ms": 1700000000000,
    "benchmark_duration_ms": 60000,
    "jfr_recording_duration_ms": 65000,
    "overlap_duration_ms": 60000,
    "overlap_percentage": 100.0
  },
  "gc_latency_correlation": {
    "gc_pauses_during_benchmark": 5,
    "total_gc_time_during_benchmark_ms": 120,
    "avg_gc_pause_during_benchmark_ms": 24.0,
    "gc_frequency_per_second": 5.0,
    "estimated_gc_impact_level": "moderate",
    "pauses_details": [
      {"gc_type": "G1 Young Generation", "duration_ms": 20, "offset_from_benchmark_start_ms": 15000}
    ]
  },
  "thread_correlation": {
    "thread_start_count": 45,
    "net_thread_creation": 5,
    "estimated_thread_pool_saturation": "low"
  },
  "memory_pressure_correlation": {
    "allocation_rate_mb_per_sec": 1.67,
    "outside_tlab_ratio": 0.01,
    "estimated_memory_pressure": "low"
  },
  "blocking_correlation": {
    "monitor_wait_count": 25,
    "blocking_frequency_per_second": 0.42,
    "estimated_contention_level": "low"
  },
  "summary": {
    "potential_bottlenecks": [],
    "overall_assessment": "No significant bottlenecks detected"
  }
}
```

#### 4. **BenchmarkRunner Integration** (`src/main/java/org/springframework/samples/petclinic/benchmark/BenchmarkRunner.java`)

Modified to orchestrate JFR collection around benchmark execution:

1. Starts application and warm-up
2. **Starts JFR recording** (`jfrHarness.startRecording()`)
3. Records benchmark start time
4. Executes JMH benchmarks via `runJmhBenchmarks()`
5. **Stops JFR recording** (`jfrHarness.stopRecording()`)
6. **Parses JFR metrics** (`JFREventParser.parseJFRFile()`)
7. **Correlates with benchmarks** (`JFRCorrelator.correlate()`)
8. Merges results into unified JSON output
9. Exports combined results to `benchmark-results.json`

**Execution Flow:**
```
[Start Application] → [Warm-up] → [START JFR] → [RUN JMH] → [STOP JFR] → [Parse JFR] → [Correlate] → [Export]
```

## JSON Output Schema

The combined output in `benchmark-results.json` includes both JMH and JFR results:

```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "variantCount": 3,
  "variants": [
    {
      "variant": "java17-baseline",
      "timestamp": "2024-01-15T10:35:00Z",
      "benchmarks": [
        {
          "name": "getOwners",
          "benchmark_type": "latency",
          "mode": "avgt",
          "unit": "ms",
          "score": 45.32,
          "std_dev": 3.2,
          "min": 42.1,
          "max": 52.5
        }
      ],
      "jfr_metrics": {
        "gc_metrics": { ... },
        "thread_metrics": { ... },
        "memory_metrics": { ... },
        "blocking_metrics": { ... },
        "total_events_processed": 15234
      },
      "correlation_analysis": {
        "timing": { ... },
        "gc_latency_correlation": { ... },
        "thread_correlation": { ... },
        "memory_pressure_correlation": { ... },
        "blocking_correlation": { ... },
        "summary": { ... }
      }
    }
  ]
}
```

## Impact Assessment Levels

### GC Impact
- **None**: No GC pauses during benchmark (gc_count = 0)
- **Low**: < 2 GC pauses per second
- **Moderate**: 2-5 GC pauses per second
- **High**: > 5 GC pauses per second

### Thread Pool Saturation
- **Low**: Net thread creation < 50
- **Moderate**: Net thread creation 50-100
- **High**: Net thread creation > 100

### Memory Pressure
- **Low**: Off-TLAB allocation ratio < 10%
- **Moderate**: Off-TLAB allocation ratio 10-30%
- **High**: Off-TLAB allocation ratio > 30%

### Lock Contention
- **None**: No monitor waits (wait_count = 0)
- **Low**: < 10 waits per second
- **Moderate**: 10-100 waits per second
- **High**: > 100 waits per second

## Key Metrics for Analysis

### GC Metrics
- `pause_count`: Total number of GC pauses during recording
- `total_pause_duration_ms`: Cumulative GC pause time
- `avg_pause_duration_ms`: Average pause duration (useful for latency impact)
- `gc_type_counts`: Breakdown by GC type (Young, Old, Full, etc.)
- `pause_details`: Individual pause timestamps and durations for spike correlation

**Use Case**: Identify if latency spikes in JMH results correspond to GC pauses. High correlation indicates GC is a performance bottleneck.

### Thread Metrics
- `thread_start_count`: Total threads created during benchmark
- `thread_end_count`: Total threads destroyed during benchmark
- `net_thread_creation`: Net increase in active threads
- `thread_park_count`: Virtual thread park operations (Java 21 virtual threads)

**Use Case**: For Java 21 virtual threads, verify that thread_park_count is higher than platform threads, indicating virtual thread scheduling efficiency.

### Memory Metrics
- `tlab_allocations`: Fast-path allocations from Thread-Local Allocation Buffers
- `outside_tlab_allocations`: Slow-path allocations (contended global heap)
- `total_allocation_mb`: Total bytes allocated during benchmark
- `allocation_rate_mb_per_sec`: Throughput metric normalized by benchmark duration

**Use Case**: Detect allocation hot spots and CAS contention. High outside_tlab ratio indicates memory allocation contention.

### Blocking Metrics
- `monitor_enter_count`: Lock acquisitions
- `monitor_wait_count`: Lock waits (indicates contention)
- `total_wait_duration_ms`: Cumulative lock wait time
- `blocking_frequency_per_second`: Wait rate normalized to benchmark duration

**Use Case**: Identify if synchronization is limiting throughput or increasing latency variance.

## Variant Comparison

The JFR integration enables variant comparisons across three configurations:

### Java 17 Baseline (`java17-baseline`)
- Platform threads only
- Baseline GC and threading behavior
- Expected: High thread creation, potential TLAB contention under load

### Java 21 Traditional (`java21-traditional`)
- Platform threads on Java 21
- Improved GC (ZGC/G1 enhancements)
- Expected: Lower GC pause times, similar threading patterns

### Java 21 Virtual Threads (`java21-virtual`)
- Virtual threads (Project Loom)
- Lower thread creation cost, more concurrency
- Expected: High thread_park_count, low net_thread_creation, excellent latency consistency

**Correlation Analysis Should Show:**
- Variant A (Java 17): High GC pauses, potential latency spikes
- Variant B (Java 21 Traditional): Reduced GC pauses vs Variant A
- Variant C (Java 21 Virtual): Minimal GC impact, consistent latency due to cheaper context switching

## Running Benchmarks with JFR

### Standard Execution
```bash
cd <project-root>
mvn clean package -DskipTests

java -cp target/benchmarks.jar \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner
```

### Output Files
- `benchmark-results.json`: Combined JMH + JFR + correlation analysis
- `jfr-recordings/benchmark-*.jfr`: Raw JFR binary files (for manual analysis with JDK Mission Control)
- `benchmark-results.csv`: CSV summary of JMH results

### JFR File Analysis (Optional)
Raw JFR files can be analyzed with JDK Mission Control:
```bash
jmc
# Open File > Open Flight Recording > select jfr-recordings/benchmark-*.jfr
```

## Troubleshooting

### JFR Recording Fails
**Symptoms**: "JFR recording failed" or empty JFR metrics

**Causes & Solutions:**
1. **JFR not available**: JFR requires Java 11+. Verify with `java -version`
2. **Flight Recorder disabled**: Some JVM builds disable JFR. Ensure it's available in your JDK
3. **File permissions**: Verify write permissions to `jfr-recordings/` directory
4. **Disk space**: Ensure sufficient disk space for `.jfr` files (typically 10-100 MB per benchmark run)

**Solutions:**
```bash
# Check if JFR is available
java -XX:+PrintFlagsFinal -version 2>&1 | grep -i flight

# Ensure write permissions
mkdir -p jfr-recordings && chmod 755 jfr-recordings

# Run with explicit JFR options
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=disk=true,maxsize=100m \
     -jar target/benchmarks.jar ...
```

### Correlation Analysis Shows No Bottlenecks but Latency Is High
**Possible Causes:**
1. Network latency (not captured by JFR)
2. Application-level contention (e.g., database locks)
3. JFR overhead (recording adds ~5% overhead)

**Investigation Steps:**
1. Check JMH latency measurements for outliers
2. Review GC pause details for timing correlation
3. Examine application logs for errors during benchmark

### Memory Metrics Not Captured
**Issue**: `total_allocation_mb` is 0

**Causes:**
1. Allocation event buffer overflow (rare)
2. Short benchmark duration with low allocation rate

**Solutions:**
1. Extend benchmark duration in BenchmarkRunner
2. Reduce event sampling threshold (in JFRHarness.configureEvents())

## Performance Impact of JFR

JFR recording adds minimal overhead:
- CPU overhead: ~3-5% (sampling-based)
- Memory overhead: ~10-30 MB per benchmark run
- Disk I/O: Negligible (async recording to disk)

The added latency variance from JFR is typically < 2%, well below JMH measurement precision.

## Best Practices

1. **Run consistent benchmarks**: Multiple forks (5+) average out JFR overhead
2. **Monitor JFR file size**: If > 100 MB, reduce event buffer sizes or shorten benchmarks
3. **Cross-reference with JMC**: For spike correlation, open raw JFR files in JDK Mission Control
4. **Compare variants**: JFR is most valuable for multi-variant comparison (Java 17 vs 21)
5. **Interpret conservatively**: Correlation analysis suggests relationships, not causation

## Implementation Details

### Time Synchronization
- JMH records time via `System.currentTimeMillis()` (1ms precision)
- JFR records events via `System.nanoTime()` (nanosecond precision)
- Correlation maps JFR events (nanoseconds) to benchmark windows (milliseconds)
- Conversion: `eventTimeNanos / 1_000_000 = eventTimeMs`

### Event Filtering
JFR collects thousands of events per second. The parser:
1. Reads all events from JFR file
2. Filters by event type (GC, thread, memory, blocking)
3. Aggregates into summary statistics
4. Retains timestamps for spike correlation

### Bottleneck Detection Logic
The correlator identifies bottlenecks by analyzing metric ratios:
- **GC impact**: Pause frequency and duration normalized to benchmark duration
- **Thread saturation**: Net thread creation relative to benchmark workload
- **Memory pressure**: Off-TLAB allocation ratio (indicates CAS contention)
- **Lock contention**: Monitor wait frequency relative to benchmark operations

## Future Enhancements

Potential improvements for Task 5 (visualization):
- D3.js timeline visualization of GC pauses vs latency
- Heat maps of thread activity over time
- Comparative dashboard for Java 17 vs 21 variants
- Correlation strength metrics (Pearson correlation for GC-latency spike alignment)
- Real-time JFR streaming (instead of batch analysis)

## References

- [Java Flight Recorder Documentation](https://docs.oracle.com/javabase/jdk17/docs/api/jdk.jfr/module-summary.html)
- [JDK Mission Control User Guide](https://docs.oracle.com/en/java/javase/21/docs/specs/jfr_streaming_api.html)
- [JMH Documentation](https://github.com/openjdk/jmh)
- [Virtual Threads in Java 21](https://openjdk.org/jeps/444)

