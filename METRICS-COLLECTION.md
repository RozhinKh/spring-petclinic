# Spring Boot Actuator Metrics Collection

## Overview

This document describes the comprehensive metrics collection system implemented for the PetClinic benchmark suite. The system provides real-time performance monitoring across all benchmark phases using Spring Boot Actuator endpoints and custom Micrometer listeners.

## Architecture

### Core Components

#### 1. MetricsCollector
Main component that polls the `/actuator/metrics` endpoint at configurable intervals (default 5-10 seconds) and collects the following metrics:

**Metrics Categories:**
- **HTTP**: Request count, response times (mean, max), error counts by status code
- **JVM**: Heap/non-heap memory usage, garbage collection pause times and counts
- **Thread Pools**: Active threads, queue depth, pool size (platform and virtual threads)
- **Cache**: Hit/miss rates for Caffeine caches (esp. "vets" endpoint)
- **Database**: Connection pool statistics (active, idle, pending, max)

**Key Methods:**
```java
// Start collection with default 5-second interval
metricsCollector.start("java21-virtual");

// Start with custom interval (in seconds)
metricsCollector.start("java21-virtual", 10);

// Stop collection and export to JSON
String exportPath = metricsCollector.stop();
```

#### 2. MetricsExporter
Handles JSON serialization and file storage of metrics snapshots. Creates timestamped files in `target/metrics/` directory for correlation with JMH/JFR events.

**File Naming Convention:**
- Single snapshot: `metrics-{variant}-{timestamp}.json`
- Batch export: `metrics-batch-{variant}-{timestamp}.json`

**Schema:**
```json
{
  "export_timestamp": "ISO-8601",
  "variant": "java21-virtual",
  "snapshot_count": 50,
  "snapshots": [
    {
      "timestamp_ms": 1704067200000,
      "timestamp_iso": "2024-01-01T12:00:00Z",
      "metrics": {
        "http": { ... },
        "jvm": { ... },
        "threads": { ... },
        "cache": { ... },
        "database": { ... }
      }
    }
  ]
}
```

#### 3. BenchmarkMetricsHarness
Provides convenient API for integrating metrics collection with JMH benchmark execution. Manages lifecycle and provides summary reports.

**Key Methods:**
```java
// Start metrics collection for a benchmark
harness.startBenchmark("throughput", "java21-virtual", 5);

// Run benchmark...
// runBenchmark();

// Stop collection and export
String exportPath = harness.stopBenchmark("throughput", "java21-virtual");

// Get summaries
Map<String, Object> httpMetrics = harness.getHttpMetricsSummary();
Map<String, Map<String, Object>> cacheMetrics = harness.getCacheMetricsSummary();
```

#### 4. HttpMetricsListener
Custom WebMvcTagsContributor that tracks HTTP request/response metrics in real-time without polling overhead.

**Tracks:**
- Total request count
- Total error count by status code
- Error rate by HTTP status

#### 5. CaffeineCacheMetricsCollector
Captures cache performance metrics for all Caffeine caches, particularly the "vets" endpoint cache.

**Metrics:**
- Hit/miss counts and rates
- Eviction and removal counts
- Cache size

## Configuration

### Enable Metrics Collection

Use the `application-benchmark.properties` profile when running benchmarks:

```bash
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=benchmark \
  --server.port=8080
```

### Configuration Properties

All Actuator endpoints are exposed via HTTP in benchmark mode:

```properties
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always
management.endpoint.metrics.enabled=true

# Detailed metrics for each category
management.metrics.enable.http.server.requests=true
management.metrics.enable.jvm.memory.used=true
management.metrics.enable.jvm.gc.pause=true
management.metrics.enable.executor.active=true
management.metrics.enable.cache.gets=true
management.metrics.enable.hikaricp.connections=true
```

## Usage Examples

### Basic Metrics Collection

```java
@Autowired
private MetricsCollector metricsCollector;

public void runBenchmark() throws IOException {
    // Start collecting
    metricsCollector.start("java21-virtual", 5);
    
    try {
        // Run your benchmark
        performBenchmarkOperations();
    } finally {
        // Stop and export
        String exportPath = metricsCollector.stop();
        logger.info("Metrics exported to: {}", exportPath);
    }
}
```

### Using BenchmarkMetricsHarness

```java
@Autowired
private BenchmarkMetricsHarness harness;

@Benchmark
public void throughputBenchmark() {
    harness.startBenchmark("throughput", "java21-virtual", 5);
    
    try {
        // Run benchmark operations
        for (int i = 0; i < 1000; i++) {
            vetRepository.findAll();
        }
    } finally {
        String exportPath = harness.stopBenchmark("throughput", "java21-virtual");
        
        // Get metrics summaries
        Map<String, Object> httpMetrics = harness.getHttpMetricsSummary();
        Map<String, Map<String, Object>> cacheMetrics = harness.getCacheMetricsSummary();
        
        logger.info("HTTP Metrics: {}", httpMetrics);
        logger.info("Cache Metrics: {}", cacheMetrics);
    }
}
```

### Multi-Variant Execution

```java
String[] variants = {"java17", "java21-traditional", "java21-virtual"};
String[] profiles = {"", "", "vthreads"};

for (int i = 0; i < variants.length; i++) {
    String variant = variants[i];
    String profile = profiles[i];
    
    // Start application with profile
    // ...
    
    harness.startBenchmark("full-suite", variant, 5);
    
    try {
        // Run all benchmarks
        runAllBenchmarks();
    } finally {
        String exportPath = harness.stopBenchmark("full-suite", variant);
        logger.info("Exported metrics for {}: {}", variant, exportPath);
    }
}
```

## Metrics Extraction

### HTTP Metrics

Extracted from `/actuator/metrics/http.server.requests`:
- `mean_response_time_ms` - Average response time
- `max_response_time_ms` - Maximum response time
- `request_count` - Total requests processed

### JVM Metrics

**Memory Metrics** (from `/actuator/metrics/jvm.memory.*`):
- `heap_memory_used_bytes` - Current heap usage
- `heap_memory_max_bytes` - Maximum heap size
- `nonheap_memory_used_bytes` - Non-heap memory usage

**GC Metrics** (from `/actuator/metrics/jvm.gc.pause`):
- `gc_pause_mean_ms` - Average GC pause time
- `gc_pause_max_ms` - Maximum GC pause time
- `gc_pause_count` - Number of GC events

**Thread Metrics** (from `/actuator/metrics/jvm.threads.*`):
- `live_threads` - Currently active threads
- `peak_threads` - Peak thread count since JVM start
- `daemon_threads` - Active daemon threads

### Thread Pool Metrics

Extracted from `/actuator/metrics/executor.*`:
- `executor_active_threads` - Active thread pool threads
- `executor_queued_tasks` - Queued tasks awaiting execution
- `executor_pool_size` - Current thread pool size
- `executor_pool_max` - Maximum thread pool size

**Note:** These metrics are crucial for comparing platform threads vs virtual threads behavior.

### Cache Metrics

Extracted from `/actuator/metrics/cache.*`:
- `cache_gets_count` - Total cache access attempts
- `cache_puts_count` - Total cache write operations
- `cache_evictions_count` - Total evicted entries
- `cache_removals_count` - Total manually removed entries

### Database Metrics

Extracted from `/actuator/metrics/hikaricp.connections.*`:
- `hikari_active_connections` - Currently in-use connections
- `hikari_idle_connections` - Available idle connections
- `hikari_pending_connections` - Waiting for connection
- `hikari_max_connections` - Maximum pool size

## Output Files

### Location
All metrics are stored in `target/metrics/` directory with ISO-8601 timestamps:

```
target/metrics/
├── metrics-batch-java17-1704067200000.json
├── metrics-batch-java21-traditional-1704067210000.json
└── metrics-batch-java21-virtual-1704067220000.json
```

### File Structure

Each file contains:
- `export_timestamp` - When metrics were exported
- `variant` - Variant name for filtering
- `snapshot_count` - Number of snapshots in batch
- `snapshots` - Array of timestamped metric snapshots

## Correlation with JMH/JFR

### Timestamp Matching

Metrics are captured with both millisecond and ISO-8601 timestamps:
```json
{
  "timestamp_ms": 1704067200000,
  "timestamp_iso": "2024-01-01T12:00:00Z"
}
```

This allows precise correlation:
1. JMH benchmark starts at T0
2. Metrics collection starts at T0 (before @Benchmark)
3. Metrics captured at T0+5s, T0+10s, etc.
4. JMH benchmark ends at T0+Tn
5. Metrics stopped at T0+Tn
6. Both datasets aligned via timestamps

### Multi-Window Analysis

For benchmarks with multiple iterations:
- Each iteration window has matched metrics snapshots
- Snapshots between iteration N and N+1 correlated to iteration N
- Enables regression analysis across iterations

## Performance Impact

The metrics collection system is designed for minimal overhead:

### Polling Overhead
- Typically <1ms per poll operation
- Network latency to `/actuator/metrics` endpoint: ~1-5ms
- Default 5-second interval = <1% CPU impact

### Memory Overhead
- Snapshot storage: ~2-5KB per snapshot
- 120 snapshots over 10 minutes = ~0.5MB
- No excessive GC pressure

### Validation
Confirm metrics collection impact <2% on benchmark results by:
1. Running benchmark without metrics collection
2. Running same benchmark with metrics collection
3. Comparing JMH score differences (should be <2%)

## Troubleshooting

### Metrics Endpoint Not Accessible

```bash
# Verify endpoint is accessible
curl http://localhost:8080/actuator/metrics

# Check Actuator configuration
curl http://localhost:8080/actuator
```

### Empty or Missing Metrics

1. Ensure application is running with `--spring.profiles.active=benchmark`
2. Wait at least one polling interval before checking metrics
3. Check application logs for errors

### Virtual Thread Metrics

Virtual thread metrics appear in:
- `executor.active` - Number of mounted virtual threads
- `executor.pool.size` - Virtual thread executor pool size
- Thread pool metrics show higher counts than platform threads for same load

## Performance Analysis

### Cache Performance Tracking

The vets endpoint cache metrics show:
```
Hit Rate = cache_gets (result: hit) / cache_gets (total) × 100%
```

Expected improvements:
- Java 21 Virtual: Higher hit rate due to better concurrency
- Large result sets: Hit rate may vary with pagination

### Virtual Thread Benefits

Look for these indicators in metrics:
- Higher executor thread counts (virtual threads are lightweight)
- Lower GC pause times (fewer thread stack frames)
- Better memory utilization (virtual threads use ~1KB vs ~1MB)
- Lower peak memory usage under load

## Integration with Analysis Tools

### Correlation with JFR

1. Export JFR data: `jcmd <pid> JFR.dump filename=app.jfr`
2. Open in JDK Mission Control
3. Load corresponding metrics JSON file
4. Align timestamps between JFR and metrics

### Analysis Scripts

Example Python script for metrics analysis:
```python
import json
from datetime import datetime

# Load metrics
with open('metrics-batch-java21-virtual.json') as f:
    metrics = json.load(f)

# Extract cache hit rates
for snapshot in metrics['snapshots']:
    cache = snapshot['metrics'].get('cache', {})
    if cache:
        gets = cache.get('cache_gets_count', 0)
        print(f"Time: {snapshot['timestamp_iso']}, Gets: {gets}")
```

## Best Practices

1. **Interval Selection**
   - 5 seconds: Default, good for most benchmarks
   - 2 seconds: High-frequency workloads
   - 10+ seconds: Long-running benchmarks

2. **Storage Management**
   - Metrics can grow to 1-2MB per hour of collection
   - Clean up old metrics files after analysis
   - Archive metrics for historical comparison

3. **Accuracy**
   - Ensure application is running before starting metrics collection
   - Wait for steady state before starting benchmark
   - Run multiple iterations for statistical significance

4. **Multi-Variant Comparison**
   - Collect metrics at same interval for all variants
   - Same benchmark workload for all variants
   - Same hardware/environment for comparison

## See Also

- [Spring Boot Actuator Documentation](https://spring.io/guides/gs/actuator-service/)
- [Micrometer Metrics Documentation](https://micrometer.io/)
- [JVM Flight Recorder Guide](https://www.oracle.com/java/technologies/jdk-mission-control.html)
- [BENCHMARK-WORKFLOWS.md](./BENCHMARK-WORKFLOWS.md) - Integration with JMH execution
- [EXPORT-IMPLEMENTATION.md](./EXPORT-IMPLEMENTATION.md) - Metrics export and aggregation
