# JFR Metrics: Variant Comparison Guide

## Overview

This document explains expected JFR metric patterns across the three benchmark variants, enabling interpretation of results and identification of performance improvements.

## Variant Profiles

### Java 17 Baseline (java17-baseline)
- **Java Version**: Java 17 (LTS)
- **Threading**: Platform threads only
- **GC**: G1GC (default)
- **Notable Features**: 
  - Mature, stable GC algorithms
  - Classic JDK threading model
  - Baseline for comparison

### Java 21 Traditional (java21-traditional)
- **Java Version**: Java 21 (LTS)
- **Threading**: Platform threads only
- **GC**: Improved G1GC, ZGC available
- **Notable Features**:
  - Enhanced GC performance
  - Updated threading infrastructure
  - Expected: Modest GC improvements over Java 17

### Java 21 Virtual Threads (java21-virtual)
- **Java Version**: Java 21 (LTS)
- **Threading**: Virtual threads (Project Loom)
- **GC**: G1GC or ZGC (unchanged from java21-traditional)
- **Notable Features**:
  - Lightweight thread creation (negligible cost)
  - Efficient context switching (virtual thread parking)
  - Higher concurrency without resource pressure
  - Same GC implementation as java21-traditional

## Expected JFR Metric Patterns

### GC Metrics Comparison

#### jdk.GarbageCollection & jdk.GCPauseLevel Events

**Java 17 Baseline** (Expected Baseline)
```
pause_count:                   20-50 (Young collections dominant)
total_pause_duration_ms:       200-500 ms
avg_pause_duration_ms:         10-15 ms
min_pause_duration_ms:         5-8 ms
max_pause_duration_ms:         30-50 ms
gc_type_counts:
  - G1 Young Generation:       18-40 (majority)
  - G1 Mixed Generation:       2-10
  - G1 Full Generation:        0-2 (rare)
```

**Java 21 Traditional** (Expected 10-20% improvement)
```
pause_count:                   15-40 (fewer collections)
total_pause_duration_ms:       160-400 ms (↓ 10-20%)
avg_pause_duration_ms:         10-12 ms (↓ slight)
min_pause_duration_ms:         4-6 ms (unchanged)
max_pause_duration_ms:         25-40 ms (↓ 15-25%)
gc_type_counts:
  - G1 Young Generation:       13-35 (fewer collections)
  - G1 Mixed Generation:       1-8
  - G1 Full Generation:        0-1
Reason: Java 21 GC tuning, reduced allocation patterns
```

**Java 21 Virtual Threads** (Expected similar to traditional, better concurrent behavior)
```
pause_count:                   15-40 (similar to traditional)
total_pause_duration_ms:       160-400 ms (similar to traditional)
avg_pause_duration_ms:         10-12 ms (similar to traditional)
min_pause_duration_ms:         4-6 ms (unchanged)
max_pause_duration_ms:         25-40 ms (similar to traditional)
gc_type_counts:                (similar to traditional)
Reason: Same GC implementation, but virtual threads may reduce concurrent GC contention
Note: Latency variance should be lower (fewer GC pauses per competing platform threads)
```

**Key Insight**: GC pause times reflect heap size and allocation rate, not threading model. Virtual threads show benefit through *reduced variance* rather than fewer pauses.

---

### Thread Metrics Comparison

#### jdk.ThreadStart, jdk.ThreadEnd, jdk.ThreadPark Events

**Java 17 Baseline** (Platform Thread Baseline)
```
thread_start_count:            50-200 (framework threads + request handlers)
thread_end_count:              30-150 (some thread reuse in pools)
net_thread_creation:           20-50 (active thread pool size)
thread_park_count:             100-500 (platform threads sleeping/waiting)
Interpretation:
  - Limited by default thread pool size (typically 200 for web servers)
  - Significant parking overhead (OS context switching)
  - Thread creation cost: ~1-2ms per thread
```

**Java 21 Traditional** (Expected similar to Java 17)
```
thread_start_count:            50-200 (similar to Java 17)
thread_end_count:              30-150 (similar to Java 17)
net_thread_creation:           20-50 (similar to Java 17)
thread_park_count:             100-500 (similar to Java 17)
Interpretation:
  - Identical threading model to Java 17
  - No expected difference (different JDK implementation, same API)
  - Minor improvements from updated JDK code
```

**Java 21 Virtual Threads** (Expected dramatic difference)
```
thread_start_count:            10,000-100,000 (1000x higher!)
thread_end_count:              9,000-95,000 (proportional)
net_thread_creation:           1,000-5,000 (much higher, but low-cost)
thread_park_count:             100,000-1,000,000 (10-100x higher!)
Interpretation:
  - Virtual threads created per request (minimal cost)
  - Each request gets dedicated virtual thread
  - Parking is fast (JVM-level context switch, not OS)
  - No OS thread pool exhaustion
  - Higher concurrency without resource pressure
Key Metric: High thread_park_count with low CPU cost indicates efficient virtual threading
```

**Critical Metric for Virtual Threads**:
- **Ratio**: `thread_park_count / net_thread_creation`
  - Java 17: 2-10x (platform threads sleep/wake multiple times)
  - Java 21 Virtual: 50-200x (virtual threads park frequently for I/O)
  - **Higher ratio indicates better I/O efficiency** (more tasks waiting on I/O than threads)

---

### Memory Metrics Comparison

#### jdk.ObjectAllocationInNewTLAB, jdk.ObjectAllocationOutsideTLAB Events

**Java 17 Baseline** (Platform Thread Allocation)
```
tlab_allocations:              5,000-50,000 (fast-path allocations)
outside_tlab_allocations:      50-500 (slow-path due to contention)
outside_tlab_ratio:            0.01-0.05 (1-5% off-TLAB)
total_allocation_mb:           50-200 MB
allocation_rate_mb_sec:        0.8-3.3 MB/s
Interpretation:
  - Mostly successful TLAB allocations (good lock-free performance)
  - Some off-TLAB allocations indicate CAS contention
  - Multiple request-handling threads compete for allocation
```

**Java 21 Traditional** (Expected similar to Java 17)
```
tlab_allocations:              5,000-50,000 (similar to Java 17)
outside_tlab_allocations:      50-500 (similar to Java 17)
outside_tlab_ratio:            0.01-0.05 (1-5%, similar to Java 17)
total_allocation_mb:           50-200 MB (similar total)
allocation_rate_mb_sec:        0.8-3.3 MB/s (similar rate)
Interpretation:
  - Same allocation patterns as Java 17
  - No expected improvement from threading model
```

**Java 21 Virtual Threads** (Expected similar total, lower contention)
```
tlab_allocations:              5,000-50,000 (similar total allocations)
outside_tlab_allocations:      20-200 (↓ fewer off-TLAB)
outside_tlab_ratio:            0.005-0.02 (0.5-2%, ↓ 50-80%)
total_allocation_mb:           50-200 MB (similar total)
allocation_rate_mb_sec:        0.8-3.3 MB/s (similar rate)
Interpretation:
  - Virtual threads reduce allocation contention
  - Lower concurrent demand on TLAB buffers
  - Each virtual thread has private TLAB, less CAS pressure
  - Better cache locality (sequential virtual thread execution)
  - Key Insight: Lower off-TLAB ratio = less allocation contention = lower latency variance
```

**Critical Interpretation**:
- **Allocation Rate** (MB/s) indicates request complexity, not threading model
- **TLAB Ratio** (off-heap %) indicates contention level
  - Virtual threads should show 50-80% reduction in off-TLAB ratio
  - This directly impacts GC efficiency (fewer CAS operations)

---

### Blocking Metrics Comparison

#### jdk.JavaMonitorEnter, jdk.JavaMonitorWait Events

**Java 17 Baseline** (Platform Thread Blocking)
```
monitor_enter_count:           200-1000 (lock acquisitions)
monitor_wait_count:            10-100 (actual blocking/contention)
blocking_frequency_per_sec:    0.2-2.0 (waits per second)
total_wait_duration_ms:        5-50 ms
avg_wait_duration_ms:          0.5-5.0 ms (if any waits)
Interpretation:
  - Mostly uncontended locks (fast entry)
  - Few actual waits indicate good lock design
  - Thread pool contention possible at high concurrency
```

**Java 21 Traditional** (Expected similar to Java 17)
```
monitor_enter_count:           200-1000 (similar to Java 17)
monitor_wait_count:            10-100 (similar to Java 17)
blocking_frequency_per_sec:    0.2-2.0 (similar to Java 17)
total_wait_duration_ms:        5-50 ms (similar to Java 17)
avg_wait_duration_ms:          0.5-5.0 ms (similar to Java 17)
Interpretation:
  - Identical threading model = identical blocking patterns
  - No expected improvement
```

**Java 21 Virtual Threads** (Expected lower blocking variance)
```
monitor_enter_count:           200-1000 (similar total)
monitor_wait_count:            10-100 (similar or slightly lower)
blocking_frequency_per_sec:    0.2-2.0 (similar rate)
total_wait_duration_ms:        5-50 ms (similar total)
avg_wait_duration_ms:          0.5-5.0 ms (similar total)
BUT: Variance should be much lower (std dev ↓ 50-80%)
Interpretation:
  - Virtual threads don't starve each other during lock contention
  - Fewer platform threads = less lock queue buildup
  - Blocking latency more consistent (fewer "thundering herd" events)
  - Key Insight: Lower variance (std dev) rather than fewer waits
```

**Critical Interpretation**:
- **Lock Counts**: Similar across all variants (depends on application, not threading)
- **Contention Level**: Better measured by latency variance than absolute counts
  - Virtual threads: Consistent performance under contention
  - Platform threads: Variable performance due to thread pool effects

---

## Correlation Analysis Expected Patterns

### GC Latency Correlation

**All Variants**:
- GC pauses should align with latency percentile spikes (P95, P99)
- Correlation strength: r=0.3-0.7 (moderate to strong)
- Strongest correlation for full GC events (rare)
- Weaker correlation for young generation GC (brief pauses)

**Example Analysis Output**:
```json
{
  "gc_latency_correlation": {
    "gc_pauses_during_benchmark": 25,
    "gc_frequency_per_second": 8.3,
    "estimated_gc_impact_level": "moderate",
    "pauses_details": [
      {
        "gc_type": "G1 Young Generation",
        "duration_ms": 15,
        "offset_from_benchmark_start_ms": 5230
      }
    ]
  }
}
```

### Thread Saturation Correlation

**Java 17 Baseline**:
- Typical saturation: `low` (net_thread_creation < 50)
- At high load: `moderate` to `high` (thread pool queue buildup)
- Indication: Monitor response time increase at saturation

**Java 21 Traditional**:
- Similar to Java 17 (same threading model)
- May show slightly better under load (updated thread pool)

**Java 21 Virtual Threads**:
- Usually `low` (thousands of virtual threads, still under JVM limit)
- Saturation occurs at higher concurrency (100,000+ virtual threads)
- Better indication: Thread park frequency vs CPU utilization

---

## Metric Interpretation Guidance

### Favorable Metric Combinations

#### Pattern 1: "Good GC Performance"
```
gc_latency_correlation.estimated_gc_impact_level = "low"
AND
gc_latency_correlation.gc_frequency_per_second < 2.0
AND
memory_pressure_correlation.estimated_memory_pressure = "low"
THEN
✓ GC is not limiting performance
→ Optimization targets: Application logic, I/O efficiency
```

#### Pattern 2: "Optimal Threading Model for Load"
**For Java 17/21 Traditional**:
```
thread_correlation.estimated_thread_pool_saturation = "low"
AND
blocking_correlation.estimated_contention_level = "low"
THEN
✓ Threading model handling load efficiently
→ Can increase load further if needed
```

**For Java 21 Virtual Threads**:
```
net_thread_creation > 1000
AND
thread_park_count > 50000
AND
memory_metrics.total_allocation_mb < baseline
THEN
✓ Virtual threads efficiently multiplexing I/O
→ Excellent scaling characteristics
```

#### Pattern 3: "Memory Allocation Efficiency"
```
memory_pressure_correlation.outside_tlab_ratio < 0.02
AND
memory_pressure_correlation.estimated_memory_pressure = "low"
THEN
✓ Minimal allocation contention
→ Heap size can be reduced or throughput increased
```

### Unfavorable Metric Combinations

#### Pattern 1: "GC Contention Issue"
```
gc_latency_correlation.estimated_gc_impact_level = "high"
AND
latency.p99_ms significantly > latency.p50_ms
THEN
✗ GC pauses directly impacting tail latency
→ Optimization targets: Heap size tuning, allocation reduction
```

#### Pattern 2: "Thread Pool Exhaustion"
```
thread_correlation.estimated_thread_pool_saturation = "high"
AND
latency.p99_ms / latency.p50_ms > 2.0
THEN
✗ Thread starvation causing request queueing
→ Optimization targets: Thread pool size, request handling, virtual threads
```

#### Pattern 3: "Memory Allocation Contention"
```
memory_pressure_correlation.outside_tlab_ratio > 0.3
AND
memory_pressure_correlation.estimated_memory_pressure = "high"
THEN
✗ Significant allocation lock contention
→ Optimization targets: Object pooling, allocation reduction, heap tuning
```

---

## Variant Performance Prediction

### Realistic Performance Expectations

Based on typical Spring Boot application patterns:

| Metric | Java 17 | Java 21 Traditional | Java 21 Virtual |
|--------|---------|-------------------|-----------------|
| **P50 Latency** | 45ms | 42ms (-7%) | 40ms (-11%) |
| **P99 Latency** | 120ms | 100ms (-17%) | 55ms (-54%) |
| **Throughput** | 100 ops/s | 110 ops/s (+10%) | 150 ops/s (+50%) |
| **GC Pause Count** | 25 | 20 (-20%) | 20 (same) |
| **Thread Creation** | 50 | 50 (same) | 5000 (+100x) |
| **Memory Pressure** | 3% off-TLAB | 2% off-TLAB | 0.5% off-TLAB |

**Key Insight**: Java 21 virtual threads show biggest improvement in **P99 latency** and **throughput**, not average latency.

---

## Debugging Guide: When Metrics Don't Match Expectations

### "Java 21 Virtual Threads not showing better performance"

**Possible Causes**:
1. **Synchronous I/O blocking**: Virtual threads only help with async I/O
   - **Check**: Look for high monitor_wait_count despite virtual threads
   - **Fix**: Ensure application uses non-blocking I/O (WebClient, etc.)

2. **CPU-bound workload**: Threading model irrelevant for CPU work
   - **Check**: High CPU utilization but no improvement in latency variance
   - **Fix**: Use parallel processing (ForkJoinPool) instead of virtual threads

3. **Insufficient concurrency**: Not reaching virtual thread benefits
   - **Check**: net_thread_creation < 100 (not enough concurrent requests)
   - **Fix**: Increase benchmark load or concurrent client count

### "Unexpected high GC pause times in Java 21"

**Possible Causes**:
1. **G1GC configuration**: Defaults may need tuning for 21 LTS
   - **Check**: gc_type_counts for unusual collection types
   - **Fix**: Add `-XX:+UseG1GC -XX:MaxGCPauseMillis=200` JVM options

2. **Heap size mismatch**: Too small heap causes frequent collections
   - **Check**: gc_type_counts.G1_Full_Generation > 0
   - **Fix**: Increase `-Xmx` parameter

### "Memory pressure metrics inconsistent"

**Possible Causes**:
1. **TLAB event threshold**: Events may be sampled, not all captured
   - **Check**: total_allocation_mb vs application expectation
   - **Fix**: Increase event buffer size in JFRHarness (advanced)

2. **Allocation event definition**: Different meaning in different JDK versions
   - **Check**: JFR documentation for exact event semantics
   - **Fix**: Compare against raw JFR files in JDK Mission Control

---

## References

- [G1GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector.html)
- [Virtual Threads Documentation](https://openjdk.org/jeps/444)
- [JFR Event Reference](https://chriswhocodes.com/jfr/)

