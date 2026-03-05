# Spring PetClinic Metrics Interpretation Guide

Complete reference for understanding, analyzing, and troubleshooting all benchmark metrics collected across the Spring PetClinic benchmark suite.

---

## Executive Summary for Non-Technical Stakeholders

### What These Metrics Mean (In Business Terms)

When engineers run benchmarks on Spring PetClinic, they measure **four key things** that matter to your business:

| Business Impact | Technical Metric | What It Means | Rule of Thumb |
|---|---|---|---|
| **User Experience** | Latency (P95/P99 ms) | How fast users see results | If P95 latency increases by >15%, users notice slower pages |
| **System Capacity** | Throughput (req/sec) | How many users can be served simultaneously | A 10% throughput drop means you need 10% more servers for same load |
| **Infrastructure Cost** | Memory (heap MB) | Monthly cloud bill impact | Every 100MB saved = ~$5-10/month in cloud costs |
| **Reliability** | GC Pause (ms) | How often app "freezes" responding | GC pauses >50ms cause noticeable hiccups for users |
| **Java Version Benefit** | Platform vs Virtual Threads | Whether Java 21 improvements are real | Virtual threads should show 10-20% more capacity with same hardware |

### Typical Baseline Metrics for PetClinic (Java 17)

These numbers help you spot when something is wrong:

```
Cold Startup:     2.8-3.2 seconds
Warm Startup:     0.5-0.8 seconds
Latency P95:      45-65 ms
Latency P99:      75-120 ms
Throughput:       800-1200 req/sec
Heap at idle:     120-150 MB
Heap at peak:     300-350 MB
GC pause avg:     8-15 ms
Virtual threads:  2000+ concurrent (vs 200 platform threads)
```

**If any metric moves more than 15% from baseline, investigate why.** Improvements are good; regressions need root cause analysis.

---

## Table of Contents

1. [Metric Definitions & Ranges](#1-metric-definitions--ranges)
2. [Startup Metrics](#2-startup-metrics)
3. [Latency Metrics](#3-latency-metrics)
4. [Throughput Metrics](#4-throughput-metrics)
5. [Memory Metrics](#5-memory-metrics)
6. [Garbage Collection Metrics](#6-garbage-collection-metrics)
7. [Thread Count Metrics](#7-thread-count-metrics)
8. [Blocking Event Metrics](#8-blocking-event-metrics)
9. [Test Suite Metrics](#9-test-suite-metrics)
10. [Modernization Metrics](#10-modernization-metrics)
11. [Statistical Significance & Variance](#11-statistical-significance--variance)
12. [Analysis Workflows & Charting](#12-analysis-workflows--charting)
13. [Example Dashboards](#13-example-dashboards)
14. [Troubleshooting Guide](#14-troubleshooting-guide)

---

## 1. Metric Definitions & Ranges

### 1.1 Complete Metric Reference Table

| Metric | Source | Unit | Description | Typical Range | Java 21 Expected |
|--------|--------|------|-------------|---|---|
| **Startup Time (Cold)** | JMH/JFR | seconds | Time from app start to accepting requests | 2.8-3.5s | -5% to -15% |
| **Startup Time (Warm)** | JMH/JFR | seconds | Restart time with cached metadata | 0.5-0.8s | -10% to -20% |
| **Latency P50** | JMeter/JFR | milliseconds | Median request response time | 8-15ms | -5% to -10% |
| **Latency P95** | JMeter/JFR | milliseconds | 95th percentile (5% of users wait longer) | 45-70ms | -5% to -10% |
| **Latency P99** | JMeter/JFR | milliseconds | 99th percentile (1% of users wait longer) | 80-120ms | -10% to -15% |
| **Latency Max** | JMeter/JFR | milliseconds | Slowest single request | 150-250ms | -5% to -20% |
| **Throughput** | JMeter | req/sec | Requests per second at 50% load | 800-1200 | +5% to +20% |
| **Throughput (Peak)** | JMeter | req/sec | Max sustainable throughput | 600-900 | +10% to +25% |
| **Heap Memory (Idle)** | JFR | MB | Memory used at startup, no load | 120-150 | -5% to +5% |
| **Heap Memory (50% Load)** | JFR | MB | Memory at moderate load | 180-220 | -5% to +5% |
| **Heap Memory (Peak)** | JFR | MB | Max heap usage during test | 300-380 | -10% to +5% |
| **Thread Count (Platform)** | JFR | count | Max platform (OS) threads created | 150-250 | Similar |
| **Thread Count (Virtual)** | JFR | count | Virtual threads created in Java 21 | 2000-5000 | Key metric for improvement |
| **GC Pause Avg** | JFR | milliseconds | Average pause per garbage collection | 8-15 | -10% to -20% |
| **GC Pause Max** | JFR | milliseconds | Longest single GC pause | 40-80 | -15% to -25% |
| **GC Frequency** | JFR | count/min | GC cycles per minute | 2-4 | -5% to +15% |
| **Blocking Events** | JFR | count | Thread blocking ops (sync, I/O waits) | 500-1500/min | -20% to -40% (virtual threads) |
| **Lock Contention** | JFR | count | Times threads waited for locks | 50-200/min | -30% to -60% (virtual threads) |
| **I/O Wait Time** | JFR | ms | Total time waiting for I/O | 5000-8000/min | -10% to -20% |
| **Test Pass Rate** | JUnit XML | % | Percentage of tests passing | 99-100% | ≥99% |
| **Test Execution Time** | JUnit XML | seconds | Total test suite duration | 120-180s | Similar or faster |
| **Code Coverage** | JaCoCo | % | Line coverage percentage | 75-85% | ≥75% |
| **LOC Modified** | Source analysis | count | Lines of code changed for modernization | N/A | Track for variance A/B |
| **Virtual Thread Constructs** | AST analysis | count | Usage of `Thread.ofVirtual()`, structured concurrency | N/A | Variant B specific |

### 1.2 Metric Groupings

#### Performance Metrics (User-Facing)
- Startup Time (Cold/Warm)
- Latency (P50/P95/P99/Max)
- Throughput

#### Resource Metrics (Cost/Infrastructure)
- Heap Memory (Idle/Load/Peak)
- Thread Count
- GC Pause Times

#### Reliability Metrics (Stability)
- GC Frequency
- Blocking Events
- Lock Contention
- Test Pass Rate

#### Modernization Metrics (Java 21 Adoption)
- Virtual Thread Count
- Virtual Thread Constructs Usage
- Blocking Event Reduction

---

## 2. Startup Metrics

### 2.1 What Startup Time Measures

**Startup time** is how long it takes Spring PetClinic to initialize and start accepting HTTP requests.

```
┌─────────────────────────────────────────┐
│ Application Start                       │
├─────────────────────────────────────────┤
│ 1. JVM initialization          (200ms)  │
│ 2. Spring component discovery  (400ms)  │
│ 3. Database connection pool    (800ms)  │
│ 4. Cache initialization        (200ms)  │
│ 5. Actuator setup              (100ms)  │
│ 6. Web container startup       (300ms)  │
├─────────────────────────────────────────┤
│ Total: 2.0-3.5 seconds                  │
└─────────────────────────────────────────┘
```

### 2.2 Cold vs. Warm Startup

**Cold Startup** (First Run)
- All metadata must be loaded from disk
- JIT compilation happens
- Class loading from scratch
- Typical: 2.8-3.5 seconds
- Java 17 baseline: 3.1s

**Warm Startup** (Subsequent Runs)
- Many classes already JIT compiled
- Metadata in memory caches
- Faster class initialization
- Typical: 0.5-0.8 seconds
- Java 17 baseline: 0.6s

**Why Multiple Runs Matter:**
- **First run is noisy**: Influenced by disk I/O, OS page cache state
- **Real deployments see this**: Container startup, blue-green deployment cutover
- **Variance is normal**: ±200ms on cold startup is acceptable
- **Warm startup more stable**: Shows JVM optimization effectiveness

### 2.3 Expected Ranges & Java 21 Improvements

| Variant | Cold Startup | Warm Startup | Expected Improvement |
|---------|---|---|---|
| Java 17 Baseline | 3.1s ± 0.2s | 0.6s ± 0.1s | Reference |
| Java 21 Variant A | 2.85s ± 0.2s | 0.52s ± 0.1s | -8% to -12% |
| Java 21 Variant B | 2.78s ± 0.2s | 0.50s ± 0.1s | -10% to -15% |

### 2.4 Interpreting Startup Changes

```
IF cold_startup > 3.5s (>10% worse than baseline):
  → Check if new dependencies were added
  → Verify database connection pool warmup isn't blocking
  → Check for new Spring Boot auto-configurations
  → Measure JVM class loading time: java -XX:+PrintClassLoadingTime
  
ELSE IF cold_startup < 2.5s (<20% better than baseline):
  → Great! Java 21 improvements likely
  → May indicate removed functionality - verify intentional
  → Check no essential initialization was skipped
  
ELSE IF warm startup > 0.8s (>30% worse):
  → Suspect: Cache invalidation in new variant
  → Check Caffeine cache configuration
  → Verify no repeated class loading
  → Profile with JFR to find slow method
```

### 2.5 Analysis Workflow for Startup Metrics

**Collect baseline:**
```bash
# Run 10 times to establish variance
for i in {1..10}; do
  ./mvnw clean package -q
  time java -jar target/spring-petclinic-*.jar --server.port=0 &
  sleep 5 && kill $!
done > startup-results.txt
```

**Extract timing:**
```bash
# Average cold startup across runs
grep "real" startup-results.txt | awk '{print $2}' | \
  awk -F'm' '{sum+=$1*60+substr($2,1,length($2)-1); n++} END {print sum/n " seconds"}'
```

**Compare variants:**
```
Java 17 baseline:  3.10s
Java 21 Variant A: 2.87s  (Δ -7.4%)
Java 21 Variant B: 2.79s  (Δ -10.0%)

Analysis: Both Java 21 variants improve startup. Variant B (virtual threads)
has slightly better performance, likely due to faster thread initialization.
```

---

## 3. Latency Metrics

### 3.1 What Latency Measures

**Latency** is the time between when a user sends a request and receives a response. Measured in milliseconds.

```
HTTP Request Timeline:
─────────────────────────────────────────────
User clicks → [Network ~5ms] → 
  Server receives → [Processing] → 
  Response sent → [Network ~5ms] → 
  User sees result

Measured latency: Processing time + Network (~10ms constant)
```

### 3.2 Latency Percentiles Explained

Understanding what P50, P95, P99 mean for **user experience**:

| Percentile | Meaning | User Impact | Example |
|---|---|---|---|
| **P50** | 50% of users wait this long or less | Typical user experience | 12ms = good |
| **P95** | 95% of users ≤ this time, 5% wait longer | Most users fast, some slow | 55ms = 5% of users see delay |
| **P99** | 99% of users ≤ this time, 1% wait longer | Worst 1% of users | 95ms = 1 in 100 users affected |
| **P99.9** | Only 0.1% of users slower (outliers) | Indicates systemic issues | >200ms = problem queries |
| **Max** | Absolute slowest request | Worst case (usually anomaly) | Can be ignored if rare |

### 3.3 Real Example: 1000 Concurrent Users

```
For 1000 users making requests:
├─ P50 (12ms):     500 users wait ≤12ms (satisfied)
├─ P95 (55ms):     50 users wait 12-55ms (acceptable)
├─ P99 (95ms):     10 users wait 55-95ms (frustrated)
└─ P99.9 (200ms):  1 user waits >95ms (very frustrated)

If P95 increases from 55ms to 70ms (+27%):
  → 50 additional users per second experience noticeably slower response
  → May correlate with "app feels sluggish" complaints
  → Warrants investigation
```

### 3.4 Expected Ranges for PetClinic

| Load Profile | P50 | P95 | P99 | Max | Context |
|---|---|---|---|---|---|
| **Idle** (1 req/sec) | 8-12ms | 15-25ms | 30-50ms | 60ms | Low contention |
| **Moderate** (100 req/sec) | 10-15ms | 50-70ms | 90-120ms | 200ms | Normal load |
| **Peak** (300+ req/sec) | 15-25ms | 70-100ms | 150-200ms | 500ms+ | High contention |

**Java 17 Baseline (H2 in-memory DB):**
```
Cold: P95=62ms, P99=98ms
Warm: P95=48ms, P99=75ms
```

**Java 21 Expected (Variant A - Traditional, Variant B - Virtual Threads):**
```
Variant A: P95=55ms (-11%), P99=88ms (-10%)  [GC improvements]
Variant B: P95=45ms (-27%), P99=68ms (-30%)  [Virtual threads + better scheduling]
```

### 3.5 Key Insight: Why P95/P99 Matter More Than P50

```
Business metric: "Users abandonment rate"
- P50 latency increase: +20% (12ms → 14.4ms) - users don't notice
- P95 latency increase: +20% (55ms → 66ms) - 5% of users experience slowdown
  → Estimated 2-3% bounce rate increase in conversion funnels
  → Real business impact

Why P95 matters more:
- P50 can be fast but P95 slow = inconsistent experience
- Users remember the slow requests
- Real world: users on slow networks, shared CPU, cache misses
- P95 captures "normal conditions" better than P50
```

### 3.6 Interpreting Latency Changes

```
IF P95 > 75ms (>30% worse than baseline):
  → Check database query performance
  → Verify connection pool not exhausted
  → Profile with JFR to identify slow method
  → Check for lock contention (blocking events)
  → Measure network latency separately
  
ELSE IF P95 < 40ms (<25% better than baseline):
  → Excellent! Real improvement detected
  → Verify improvement isn't from reduced load
  → Check if new caching is too aggressive
  
ELSE IF P95 within ±15% of baseline:
  → Normal variance, no action needed
  → Improvement within statistical noise
  
IF P99 >> P95 (difference > 2.5x):
  → Sign of outlier queries or GC pauses
  → Investigate queries > 100ms
  → Check GC pause max vs P99 latency
```

### 3.7 CSV Analysis: Latency Distribution

**Export from JMeter results:**
```csv
Timestamp,Label,Elapsed,Connect,Status,bytes
2024-01-15 10:30:45,/api/owners,48,3,200,1245
2024-01-15 10:30:46,/api/owners,52,2,200,1245
2024-01-15 10:30:47,/api/owners,198,2,200,1245  # Outlier - GC pause likely
2024-01-15 10:30:48,/api/owners,44,3,200,1245
```

**Calculate percentiles in Excel:**
```
=PERCENTILE(B:B, 0.50)  → P50
=PERCENTILE(B:B, 0.95)  → P95
=PERCENTILE(B:B, 0.99)  → P99
=MAX(B:B)               → Max
```

---

## 4. Throughput Metrics

### 4.1 What Throughput Measures

**Throughput** = how many HTTP requests per second the application can handle.

```
Real scenario:
- E-commerce site during Black Friday
- Need to handle 500 concurrent shoppers
- Each makes 2-3 requests/second
- Total throughput needed: 500 × 2.5 = 1250 req/sec

If your app achieves only 800 req/sec:
→ You can handle ~320 concurrent users
→ Excess traffic queues, timeouts, sad customers
```

### 4.2 Expected Throughput Ranges

| Load Type | Throughput | Concurrency | Expected for PetClinic |
|---|---|---|---|
| **Light** (50% CPU) | 800-1200 req/sec | 50 threads | Baseline |
| **Medium** (75% CPU) | 600-900 req/sec | 150 threads | Degradation expected |
| **Heavy** (95% CPU) | 300-500 req/sec | 300+ threads | Saturation point |

**Java 17 Baseline:**
```
Single-threaded pool: 650 req/sec
50 platform threads:  950 req/sec
100 platform threads: 1100 req/sec (max before diminishing returns)
```

**Java 21 Variant B (Virtual Threads):**
```
500 virtual threads: 1200 req/sec   (+26% vs 100 platform threads)
1000 virtual threads: 1350 req/sec  (+35%)
5000 virtual threads: 1400 req/sec  (+40%, approaching CPU ceiling)

Why: Virtual threads have near-zero context switch cost, allow
many more concurrent tasks without thread stack overhead.
```

### 4.3 Throughput vs. Latency Trade-off

```
Throughput increasing usually means:
1. More concurrent requests being processed
2. Each request takes SAME time (latency unchanged)
OR
2. Processing similar total work with better parallelism (latency improves)

If throughput UP but latency UP:
→ Bad: System is overloaded, queuing requests
→ Action: Scale out, optimize hot path, reduce lock contention

If throughput UP and latency DOWN:
→ Good: Real improvement in utilization
→ Likely: Better thread scheduling, virtual threads
```

### 4.4 Interpreting Throughput Changes

```
IF throughput < 700 req/sec (<20% worse than 950 baseline):
  → Regression detected
  → Check for new locks/synchronization
  → Verify database connection pool not bottleneck
  → Profile lock contention with JFR
  → Check GC pause max (causing request queueing)
  
ELSE IF throughput > 1100 req/sec (>15% better):
  → Improvement! Likely from:
     - Virtual threads (Variant B)
     - Reduced lock contention
     - Better connection pooling
  → Verify latency didn't increase (not just queueing)
  
ELSE IF throughput within ±15% of baseline:
  → Normal, acceptable variance
  → Check if variance due to different workload profile
```

### 4.5 Load Profile Impact

Throughput varies significantly based on what users are doing:

| Load Type | Example | Expected Throughput |
|---|---|---|
| **Read-heavy** (GET /owners) | 80% reads, 20% writes | 1200-1400 req/sec |
| **Balanced** (CRUD mix) | 50% reads, 50% writes | 900-1100 req/sec |
| **Write-heavy** (POST /owners) | 20% reads, 80% writes | 600-800 req/sec |
| **Complex** (Multi-step workflows) | Transactions, locks | 400-600 req/sec |

**In your benchmark suite:** Check what JMeter test plan is configured
```
See: src/test/jmeter/petclinic_test_plan.jmx
Default: 500 threads, 10s ramp-up, mix of endpoints
→ Measures typical (balanced) workload, not worst-case
```

---

## 5. Memory Metrics

### 5.1 What Memory Metrics Measure

Java applications use memory for:
- **Heap**: Objects, caches, data structures
- **Off-heap**: NIO buffers, metadata
- **Stack**: Local variables, method calls (per thread)

For benchmarking, we track **heap usage** primarily.

```
Memory Timeline:
─────────────────────────────────────────────────
Start
  ↓ [JVM loads classes]      (30-50 MB)
  ├─ [Spring initializes]    (80-100 MB additional)
  ├─ [Connection pool opens] (20-30 MB additional)
  ├─ [Cache initialized]     (40-60 MB additional)
  ↓ [Idle, no requests]      (120-150 MB total) ← Measure here

  Load test starts (300 requests/sec)
  ↓ [Objects created per request]
  ├─ [Queries return result sets]
  ├─ [Caches fill]
  ├─ [String pooling]
  ↓ [Peak memory during sustained load] (300-350 MB) ← Measure here

  Load test ends
  ↓ [GC collects garbage]
  └─ [Return to baseline]     (120-150 MB again) ← Measure stability

Peak: 300-350 MB
Leaked? If drops < 150 MB after GC, no leak
```

### 5.2 Memory Metrics by Scenario

| Scenario | Heap Usage | Typical Duration | What to Measure |
|---|---|---|---|
| **Idle (Baseline)** | 120-150 MB | 5 min post-startup | "How much memory per app instance?" |
| **Light Load** | 180-220 MB | While ~100 req/sec | "Cache/buffer growth?" |
| **Sustained Load** | 250-300 MB | 10 min at 300 req/sec | "Peak memory, GC behavior" |
| **Spike Load** | 300-380 MB | Brief 30s burst | "Max heap before OOM risk" |

### 5.3 Expected Ranges for PetClinic

**Java 17 Baseline (H2 in-memory DB):**
```
Idle:           130 MB ± 10 MB
Moderate load:  210 MB ± 20 MB
Peak (300 req/s): 340 MB ± 30 MB
```

**Java 21 Variant A (Same GC, slightly optimized):**
```
Idle:           125 MB ± 10 MB  (-4%)
Moderate load:  200 MB ± 20 MB  (-5%)
Peak:           320 MB ± 30 MB  (-6%)
Reason: Better class layout, reduced boxing overhead
```

**Java 21 Variant B (Virtual Threads):**
```
Idle:           130 MB ± 10 MB  (Similar)
Moderate load:  220 MB ± 20 MB  (+5%) [More concurrent objects]
Peak:           350 MB ± 30 MB  (+3%)  [Virtual threads don't share thread stacks]
Reason: Virtual threads have separate memory space, add ~8KB per thread
```

### 5.4 Per-Thread Memory Breakdown

Understanding memory cost of concurrency:

```
Platform Thread (Java 17):
- JVM stack:        1-2 MB (fixed per thread)
- Local variables:  ~1-5 KB per thread
- ThreadLocal data: varies
Total per thread:   ~1-2 MB
Consequence: 100 threads = 100-200 MB overhead

Virtual Thread (Java 21):
- JVM stack:        Not allocated until first use
- Local variables:  ~1-5 KB per thread (same)
- ThreadLocal data: varies
Total per thread:   ~8-16 KB initially (grows dynamically)
Consequence: 1000 threads = 8-16 MB overhead (10x reduction!)
```

### 5.5 Interpreting Memory Changes

```
IF idle_heap > 160 MB (+23% worse than 130 baseline):
  → Suspect: New Spring component auto-loading
  → Check: New @Bean instantiations at startup
  → Measure: ./mvnw dependency:analyze, check for unused deps
  → Profile: jcmd <pid> GC.class_histogram | head -20
    (identifies largest objects)
  
ELSE IF idle_heap < 110 MB (-15% vs baseline):
  → Good: Less memory usage at baseline
  → Verify: No critical component skipped
  → Check: Caches intentionally disabled?
  
ELSE IF peak_heap grows > 400 MB (exceed platform limit):
  → Critical: Heap too small or memory leak
  → Action: Analyze dump with Eclipse Memory Analyzer
  → Command: jcmd <pid> GC.heap_dump filename=heap.hprof
  
IF heap doesn't drop after load stops (stays >300 MB):
  → Probable memory leak
  → Collect multiple heap dumps at 5min intervals
  → Analyze with Eclipse MAT: Find retained objects
```

### 5.6 Memory Leak Detection Workflow

```bash
# 1. Record memory usage during test
jcmd <pid> JFR.start name=memory_test duration=300s settings=profile

# 2. Run load test
# (100 req/sec for 5 minutes)

# 3. Force GC and measure
jcmd <pid> GC.run
sleep 2
jcmd <pid> GC.heap_info

# 4. Compare memory before/after:
# If after GC < 150 MB:  No leak (normal)
# If after GC > 300 MB:  Memory leak likely

# 5. If leak suspected:
jcmd <pid> GC.heap_dump filename=heap-after-gc.hprof
# Use Eclipse MAT to analyze
```

### 5.7 Memory Optimization Rules of Thumb

| Rule | Applies When | Action |
|---|---|---|
| **Idle > 150 MB** | Every instance wastes memory | Disable unused Spring modules |
| **Peak > 400 MB** | Scaling costs increase | Review object pools, cache sizes |
| **Variance > 100 MB** | GC tuning suboptimal | Adjust -Xms, -Xmx, ZGC settings |
| **Per-thread > 2 MB** | (Platform threads) Thread pool too large | Scale to 50-100 threads max |
| **Per-thread > 100 KB** | (Virtual threads) Unusual usage | Check for ThreadLocal leaks |

---

## 6. Garbage Collection Metrics

### 6.1 What GC Metrics Measure

**Garbage Collection** = when JVM stops everything to clean up unused objects from memory.

```
Timeline of single GC pause:
─────────────────────────────────────────────
Application running normally
│
├─ Heap fills up (decision to GC)
├─ All threads pause (STOP THE WORLD)
├─ GC thread finds unused objects
├─ GC thread removes them
├─ All threads resume
│
└─ Application continues (pause was 12ms)

Impact: For 12ms, app doesn't respond to requests
If GC pause = 12ms and user made request at that moment:
  Request latency increased by 12ms!
```

### 6.2 GC Pause Metrics

| Metric | Unit | Meaning | Impact |
|---|---|---|---|
| **GC Pause Avg** | ms | Average pause per GC event | Baseline for normal delays |
| **GC Pause Max** | ms | Longest pause | Tail latency (P99) correlation |
| **GC Pause P95** | ms | 95th percentile pause | Most pauses under this |
| **GC Frequency** | count/min | How often GC runs | Heap pressure indicator |
| **GC Time %** | % | Total time paused per minute | System impact (ideally < 5%) |

### 6.3 Expected Ranges for PetClinic

**Java 17 Baseline (G1GC, default):**
```
GC pause avg:     10-15 ms
GC pause max:     45-60 ms
GC pause P95:     25-35 ms
GC frequency:     2-4 per minute
GC time/min:      0.3-0.6% of CPU time (normal)
```

**Java 21 Variant A (G1GC, tuned):**
```
GC pause avg:     8-12 ms    (-20%)
GC pause max:     35-45 ms   (-25%)
GC pause P95:     20-28 ms   (-20%)
GC frequency:     2-4 per minute (same)
GC time/min:      0.2-0.4% of CPU time (improved)
Reason: Better string deduplication, improved G1GC heuristics
```

**Java 21 Variant B (ZGC if available, or G1GC):**
```
With ZGC:
  GC pause avg:   2-5 ms    (-60%)
  GC pause max:   10-20 ms  (-75%)
  GC frequency:   1-2 per minute
  GC time/min:    0.2% of CPU time
  Reason: Concurrent GC, pauses mostly ~1ms

With G1GC:
  Similar to Variant A but potentially
  better due to virtual thread efficiency
```

### 6.4 Relationship Between GC and Latency

```
Correlation analysis (during 10min load test):
- GC pause max:     55 ms
- Latency P99:      92 ms
- Difference:       37 ms

Interpretation:
- When a request hits during a GC pause (55ms),
  plus normal processing time (37ms) = 92ms latency
- If GC pauses < 5ms, worst case P99 → 42ms
- GC improvements directly reduce tail latency
```

### 6.5 GC Tuning Indicators

```
GOOD GC Profile:
├─ Pause max < 30ms  (doesn't ruin P99 latency)
├─ Frequency < 4/min (memory usage healthy)
├─ Avg pause < 10ms  (consistent)
└─ Time% < 1%        (not burning CPU on GC)

BAD GC Profile (Investigate):
├─ Pause max > 100ms  → Heap too small for load
├─ Frequency > 10/min → Memory leak or object churn
├─ Avg pause > 30ms   → GC tuning needed
└─ Time% > 5%         → GC is bottleneck, scale up

Recommendation by symptom:
  High frequency:  Increase -Xmx heap size
  High pause time: Switch to ZGC if Java 21+
  Intermittent:    Check for Full GC events (bad)
```

### 6.6 Interpreting GC Changes

```
IF GC pause max increased from 45ms to 70ms (+55%):
  → Likely cause: Heap growing, more objects to collect
  → Action 1: Check idle_heap metric (memory section)
  → Action 2: Measure GC.heap_info to see fragmentation
  → Action 3: If frequency also UP, likely memory leak

ELSE IF GC pause avg increased but frequency DOWN:
  → May indicate: GC tuning compensating (fewer, longer pauses)
  → This is acceptable if GC time% stays < 3%
  → Monitor to ensure doesn't get worse

ELSE IF GC pause max < 5ms (Java 21 with ZGC):
  → Excellent: Concurrent GC working
  → Latency P99 significantly improved
  → Virtual threads now practical for high concurrency
```

### 6.7 Advanced GC Analysis

**Detailed GC timeline from JFR:**
```bash
# Extract GC events from JFR recording
jfr dump --events 'jdk.GarbageCollection' recording.jfr > gc-events.txt

# Analysis:
# - Count "G1 Young Generation" vs "G1 Old Generation"
# - Young gen pauses normal (5-10ms)
# - Old gen pauses concerning (30-60ms)
# - Frequent old gen GC = heap too small

# For Variant B with ZGC:
# - Look for "ZGC Concurrent" (should be most events)
# - "ZGC Pause" should be < 5ms
```

---

## 7. Thread Count Metrics

### 7.1 What Thread Metrics Measure

**Thread count** = how many concurrent tasks the JVM can handle efficiently.

```
Platform threads (Java 17):
- 1 OS thread per Java thread
- 1-2 MB memory per thread
- Expensive context switch (~1000 CPU cycles)
- Max practical: 200-500 threads before overhead dominates
- Cost: Server with 200 threads needs expensive CPU

Virtual threads (Java 21):
- Millions of lightweight fibers in JVM
- ~8-16 KB memory per thread initially
- Negligible context switch cost
- Practical: 10,000+ threads
- Cost: Same server resources, ~50x more concurrency
```

### 7.2 Expected Thread Counts

| Variant | Platform Threads | Virtual Threads | Throughput | Load Capacity |
|---|---|---|---|---|
| **Java 17 Baseline** | 180-220 | N/A | 950 req/sec | 100 users @ 10 req/sec |
| **Java 21 Variant A** | 180-220 | N/A | 950 req/sec | Same as Java 17 |
| **Java 21 Variant B** | 50-80 | 1000-5000 | 1200+ req/sec | 500+ users @ 10 req/sec |

### 7.3 Platform Thread Breakdown

From JFR data, platform threads fall into categories:

```
Total platform threads: 200

Breakdown:
├─ HTTP handling:     100 (tomcat-exec-1 through tomcat-exec-100)
├─ Database pool:     20 (hikariPool-1 through hikariPool-20)
├─ Background tasks:  5 (scheduled executor)
├─ GC threads:        20 (G1GC parallel threads)
└─ JVM/System:        55 (JIT compiler, monitoring, etc)
```

### 7.4 Virtual Thread Adoption Metrics

When measuring virtual thread improvements, track:

| Metric | Meaning | Expected Improvement |
|---|---|---|
| **Max concurrent tasks** | Total virtual threads created during test | 2000-5000 (10-20x vs platform) |
| **Threads per CPU core** | CPU utilization efficiency | 20-50 per core (vs 2-3 for platform threads) |
| **Context switch rate** | OS-level context switches | -80% to -95% reduction |
| **Stack allocation** | Memory for thread stacks | -90% reduction |

### 7.5 Interpreting Thread Metrics

```
IF platform_thread_count > 250:
  → Possible issue: Thread pool too large
  → Check Tomcat executor configuration
  → Verify not creating new threads per request
  → Action: Tune server.tomcat.threads.max

ELSE IF virtual_thread_count < 500 (Variant B):
  → Underutilized: Could handle more concurrency
  → Increase load in benchmark
  → But: If latency good at 500 threads, enough for use case

ELSE IF virtual_thread_count > 5000 (Variant B):
  → Possible memory overhead if idle
  → Check if threads actually being used
  → Idle virtual threads consume minimal memory, so OK
  
IF context_switch_rate SAME for Variant B vs A:
  → Variant B not benefiting from virtual threads
  → Likely: Blocking I/O or synchronization still dominating
  → Action: Measure blocking events (next section)
```

### 7.6 Thread Efficiency Metrics

Calculate how effectively threads are being used:

```
Efficiency = (Throughput / Thread Count) × 100

Java 17:     (950 req/sec / 200 threads) × 100 = 4.75 req/sec per thread
Java 21 Var B: (1250 req/sec / 50 visible platform threads) × 100 = 25 req/sec
             (But backed by 3000 virtual threads, more fair comparison)
             (1250 req/sec / 3000 threads) × 100 = 0.42 req/sec per thread
             ↑ Virtual threads lower individual utilization,
               but total capacity 30% higher with similar memory

Rule of thumb:
- Platform: Aim for 5-8 req/sec per thread
- Virtual:  Expect 0.3-1 req/sec per thread (it's OK, design is different)
```

---

## 8. Blocking Event Metrics

### 8.1 What Blocking Events Measure

**Blocking events** = moments when a thread must wait (for lock, I/O, etc.) instead of processing requests.

```
Timeline of blocked thread:
─────────────────────────────────────────────
Thread A is running
  │
  ├─ Needs data from database
  ├─ Database query is slow (50ms)
  ├─ Thread A BLOCKS (can't proceed)
  │  └─ CPU switches to Thread B (if available)
  ├─ 50ms later: Database returns
  ├─ Thread A resumes
  └─ Total blocked: 50ms

Impact: During blocking, thread can't serve other users
Solution: More threads to handle waiting
Virtual threads: Designed to minimize blocking impact
```

### 8.2 Types of Blocking Events

| Blocking Type | Cause | Impact | Example | Java 21 Improvement |
|---|---|---|---|---|
| **I/O Wait** | Database, network, disk | Request latency + | SELECT queries | -15% I/O time |
| **Lock Contention** | Synchronized code, locks | Request latency + | Cache updates | -60% with virtual threads |
| **Thread Park** | Waiting for notification | Request latency + | Message queue | -80% with virtual threads |
| **Monitor Wait** | Object.wait() | Request latency + | Synchronized collections | -70% with virtual threads |

### 8.3 Expected Blocking Event Counts

**Java 17 Baseline (H2 in-memory, low I/O):**
```
I/O events:          500-800 per minute
Lock contentions:    50-150 per minute
Thread parks:        100-200 per minute
Monitor waits:       20-50 per minute
Total blocks:        700-1200 per minute
```

**Java 21 Variant A (Same architecture):**
```
Blocking events:     700-1200 per minute (unchanged)
Reason: No virtual thread benefits
```

**Java 21 Variant B (Virtual Threads):**
```
I/O events:          500-800 per minute (same)
Lock contentions:    10-30 per minute (-80% !!!)
Thread parks:        10-40 per minute (-80% !!!)
Monitor waits:       2-10 per minute (-80% !!!)
Total blocks:        520-900 per minute (-25%)
Reason: Virtual threads don't block OS threads, run efficiently
```

### 8.4 Why Blocking Events Matter

```
Blocking event = WASTED THREAD TIME

Example with 100 platform threads:
├─ Server handles 100 concurrent users
├─ User A: "Get my pet details" (40ms of I/O wait)
│  └─ Thread 1 BLOCKED during 40ms
│  └─ Can't serve anyone else for 40ms
├─ User B: Same request, also blocks Thread 2
├─ ... 100 users, 100 threads blocked
└─ Total throughput: Limited by I/O, not processing

With virtual threads:
├─ Server handles 10,000 concurrent users
├─ 10,000 virtual threads (backed by ~50 OS threads)
├─ When User A blocks, OS thread handles User B instead
├─ Virtual threads yield CPU while blocked (no waste)
├─ Total throughput: 15-30% higher despite same I/O
└─ Reason: Better utilization of limited OS threads
```

### 8.5 Interpreting Blocking Changes

```
IF lock_contentions > 200 per min (Variant A):
  → Concerning: Multiple threads waiting for same lock
  → Likely cause: Shared cache, connection pool
  → Action 1: Profile with JFR to identify hot locks
           jfr dump --events 'jdk.JavaMonitorContentionStart' recording.jfr
  → Action 2: Consider:
        - Partition cache (reduce lock scope)
        - Connection pool increase
        - Non-blocking alternative (ConcurrentHashMap, atomic)
  
ELSE IF lock_contentions HIGH in Variant A but LOW in Variant B:
  → Excellent: Virtual threads benefit
  → Reason: Contention still exists, but not blocking OS threads
  → Throughput should be 15-25% higher
  
IF monitor_waits spike above 100 per min (Variant A):
  → Indicates: Object.wait() called frequently
  → Likely: Conditional queues, shared state waiting
  → Action: Review synchronization strategy
     Consider: java.util.concurrent structures
     Or:      Virtual thread-friendly alternatives
```

### 8.6 Blocking Event Analysis Workflow

**Collect detailed blocking data from JFR:**
```bash
# Extract all blocking events
jfr dump --events 'jdk.JavaMonitorWait,jdk.JavaMonitorContention,jdk.ThreadPark' \
         recording.jfr > blocking-events.txt

# Parse with grep/awk to count:
grep "JavaMonitorContention" blocking-events.txt | wc -l
# → Tells you: How many lock contentions occurred

# Filter by class to find hot spots:
grep "JavaMonitorContention" blocking-events.txt | \
  awk '{print $5}' | sort | uniq -c | sort -rn | head -10
# → Top 10 most contended classes
```

**Interpretation example:**
```
   30 java.util.concurrent.ConcurrentHashMap  ← Major contention
   15 com.github.benmanes.caffeine.cache.Cache
   8  java.util.Collections$SynchronizedMap
   
Analysis: ConcurrentHashMap still has contention (expected)
Action: Consider:
  - Partition into multiple maps
  - Use lockless structures (copy-on-write)
  - For cache: Verify Caffeine settings, not bottleneck
```

---

## 9. Test Suite Metrics

### 9.1 What Test Metrics Measure

**Test metrics** = validate that Spring PetClinic still works correctly in each variant.

| Metric | Measured By | Purpose |
|---|---|---|
| **Pass Rate** | JUnit test results | Regression detection |
| **Execution Time** | JUnit timing | Stability, performance regression |
| **Code Coverage** | JaCoCo report | Quality, unintended changes |

### 9.2 Expected Test Results

**Baseline (Java 17):**
```
Total Tests:    234
Passed:         234 (100%)
Failed:         0 (0%)
Skipped:        0 (0%)
Duration:       145 seconds
Code Coverage:  78.2%
```

**Expected for Java 21 Variants (should be unchanged):**
```
Total Tests:    234
Passed:         234 (100%)    ← MUST be 100% for code quality
Failed:         0 (0%)        ← No regressions allowed
Skipped:        0 (0%)
Duration:       140-150s      (±10% acceptable)
Code Coverage:  78.2%         (within ±1%)
```

### 9.3 Test Execution Time Ranges

| Test Category | Expected Duration | Java 17 | Java 21 Impact |
|---|---|---|---|
| **Unit Tests** | 10-20 sec | 15s | Similar or faster |
| **Integration Tests** | 60-80 sec | 75s | Similar (-5% with optimizations) |
| **Database Tests** | 30-50 sec | 40s | Similar or slower (more concurrency) |
| **Full Suite** | 120-180 sec | 145s | Similar |

### 9.4 Code Coverage Interpretation

```
Coverage of 78% means:
├─ 78% of lines of code tested
├─ 22% untested (dead code, error paths, rarely used features)
│
Known gaps in PetClinic:
├─ Error handling branches (~10% of code)
├─ Admin-only features (~5%)
├─ Legacy code paths (~7%)
└─ These gaps are acceptable (they're non-critical)

Red flag scenarios:
├─ Coverage drops below 75%: New untested code added
├─ Coverage > 85%: Possibly over-testing, diminishing returns
└─ Variance > 3%: Intermittent test failures (timing issues?)
```

### 9.5 Interpreting Test Changes

```
IF pass_rate < 100%:
  → STOP - Regression detected
  → Identify failing test (in JUnit XML report)
  → Example failures to investigate:
     - Thread-related tests (virtual threads may behave differently)
     - Timing-dependent tests (Java 21 faster startup)
     - Concurrent access tests (lock behavior changed)
  
ELSE IF execution_time increases > 15%:
  → Possible causes:
     1. More/slower tests added (check git diff)
     2. Database slower (check variant database config)
     3. GC overhead (check GC metrics)
  → Action: Compare test times by category:
       grep 'classname' test-results.xml | extract timings
       
ELSE IF code_coverage drops > 2%:
  → Investigate new code paths
  → Check if intentional (new feature, removed dead code)
  → Action: JaCoCo HTML report shows which lines uncovered
  
ELSE IF code_coverage increases > 2%:
  → Good: Dead code removed or new tests added
  → Verify intentional (check git diff test files)
```

### 9.6 Test Suite Parsing Workflow

**Extract test results from JUnit XML:**
```bash
# Count test results
grep '<testcase' test-results.xml | wc -l           # Total tests
grep '<failure' test-results.xml | wc -l            # Failures
grep '<skipped' test-results.xml | wc -l            # Skipped

# Extract timings
grep '<testcase' test-results.xml | \
  awk -F'time="' '{print $2}' | awk -F'"' '{sum+=$1; n++} END {print "Avg: " sum/n "s"}'

# Find slowest test
grep '<testcase' test-results.xml | \
  awk -F'time="' '{test=$(NF-2); time=$2; print test, time}' | \
  sort -k2 -rn | head -5
```

**Analyze JaCoCo coverage:**
```bash
# Extract coverage percentage
grep 'LINECOVERAGE' coverage-report.txt | awk '{print $2}'

# Find uncovered classes
find target/site/jacoco -name '*.html' -exec grep 'UNCOV' {} \; | head -10
```

---

## 10. Modernization Metrics

### 10.1 What Modernization Metrics Measure

**Modernization metrics** = quantify the effort and adoption of Java 21+ features in Variant B.

| Metric | Measures | Why It Matters |
|---|---|---|
| **Virtual Thread Constructs** | Usage of `Thread.ofVirtual()`, structured concurrency | Validates that code actually uses new capabilities |
| **LOC Changed** | Lines modified for virtual thread adoption | Quantifies effort/scope of changes |
| **Synchronized→Lock Replacements** | Conversion from synchronized → ReentrantLock | Security, better performance characteristics |
| **Deprecated API Removals** | Old APIs replaced with modern equivalents | Future-proofing codebase |

### 10.2 Expected Modernization Changes

**For Variant B (Java 21 with Virtual Threads):**

```
Virtual Thread Adoption:
├─ New Thread.ofVirtual() usages:        5-15 locations
├─ Structured concurrency (try-with-resources): 3-8 places
├─ Thread pools replaced with virtual:   2-4 places
└─ Total code changes:                   150-300 LOC modified

Lock Contention Reductions:
├─ synchronized → ReentrantLock:        3-5 replacements
├─ synchronized → ConcurrentHashMap:    2-4 replacements
└─ synchronized → AtomicReference:      1-3 replacements

Configuration Changes:
├─ New application properties:           2-5 new settings
├─ Updated JVM arguments:                3-5 additions
└─ spring-boot version upgrades:        Minor version bump
```

### 10.3 Virtual Thread Construct Types

Track these patterns in Variant B code:

```java
// Type 1: Virtual thread creation (explicit)
Thread.ofVirtual()
  .name("worker-", 1)
  .start(task);

// Type 2: Structured concurrency (Java 19+)
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
  var subtask1 = scope.fork(task1);
  var subtask2 = scope.fork(task2);
  scope.join();
  return subtask1.result() + subtask2.result();
}

// Type 3: Virtual thread executors
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// Type 4: Async operations (reactive streams)
Mono.fromCallable(blockingOperation)
  .subscribeOn(Schedulers.fromExecutor(virtualThreadExecutor));
```

Count each type in codebase:
```bash
grep -r "Thread.ofVirtual" src/ | wc -l
grep -r "StructuredTaskScope" src/ | wc -l
grep -r "newVirtualThreadPerTaskExecutor" src/ | wc -l
```

### 10.4 Interpreting Modernization Metrics

```
IF virtual_thread_constructs < 3 (Variant B):
  → Minimal adoption
  → Variant B may not show full benefits
  → Action: Identify blocking operations that should use virtual threads
  → Example: ThreadPoolTaskExecutor for async tasks
  
ELSE IF virtual_thread_constructs > 20:
  → Extensive adoption
  → High confidence in virtual thread benefits
  → Variant B should show 20%+ throughput improvement
  
IF LOC_modified > 500:
  → Major refactoring required
  → Ensure all tests still pass (code coverage stable)
  → Spread changes to multiple code reviews
  
ELSE IF LOC_modified < 100:
  → Minimal code changes needed
  → Mostly configuration/annotation changes
  → Easy to merge and deploy
  
IF lock_contention_reductions == 0:
  → Code doesn't have contention to reduce
  → May not benefit much from virtual threads
  → Variant B throughput may be similar to Variant A
```

### 10.5 Modernization Effort Tracking

**Track changes across variants:**

```
Variant A (Traditional Java 21):
- JVM flags optimized:         3 changes
- New dependencies:            0
- Code changes:                0 LOC
- Build time:                  52 sec
- Startup time:                2.87s
- Virtual threads:             N/A

Variant B (Virtual Threads):
- JVM flags optimized:         5 changes (additional)
- New dependencies:            1 (virtual thread framework)
- Code changes:                220 LOC (retrofit for VT)
- Build time:                  55 sec
- Startup time:                2.78s (-3%)
- Virtual threads created:     1200 during 300 req/sec load

Comparison:
├─ Variant B cost: +52 LOC, +3 sec build time, +1 dependency
├─ Variant B benefit: +3% startup improvement, +35% throughput
├─ ROI: High (small effort, significant performance gain)
```

---

## 11. Statistical Significance & Variance

### 11.1 Understanding Variance in Benchmarks

Every benchmark run produces slightly different results due to:

```
Sources of variance:
├─ JVM warmup state (first run always different)
├─ Garbage collection timing (unpredictable)
├─ CPU frequency scaling (thermal throttling)
├─ OS process scheduling (background processes)
├─ Cache effects (CPU L3 cache state varies)
├─ System load (other running processes)
└─ Random number generation (test workload randomization)

Typical variance ranges:
├─ Startup time:     ±5-7% (affected by GC, disk I/O)
├─ Latency:          ±8-12% (affected by CPU, thread scheduling)
├─ Throughput:       ±5-10% (affected by system load)
├─ Memory:           ±10-15% (affected by GC timing, object lifespans)
└─ GC pauses:        ±20-30% (GC is inherently variable)
```

### 11.2 When Is A Difference "Real"?

**Rule of Thumb: 95% Confidence Threshold**

```
Difference is statistically significant if:

|Metric_A - Metric_B| / Metric_A > Threshold
Where threshold depends on variance:

LOW variance (Latency P50):  Threshold = 3% (difference must be > ±3%)
MEDIUM variance (Throughput): Threshold = 8% (difference must be > ±8%)
HIGH variance (GC Max pause): Threshold = 25% (difference must be > ±25%)
```

**Real Examples:**

```
Java 17 vs Java 21 Variant A:
├─ P50 latency: 12ms → 11.4ms (Δ-5%)
├─ Is this real? YES (>3% threshold) → Improvement likely real
│
├─ Throughput: 950 → 945 req/sec (Δ-0.5%)
├─ Is this real? NO (<8% threshold) → Within noise
│
├─ GC pause max: 45ms → 48ms (Δ+6.7%)
├─ Is this real? NO (<25% threshold) → Within GC variance

Java 17 vs Java 21 Variant B:
├─ P95 latency: 55ms → 40ms (Δ-27%)
├─ Is this real? YES (>3%) → Clear improvement
│
├─ Throughput: 950 → 1250 req/sec (Δ+31%)
├─ Is this real? YES (>8%) → Significant improvement
│
├─ Virtual threads: 0 → 3200 (new feature) → Real improvement metric
```

### 11.3 Minimum Runs Required

```
To achieve 95% confidence, run benchmarks multiple times:

Metric type        Minimum runs    Reason
─────────────────────────────────────────────
Startup time       10 runs         High variance (±5-7%)
Latency P50        5-10 runs       Medium variance
Latency P95/P99    10-20 runs      Tail metrics need more data
Throughput         10-15 runs      Need stable server state
Memory metrics     5-10 runs       GC-dependent, needs stabilization
GC pauses          15-20 runs      Very high variance
```

**In practice for PetClinic:**
- **Quick validation**: 2-3 runs per variant (good enough)
- **Engineering decision**: 5-10 runs per variant (recommended)
- **Production decision**: 10-15 runs per variant (required)

### 11.4 Statistical Significance Rules

| Difference | Variance | Real? | Action |
|---|---|---|---|
| 2% | Low (Latency) | Yes | Investigate |
| 2% | High (GC) | No | Ignore |
| 5% | Medium (Throughput) | Maybe | Measure again |
| 10% | Any | Yes | Definitely real |
| 15% | Any | Yes | Certainly real |
| 20%+ | Any | Yes | Major impact |

### 11.5 Detecting Anomalies (Statistical Outliers)

```
In a set of 10 benchmark runs, outliers indicate problems:

Latency results (ms): [52, 54, 48, 198, 55, 49, 50, 195, 51, 52]
                                  ↑ anomaly     ↑ anomaly

Analysis:
├─ Median: 51.5 ms (robust to outliers)
├─ Mean: 75.4 ms (skewed by anomalies)
├─ Std dev: 59 ms (indicates high variance)
├─ Outlier threshold: Q3 + 1.5×IQR = 59 + 1.5×4 = 65.5 ms
├─ Outliers: 195ms, 198ms (above threshold)
│
Action:
├─ Investigate the two 195-198ms responses
├─ Check JFR recording at those timestamps
├─ Likely cause: Full GC pause, major request
├─ Decide: Keep or remove from analysis
│  (Usually: Remove if identifiable cause, report separately)

Report as: P95=55ms (excluding 2 outlier GC pauses)
```

### 11.6 Comparison Framework

**When comparing two variants, use this framework:**

```
Metric: Latency P95
────────────────────────────────────────────
Java 17:        55 ± 3 ms (mean ± std dev)
Java 21 Variant A: 52 ± 3 ms
Java 21 Variant B: 40 ± 2 ms

Statistical test (two-sample t-test):
├─ Variant A vs 17: p-value = 0.08 (NOT significant)
│  └─ Difference within normal variance
├─ Variant B vs 17: p-value < 0.001 (HIGHLY significant)
│  └─ Improvement is real and meaningful

Conclusion:
├─ Java 21 Variant A: No significant latency change
├─ Java 21 Variant B: -27% latency improvement (real, from virtual threads)
```

### 11.7 Confidence Intervals

For reporting results to stakeholders:

```
Instead of: "Throughput is 1250 req/sec"
Report as:  "Throughput is 1250 ± 45 req/sec (95% confidence)"
           OR "Throughput range: 1205-1295 req/sec (95%)"

This communicates:
├─ Most likely value: 1250
├─ Could be as low as: 1205
├─ Could be as high as: 1295
├─ We're 95% confident it's in this range
└─ Not a different number each time you measure
```

---

## 12. Analysis Workflows & Charting

### 12.1 Setting Up for Analysis

**Required tools:**
- Spreadsheet software (Excel, Google Sheets, LibreOffice)
- JQ (JSON query tool)
- Gnuplot or Matplotlib (optional, for advanced charting)

**Output files from benchmarks:**
```
benchmark-results/
├── jmh/timestamp/
│   ├── jmh-java17-baseline.json
│   ├── jmh-java21-variant-a.json
│   └── jmh-java21-variant-b.json
├── jfr/timestamp/
│   ├── recording-java17.jfr
│   └── (use jfr dump to extract metrics)
├── load-test/timestamp/
│   ├── jmeter-results.jtl (CSV format)
│   └── jmeter-report.html
├── metrics/timestamp/
│   ├── actuator-metrics.jsonl
│   └── (polling samples)
└── tests/timestamp/
    ├── test-results.xml (JUnit)
    └── jacoco-report/ (HTML)
```

### 12.2 CSV Import & Preparation

**JMeter latency to CSV (for charting):**

```bash
# Export latency percentiles from JMeter results
# Default location: target/jmeter/results.jtl

# Convert to clean CSV:
awk -F',' '{
  if (NR==1) print "Timestamp,Latency_ms,Status,Label"
  else print $1","$2","$5","$3
}' results.jtl > latency-clean.csv

# Alternative: Use JMeter built-in CSV listeners
# Results → save as CSV with columns:
# timeStamp, elapsed, label, responseCode, responseMessage, success
```

**JFR metrics to CSV (for charting):**

```bash
# Export JFR heap usage timeline
jfr dump --events 'jdk.ObjectAllocationInNewTLAB' \
         --stack-depth=0 recording.jfr > allocation.json

# Parse with jq to CSV:
jq -r '.[] | [.startTime, .allocationSize] | @csv' \
   allocation.json > heap-timeline.csv

# For memory-related events:
jfr dump --events 'jdk.GarbageCollection' recording.jfr | \
  jq -r '.[] | [.startTime, .duration, .heapBeforeGC, .heapAfterGC] | @csv' \
  > gc-timeline.csv
```

### 12.3 Charting in Spreadsheets

#### Chart 1: Latency Percentiles Comparison

**Data structure:**
```csv
Variant,P50,P95,P99,Max
Java 17,12,55,95,198
Java 21 Variant A,11,52,88,195
Java 21 Variant B,9,40,68,140
```

**Steps in Excel/Sheets:**
1. Select data range (A1:E4)
2. Insert → Chart
3. Chart type: Column chart (clustered columns)
4. Series: Each variant is a series
5. X-axis labels: P50, P95, P99, Max
6. Add data labels showing values
7. Format:
   - Y-axis: "Latency (ms)", 0-250 range
   - Title: "Latency Percentiles by Java Variant"
   - Add trendline showing improvement

**Visual result:**
```
Latency Percentiles Comparison

250 |
200 |     ╱╲ Java17
150 |    ╱ ╲╱╲
100 |   ╱   ╲╱╲ Variant A
 50 | ╱╲╱╱╱╱╱╱╲ Variant B
  0 |___________________________
      P50  P95  P99  Max
      
Key insight: P95/P99 show biggest improvement (Variant B)
```

#### Chart 2: Throughput Over Time (Load Test)

**Data structure** (from JMeter running average):
```csv
Time_sec,Java17_reqsec,VariantA_reqsec,VariantB_reqsec
0,0,0,0
10,200,200,210
20,400,395,410
...
300,900,900,1100
```

**Steps in Excel/Sheets:**
1. Select data range
2. Insert → Chart
3. Chart type: Line chart (smooth lines)
4. Series: Each variant is a line
5. X-axis: Time (seconds), 0-300
6. Y-axis: "Throughput (req/sec)", 0-1400 range
7. Format:
   - Different line colors per variant
   - Add legend
   - Title: "Throughput Stability During 5-Minute Load Test"

**Interpretation tips:**
- Flat line = stable throughput (good)
- Declining line = saturation reached (expected)
- Jagged line = variance/instability (investigate)
- Variant B line higher = throughput improvement

#### Chart 3: Memory Usage Timeline

**Data structure**:
```csv
Time_sec,Idle_MB,Moderate50pct_MB,Peak_MB
0,130,130,130
30,130,130,130
60,130,195,195
120,130,210,210
...
300,130,210,340
post-GC,130,190,150
```

**Steps in Excel/Sheets:**
1. Select memory data
2. Insert → Chart
3. Chart type: Line chart with area fill
4. Show 3 series for each load level
5. X-axis: Time (seconds)
6. Y-axis: "Memory (MB)", 100-400 range
7. Format:
   - Shade area under curves
   - Add horizontal line at 150 MB (baseline)
   - Title: "Heap Memory During Load Test"

**Interpretation:**
```
Memory Timeline Chart:
400 |         ╱╲peak
    |        ╱  ╲___
300 |       ╱     ╲
    |      ╱       ╲
200 |     ╱ 50% load ╲
    |    ╱           ╲___GC happens
100 |___╱________________╲___idle
    0   60      120      180   240
      (time in seconds)

Messages:
- Rises to 340MB under peak load (normal)
- Returns to 130MB after GC (no leak)
- Stable at 130MB idle (good)
```

#### Chart 4: GC Pause Distribution (Histogram)

**Data structure** (from JFR GC events):
```csv
GC_Pause_ms,Frequency_Java17,Frequency_VariantA,Frequency_VariantB
0-5,0,0,50
5-10,20,30,100
10-15,15,15,25
15-20,8,8,5
20-30,4,4,2
30-50,3,2,1
50-100,1,0,0
100+,0,0,0
```

**Steps in Excel/Sheets:**
1. Select pause range + frequency data
2. Insert → Chart
3. Chart type: Histogram (Column chart)
4. X-axis: GC pause bins
5. Y-axis: "Frequency (count)"
6. Format:
   - Side-by-side columns per variant
   - Different color per variant
   - Title: "GC Pause Distribution (n=1000 pauses)"

**Interpretation:**
```
GC Pause Distribution:

Frequency
100 |    ╱╲    Java17 (gray)
 75 |   ╱  ╲   VariantA (blue)
 50 |  ╱    ╲  VariantB (green)
 25 | ╱      ╲╱╲
  0 |________________________
    0-5 5-10 10-15 15-20 20-30 30+
    (GC pause in ms)

Analysis:
- Java17: Spread from 5-100ms (high variance)
- VariantB: Concentrated in 5-15ms (stable)
- VariantB has fewer long pauses (< 30ms)
```

### 12.4 Filtering & Comparison Workflows

**Scenario 1: Isolate GET vs POST endpoints in JMeter data**

```
Raw JMeter data: 10,000 requests mixed endpoints

Goal: Compare latency for only GET /api/owners endpoint

In spreadsheet:
1. Add filter to Label column (Data → Filter)
2. Select only "GET /api/owners" label
3. Re-calculate P95 latency for filtered data
4. Compare before/after filtering

CSV command to extract:
grep "GET /api/owners" jmeter-results.csv > get-owners-only.csv
```

**Scenario 2: Compare Variant A vs B with same load profile**

```
Condition: Only compare results from 300 req/sec load level
(Other load levels might have different characteristics)

Steps:
1. Create separate sheets: VariantA_300reqsec, VariantB_300reqsec
2. Filter JMeter results to only samples during steady-state load
   Time 60-240 seconds (ignore ramp-up 0-60, cooldown after 240)
3. Calculate P95, P99, throughput from each sheet
4. Compare metrics
5. Analyze difference: Is 20% better than Variant A real?
   → Run statistical test or calculate variance
```

**Scenario 3: Track metric trend across multiple test runs**

```
Goal: See if performance degrades with each successive benchmark run

Data structure:
Run#,Time,P95,P99,Throughput,Heap_MB
1,10:00,55,95,950,340
2,10:30,54,94,955,335
3,11:00,56,98,945,345
4,11:30,54,93,960,338
5,12:00,55,96,948,342
```

In spreadsheet:
1. Create chart with Run# on X-axis
2. Plot P95, P99, Throughput, Heap on Y-axis (secondary axes as needed)
3. Add trendline to each metric
4. Interpretation:
   - Flat trendline: Stable performance (good)
   - Upward trendline: Possible memory leak or degradation
   - Downward: System warming up, then stabilizing (expected)

### 12.5 Automated Analysis Script Example

**Script to generate comparison report (Bash + CSV):**

```bash
#!/bin/bash
# generate-comparison-report.sh

JMH_BASELINE="benchmark-results/jmh-java17.json"
JMH_VAR_A="benchmark-results/jmh-java21-vara.json"
JMH_VAR_B="benchmark-results/jmh-java21-varb.json"

# Extract latency metrics
echo "Variant,P50,P95,P99,P99.9" > latency-comparison.csv

for file in $JMH_BASELINE $JMH_VAR_A $JMH_VAR_B; do
  VARIANT=$(basename "$file" | sed 's/.json//')
  P50=$(jq '.[0].primaryMetric.scorePercentiles["50.0"]' "$file")
  P95=$(jq '.[0].primaryMetric.scorePercentiles["95.0"]' "$file")
  P99=$(jq '.[0].primaryMetric.scorePercentiles["99.0"]' "$file")
  P999=$(jq '.[0].primaryMetric.scorePercentiles["99.9"]' "$file")
  
  echo "$VARIANT,$P50,$P95,$P99,$P999" >> latency-comparison.csv
done

echo "Report generated: latency-comparison.csv"
cat latency-comparison.csv
```

---

## 13. Example Dashboards

### 13.1 Dashboard 1: Executive Summary

**Purpose:** Single-page overview for leadership/ops

**Metrics displayed:**
```
┌───────────────────────────────────────────────────┐
│     Spring PetClinic - Performance Baseline       │
│            Java 17 → Java 21 Comparison          │
└───────────────────────────────────────────────────┘

┌─────────────────────┬──────────────────────────┐
│  STARTUP TIME       │  THROUGHPUT (300 req/sec) │
│                     │                          │
│  Java 17: 3.10s     │  Java 17: 950 req/sec   │
│  Variant A: 2.87s   │  Variant A: 950 req/sec │
│  Variant B: 2.78s   │  Variant B: 1250 req/sec│
│  ↓ Improvement: -10%│  ↑ Improvement: +32%    │
└─────────────────────┴──────────────────────────┘

┌─────────────────────┬──────────────────────────┐
│  LATENCY P95        │  HEAP (IDLE)             │
│                     │                          │
│  Java 17: 55ms      │  Java 17: 130MB         │
│  Variant A: 52ms    │  Variant A: 125MB       │
│  Variant B: 40ms    │  Variant B: 130MB       │
│  ↓ Improvement: -27%│  ↓ Improvement: -4%     │
└─────────────────────┴──────────────────────────┘

┌─────────────────────────────────────────────────┐
│  BUSINESS IMPACT SUMMARY                        │
│                                                 │
│  ✓ Variant B shows 32% throughput improvement   │
│  ✓ 27% lower P95 latency (users see faster page)│
│  ✓ Memory usage stable across variants          │
│  ✓ All tests passing (100%)                     │
│  ✓ Code coverage maintained at 78%              │
│                                                 │
│  RECOMMENDATION: Deploy Variant B to production │
│  Expected benefit: 30% more capacity or 20% cost├─┐ 
│                                                 │ │
│  Risk Level: LOW (comprehensive test coverage)  │ │
│  Effort: MEDIUM (220 LOC changes)               │ │
│  Timeline: 2-3 weeks (regression testing)       │ │
└─────────────────────────────────────────────────┘
```

**How to build in Excel:**
1. Create summary table with 3 columns: Metric, Value, Delta%
2. Color cells: Green for improvements, Red for regressions
3. Add sparklines showing trend from run 1→5
4. Include decision boxes (GO/NO-GO criteria)

### 13.2 Dashboard 2: Engineer Deep-Dive

**Purpose:** Detailed analysis for performance engineers

**Metrics displayed:**

```
┌────────────────────────────────────────────────────────┐
│   Spring PetClinic - Detailed Performance Analysis     │
│            Java 21 Variant B Deep-Dive                 │
└────────────────────────────────────────────────────────┘

SECTION 1: Latency Analysis
┌──────────────────────────────────────────┐
│ Latency Percentiles (ms)                 │
│                                          │
│ ╱────────────── Variant A                │
│╱╲                                        │
│  ╲────────────── Baseline (17)           │
│   ╲╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱ Variant B       │
│                                          │
│ P50: 9ms   P95: 40ms   P99: 68ms        │
│ Δ from baseline: -25%, -27%, -28%       │
│                                          │
│ Analysis: P95/P99 tail-latency reduced  │
│ Cause: Better virtual thread scheduling │
└──────────────────────────────────────────┘

SECTION 2: Throughput Under Load
┌──────────────────────────────────────────┐
│ Throughput Saturation Curve              │
│                                          │
│ 1400  ╱─ Variant B                      │
│ 1200 ╱╱─ Variant A                      │
│ 1000╱  ╱─ Baseline                      │
│  800   ╱   (threaded pool limit reached)│
│  600  ╱                                  │
│  400 ╱                                   │
│  200╱                                    │
│    0└──────────────────────────────────┘│
│      50 100 150 200 300 400 500 Threads │
│                                          │
│ Load at 300 threads: 1250 req/sec (VB)  │
│ Platform thread max:  200 threads       │
│ Virtual threads created: 3200           │
│                                          │
│ Analysis: Variant B handles 50% more    │
│ concurrent users without saturation     │
└──────────────────────────────────────────┘

SECTION 3: Memory & GC Efficiency
┌──────────────────────────────────────────┐
│ Heap Usage Timeline (Peak: 350MB limit)  │
│                                          │
│ 350  ╱╲╱╲╱╲╱╲ Variant A (spike-based GC)│
│ 300 ╱  ╲╱  ╲╱ Variant B (smoother, ZGC?)│
│ 250                                      │
│ 200                                      │
│ 150                                      │
│ 100────────────────────────────────────  │
│   0└──────────────────────────────────┘  │
│    0    60    120   180    240   300 sec │
│                                          │
│ Pauses: Variant A: 10-50ms               │
│         Variant B: 2-10ms (95% within 5) │
│ Frequency: 2-4 GC/min (both stable)      │
│                                          │
│ Analysis: Variant B with ZGC provides   │
│ sub-5ms GC pauses, improves tail latency│
└──────────────────────────────────────────┘

SECTION 4: Lock Contention (Virtual Thread Benefit)
┌──────────────────────────────────────────┐
│ Lock Contention Events Over 10-Min Test  │
│                                          │
│ Baseline:     125 contention events      │
│ Variant A:    130 contention events (+4%)│
│ Variant B:    18 contention events (-85%)│
│                                          │
│ Analysis: Virtual threads reduce        │
│ practical lock contention despite same  │
│ synchronized code (non-blocking yield)  │
│                                          │
│ Hot locks identified:                   │
│ - java.util.ConcurrentHashMap: 10 events│
│ - Caffeine cache: 5 events              │
│ - Connection pool: 3 events             │
│                                          │
│ Recommendation: These locks now          │
│ acceptable with virtual threads         │
└──────────────────────────────────────────┘

SECTION 5: Code Coverage & Quality
┌──────────────────────────────────────────┐
│ Test Suite Results                       │
│                                          │
│ Baseline:   234 tests, 100% pass, 78%  │
│ Variant B:  234 tests, 100% pass, 78%  │
│                                          │
│ ✓ No regressions detected                │
│ ✓ Coverage stable                        │
│ ✓ Execution time: 145s (±5%)            │
│ ✓ New virtual thread constructs: 12     │
│ ✓ Code changes: 220 LOC                  │
│                                          │
│ Confidence level: HIGH (comprehensive)  │
└──────────────────────────────────────────┘

CONCLUSION: Variant B ready for production
```

**How to build in Excel:**
1. Create multiple named ranges for different sections
2. Use Data → Sparklines to add small graphs in cells
3. Conditional formatting for anomalies (red highlight)
4. Embed small charts for each section
5. Use cross-references to main data sheets

### 13.3 Dashboard 3: Troubleshooting Decision Tree

**Purpose:** Quick diagnostic guide for ops/on-call

**Format:**

```
┌─────────────────────────────────────────────────┐
│   Troubleshooting Dashboard - Quick Diagnostics │
└─────────────────────────────────────────────────┘

START: Something seems wrong with performance
│
├─ Users reporting slowness?
│  ├─ YES: Check Latency P95 metric
│  │  ├─ P95 > 75ms? (20% worse than baseline)
│  │  │  ├─ Check: Database query performance
│  │  │  ├─ Check: GC pause max time
│  │  │  ├─ Check: Lock contention count
│  │  │  └─ Action: Profile with JFR (see troubleshooting section 14)
│  │  │
│  │  └─ P95 < 75ms? (within baseline variance)
│  │     └─ Likely: User's internet, not our app
│  │
│  └─ NO: Continue diagnostic
│
├─ Handling fewer requests than expected?
│  ├─ YES: Check Throughput metric
│  │  ├─ Throughput < 700 req/sec? (>25% worse)
│  │  │  ├─ Check: Thread count (near limit?)
│  │  │  ├─ Check: Database connection pool exhausted?
│  │  │  ├─ Check: CPU utilization (100% → bottleneck elsewhere)
│  │  │  └─ Action: Scale horizontally (add servers)
│  │  │
│  │  └─ Throughput OK?
│  │     └─ Capacity not the issue
│  │
│  └─ NO: Continue diagnostic
│
├─ Application memory usage growing?
│  ├─ YES: Check Heap metric (idle, moderate load, peak)
│  │  ├─ Heap grows after GC? (never drops below 200MB)
│  │  │  ├─ Suspected memory leak
│  │  │  ├─ Action: Collect heap dump
│  │  │  ├─ Tool: jcmd <pid> GC.heap_dump filename=heap.hprof
│  │  │  └─ Analysis: Eclipse MAT to find retained objects
│  │  │
│  │  └─ Heap drops to 130MB after GC?
│  │     └─ Normal behavior, no leak
│  │
│  └─ NO: Memory usage stable (good)
│
└─ All metrics normal
   └─ Issue likely external (network, database, client-side)

─────────────────────────────────────────────────
METRIC QUICK REFERENCE

If → Action:
───────────────────────────────────────────────
P95 latency ↑ 20%    → Check GC pauses, database
Throughput ↓ 25%     → Check thread count, CPU
Heap ↑ after GC      → Investigate memory leak
GC pause > 100ms     → Consider ZGC or heap size
Lock contention ↑    → Profile synchronized sections
Test failures        → Variant-specific compatibility
Thread count ↑ 2x    → Thread pool misconfig or leak

─────────────────────────────────────────────────
STATUS INDICATORS

🟢 GREEN (OK):
  - Metrics within ±15% of baseline
  - All tests passing
  - Smooth metric curves (no anomalies)
  - Heap returns to baseline after load

🟡 YELLOW (INVESTIGATE):
  - Metrics 15-25% off baseline
  - 1-2 test failures (flaky)
  - Occasional spikes in metrics
  - Heap slow to recover after load

🔴 RED (CRITICAL):
  - Metrics > 25% worse
  - Multiple test failures
  - Constant metric anomalies
  - Heap keeps growing (memory leak)
  - Throughput < 600 req/sec

─────────────────────────────────────────────────
COMMON ISSUES & SOLUTIONS

Issue: "P95 latency jumped from 55ms to 85ms"
Likely: GC pause spike or database query regression
Check: SELECT * FROM slow_query_log LIMIT 5
Fix:   Add database index, tune GC parameters

Issue: "Throughput dropped from 950 to 600 req/sec"
Likely: Thread pool exhausted or CPU bottleneck
Check: jcmd <pid> Thread.print | grep "tid"
Fix:   Increase tomcat.threads.max, scale horizontally

Issue: "Memory grew from 200MB to 400MB overnight"
Likely: Memory leak or cache misconfiguration
Check: jcmd <pid> GC.heap_dump filename=heap.hprof
Fix:   Analyze dump, find retained objects, plug leak

Issue: "All metrics look OK but app feels slow"
Likely: Not app performance, external bottleneck
Check: curl -w "@curl-format.txt" https://app/api/health
       Check response headers: includes network latency
Fix:   CDN, caching, reduce payload size
```

---

## 14. Troubleshooting Guide

### 14.1 Problem 1: High Latency Variance (P99 >> P95)

**Symptom:**
```
Latency P50: 12ms (stable)
Latency P95: 55ms (stable)
Latency P99: 150ms (SPIKE!)
Max latency: 250ms
```

**Why this indicates a problem:**
```
Normal distribution:
├─ P95 ~2x P50 (expected: 55 vs 12)
├─ P99 ~2.5x P50 (expected: 30 vs 12) [if smooth]
└─ Your P99 ~12.5x P50 (observed: 150 vs 12) [UNUSUAL]

Indicates: Occasional major slowdowns
```

**Diagnosis Steps:**

```bash
# Step 1: Identify WHEN outliers occur
# Extract latency timeline from JMeter
awk -F',' '$2 > 150 {print $1}' jmeter-results.csv | \
  sort | uniq -c
# Output:
# 10:30:42 - 5 slow requests
# 10:30:50 - 8 slow requests
# 10:31:05 - 12 slow requests
# Pattern: Every ~13 seconds = periodic event

# Step 2: Check JFR recording at those timestamps
# Most likely cause: GC pause (every 10-15 seconds)
jfr dump --events 'jdk.GarbageCollection' recording.jfr | \
  grep -A5 "10:30:42"

# Step 3: Correlate with GC timeline
# If GC pause timestamp matches high latency timestamp
# → Confirmed: GC pause is culprit
```

**Root Causes & Fixes:**

| Root Cause | Detection | Fix |
|---|---|---|
| **GC pause** | Matches JFR GC timestamps | Use ZGC, increase heap -Xmx |
| **Full GC** | Pause > 100ms | Increase young gen size -Xmn |
| **Lock contention** | Other threads blocked | Reduce synchronized sections |
| **DB query timeout** | Query log shows slow query | Add index, optimize query |
| **Network blip** | Network packet loss | Not app's issue |

**Example Remediation:**

```bash
# If GC identified as culprit:

# Current JVM args
java ... -Xms256m -Xmx512m ...
# Changes for Java 21:
java ... -Xms512m -Xmx1024m \
         -XX:+UnlockExperimentalVMOptions \
         -XX:+UseZGC ...
# Result: GC pauses drop from 45ms to 5ms
```

---

### 14.2 Problem 2: Unexpected Throughput Drop

**Symptom:**
```
Expected baseline: 950 req/sec
Actual measurement: 650 req/sec (-31%)
Variance: Too large to be normal
```

**Diagnosis Steps:**

```bash
# Step 1: Check if load ramped up smoothly
# JMeter should ramp from 0 to 300 threads over 10 seconds
# Plot thread count over time
awk -F',' '{print $1, $NF}' jmeter-results.csv | \
  sort | uniq -c | \
  awk '{print $2, $3, $NF}'

# Expected output (thread count grows):
# 10:30:00 50
# 10:30:02 100
# 10:30:04 150
# 10:30:06 200
# 10:30:08 250
# 10:30:10 300

# If thread count doesn't match → JMeter misconfigured

# Step 2: Check if requests are queuing
# Low throughput + high P99 latency = Queueing
if [ P99 > 150ms ] && [ throughput < 700 ]; then
  echo "Symptom: Requests queueing (server overloaded)"
  # Check 1: Thread pool size
  jcmd <pid> Thread.print | grep "tomcat-exec" | wc -l
  # If count near config limit (e.g., 100) → FOUND PROBLEM
  
  # Check 2: Connection pool
  jcmd <pid> VM.system_properties | grep "pool"
  # Check if connections exhausted
fi

# Step 3: Check CPU utilization
# If CPU < 50% but throughput low → I/O bottleneck
# If CPU > 95% but throughput low → CPU bottleneck
```

**Root Causes & Fixes:**

| Root Cause | Detection | Fix |
|---|---|---|
| **Thread pool too small** | Thread count near max, requests queue | Increase `server.tomcat.threads.max` |
| **Connection pool too small** | DB connection count at max | Increase `spring.datasource.hikari.maximum-pool-size` |
| **Database slow** | Slow query log full | Profile queries, add indexes |
| **Memory pressure** | GC frequency high, heap full | Increase heap size -Xmx |
| **CPU bottleneck** | CPU always 100% | Optimize hot methods, profile |

**Example fix:**

```properties
# application.properties

# Before (default):
server.tomcat.threads.max=200
spring.datasource.hikari.maximum-pool-size=10

# After (optimized for 300 concurrent):
server.tomcat.threads.max=300
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=10
```

---

### 14.3 Problem 3: Memory Leak Detection

**Symptom:**
```
Idle heap:      130 MB ✓
After 1-min load: 250 MB ✓
After load stops:  240 MB ✗ (should drop to 130 MB)
After 5 more min: 300 MB ✗ (keeps growing!)
```

**Diagnosis Steps:**

```bash
# Step 1: Confirm GC is running and freeing memory
jcmd <pid> GC.run
sleep 2
jcmd <pid> GC.heap_info

# Output after GC:
# [Heap]
#  def new generation   total 157248K, used 12345K
#  Eden space  allocated 131072K, used 10000K
#  Survivor space allocated 26176K, used 2345K
#  tenured generation   total 349568K, used 100000K

# Interpretation:
# - If stays at 240 MB after GC → Memory leak
# - If drops to 130 MB after GC → Normal (no leak)

# Step 2: Collect heap dump
jcmd <pid> GC.heap_dump filename=/tmp/heap-$(date +%s).hprof

# Step 3: Analyze with Eclipse MAT
# Download: https://www.eclipse.org/downloads/download.php
# Open: /tmp/heap-*.hprof
# Look for:
# - "Retained Objects" (objects keeping memory alive)
# - "Leak Suspects" (obvious leaks)
# - ClassHistogram (which classes consuming memory)

# Step 4: Identify pattern (collect 3-5 dumps at 5min intervals)
# If same objects appear in all dumps → Leak
# If different objects each time → GC-related variance
```

**Common Memory Leaks in Spring Apps:**

| Leak Pattern | Cause | Fix |
|---|---|---|
| **Cache grows unbounded** | No eviction policy | Enable TTL, size limits in Caffeine |
| **Listener never unregistered** | Event listener left in registry | Use `@PreDestroy` to clean up |
| **ThreadLocal not cleared** | Application thread pool | Clear in finally block |
| **Connection pool leaks** | Connections not returned | Verify connection.close() called |
| **File handle leak** | Streams not closed | Use try-with-resources |

**Example fix for cache leak:**

```java
// Before (unbounded growth):
@Bean
public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager("pets", "owners");
}

// After (with eviction):
@Bean
public CacheManager cacheManager() {
    return Caffeine.newBuilder()
        .maximumSize(1000)                    // ← Limit size
        .expireAfterWrite(10, TimeUnit.MINUTES) // ← Expire old entries
        .recordStats()
        .build();
}
```

---

### 14.4 Problem 4: Variant B (Virtual Threads) Not Showing Expected Improvement

**Symptom:**
```
Java 17 throughput:        950 req/sec
Java 21 Variant A:         950 req/sec (no change as expected)
Java 21 Variant B:         960 req/sec (only 1% improvement, expected 20%!)
Virtual threads created:   1200 (should be 3000+)
Lock contentions:          120 (should be < 30)
```

**Diagnosis:**

```bash
# Step 1: Verify virtual threads are actually being used
grep -r "Thread.ofVirtual" src/
# If output is empty or too few → Not using virtual threads

# Step 2: Check what threads are actually created
jfr dump --events 'jdk.ThreadStart' recording.jfr | \
  grep -o 'thread.*type.*"[^"]*"' | sort | uniq -c

# Expected for Variant B:
# - Mostly "VIRTUAL" thread type
# If mostly "PLATFORM" → Still using platform threads

# Step 3: Check for blocking operations still using platform threads
# Example: Thread pool not configured for virtual threads
jcmd <pid> VM.system_properties | grep "threads"

# Step 4: Profile blocking operations
jfr dump --events 'jdk.JavaMonitorWait,jdk.ThreadPark' \
         --stack-depth=3 recording.jfr > blocking-stack.txt
# Inspect stacks: See if blocking in application code
```

**Root Causes & Fixes:**

| Root Cause | Detection | Fix |
|---|---|---|
| **Thread pool not using virtual threads** | Platform thread count not < 100 | Use `Executors.newVirtualThreadPerTaskExecutor()` |
| **Still using synchronized code** | Lock contention not low | Replace with `ReentrantLock`, `ConcurrentHashMap` |
| **Blocking I/O not optimized** | I/O wait time high | Use virtual threads wrapper, Project Loom async |
| **Not creating enough virtual threads** | Thread count < 500 | Increase load in test, or configure higher limit |

**Example fix for thread pool:**

```java
// Before (platform threads, Variant A):
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.initialize();
    return executor;
}

// After (virtual threads, Variant B):
@Bean
@ConditionalOnProperty(name = "app.variant", havingValue = "virtual-threads")
public TaskExecutor taskExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

---

### 14.5 Problem 5: High GC Pause Times Despite Adequate Heap

**Symptom:**
```
Heap allocated: 1024 MB
Heap used at peak: 600 MB (only 60% full!)
GC pause: 80 ms (much longer than expected)
GC frequency: Every 5 seconds (too frequent)
```

**Diagnosis:**

```bash
# Step 1: Check GC algorithm in use
jcmd <pid> VM.flags | grep -i "gc"

# Typical output:
# -XX:+UseG1GC (or -XX:+UseSerialGC, -XX:+UseParallelGC)

# Step 2: Analyze GC events in detail
jfr dump --events 'jdk.GarbageCollection' recording.jfr > gc-detailed.txt

# Look for:
# - Type: "G1 Young Generation" (fast, 5-20ms expected)
# - Type: "G1 Old Generation" (slow, 30-80ms, indicates trouble)
# - Type: "G1 Full GC" (very slow, 100ms+, bad sign)

# Step 3: Check heap fragmentation
jcmd <pid> GC.heap_info | grep -i "region"

# Example (healthy):
# Heap occupancy: 350 MB / 1024 MB (34%)
# Young regions: 20
# Old regions: 40
# Humongous regions: 2

# Example (problematic):
# Heap occupancy: 350 MB / 1024 MB (34%)
# Young regions: 5
# Old regions: 95 (too many old, causing slow collections)
# Humongous regions: 20 (large objects, hard to move)
```

**Root Causes & Fixes:**

| Root Cause | Detection | Fix |
|---|---|---|
| **Young gen too small** | Many young gen GC | Increase -Xmn (young gen size) |
| **Object promotion too fast** | Tenured generation filling quickly | Tune survival threshold |
| **Large objects (>50% region)** | Humongous regions > 10 | Refactor to avoid huge allocations |
| **Wrong GC algorithm for workload** | Full GC happening | Switch to ZGC (Java 21) |
| **Heap fragmentation** | Pause increases over time | Enable G1 tuning, try ZGC |

**Example JVM tuning for PetClinic:**

```bash
# Before (default):
java -Xms256m -Xmx512m -XX:+UseG1GC ...
# Result: 45ms avg pause

# After (optimized):
java -Xms512m -Xmx1024m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=10 \
     -XX:InitiatingHeapOccupancyPercent=35 ...
# Result: 15ms avg pause

# Best (Java 21 with ZGC):
java -Xms512m -Xmx1024m \
     -XX:+UnlockExperimentalVMOptions \
     -XX:+UseZGC \
     -XX:+ZGenerational ...
# Result: 3ms avg pause
```

---

### 14.6 Diagnostic Checklist

When troubleshooting any performance issue, follow this checklist:

```
[ ] Step 1: Identify affected metric
    - Latency? Throughput? Memory? GC? Threads?
    - Which percentile or scenario?

[ ] Step 2: Establish baseline
    - What was expected?
    - What's actual?
    - Δ = actual - expected
    - Is Δ > variance threshold? (±15% for most metrics)

[ ] Step 3: Collect data
    - JMeter results (jtl file with all requests)
    - JFR recording (1-5 minutes during issue)
    - Application logs (errors, exceptions)
    - System metrics (CPU, memory, disk I/O)
    - Database logs (slow queries)

[ ] Step 4: Correlate events
    - Timeline of metric change
    - Match with other events (GC, network, database)
    - Identify timestamp of issue onset

[ ] Step 5: Form hypothesis
    - Based on correlations
    - From symptoms (latency up = blocking, throughput down = queueing)
    - Validate with data

[ ] Step 6: Test hypothesis
    - Profile specific code section
    - Isolate variable (disable feature, reduce load)
    - Re-run with change applied

[ ] Step 7: Document root cause
    - What was the problem?
    - What caused it?
    - Why did it happen with variant B but not A?

[ ] Step 8: Implement fix
    - Code change, configuration, or environment
    - Re-run full benchmark suite
    - Verify fix doesn't introduce new issue
```

---

## 15. Quick Reference Tables

### 15.1 Metric Health Status

Quick lookup to determine if metric is good or bad:

```
LATENCY (milliseconds)
────────────────────────────────────────────
Metric      Good        OK          Bad
─────────────────────────────────────────────
P50         < 15ms      15-20ms     > 20ms
P95         < 60ms      60-80ms     > 80ms
P99         < 100ms     100-150ms   > 150ms
Max         < 200ms     200-300ms   > 300ms

THROUGHPUT (requests/second at 300 concurrent)
────────────────────────────────────────────────
Metric      Good        OK          Bad
─────────────────────────────────────────────
Load 50%    > 1000      800-1000    < 800
Load 75%    > 800       600-800     < 600
Load 100%   > 500       300-500     < 300

MEMORY (MB)
──────────────────────────────────────────
Metric      Good        OK          Bad
──────────────────────────────────────────
Idle        < 140       140-160     > 160
Peak        < 360       360-400     > 400
Leak test   Drops 80%+  Drops 50%   Drops < 50%

GC PAUSES (milliseconds)
──────────────────────────────────────────
Metric      Good        OK          Bad
──────────────────────────────────────────
Avg pause   < 10ms      10-20ms     > 20ms
Max pause   < 30ms      30-60ms     > 60ms
Freq        < 4/min     4-6/min     > 6/min
Time%       < 1%        1-3%        > 3%

TEST SUITE (%)
──────────────────────────────────────────
Metric      Good        OK          Bad
──────────────────────────────────────────
Pass rate   100%        95-100%     < 95%
Coverage    > 80%       75-80%      < 75%
Time delta  ±5%         ±10%        > ±10%
```

### 15.2 Variance Tolerance by Metric

Expected variance range (±X%) for benchmark runs:

```
METRIC TYPE          VARIANCE    REASON
─────────────────────────────────────────────────
Startup time         ±5%         Disk I/O, GC
Latency P50          ±8%         Cache warming
Latency P95/P99      ±12%        Tail events
Throughput           ±8%         System load
Memory (idle)        ±5%         JVM cache
Memory (peak)        ±15%        GC timing
GC pause avg         ±15%        GC algorithm
GC pause max         ±30%        Outlier-sensitive
Thread count         ±3%         Fixed creation
Blocking events      ±20%        Scheduling
Test execution       ±8%         Load variance
Code coverage        ±2%         Tests are fixed
```

---

## Appendix: Tool Reference

### A.1 Required Tools

| Tool | Purpose | Install |
|---|---|---|
| **JQ** | JSON parsing | `brew install jq` or `apt-get install jq` |
| **JFR** | Flight Recorder analysis | Included in JDK 17+ |
| **JMeter** | Load testing | `brew install jmeter` or download |
| **Excel/Sheets** | Analysis spreadsheets | Microsoft/Google |
| **Eclipse MAT** | Heap dump analysis | https://www.eclipse.org/downloads |
| **Gnuplot** | Advanced charting | `brew install gnuplot` (optional) |

### A.2 Key Commands Quick Reference

```bash
# JFR Recording & Analysis
jcmd <pid> JFR.start name=bench duration=300s
jfr dump recording.jfr > events.json
jfr dump --events 'jdk.GarbageCollection' recording.jfr

# JMeter Analysis
grep "GET /api/owners" jmeter-results.csv > filtered.csv
awk -F',' '{sum+=$2; n++} END {print sum/n}' filtered.csv

# Heap Dump Analysis
jcmd <pid> GC.heap_dump filename=heap.hprof
# Open in Eclipse MAT → Analyze Retained Objects

# Thread Analysis
jcmd <pid> Thread.print > threads.txt
grep "tid" threads.txt | wc -l  # Count threads

# JSON Processing
jq '.[] | {name: .benchmark, score: .primaryMetric.score}' results.json

# Memory Timeline
jcmd <pid> GC.heap_info
jcmd <pid> GC.run; sleep 1; jcmd <pid> GC.heap_info  # After GC
```

### A.3 File Locations

```
Benchmark outputs:
benchmark-results/
├── jmh/TIMESTAMP/
│   ├── jmh-java17-baseline.json
│   ├── jmh-java21-variant-a.json
│   └── jmh-java21-variant-b.json
├── jfr/TIMESTAMP/
│   ├── recording-java17.jfr
│   ├── recording-variant-a.jfr
│   └── recording-variant-b.jfr
├── load-test/TIMESTAMP/
│   └── jmeter-results.jtl
├── metrics/TIMESTAMP/
│   └── actuator-metrics.jsonl
└── tests/TIMESTAMP/
    ├── test-results.xml
    └── jacoco-report/

Analysis templates:
./METRICS-INTERPRETATION-GUIDE.md  (this file)
./scripts/generate-comparison-report.sh  (automated analysis)
./dashboards/ (example Excel templates)
```

---

## Conclusion

This guide provides comprehensive tools for understanding Spring PetClinic benchmark metrics. Key takeaways:

1. **Start with the right metrics**: Latency P95/P99 and throughput matter more than P50 for user experience
2. **Understand variance**: Not all changes are significant; use statistical thresholds
3. **Correlate events**: GC pauses explain latency spikes, memory pressure explains GC frequency
4. **Track trends**: Compare variants A & B to validate Java 21 improvements
5. **Act on data**: Only investigate anomalies that exceed variance thresholds
6. **Document decisions**: Record why issues occurred and how they were fixed

For questions on specific metrics, refer to the detailed sections above. For troubleshooting, follow the decision trees in Section 14.

---

**Document Version:** 1.0
**Created:** [Timestamp]
**Variants Covered:** Java 17, Java 21 Variant A (Traditional), Java 21 Variant B (Virtual Threads)
**Metrics Tracked:** 25+ detailed, grouped into 6 categories
**Troubleshooting Scenarios:** 5 detailed problems + diagnostic checklist
**Dashboard Examples:** 3 complete reference dashboards
