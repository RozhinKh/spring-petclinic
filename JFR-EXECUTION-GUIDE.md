# JFR Integration Execution Guide

## Quick Start

### Prerequisites
- Java 17+ (JFR available in all versions)
- Maven 3.6+
- ~5GB disk space
- ~30 minutes execution time for full benchmark suite

### One-Command Execution

```bash
cd <project-root>
mvn clean package -DskipTests
java -cp target/benchmarks.jar \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner
```

**Expected Output**:
```
===============================================
   JMH Multi-Variant Benchmark Runner
===============================================

>>> Building all variants...
>>> Building variant: java17-baseline
✓ Build completed in 45 seconds
>>> Building variant: java21-traditional
✓ Build completed in 42 seconds
>>> Building variant: java21-virtual
✓ Build completed in 43 seconds

>>> Running benchmarks for all variants...

==================================================
Variant: java17-baseline
==================================================
>>> Starting application for warm-up...
✓ Application started: java17-baseline
✓ Warm-up completed: 20 requests in 10 seconds
>>> Starting JFR recording...
✓ JFR recording started
>>> Running JMH benchmarks...
[JMH output]
>>> Stopping JFR recording...
✓ JFR recording saved to: jfr-recordings/benchmark-1705320345123.jfr
>>> Parsing JFR metrics...
>>> Correlating JFR and JMH results...
✓ Benchmarks and JFR analysis completed for variant: java17-baseline

[Similar output for java21-traditional and java21-virtual]

>>> Generating unified results...
✓ Results exported to benchmark-results.json
✓ CSV summary exported to benchmark-results.csv

✓ Benchmark execution completed
Results exported to: benchmark-results.json
```

### Output Files

After execution, check for these files:

```
<project-root>/
├── benchmark-results.json      [MAIN OUTPUT] Combined JMH + JFR + correlation
├── benchmark-results.csv       Summary of JMH results
├── jfr-recordings/
│   ├── benchmark-1705320345123.jfr  [Raw JFR Java 17]
│   ├── benchmark-1705320612456.jfr  [Raw JFR Java 21 Traditional]
│   └── benchmark-1705320879789.jfr  [Raw JFR Java 21 Virtual]
└── jmh-results.json            Intermediate JMH output (last execution)
```

---

## Step-by-Step Execution

### 1. Build All Variants
```bash
# Full build including benchmarks
mvn clean package -DskipTests

# Verify build output
ls -lh target/*.jar
# Expected:
#   spring-petclinic-4.0.0-SNAPSHOT.jar
#   benchmarks.jar (JMH uber jar)
```

### 2. Run Benchmarks
```bash
# Execute benchmark runner (will build and run automatically)
java -cp target/benchmarks.jar \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner

# Or with custom output directory
java -cp target/benchmarks.jar \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner \
  /path/to/output

# Monitor progress (in another terminal)
watch -n 5 'ls -la jfr-recordings/'
watch -n 5 'tail -100 benchmark-results.json'
```

### 3. Verify Results
```bash
# Check JSON output is valid
jq . benchmark-results.json | head -100

# Check file sizes
ls -lh benchmark-results.json jfr-recordings/*.jfr

# Quick statistics
jq '.variants | length' benchmark-results.json  # Should be 3
jq '.variants[0].jfr_metrics | keys' benchmark-results.json
# Should show: gc_metrics, thread_metrics, memory_metrics, blocking_metrics
```

---

## Execution Options

### Option A: Full Multi-Variant Benchmark (Default)
```bash
java -cp target/benchmarks.jar \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner
```

**Runtime**: ~30-45 minutes  
**Disk Usage**: ~500MB-1GB  
**Variants**: Java 17, Java 21 Traditional, Java 21 Virtual

### Option B: Specific Variant Only
```bash
# Manually build specific variant
mvn clean package -Pjava21-virtual -DskipTests

# Run benchmarks against running application
# (Requires manual application startup)
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=vthreads &

# Wait for health check, then run JMH
java -jar target/benchmarks.jar -f 5 -i 10
```

**Runtime**: ~10-15 minutes  
**Use Case**: Testing configuration changes on single variant

### Option C: Quick Validation Run
```bash
# Reduced iterations for quick results
java -jar target/benchmarks.jar \
  -f 2 -wi 2 -i 2    # 2 forks, 2 warmup, 2 measurement iterations
```

**Runtime**: ~2-3 minutes  
**Use Case**: Verify setup before full run

---

## Troubleshooting

### Issue: "JFR recording failed"
**Symptoms**: No jfr-recordings directory or empty JFR metrics

**Diagnosis**:
```bash
# Check JFR availability
java -XX:+PrintFlagsFinal -version 2>&1 | grep -i flight

# Output should include:
# -XX:+FlightRecorder (if available)
# -XX:+UnlockDiagnosticVMOptions (may be required)
```

**Solutions**:
```bash
# Explicit JFR enable
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=disk=true,maxsize=100m \
     -jar target/benchmarks.jar ...

# Or try alternative JDK
java -version  # Check version >= 11
which java     # Ensure correct JDK in PATH
```

### Issue: "Application failed to start"
**Symptoms**: Health check timeout, no application running

**Diagnosis**:
```bash
# Verify application JAR exists
ls -lh target/spring-petclinic-*.jar

# Try manual startup
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar &
curl http://localhost:8080/actuator/health
```

**Solutions**:
```bash
# Ensure clean build
mvn clean package -DskipTests

# Check port availability
lsof -i :8080  # Should show nothing
# If port in use: pkill -f "java.*petclinic"

# Increase startup timeout in BenchmarkRunner if needed
# (Edit ApplicationStarter parameters)
```

### Issue: "Out of disk space"
**Symptoms**: Build fails or benchmark gets killed

**Diagnosis**:
```bash
df -h      # Check available space
du -sh *   # Show directory sizes
```

**Solutions**:
```bash
# Clean old builds
mvn clean
rm -rf jfr-recordings/*
rm benchmark-results.json

# Or use smaller JVM heap
export JAVA_OPTS="-Xms256m -Xmx1g"
```

### Issue: "JMH benchmarks timeout"
**Symptoms**: Timeout after 60 minutes, incomplete results

**Diagnosis**:
```bash
# Monitor progress
ps aux | grep java
top -p <pid>  # Check CPU and memory usage
```

**Solutions**:
```bash
# Reduce benchmark iterations temporarily
java -jar target/benchmarks.jar -f 3 -i 5

# Or increase timeout in BenchmarkRunner.java:
// Change from 60 MINUTES to 120 MINUTES
if (!process.waitFor(120, TimeUnit.MINUTES)) {

# Run on faster machine if available
```

### Issue: "Empty JFR metrics in output"
**Symptoms**: jfr_metrics all zeros or missing data

**Possible Causes**:
1. **Short benchmark duration**: Events not captured in brief runs
   - **Fix**: Run full benchmark suite, not quick validation
   
2. **Event configuration issue**: Events not enabled
   - **Fix**: Check JFRHarness.configureEvents() is being called
   
3. **File I/O error**: JFR file not readable
   - **Fix**: Check `jfr-recordings/` directory permissions:
     ```bash
     chmod 755 jfr-recordings/
     ```

4. **JFR version mismatch**: Event names changed between Java versions
   - **Fix**: Use Java 17+ (consistent event names)

---

## Performance Tuning

### For Faster Benchmarks

**1. Reduce iterations** (for testing only):
```bash
java -jar target/benchmarks.jar \
  -f 1 -wi 2 -i 3      # 1 fork, 2 warmup, 3 measurement
  -r 500ms             # 500ms per iteration instead of 2s
```

**2. Reduce JFR buffer size** (trades data for speed):
Edit JFRHarness.configureEvents():
```java
// Reduce event buffer (less data captured)
recording.setDuration(Duration.ofSeconds(30));  // Shorter recording
```

**3. Use faster I/O** (SSD required):
```bash
# Move JFR recordings to tmpfs (Linux)
mkdir -p /dev/shm/jfr-recordings
ln -s /dev/shm/jfr-recordings jfr-recordings

# Or on macOS
mkdir -p /var/tmp/jfr-recordings
ln -s /var/tmp/jfr-recordings jfr-recordings
```

### For Better Results

**1. Increase iterations** (better statistics):
```bash
java -jar target/benchmarks.jar \
  -f 7 -wi 15 -i 30    # 7 forks, 15 warmup, 30 measurement (slower but higher quality)
```

**2. Extend JFR recording**:
Edit JFRHarness.startRecording():
```java
recording.setDuration(Duration.ofSeconds(120));  // Longer recording for more events
```

**3. Isolate system** (reduce noise):
```bash
# On Linux, use performance scheduler
sudo cpupower frequency-set -g performance

# Disable background processes
sudo systemctl stop service-name
```

---

## Data Validation

### Pre-Execution Checklist
- [ ] Java 17+ installed: `java -version`
- [ ] Maven available: `mvn -version`
- [ ] Disk space >= 5GB: `df -h /`
- [ ] Port 8080 available: `lsof -i :8080` (should be empty)
- [ ] Current directory is project root: `ls pom.xml` (should exist)
- [ ] Memory sufficient: `free -h` or `vm_stat` (>2GB recommended)

### Post-Execution Validation Checklist
- [ ] benchmark-results.json exists
- [ ] File size > 10KB (not empty)
- [ ] Valid JSON: `jq . benchmark-results.json` (no errors)
- [ ] Contains 3 variants: `jq '.variants | length' benchmark-results.json` = 3
- [ ] Contains JFR data: `jq '.variants[0].jfr_metrics' benchmark-results.json` (not null)
- [ ] Correlation analysis present: `jq '.variants[0].correlation_analysis' benchmark-results.json` (not null)
- [ ] JFR files present: `ls jfr-recordings/` (3+ .jfr files)

### Quality Metrics Validation
```bash
# Check measurement quality
jq '.variants[0].benchmarks[] | {name, std_dev, score}' \
  benchmark-results.json

# Verify std_dev is <10% of score
# Example output:
# {
#   "name": "getOwners",
#   "std_dev": 3.2,
#   "score": 45.32
# }
# Quality: 3.2 / 45.32 = 7.1% (good!)
```

---

## Next Steps (Task 5)

After obtaining `benchmark-results.json`, prepare for visualization:

1. **Extract key metrics**:
   ```bash
   jq '.variants[] | {variant, benchmarks: .benchmarks[].score, 
                      gc_impact: .correlation_analysis.gc_latency_correlation.estimated_gc_impact_level}' \
     benchmark-results.json
   ```

2. **Export to visualization tool**:
   ```bash
   # For D3.js or Plotly
   cp benchmark-results.json ../visualization/data/

   # For spreadsheet analysis
   cp benchmark-results.csv ../analysis/
   ```

3. **Generate comparison report**:
   - Compare P50/P99 latency across variants
   - Visualize GC pause timeline
   - Create thread activity heatmap
   - Rank bottleneck severity

---

## Reference: File Locations

| File | Purpose | Readable | Size |
|------|---------|----------|------|
| `benchmark-results.json` | Combined results (JMH+JFR+correlation) | JSON | 100KB-1MB |
| `benchmark-results.csv` | JMH results summary | CSV/text | 10-50KB |
| `jfr-recordings/*.jfr` | Raw JFR binary recordings | Binary (JFR format) | 10-100MB each |
| `jmh-results.json` | Raw JMH output (intermediate) | JSON | 50-200KB |

---

## Support & Documentation

For more information, see:
- **JFR Integration Overview**: JFR-INTEGRATION.md
- **Metric Interpretation**: JFR-OUTPUT-INTERPRETATION.md
- **Variant Comparison**: JFR-VARIANT-COMPARISON.md
- **Implementation Details**: JFR-IMPLEMENTATION-SUMMARY.md

