# Blocking Detection & Analysis

## Overview

This document describes the dual blocking detection system implemented for the Spring PetClinic benchmark suite. The system combines **static analysis** (code scanning for potential blockers) with **runtime detection** (JFR event listening for actual blocking) to provide comprehensive blocking pattern analysis across Java 17, Java 21 traditional, and Java 21 virtual thread variants.

## Architecture

### Components

#### 1. **StaticBlockingAnalyzer**
Static code analysis component that scans compiled bytecode and reports for blocking patterns.

**Blocking Patterns Detected:**
- `synchronized_method`: Methods declared with `synchronized` keyword
- `synchronized_block`: Synchronized blocks `synchronized(...)`
- `thread_sleep`: `Thread.sleep()` calls
- `blocking_queue`: `BlockingQueue`/`BlockingDeque` operations (put/take)
- `file_input`: `FileInputStream` and other blocking I/O
- `url_connection`: `URLConnection` and HTTP blocking calls
- `wait_notify`: `object.wait()` and `object.notify()` calls
- `object_lock`: Synchronization on `this` reference

**Output:**
- List of `BlockingFinding` objects with:
  - Pattern type
  - Class name and location
  - Severity level (HIGH/MEDIUM/LOW)
  - Source (STATIC_SCAN or SPOTBUGS)
  - Line number (when available)

**Implementation Details:**
- Scans `target/classes` directory for compiled `.class` files
- Parses SpotBugs XML report if available (`target/spotbugs/spotbugsXml.xml`)
- Uses regex patterns for efficient pattern matching
- Generates summary with pattern counts and severity breakdown

#### 2. **JfrBlockingListener**
Runtime event listener that captures blocking events from Java Flight Recorder.

**JFR Events Monitored:**
- `jdk.MonitorEnter` / `jdk.JavaMonitorEnter`: Lock contention events
- `jdk.MonitorWait`: `object.wait()` events with duration
- `jdk.ThreadPark`: Virtual thread parking events

**Event Data Captured:**
- Event type and timestamp
- Event duration in milliseconds
- Thread name and thread ID
- Stack trace information (class/method of blocking call)
- Lock class (for monitor events)

**Output:**
- `BlockingEvent` objects with full context
- Can be filtered by event type
- Includes start/stop lifecycle for benchmark correlation

#### 3. **RuntimeBlockingTracker**
Aggregates raw JFR events into method-level and class-level statistics.

**Aggregation Levels:**
- **Method-level**: `MethodBlockingStats` with:
  - Count of blocking events
  - Total, average, min, max durations
  - Blocking type (MONITOR_ENTER, MONITOR_WAIT, THREAD_PARK)

- **Class-level**: `ClassBlockingStats` with:
  - Count per class
  - Total duration per class
  - Breakdown by blocking type

**Usage Pattern:**
```java
// Aggregate events after JFR collection stops
runtimeTracker.aggregateBlockingEvents();

// Get statistics
Map<String, MethodBlockingStats> methodStats = runtimeTracker.getMethodBlockingStats();
Map<String, ClassBlockingStats> classStats = runtimeTracker.getClassBlockingStats();
```

#### 4. **BlockingComparisonReporter**
Correlates static findings with runtime observations.

**Comparison Analysis:**
- For each static finding: check if it was observed at runtime
- Calculate correlation rate: `(triggered / total_static) * 100%`
- Identify false positives: static findings not triggered
- Identify false negatives: runtime blocking not detected statically

**Output:**
- `BlockingComparison` objects with:
  - Pattern, class, method info
  - Static counts and runtime counts
  - Triggered flag (was this finding observed?)
  - False negative flag (runtime-only finding)

**Example Table:**
| Pattern | Class | Static | Runtime | Triggered | FP? | FN? |
|---------|-------|--------|---------|-----------|-----|-----|
| sync_block | ServiceA | 1 | 5 | YES | - | - |
| sync_block | ServiceB | 1 | 0 | NO | YES | - |
| thread_sleep | UtilClass | 0 | 2 | - | - | YES |

#### 5. **BlockingDetectionHarness**
Orchestrator component that coordinates all blocking detection components during benchmark execution.

**Key Methods:**
- `initialize()`: Run static analysis once at startup
- `startBenchmark(variant)`: Begin JFR listening for a variant
- `stopBenchmark(variant)`: Stop listening and generate comparison
- `exportResults(variant)`: Export JSON/CSV for specific variant
- `exportComparison()`: Export cross-variant comparison

**Result Container:**
- `BlockingDetectionResult` aggregates all analyses for a variant
- Contains static findings, runtime events, comparisons, and summaries

#### 6. **BlockingExporter**
Exports blocking analysis results to JSON and CSV formats.

**Output Formats:**

**JSON** (Pretty-printed):
```json
{
  "export_timestamp": "2025-01-15T10:30:00Z",
  "variant": "java21-virtual",
  "analysis": {
    "variant": "java21-virtual",
    "static_summary": {
      "total_findings": 12,
      "findings_by_pattern": {...},
      "findings_by_severity": {...}
    },
    "runtime_summary": {
      "total_blocking_events": 45,
      "total_blocking_time_ms": 1250,
      "affected_methods": 8,
      "top_blocking_methods": [...]
    },
    "comparison_summary": {
      "total_static_findings": 12,
      "total_runtime_findings": 45,
      "triggered_findings": 10,
      "false_positives": 2,
      "false_negatives": 35,
      "correlation_rate_percent": 83.3
    },
    "comparisons": [...]
  }
}
```

**CSV** (Spreadsheet-friendly):
```
blocking_pattern,class,static_count,static_location,static_severity,runtime_count,runtime_duration_ms,triggered,false_negative
synchronized_block,org.springframework.ServiceA,1,,MEDIUM,5,250,true,false
synchronized_method,org.springframework.ServiceB,1,,MEDIUM,0,0,false,false
```

**Comparison JSON** (Cross-variant):
```json
{
  "export_timestamp": "2025-01-15T10:30:00Z",
  "variant_count": 3,
  "variants": ["java17", "java21-traditional", "java21-virtual"],
  "comparative_analysis": {
    "variant_summaries": {...},
    "java17_vs_java21_comparison": {
      "variant1": "java17",
      "variant2": "java21-traditional",
      "runtime_events_delta_percent": -15.2,
      "total_blocking_time_delta_percent": -8.5
    },
    "traditional_vs_virtual_comparison": {
      "variant1": "java21-traditional",
      "variant2": "java21-virtual",
      "runtime_events_delta_percent": -45.3,
      "total_blocking_time_delta_percent": -62.1
    }
  },
  "variants_details": {...}
}
```

## Integration with Benchmarks

### Basic Usage Pattern

```java
@SpringBootTest
public class BlockingDetectionBenchmarkTest {
    
    private BlockingDetectionHarness blockingHarness;
    
    @BeforeAll
    static void setUpClass() throws IOException {
        blockingHarness = new BlockingDetectionHarness();
        blockingHarness.initialize();
    }
    
    @Test
    void benchmarkWithBlockingDetection() throws IOException {
        String variant = "java21-virtual";
        
        // Start collection
        blockingHarness.startBenchmark(variant);
        
        // Run benchmark code
        // ... perform benchmark operations ...
        
        // Stop collection and get results
        BlockingDetectionHarness.BlockingDetectionResult result = 
            blockingHarness.stopBenchmark(variant);
        
        // Export results
        Map<String, String> exports = blockingHarness.exportResults(variant);
        System.out.println("Exported to: " + exports.get("json"));
        System.out.println("Exported to: " + exports.get("csv"));
    }
}
```

### Multi-Variant Comparison

```java
public void benchmarkAllVariants() throws IOException {
    BlockingDetectionHarness harness = new BlockingDetectionHarness();
    harness.initialize();
    
    String[] variants = {"java17", "java21-traditional", "java21-virtual"};
    
    for (String variant : variants) {
        harness.startBenchmark(variant);
        
        // Run benchmark for this variant
        runBenchmark(variant);
        
        harness.stopBenchmark(variant);
    }
    
    // Export individual results
    for (String variant : variants) {
        harness.exportResults(variant);
    }
    
    // Export cross-variant comparison
    harness.exportComparison();
}
```

## Configuration

### Static Analysis Configuration (pom.xml)

```xml
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <version>4.8.2.0</version>
  <configuration>
    <effort>max</effort>
    <threshold>medium</threshold>
    <xmlOutput>true</xmlOutput>
    <xmlOutputDirectory>${project.build.directory}/spotbugs</xmlOutputDirectory>
  </configuration>
</plugin>
```

**Configuration Options:**
- `effort`: Analysis effort (min, medium, max) - higher = more thorough but slower
- `threshold`: Report severity threshold
- `xmlOutput`: Generate XML report for parsing

### Runtime Monitoring Configuration

**JFR Event Selection:**
By default, the harness captures:
- `jdk.MonitorEnter` - all lock contention
- `jdk.MonitorWait` - all wait/notify calls
- `jdk.ThreadPark` - virtual thread parking (Java 21+)

**To enable in benchmark JVM:**
```bash
java -XX:+UnlockDiagnosticVMOptions \
     -XX:+DebugNonSafepoints \
     -XX:+FlightRecorder \
     -XX:StartFlightRecording=filename=recording.jfr,duration=300s,settings=default \
     -jar application.jar
```

## Interpreting Results

### Static Analysis

**Example Summary:**
```json
{
  "total_findings": 23,
  "findings_by_pattern": {
    "synchronized_block": 8,
    "blocking_queue": 7,
    "thread_sleep": 5,
    "url_connection": 3
  },
  "findings_by_severity": {
    "HIGH": 5,
    "MEDIUM": 12,
    "LOW": 6
  },
  "affected_classes": 15
}
```

**Interpretation:**
- 23 potential blocking locations identified
- Most common: synchronized blocks (8) and blocking queues (7)
- 5 high-severity issues warrant priority review
- 15 classes require refactoring for better concurrency

### Runtime Analysis

**Example Summary:**
```json
{
  "total_blocking_events": 1250,
  "total_blocking_time_ms": 5420,
  "affected_methods": 12,
  "affected_classes": 8,
  "average_blocking_time_ms": 4,
  "top_blocking_methods": [
    {
      "method": "fetchVets",
      "class": "org.springframework.samples.petclinic.vet.VetService",
      "type": "MONITOR_ENTER",
      "count": 45,
      "total_duration_ms": 1250,
      "average_duration_ms": 28
    }
  ]
}
```

**Interpretation:**
- 1250 blocking events with ~5.4 seconds total blocking time
- Average 4ms per blocking event (small, acceptable)
- `VetService.fetchVets()` is the top blocker with 45 events
- Only 8 classes actually showed blocking at runtime

### Comparison Analysis

**Correlation Rate:**
```
Correlation Rate = (triggered_findings / total_static_findings) * 100%
```

**Example:**
- Static findings: 23
- Triggered at runtime: 18
- Correlation rate: 78%
- False positives: 5 (2 are in rarely-used code paths)
- False negatives: 1247 (from underlying libraries like Tomcat connection pooling)

**Interpretation:**
- Good correlation (78%) - static analysis catches most actual blockers
- False positives need review - may indicate rare code paths or conservative assumptions
- False negatives from libraries are expected - static analysis only covers application code

### Variant Comparison

**Java 17 vs Java 21 Traditional:**
```json
{
  "variant1": "java17",
  "variant2": "java21-traditional",
  "runtime_events_delta_percent": -8.3,
  "total_blocking_time_delta_percent": -12.1,
  "variant1_triggered": 18,
  "variant2_triggered": 17
}
```

**Interpretation:**
- Java 21 traditional slightly reduces blocking events (-8.3%)
- Java 21 reduces blocking time more (-12.1%) - better lock algorithms
- Triggered findings remain similar (18 vs 17)

**Java 21 Traditional vs Virtual:**
```json
{
  "variant1": "java21-traditional",
  "variant2": "java21-virtual",
  "runtime_events_delta_percent": -45.3,
  "total_blocking_time_delta_percent": -62.1,
  "variant1_triggered": 17,
  "variant2_triggered": 12
}
```

**Interpretation:**
- Virtual threads reduce blocking events by ~45%
- Virtual threads reduce total blocking time by ~62% (better concurrency handling)
- Fewer unique patterns triggered (12 vs 17) - virtual threads avoid some blocking
- **Virtual thread benefit**: Better handling of I/O blocking doesn't block carrier threads

## Success Criteria Verification

### ✓ Static Analysis Validation

**Criterion:** Static analysis identifies all known synchronized blocks

**Verification:**
```bash
# Build with SpotBugs analysis
mvn clean verify

# Check report
ls target/spotbugs/spotbugsXml.xml
```

**Success:** Report generated with > 0 HIGH/MEDIUM severity findings

### ✓ JFR Event Capture

**Criterion:** JFR listener successfully captures blocking events

**Verification:**
```java
// In tests
JfrBlockingListener listener = new JfrBlockingListener();
listener.start();
// Perform blocking operation
listener.stop();
List<BlockingEvent> events = listener.getBlockingEvents();
assertThat(events.size()).isGreaterThan(0);
```

**Success:** Runtime events captured with realistic durations

### ✓ Correlation Rate

**Criterion:** > 80% of static findings observed at runtime

**Verification:**
- Check comparison report correlation_rate_percent
- Should be > 80% for application code
- Lower rates acceptable if many findings are in defensive/unused code paths

### ✓ Virtual Thread Differentiation

**Criterion:** Virtual thread variant shows reduced THREAD_PARK events

**Verification:**
- Compare java21-traditional vs java21-virtual
- Virtual should show 30%+ fewer THREAD_PARK events
- Virtual should show lower overall blocking time

### ✓ Export Validity

**Criterion:** JSON and CSV exports are valid and parseable

**Verification:**
```bash
# Validate JSON
java -jar jsonschema-validator.jar target/blocking/blocking-*.json

# Open CSV in spreadsheet application
# Check: no corrupted quotes, proper escaping
```

## Troubleshooting

### No Static Findings Detected

**Problem:** Static analyzer returns 0 findings

**Solutions:**
1. Verify `target/classes` exists and contains compiled classes
2. Check if application code is compiled (not just dependencies)
3. Increase SpotBugs effort level to "max"
4. Manually verify synchronized blocks exist in source:
   ```bash
   grep -r "synchronized" src/main/java/
   ```

### No Runtime Events Captured

**Problem:** JFR listener shows 0 blocking events

**Solutions:**
1. Ensure JVM is running with JFR enabled:
   ```bash
   jcmd <pid> VM.check_commercial_features
   jcmd <pid> JFR.start
   ```
2. Verify benchmark actually triggers blocking:
   ```java
   // Add test blocking code
   synchronized(lock) { Thread.sleep(100); }
   ```
3. Check JFR buffer settings are adequate
4. Verify event types are correctly configured in JFR settings

### Low Correlation Rate

**Problem:** Many static findings not triggered at runtime

**Solutions:**
1. **Expected for:**
   - Defensive code (error handling paths)
   - Library code not exercised by benchmark
   - Optimized code paths that skip synchronization
   
2. **If unexpected:**
   - Review false positive findings - may be in dead code
   - Run longer benchmarks to exercise more code paths
   - Enable more detailed JFR event collection

### High False Negative Count

**Problem:** Runtime shows many blocking events not detected statically

**Solutions:**
1. **Expected for:**
   - Blocking operations in included libraries (connection pooling, etc.)
   - JDK internal blocking (not application code)
   - Dynamic proxy objects (pattern matching may fail)

2. **Mitigation:**
   - Focus on false positives in application code
   - Use library-specific blocking documentation
   - Cross-reference with known library blocking patterns

## Performance Impact

**Static Analysis:** ~500ms overhead (one-time, startup)
**Runtime Monitoring:** < 1% JVM overhead (JFR optimized)
**Export:** ~100ms per variant

**Total overhead:** Negligible for benchmark suite

## Integration with Other Metrics

The blocking detection results can be correlated with:

- **Latency metrics** from JMH: Does blocking correlate with P99 latency spikes?
- **Thread pool metrics** from Actuator: Are blocking events visible in thread pool stats?
- **Cache metrics**: Do cache misses correlate with blocking on data access?
- **HTTP metrics**: Are high response times correlated with blocking on I/O?

**Example Correlation:**
```
JMH P99 latency spike (150ms) at 13:45:00
↓
Blocking metrics show 23 MONITOR_ENTER events at 13:45:00
  - Average duration: 6ms
  - Total: 140ms (matches latency spike)
↓
Conclusion: Lock contention on ServiceA causing latency increase
```

## Future Enhancements

1. **Custom Rule Engine**: Define org-specific blocking patterns
2. **Historical Tracking**: Compare blocking patterns across builds
3. **Alerting**: Alert on high false positive rates or regression
4. **Heatmaps**: Visualize blocking hotspots by thread
5. **Recommendations**: Suggest refactoring approaches for top blockers

---

**Related Documentation:**
- [METRICS-COLLECTION.md](./METRICS-COLLECTION.md) - Metrics collection framework
- [BENCHMARK-WORKFLOWS.md](./BENCHMARK-WORKFLOWS.md) - Benchmark execution patterns
- [SETUP-ADVANCED.md](./SETUP-ADVANCED.md) - Advanced JFR configuration
