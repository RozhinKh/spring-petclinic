# Spring PetClinic Benchmark Suite - Execution Workflows

This guide provides detailed execution workflows for running all benchmark tools and collecting metrics across the Spring PetClinic benchmark suite. It covers practical examples, configuration options, and timing expectations for each tool.

---

## Table of Contents

1. [JMH Benchmark Execution](#1-jmh-benchmark-execution)
2. [JFR Recording & Analysis](#2-jfr-recording--analysis)
3. [Test Suite Execution](#3-test-suite-execution)
4. [Load Testing Workflows](#4-load-testing-workflows)
5. [Actuator Metrics Collection](#5-actuator-metrics-collection)
6. [Database Reset Procedures](#6-database-reset-procedures)
7. [Complete End-to-End Workflow](#7-complete-end-to-end-workflow)
8. [Output File Reference](#8-output-file-reference)

---

## 1. JMH Benchmark Execution

### 1.1 Overview

Java Microbenchmark Harness (JMH) is used for micro-level performance testing of specific methods and components. The PetClinic benchmark suite includes JMH benchmarks for:

- **Startup benchmarks**: Application initialization time
- **Latency benchmarks**: Request-response latency for key operations
- **Throughput benchmarks**: Requests per second for various endpoints
- **Memory benchmarks**: Heap usage and allocation patterns

### 1.2 JMH Dependencies & Setup

If JMH benchmarks are not already configured, add the following to `pom.xml`:

```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
```

Or in `build.gradle`:

```gradle
testImplementation 'org.openjdk.jmh:jmh-core:1.37'
testImplementation 'org.openjdk.jmh:jmh-generator-annprocess:1.37'
```

### 1.3 JMH Benchmark Example

Create a benchmark class at `src/test/java/org/springframework/samples/petclinic/bench/PetClinicStartupBench.java`:

```java
package org.springframework.samples.petclinic.bench;

import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.samples.petclinic.PetClinicApplication;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
public class PetClinicStartupBench {

    @Benchmark
    public void applicationStartup() {
        SpringApplication.run(PetClinicApplication.class, new String[0]);
    }
}
```

### 1.4 JMH Configuration Options

#### Fork Count

Controls the number of independent JVM runs:

```bash
# Default: 1 fork
# Recommended for benchmarks: 2-3 forks to account for JVM warmup variance

-f 2    # 2 forks (independent JVM instances)
-f 3    # 3 forks (more stable results, longer execution)
```

#### Warmup Iterations

Allows the JVM to reach steady state before measurement:

```bash
# Default: 20 iterations
# Recommended: 5-20 iterations depending on benchmark complexity

-wi 5   # 5 warmup iterations (quick benchmarks)
-wi 20  # 20 warmup iterations (stable results)
-w 2s   # 2 seconds per warmup iteration
```

#### Measurement Iterations

The actual iterations used for performance data:

```bash
# Default: 20 iterations
# Recommended: 10-20 iterations

-i 10   # 10 measurement iterations
-i 20   # 20 measurement iterations
-r 5s   # 5 seconds per measurement iteration
```

### 1.5 Running JMH Benchmarks

#### Compile Benchmark Classes

**Maven:**
```bash
# Compile all benchmarks
./mvnw clean test-compile

# Verify benchmark classes are generated
ls -la target/generated-sources/annotations/
```

**Gradle:**
```bash
# Compile all benchmarks
./gradlew clean testClasses

# Verify benchmark classes are generated
ls -la build/generated/sources/annotationProcessor/
```

#### Execute All Benchmarks

**Maven (using JMH Maven Plugin):**

First, add the JMH Maven plugin to `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <finalName>benchmarks</finalName>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>org.openjdk.jmh.Main</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then run:

```bash
# Build benchmark JAR
./mvnw clean package -pl benchmarks -am

# Run all benchmarks with output to file
java -jar target/benchmarks.jar \
    -f 2 \
    -wi 5 \
    -i 10 \
    -r 3s \
    -rf json \
    -rff target/jmh-results.json

# Expected output file: target/jmh-results.json
# Runtime: ~15-20 minutes
```

**Gradle:**

Create a benchmark task in `build.gradle`:

```gradle
task jmhJar(type: Jar) {
    dependsOn testClasses
    mainClass = 'org.openjdk.jmh.Main'
    from sourceSets.test.output
    from configurations.testRuntimeClasspath
    archiveFileName = 'benchmarks.jar'
}

task runJmh(dependsOn: jmhJar) {
    doLast {
        exec {
            commandLine 'java',
                '-jar', jmhJar.archiveFile.get().asFile,
                '-f', '2',
                '-wi', '5',
                '-i', '10',
                '-r', '3s',
                '-rf', 'json',
                '-rff', 'build/jmh-results.json'
        }
    }
}
```

Then run:

```bash
# Run all benchmarks with output to file
./gradlew runJmh

# Expected output file: build/jmh-results.json
# Runtime: ~15-20 minutes
```

#### Execute Specific Benchmark Classes

Run a single benchmark class:

```bash
java -jar target/benchmarks.jar \
    'org.springframework.samples.petclinic.bench.PetClinicStartupBench' \
    -f 2 \
    -i 10 \
    -rf json \
    -rff target/jmh-startup.json

# Runtime: ~5 minutes
```

Run benchmarks matching a pattern:

```bash
# Run all latency benchmarks
java -jar target/benchmarks.jar \
    'org.springframework.samples.petclinic.bench.*Latency.*' \
    -f 2 \
    -i 10

# Run all throughput benchmarks
java -jar target/benchmarks.jar \
    'org.springframework.samples.petclinic.bench.*Throughput.*' \
    -f 2 \
    -i 10
```

### 1.6 JMH Output Formats & Parsing

#### JSON Output

Most useful for automated analysis:

```bash
# Generate JSON output
java -jar target/benchmarks.jar \
    -rf json \
    -rff target/jmh-results.json

# Parse JSON with jq (install: brew install jq)
jq '.[] | {benchmark: .benchmark, score: .primaryMetric.score, unit: .primaryMetric.scoreUnit}' \
    target/jmh-results.json
```

Example JSON output structure:

```json
{
  "benchmark": "org.springframework.samples.petclinic.bench.PetClinicStartupBench.applicationStartup",
  "mode": "avgt",
  "threads": 1,
  "forks": 2,
  "warmupIterations": 5,
  "warmupTime": "1000 ms",
  "warmupBatchSize": 0,
  "measurementIterations": 10,
  "measurementTime": "3000 ms",
  "measurementBatchSize": 0,
  "primaryMetric": {
    "score": 2847.123,
    "scoreError": 145.678,
    "scoreConfidence": [2701.445, 2992.801],
    "scorePercentiles": {
      "0.0": 2701.0,
      "50.0": 2850.0,
      "90.0": 2950.0,
      "95.0": 2980.0,
      "99.0": 2995.0,
      "99.9": 3000.0,
      "100.0": 3010.0
    },
    "scoreUnit": "ms/op",
    "rawData": [...]
  }
}
```

#### CSV Output

For spreadsheet import:

```bash
java -jar target/benchmarks.jar \
    -rf csv \
    -rff target/jmh-results.csv
```

#### Text Output (Console)

Default human-readable format:

```bash
java -jar target/benchmarks.jar \
    'org.springframework.samples.petclinic.bench.PetClinicStartupBench' \
    -f 2 \
    -i 5

# Produces console output like:
# Benchmark                                      Mode  Cnt    Score   Error  Units
# PetClinicStartupBench.applicationStartup       avgt   10  2847.123 145.678  ms/op
```

### 1.7 JMH Multi-Variant Runner

To compare performance across Java variants, create a master script:

**`scripts/run-jmh-all-variants.sh`:**

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/jmh/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - JMH Benchmark Suite ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"

# Function to run benchmarks for a variant
run_variant_jmh() {
    local JAVA_HOME=$1
    local VARIANT_NAME=$2
    local PROFILE=$3
    
    export JAVA_HOME=$JAVA_HOME
    echo ""
    echo "--- Running JMH benchmarks on $VARIANT_NAME ---"
    echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
    
    cd "$PROJECT_ROOT"
    
    # Build
    if [ -n "$PROFILE" ]; then
        ./mvnw clean package -P $PROFILE -DskipTests
    else
        ./mvnw clean package -DskipTests
    fi
    
    # Run JMH
    java -jar target/benchmarks.jar \
        -f 2 \
        -wi 5 \
        -i 10 \
        -r 2s \
        -rf json \
        -rff "$RESULTS_DIR/jmh-$VARIANT_NAME.json"
    
    echo "✓ JMH benchmarks for $VARIANT_NAME completed"
}

# Run for each variant
if [ -n "$JAVA_17_HOME" ]; then
    run_variant_jmh "$JAVA_17_HOME" "java17-baseline"
else
    echo "Warning: JAVA_17_HOME not set, skipping Java 17 benchmarks"
fi

if [ -n "$JAVA_21_HOME" ]; then
    run_variant_jmh "$JAVA_21_HOME" "java21-variant-a"
    run_variant_jmh "$JAVA_21_HOME" "java21-variant-b" "vthreads"
else
    echo "Warning: JAVA_21_HOME not set, skipping Java 21 benchmarks"
fi

echo ""
echo "=== All JMH benchmarks completed ==="
echo "Results saved to: $RESULTS_DIR"
```

Usage:

```bash
chmod +x scripts/run-jmh-all-variants.sh
./scripts/run-jmh-all-variants.sh

# Results: benchmark-results/jmh/20240115_143022/
#   ├── jmh-java17-baseline.json
#   ├── jmh-java21-variant-a.json
#   └── jmh-java21-variant-b.json
```

---

## 2. JFR Recording & Analysis

### 2.1 Overview

Java Flight Recorder (JFR) captures detailed runtime performance data including:

- CPU and memory usage
- Thread activity and lock contention
- Garbage collection events
- Method profiling
- I/O operations

JFR is available in Java 17+ and provides low-overhead continuous monitoring.

### 2.2 JFR Configuration

#### JVM Arguments for Recording

```bash
# Basic JFR recording (5 minutes)
java \
    -XX:StartFlightRecording=duration=300s,filename=recording.jfr \
    -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# With custom settings profile
java \
    -XX:StartFlightRecording=settings=profile,duration=300s,filename=recording.jfr \
    -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# With continuous recording (retention 512MB)
java \
    -XX:StartFlightRecording=disk=true,repository=/tmp/jfr,maxsize=512m,duration=0 \
    -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

#### Available JFR Profiles

**default:**
```bash
# Balanced settings suitable for production (30s overhead per hour)
-XX:StartFlightRecording=settings=default
```

**profile:**
```bash
# More detailed profiling (~2% overhead, for benchmarking)
-XX:StartFlightRecording=settings=profile
```

**continuous:**
```bash
# Lightweight monitoring (5-10s overhead per hour)
-XX:StartFlightRecording=settings=continuous
```

### 2.3 Starting JFR Recording During Application Runtime

#### Using jcmd (Java Command)

Start recording on running application:

```bash
# List all running Java processes
jcmd

# Start recording on PID 12345
jcmd 12345 JFR.start \
    name=bench_recording \
    settings=profile \
    duration=300s \
    filename=recording.jfr

# Check recording status
jcmd 12345 JFR.check

# Stop recording
jcmd 12345 JFR.stop name=bench_recording
```

#### Using Flight Recording API (Programmatic)

Create `src/test/java/org/springframework/samples/petclinic/bench/JfrRecordingHelper.java`:

```java
package org.springframework.samples.petclinic.bench;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import java.io.IOException;
import java.nio.file.Paths;

public class JfrRecordingHelper {

    private static Recording recording;

    public static void startRecording(String filename, String settings) throws IOException {
        Configuration config = Configuration.getConfiguration(settings);
        recording = new Recording(config);
        recording.start();
        System.out.println("JFR recording started: " + filename);
    }

    public static void stopAndDumpRecording(String filename) throws IOException {
        if (recording != null) {
            recording.stop();
            recording.dump(Paths.get(filename));
            recording.close();
            System.out.println("JFR recording saved: " + filename);
        }
    }

    public static void stopRecording() {
        if (recording != null) {
            recording.stop();
            recording.close();
        }
    }
}
```

### 2.4 JFR Event Type Configuration

#### Customizing Events to Record

Create custom event configuration file `src/main/resources/jfr-custom.jfc`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0" 
    xmlns="https://java.sun.com/javafx/jmx/jfr">
    
    <!-- Profiling configuration -->
    <event name="jdk.ExecutionSample">
        <setting name="enabled">true</setting>
        <setting name="period">10ms</setting>
    </event>
    
    <!-- Thread events -->
    <event name="jdk.ThreadPark">
        <setting name="enabled">true</setting>
        <setting name="threshold">1ms</setting>
    </event>
    
    <!-- Lock contention -->
    <event name="jdk.JavaMonitorWait">
        <setting name="enabled">true</setting>
        <setting name="threshold">10ms</setting>
    </event>
    
    <!-- Garbage collection -->
    <event name="jdk.GarbageCollection">
        <setting name="enabled">true</setting>
    </event>
    
    <!-- Memory allocation -->
    <event name="jdk.ObjectAllocationInNewTLAB">
        <setting name="enabled">true</setting>
    </event>
    
    <!-- Network I/O -->
    <event name="jdk.SocketRead">
        <setting name="enabled">true</setting>
        <setting name="threshold">1ms</setting>
    </event>
</configuration>
```

Use it:

```bash
java \
    -XX:StartFlightRecording=settings=src/main/resources/jfr-custom.jfc,\
duration=300s,filename=recording.jfr \
    -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

### 2.5 Recording and Dumping Workflows

#### Workflow 1: Start Application with Recording

```bash
# Terminal 1: Start application with JFR profiling
export JAVA_HOME=$JAVA_17_HOME
java \
    -XX:StartFlightRecording=settings=profile,duration=600s,filename=target/jfr-recording.jfr \
    -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# Application runs for 10 minutes with recording active
# Results: target/jfr-recording.jfr
```

#### Workflow 2: Start Application, Then Trigger Recording

```bash
# Terminal 1: Start application without initial recording
export JAVA_HOME=$JAVA_17_HOME
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar &
APP_PID=$!

# Terminal 2: Start recording after app is ready
sleep 10
jcmd $APP_PID JFR.start \
    name=perf_record \
    settings=profile \
    duration=300s \
    filename=target/jfr-recording.jfr

# Run benchmark load
./scripts/run-load-test.sh

# Recording stops automatically after 300s
# Results: target/jfr-recording.jfr
```

#### Workflow 3: Continuous Recording (Circular Buffer)

```bash
# Create directory for JFR files
mkdir -p /tmp/jfr-data

# Start application with continuous recording
export JAVA_HOME=$JAVA_17_HOME
java \
    -XX:StartFlightRecording=disk=true,\
repository=/tmp/jfr-data,\
maxsize=512m,\
duration=0 \
    -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# JFR continuously records in circular buffer
# Manual dump when needed:
# jcmd $(pgrep -f spring-petclinic) JFR.dump name=1 filename=target/jfr-dump.jfr
```

### 2.6 Parsing and Analyzing JFR Output

#### Convert JFR to JSON

Using `jfr` command-line tool (Java 17+):

```bash
# List all event types in recording
jfr metadata target/jfr-recording.jfr

# Export to JSON (all events)
jfr print --json target/jfr-recording.jfr > target/jfr-recording.json

# Export specific event types to CSV
jfr print --csv target/jfr-recording.jfr \
    --events \
    jdk.GarbageCollection,jdk.ExecutionSample \
    > target/jfr-gc-execution.csv

# Filter events by time range
jfr print --json \
    --start 00:00:00 \
    --end 00:05:00 \
    target/jfr-recording.jfr > target/jfr-first-5min.json
```

#### Extract Key Metrics

```bash
# Extract GC pause times
jfr print --json target/jfr-recording.jfr | \
    jq '.[] | select(.type == "jdk.GarbageCollection") | {timestamp, duration, name}'

# Extract memory usage
jfr print --json target/jfr-recording.jfr | \
    jq '.[] | select(.type == "jdk.GCHeapSummary") | {timestamp, heapUsed, heapCommitted}'

# Extract thread count
jfr print --json target/jfr-recording.jfr | \
    jq '.[] | select(.type == "jdk.ThreadStatistics") | {timestamp, activeCount, peakCount}'
```

#### Using Mission Control (GUI Analysis)

```bash
# Open JFR file in JMC (requires JMC installed)
jmc target/jfr-recording.jfr

# Or download from: https://www.oracle.com/java/technologies/javase/jdk-mission-control.html
```

### 2.7 JFR Multi-Variant Collection

**`scripts/run-jfr-all-variants.sh`:**

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/jfr/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - JFR Recording Suite ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"

# Function to run with JFR
run_variant_jfr() {
    local JAVA_HOME=$1
    local VARIANT_NAME=$2
    
    export JAVA_HOME=$JAVA_HOME
    echo ""
    echo "--- Running application with JFR on $VARIANT_NAME ---"
    echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
    
    cd "$PROJECT_ROOT"
    
    # Build
    ./mvnw clean package -DskipTests -q
    
    # Start with JFR recording
    timeout 600 \
    java \
        -XX:StartFlightRecording=settings=profile,\
duration=600s,\
filename=$RESULTS_DIR/jfr-$VARIANT_NAME.jfr \
        -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar &
    
    APP_PID=$!
    
    # Wait for app startup
    sleep 5
    
    # Run load test during recording
    ./scripts/run-load-test.sh
    
    # Wait for recording to complete
    wait $APP_PID 2>/dev/null || true
    
    # Convert to JSON for easier analysis
    jfr print --json "$RESULTS_DIR/jfr-$VARIANT_NAME.jfr" \
        > "$RESULTS_DIR/jfr-$VARIANT_NAME.json"
    
    echo "✓ JFR recording for $VARIANT_NAME completed"
}

# Run for each variant
if [ -n "$JAVA_17_HOME" ]; then
    run_variant_jfr "$JAVA_17_HOME" "java17-baseline"
fi

if [ -n "$JAVA_21_HOME" ]; then
    run_variant_jfr "$JAVA_21_HOME" "java21-variant-a"
fi

echo ""
echo "=== All JFR recordings completed ==="
echo "Results saved to: $RESULTS_DIR"
```

---

## 3. Test Suite Execution

### 3.1 Overview

The test suite validates functionality and performance across all variants:

- **Unit tests**: Fast in-memory tests (~10-30s)
- **Integration tests**: Database-backed tests (~2-5min)
- **Coverage reports**: JaCoCo code coverage analysis

### 3.2 Maven Test Execution

#### Run All Tests

```bash
# Run all tests (unit + integration)
export JAVA_HOME=$JAVA_17_HOME
./mvnw clean verify

# Expected output:
# [INFO] --------
# [INFO] BUILD SUCCESS
# [INFO] Total time: XX.XXs
# [INFO] Finished at: YYYY-MM-DDTHH:MM:SSZ
# [INFO] Final Memory: XXMb/XXXMb
```

#### Run Unit Tests Only

```bash
# Unit tests only (skip integration)
./mvnw clean test

# Runtime: ~30s
```

#### Run Integration Tests Only

```bash
# Integration tests only
./mvnw clean verify -DskipUnitTests

# Runtime: ~2-5 minutes (depends on Docker availability)
```

#### Run Tests with Coverage Report

```bash
# Generate JaCoCo coverage report
./mvnw clean verify jacoco:report

# Coverage report generated at:
# target/site/jacoco/index.html

# View summary
cat target/site/jacoco/index.html | grep -A5 "Total"
```

#### Run Tests for Specific Variant

```bash
# Java 17 baseline
export JAVA_HOME=$JAVA_17_HOME
./mvnw clean verify

# Java 21 with virtual threads
export JAVA_HOME=$JAVA_21_HOME
./mvnw clean verify -P vthreads
```

### 3.3 Gradle Test Execution

#### Run All Tests

```bash
# Run all tests (unit + integration)
export JAVA_HOME=$JAVA_17_HOME
./gradlew clean build

# Expected output:
# BUILD SUCCESSFUL in XXs
# X actionable tasks: X executed
```

#### Run Unit Tests Only

```bash
# Unit tests only
./gradlew clean test

# Runtime: ~30s
```

#### Run Tests with Coverage Report

```bash
# Generate coverage report
./gradlew clean build jacocoTestReport

# Coverage report generated at:
# build/reports/jacoco/test/html/index.html
```

#### Run Tests for Specific Variant

```bash
# Java 17 baseline
export JAVA_HOME=$JAVA_17_HOME
./gradlew clean build

# Java 21
export JAVA_HOME=$JAVA_21_HOME
./gradlew clean build
```

### 3.4 JUnit XML Output Parsing

#### Generate XML Test Results

**Maven:**
```bash
# XML automatically generated at: target/surefire-reports/

./mvnw clean verify

# Test report files:
# target/surefire-reports/TEST-*.xml
```

**Gradle:**
```bash
# XML automatically generated at: build/test-results/

./gradlew clean test

# Test report files:
# build/test-results/test/TEST-*.xml
```

#### Parse Test Results

Using `xmlstarlet` (install: `brew install xmlstarlet`):

```bash
# Count total tests
xmlstarlet sel -t -v 'sum(//testcase/@*[name()="tests"])' \
    target/surefire-reports/TEST-*.xml

# Extract failures
xmlstarlet sel -t -m '//failure' \
    -v '@type' -o ': ' -v 'text()' -n \
    target/surefire-reports/TEST-*.xml

# Extract test durations
xmlstarlet sel -t -m '//testcase' \
    -v '@name' -o ' (' -v '@time' -o 's)' -n \
    target/surefire-reports/TEST-*.xml | sort -t'(' -k2 -rn
```

#### Parse JaCoCo Coverage Reports

```bash
# Extract coverage percentages
grep -oP 'class="counter">\s*<span class="hidden">\K[^<]+' \
    target/site/jacoco/index.html

# Alternative: Parse with jq (from JSON export)
jq '.counters[] | {type, missed, covered}' \
    target/site/jacoco-overall.json
```

#### Create Comparison Report

**`scripts/compare-test-results.sh`:**

```bash
#!/bin/bash

VARIANT1_REPORTS=$1  # e.g., build-java17/surefire-reports
VARIANT2_REPORTS=$2  # e.g., build-java21/surefire-reports

echo "=== Test Results Comparison ==="
echo ""

for xml in $VARIANT1_REPORTS/TEST-*.xml; do
    TEST_NAME=$(basename $xml)
    V1_TIME=$(xmlstarlet sel -t -v 'sum(//testcase/@time)' "$xml")
    
    MATCHING_V2="$VARIANT2_REPORTS/$TEST_NAME"
    if [ -f "$MATCHING_V2" ]; then
        V2_TIME=$(xmlstarlet sel -t -v 'sum(//testcase/@time)' "$MATCHING_V2")
        DIFF=$(echo "$V2_TIME - $V1_TIME" | bc)
        PCT=$(echo "scale=2; ($DIFF / $V1_TIME) * 100" | bc)
        echo "$TEST_NAME: $V1_TIME -> $V2_TIME (${PCT}%)"
    fi
done
```

### 3.5 Multi-Variant Test Execution

**`scripts/run-tests-all-variants.sh`:**

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/tests/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - Test Suite ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"

# Function to run tests
run_variant_tests() {
    local JAVA_HOME=$1
    local VARIANT_NAME=$2
    local PROFILE=$3
    
    export JAVA_HOME=$JAVA_HOME
    echo ""
    echo "--- Running tests on $VARIANT_NAME ---"
    echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
    
    cd "$PROJECT_ROOT"
    
    # Run tests
    if [ -n "$PROFILE" ]; then
        ./mvnw clean verify -P $PROFILE \
            -DskipITs=false \
            -Djacoco.destFile="$RESULTS_DIR/jacoco-$VARIANT_NAME.exec"
    else
        ./mvnw clean verify \
            -DskipITs=false \
            -Djacoco.destFile="$RESULTS_DIR/jacoco-$VARIANT_NAME.exec"
    fi
    
    # Copy test results
    cp -r target/surefire-reports \
        "$RESULTS_DIR/surefire-reports-$VARIANT_NAME"
    cp -r target/site/jacoco \
        "$RESULTS_DIR/jacoco-report-$VARIANT_NAME"
    
    echo "✓ Tests for $VARIANT_NAME completed"
}

# Run for each variant
if [ -n "$JAVA_17_HOME" ]; then
    run_variant_tests "$JAVA_17_HOME" "java17-baseline"
fi

if [ -n "$JAVA_21_HOME" ]; then
    run_variant_tests "$JAVA_21_HOME" "java21-variant-a"
    run_variant_tests "$JAVA_21_HOME" "java21-variant-b" "vthreads"
fi

echo ""
echo "=== All tests completed ==="
echo "Results saved to: $RESULTS_DIR"
```

---

## 4. Load Testing Workflows

### 4.1 Overview

Load testing measures application performance under stress:

- **JMeter**: Simulates realistic user scenarios (requests per second, response times)
- **Gatling**: High-performance load testing with simulation scripting
- **Thread groups**: Gradual ramp-up to steady-state load
- **Metrics**: Response time, throughput, error rate

### 4.2 JMeter Execution

#### Prerequisites

```bash
# Download JMeter 5.5+
wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.5.tgz
tar -xzf apache-jmeter-5.5.tgz

# Add to PATH
export PATH=$PATH:/path/to/apache-jmeter-5.5/bin
```

#### Verify Test Plan Exists

```bash
# JMeter test plan is provided at:
ls -lh src/test/jmeter/petclinic_test_plan.jmx

# File size: ~50KB
# Configuration:
#  - 500 threads
#  - 10s ramp-up
#  - 10 iterations per thread
#  - 300ms think time between requests
```

#### Run JMeter from Command Line

**Headless execution (recommended for benchmarks):**

```bash
# Start application first (Terminal 1)
export JAVA_HOME=$JAVA_17_HOME
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar &
APP_PID=$!

# Wait for startup
sleep 5

# Run JMeter test (Terminal 2)
jmeter -n \
    -t src/test/jmeter/petclinic_test_plan.jmx \
    -l target/jmeter-results.jtl \
    -j target/jmeter.log \
    -Jpetclinic.host=localhost \
    -Jpetclinic.port=8080 \
    -Jpetclinic.threads=100 \
    -Jpetclinic.rampup=10 \
    -Jpetclinic.iterations=10

# Results: target/jmeter-results.jtl (CSV format)
# Runtime: ~2-3 minutes (depends on configured threads/iterations)
```

**With custom thread configuration:**

```bash
# Light load test
jmeter -n \
    -t src/test/jmeter/petclinic_test_plan.jmx \
    -l target/jmeter-light.jtl \
    -Jpetclinic.threads=50 \
    -Jpetclinic.rampup=5 \
    -Jpetclinic.iterations=5

# Runtime: ~1 minute

# Heavy load test (stress testing)
jmeter -n \
    -t src/test/jmeter/petclinic_test_plan.jmx \
    -l target/jmeter-heavy.jtl \
    -Jpetclinic.threads=500 \
    -Jpetclinic.rampup=30 \
    -Jpetclinic.iterations=20

# Runtime: ~10-15 minutes
```

#### JMeter Output Parsing

**Extract key metrics from JTL file:**

```bash
# Install jq for JSON processing
brew install jq

# Convert JTL to JSON (or parse as CSV)
# JTL is CSV format: timestamp,elapsed,label,responseCode,responseMessage,threadName

# Calculate average response time
awk -F',' 'NR>1 {sum+=$2; count++} END {print "Avg: " sum/count "ms"}' \
    target/jmeter-results.jtl

# Get response time percentiles
awk -F',' 'NR>1 {times[NR]=$2} END {
    n=length(times)
    for(i=1;i<=n;i++) for(j=i;j<=n;j++)
        if(times[i]>times[j]) {t=times[i]; times[i]=times[j]; times[j]=t}
    p50=times[int(n*0.5)]; p95=times[int(n*0.95)]; p99=times[int(n*0.99)]
    print "p50: " p50 "ms, p95: " p95 "ms, p99: " p99 "ms"
}' target/jmeter-results.jtl

# Count error rate
awk -F',' '$4 >= 400 {errors++} NR>1 {total++} END {
    rate = (errors/total)*100
    print "Error Rate: " rate "%"
}' target/jmeter-results.jtl

# Requests per second
awk -F',' 'NR>1 {
    timestamp=$1
    if (first == "") first = timestamp
    last = timestamp
}
END {
    duration = (last - first) / 1000
    requests = NR - 1
    rps = requests / duration
    print "RPS: " rps
}' target/jmeter-results.jtl
```

**Create JMeter results summary:**

```bash
#!/bin/bash
# scripts/parse-jmeter-results.sh

JTL_FILE=$1

echo "=== JMeter Test Results ==="
echo "File: $JTL_FILE"
echo ""

TOTAL=$(tail -n +2 "$JTL_FILE" | wc -l)
ERRORS=$(awk -F',' '$4 >= 400 {count++} END {print count}' "$JTL_FILE")
RATE=$((ERRORS * 100 / TOTAL))

echo "Total Requests: $TOTAL"
echo "Errors: $ERRORS"
echo "Error Rate: $RATE%"
echo ""

echo "Response Times (ms):"
awk -F',' 'NR>1 {sum+=$2; min=($2<min||!min)?$2:min; max=($2>max)?$2:max; times[NR]=$2}
END {
    n=length(times)
    for(i=1;i<=n;i++) for(j=i;j<=n;j++)
        if(times[i]>times[j]) {t=times[i]; times[i]=times[j]; times[j]=t}
    avg=sum/n
    p50=times[int(n*0.5)]; p95=times[int(n*0.95)]; p99=times[int(n*0.99)]
    print "  Min:  " min
    print "  Avg:  " avg
    print "  p50:  " p50
    print "  p95:  " p95
    print "  p99:  " p99
    print "  Max:  " max
}' "$JTL_FILE"
```

### 4.3 Gatling Load Testing

#### Gatling Installation

```bash
# Download Gatling 3.9+ (latest)
wget https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/3.9.5/gatling-charts-highcharts-bundle-3.9.5-bundle.zip
unzip gatling-charts-highcharts-bundle-3.9.5-bundle.zip

# Add to PATH
export PATH=$PATH:gatling-charts-highcharts-bundle-3.9.5/bin
```

#### Create Gatling Simulation

Create `src/test/scala/org/springframework/samples/petclinic/PetClinicSimulation.scala`:

```scala
package org.springframework.samples.petclinic

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PetClinicSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("text/html,application/xhtml+xml")
    .acceptLanguageHeader("en-US,en;q=0.9")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Mozilla/5.0 (X11; Linux x86_64)")

  val scenarioBuilder = scenario("PetClinic Load Test")
    .exec(
      http("Home Page")
        .get("/")
        .check(status.is(200))
    )
    .pause(1)
    .exec(
      http("List Vets")
        .get("/vets.html")
        .check(status.is(200))
    )
    .pause(1)
    .exec(
      http("Find Owner")
        .get("/owners/find")
        .check(status.is(200))
    )
    .pause(1)
    .exec(
      http("Search Owners")
        .get("/owners?lastName=")
        .check(status.is(200))
    )
    .pause(1)
    .exec(
      http("Owner Details")
        .get("/owners/1")
        .check(status.is(200))
    )

  setUp(
    scenarioBuilder
      .inject(
        // Ramp-up: 0-100 users over 30 seconds
        rampUsers(100) during (30 seconds),
        // Steady state: hold at 100 users for 3 minutes
        constantUsersPerSec(10) during (180 seconds),
        // Ramp-down: gradually decrease
        rampDownUsers(100) during (30 seconds)
      )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(500),  // p99 < 500ms
      global.responseTime.percentile2.lte(200),  // p95 < 200ms
      global.successfulRequests.percent.gte(95)  // >95% success
    )
}
```

#### Run Gatling Simulation

```bash
# Compile Scala simulation
gatling.sh -sf src/test/scala -pkg petclinic -s PetClinicSimulation

# Results generated at:
# results/petcliniсsimulation-xxx/index.html

# Open results in browser
open results/petcliniсsimulation-xxx/index.html
```

### 4.4 Load Test Orchestration Script

**`scripts/run-load-test.sh`:**

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/load-tests/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - Load Testing Suite ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"

# Configuration
HOST=${1:-localhost}
PORT=${2:-8080}
THREADS=${3:-100}
RAMPUP=${4:-10}
ITERATIONS=${5:-10}

echo "Configuration:"
echo "  Host: $HOST"
echo "  Port: $PORT"
echo "  Threads: $THREADS"
echo "  Ramp-up: ${RAMPUP}s"
echo "  Iterations: $ITERATIONS"
echo ""

# Check if application is running
echo "Checking if application is running on $HOST:$PORT..."
if ! nc -z $HOST $PORT 2>/dev/null; then
    echo "Error: Application not running on $HOST:$PORT"
    exit 1
fi
echo "✓ Application is running"
echo ""

# Run JMeter test
echo "--- Starting JMeter Load Test ---"
jmeter -n \
    -t "$PROJECT_ROOT/src/test/jmeter/petclinic_test_plan.jmx" \
    -l "$RESULTS_DIR/jmeter-results.jtl" \
    -j "$RESULTS_DIR/jmeter.log" \
    -Jpetclinic.host=$HOST \
    -Jpetclinic.port=$PORT \
    -Jpetclinic.threads=$THREADS \
    -Jpetclinic.rampup=$RAMPUP \
    -Jpetclinic.iterations=$ITERATIONS

echo "✓ JMeter load test completed"
echo ""

# Parse results
echo "--- Parsing JMeter Results ---"
"$PROJECT_ROOT/scripts/parse-jmeter-results.sh" "$RESULTS_DIR/jmeter-results.jtl" \
    > "$RESULTS_DIR/jmeter-summary.txt"

cat "$RESULTS_DIR/jmeter-summary.txt"

echo ""
echo "=== Load testing completed ==="
echo "Results saved to: $RESULTS_DIR"
```

---

## 5. Actuator Metrics Collection

### 5.1 Overview

Spring Boot Actuator exposes metrics endpoints for:

- Request metrics (count, duration, errors)
- JVM metrics (memory, GC, threads)
- System metrics (CPU, disk)
- Custom application metrics

### 5.2 Accessing Actuator Endpoints

#### List All Metrics

```bash
# Start application first
export JAVA_HOME=$JAVA_17_HOME
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar &
APP_PID=$!

sleep 5

# List all available metrics
curl -s http://localhost:8080/actuator/metrics | jq '.'

# Output:
# {
#   "names": [
#     "http.server.requests",
#     "jvm.memory.used",
#     "jvm.threads.live",
#     "process.cpu.usage",
#     ...
#   ]
# }
```

#### Key Metrics Endpoints

```bash
# HTTP Request metrics
curl -s 'http://localhost:8080/actuator/metrics/http.server.requests' | jq '.'

# JVM Memory metrics
curl -s 'http://localhost:8080/actuator/metrics/jvm.memory.used' | jq '.'

# JVM Thread metrics
curl -s 'http://localhost:8080/actuator/metrics/jvm.threads.live' | jq '.'

# Process CPU usage
curl -s 'http://localhost:8080/actuator/metrics/process.cpu.usage' | jq '.'

# GC metrics
curl -s 'http://localhost:8080/actuator/metrics/jvm.gc.max.data.size' | jq '.'
```

### 5.3 Key Metrics to Collect

```bash
# Request latency (p99, p95, mean)
curl -s http://localhost:8080/actuator/metrics/http.server.requests | jq '.measurements'

# Memory usage
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap | jq '.measurements'

# Live thread count
curl -s http://localhost:8080/actuator/metrics/jvm.threads.live | jq '.measurements[]'

# GC pause time
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause | jq '.measurements'

# Error rate
curl -s http://localhost:8080/actuator/metrics/http.server.requests | \
    jq '.measurements[] | select(.statistic == "COUNT")'
```

### 5.4 Automated Metrics Collection Script

**`scripts/collect-actuator-metrics.sh`:**

```bash
#!/bin/bash
set -e

HOST=${1:-localhost}
PORT=${2:-8080}
OUTPUT_FILE=${3:-actuator-metrics.jsonl}
POLL_INTERVAL=${4:-5}  # seconds between polls
POLL_COUNT=${5:-120}   # number of polls (120 * 5s = 10 minutes)

echo "=== Actuator Metrics Collection ==="
echo "Host: $HOST:$PORT"
echo "Interval: ${POLL_INTERVAL}s"
echo "Duration: $(( POLL_COUNT * POLL_INTERVAL ))s"
echo "Output: $OUTPUT_FILE"
echo ""

# Verify application is running
if ! nc -z $HOST $PORT 2>/dev/null; then
    echo "Error: Application not running on $HOST:$PORT"
    exit 1
fi

# Function to collect metrics at specific timestamp
collect_metrics() {
    local TIMESTAMP=$1
    local CURL_TIMEOUT=5
    
    # Create JSON object with all metrics
    local METRICS_JSON=$(cat <<EOF
{
  "timestamp": "$TIMESTAMP",
  "timestamp_unix": $(date +%s%3N),
  "metrics": {
EOF
    )
    
    # Collect each metric
    local HTTP_REQUESTS=$(curl -s --max-time $CURL_TIMEOUT \
        http://$HOST:$PORT/actuator/metrics/http.server.requests)
    
    local JVM_MEMORY=$(curl -s --max-time $CURL_TIMEOUT \
        http://$HOST:$PORT/actuator/metrics/jvm.memory.used?tag=area:heap)
    
    local JVM_THREADS=$(curl -s --max-time $CURL_TIMEOUT \
        http://$HOST:$PORT/actuator/metrics/jvm.threads.live)
    
    local CPU_USAGE=$(curl -s --max-time $CURL_TIMEOUT \
        http://$HOST:$PORT/actuator/metrics/process.cpu.usage)
    
    # Parse and combine
    local REQUEST_COUNT=$(echo "$HTTP_REQUESTS" | jq -r '.measurements[] | select(.statistic == "COUNT") | .value' 2>/dev/null || echo "null")
    local REQUEST_AVG=$(echo "$HTTP_REQUESTS" | jq -r '.measurements[] | select(.statistic == "MEAN") | .value' 2>/dev/null || echo "null")
    local MEMORY_VALUE=$(echo "$JVM_MEMORY" | jq -r '.measurements[0].value' 2>/dev/null || echo "null")
    local THREADS_VALUE=$(echo "$JVM_THREADS" | jq -r '.measurements[0].value' 2>/dev/null || echo "null")
    local CPU_VALUE=$(echo "$CPU_USAGE" | jq -r '.measurements[0].value' 2>/dev/null || echo "null")
    
    cat <<EOF
{
  "timestamp": "$TIMESTAMP",
  "timestamp_unix": $(date +%s%3N),
  "http.requests.count": $REQUEST_COUNT,
  "http.requests.avg_ms": $REQUEST_AVG,
  "jvm.memory.heap.used_bytes": $MEMORY_VALUE,
  "jvm.threads.live": $THREADS_VALUE,
  "process.cpu.usage": $CPU_VALUE
}
EOF
}

# Clear output file
> "$OUTPUT_FILE"

# Collection loop
for i in $(seq 1 $POLL_COUNT); do
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")
    echo -n "[$i/$POLL_COUNT] $TIMESTAMP ... "
    
    METRICS=$(collect_metrics "$TIMESTAMP")
    echo "$METRICS" >> "$OUTPUT_FILE"
    
    echo "✓"
    
    if [ $i -lt $POLL_COUNT ]; then
        sleep $POLL_INTERVAL
    fi
done

echo ""
echo "=== Collection Complete ==="
echo "Total samples: $POLL_COUNT"
echo "Output file: $OUTPUT_FILE"
echo ""
echo "First sample:"
head -1 "$OUTPUT_FILE" | jq '.'
echo ""
echo "Last sample:"
tail -1 "$OUTPUT_FILE" | jq '.'
```

**Usage:**

```bash
# Start metrics collection (background)
./scripts/collect-actuator-metrics.sh localhost 8080 metrics.jsonl 5 120 &
METRICS_PID=$!

# Run load test while collecting
sleep 5
./scripts/run-load-test.sh

# Wait for metrics collection to complete
wait $METRICS_PID

# Analyze collected metrics
jq -s 'map(.http.requests.avg_ms) | [min, max, add/length]' metrics.jsonl
```

### 5.5 Multi-Metric Collection During Load Testing

**`scripts/full-benchmark-with-metrics.sh`:**

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/full/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - Full Benchmark with Metrics ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"
echo ""

# Configuration
JAVA_VERSION=${1:-17}
if [ "$JAVA_VERSION" = "17" ]; then
    export JAVA_HOME=$JAVA_17_HOME
    VARIANT="java17-baseline"
elif [ "$JAVA_VERSION" = "21a" ]; then
    export JAVA_HOME=$JAVA_21_HOME
    VARIANT="java21-variant-a"
elif [ "$JAVA_VERSION" = "21b" ]; then
    export JAVA_HOME=$JAVA_21_HOME
    VARIANT="java21-variant-b"
fi

echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
echo "Variant: $VARIANT"
echo ""

# Build
echo "--- Building application ---"
cd "$PROJECT_ROOT"
./mvnw clean package -DskipTests -q
echo "✓ Build completed"
echo ""

# Start application
echo "--- Starting application ---"
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar > "$RESULTS_DIR/app.log" 2>&1 &
APP_PID=$!
echo "App PID: $APP_PID"

sleep 5

if ! nc -z localhost 8080; then
    echo "Error: Application failed to start"
    cat "$RESULTS_DIR/app.log"
    exit 1
fi
echo "✓ Application started"
echo ""

# Start metrics collection (background)
echo "--- Starting metrics collection ---"
"$SCRIPT_DIR/collect-actuator-metrics.sh" localhost 8080 \
    "$RESULTS_DIR/metrics.jsonl" 5 180 &
METRICS_PID=$!
echo "Metrics PID: $METRICS_PID"
echo "✓ Metrics collection started"
echo ""

# Start JFR recording (if Java 17+)
echo "--- Starting JFR recording ---"
jcmd $APP_PID JFR.start \
    name=bench_record \
    settings=profile \
    duration=600s \
    filename="$RESULTS_DIR/recording.jfr" \
    2>/dev/null || true
sleep 2
jcmd $APP_PID JFR.check 2>/dev/null || true
echo "✓ JFR recording started"
echo ""

# Run load test
echo "--- Running load test ---"
"$SCRIPT_DIR/run-load-test.sh" localhost 8080 100 10 10

cp target/surefire-reports/* "$RESULTS_DIR/" 2>/dev/null || true
echo "✓ Load test completed"
echo ""

# Wait for metrics collection to finish
wait $METRICS_PID 2>/dev/null || true

# Stop application
echo "--- Stopping application ---"
kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true
echo "✓ Application stopped"
echo ""

# Convert JFR to JSON if available
if [ -f "$RESULTS_DIR/recording.jfr" ]; then
    echo "--- Converting JFR recording ---"
    jfr print --json "$RESULTS_DIR/recording.jfr" \
        > "$RESULTS_DIR/recording.json" 2>/dev/null || true
    echo "✓ JFR conversion completed"
    echo ""
fi

echo "=== Benchmark Complete ==="
echo "Results saved to: $RESULTS_DIR"
echo ""
echo "Output files:"
ls -lh "$RESULTS_DIR" | tail -n +2 | awk '{print "  " $9 " (" $5 ")"}'
```

---

## 6. Database Reset Procedures

### 6.1 H2 In-Memory Database

No reset required - H2 in-memory database is recreated on each application restart:

```bash
# Kill current application
pkill -f spring-petclinic

# Start new instance (automatically loads fresh schema and data)
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

### 6.2 MySQL Database Reset

```bash
# Prerequisites: MySQL running via Docker Compose
docker-compose up -d mysql

# Reset database schema and data
docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
    -e "DROP SCHEMA petclinic; CREATE SCHEMA petclinic;"

# Reload initial schema (from Spring Boot)
# Option 1: Restart application with fresh schema
export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# Option 2: Run SQL scripts manually
docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
    < src/main/resources/db/mysql/schema.sql
docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
    < src/main/resources/db/mysql/data.sql
```

### 6.3 PostgreSQL Database Reset

```bash
# Prerequisites: PostgreSQL running via Docker Compose
docker-compose up -d postgres

# Reset database
docker-compose exec -T postgres psql -U petclinic -d petclinic \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

# Reload initial schema
docker-compose exec -T postgres psql -U petclinic -d petclinic \
    -f src/main/resources/db/postgresql/schema.sql
docker-compose exec -T postgres psql -U petclinic -d petclinic \
    -f src/main/resources/db/postgresql/data.sql
```

### 6.4 Automated Database Reset Script

**`scripts/reset-database.sh`:**

```bash
#!/bin/bash
set -e

DB_TYPE=${1:-h2}  # h2, mysql, postgres

echo "=== Database Reset ==="
echo "Database Type: $DB_TYPE"

case $DB_TYPE in
    h2)
        echo "✓ H2 (in-memory) - no reset needed"
        ;;
    mysql)
        echo "Resetting MySQL..."
        docker-compose up -d mysql
        sleep 5
        
        docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
            -e "DROP SCHEMA petclinic; CREATE SCHEMA petclinic;"
        
        # Reload schema
        docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
            < src/main/resources/db/mysql/schema.sql
        docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
            < src/main/resources/db/mysql/data.sql
        
        echo "✓ MySQL reset completed"
        ;;
    postgres)
        echo "Resetting PostgreSQL..."
        docker-compose up -d postgres
        sleep 5
        
        docker-compose exec -T postgres psql -U petclinic -d postgres \
            -c "DROP DATABASE IF EXISTS petclinic; CREATE DATABASE petclinic;"
        
        docker-compose exec -T postgres psql -U petclinic -d petclinic \
            -f src/main/resources/db/postgresql/schema.sql
        docker-compose exec -T postgres psql -U petclinic -d petclinic \
            -f src/main/resources/db/postgresql/data.sql
        
        echo "✓ PostgreSQL reset completed"
        ;;
    *)
        echo "Error: Unknown database type: $DB_TYPE"
        exit 1
        ;;
esac

echo ""
echo "Ready for new benchmark run"
```

---

## 7. Complete End-to-End Workflow

### 7.1 Full Benchmark Execution: Java 17 Baseline

Complete workflow with all tools (JMH, JFR, Tests, Load Testing, Metrics):

**`scripts/benchmark-complete.sh`:**

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

# Configuration
export JAVA_HOME=${JAVA_17_HOME:-$(java -XshowSettings:properties -version 2>&1 | grep java.home | awk '{print $3}')}
VARIANT="java17-baseline"

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  Spring PetClinic - Complete Benchmark Suite                       ║"
echo "║  Variant: $VARIANT"
echo "║  Timestamp: $(date)"
echo "║  Results: $RESULTS_DIR"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""

# Timing tracking
START_TIME=$(date +%s)
PHASE_START=$START_TIME

# ============================================================================
# Phase 1: Preparation (5 minutes)
# ============================================================================
echo "┌─ PHASE 1: Preparation ─────────────────────────────────────────────┐"
PHASE_START=$(date +%s)

echo "▶ Building application..."
cd "$PROJECT_ROOT"
./mvnw clean package -DskipTests -q
BUILD_TIME=$(($(date +%s) - PHASE_START))
echo "✓ Build completed ($BUILD_TIME seconds)"

echo "▶ Verifying tools..."
java -version
jmeter --version >/dev/null 2>&1 && echo "✓ JMeter available" || echo "✗ JMeter not available"
jcmd -l >/dev/null 2>&1 && echo "✓ jcmd available" || echo "✗ jcmd not available"
echo ""

PHASE_TIME=$(($(date +%s) - PHASE_START))
echo "└─ Phase 1 completed in $PHASE_TIME seconds"
echo ""

# ============================================================================
# Phase 2: Unit & Integration Tests (5 minutes)
# ============================================================================
echo "┌─ PHASE 2: Test Suite Execution ───────────────────────────────────┐"
PHASE_START=$(date +%s)

echo "▶ Running unit and integration tests..."
./mvnw verify -DskipITs=false -q
echo "✓ Tests passed"

# Copy test results
cp -r target/surefire-reports "$RESULTS_DIR/surefire-reports" 2>/dev/null || true
cp -r target/site/jacoco "$RESULTS_DIR/jacoco-report" 2>/dev/null || true

TEST_COUNT=$(grep -r "testcase" "$RESULTS_DIR/surefire-reports" 2>/dev/null | wc -l)
echo "  Total test cases: ~$TEST_COUNT"

PHASE_TIME=$(($(date +%s) - PHASE_START))
echo "└─ Phase 2 completed in $PHASE_TIME seconds"
echo ""

# ============================================================================
# Phase 3: Startup & JFR Recording (12 minutes)
# ============================================================================
echo "┌─ PHASE 3: JFR Recording with Load Testing ────────────────────────┐"
PHASE_START=$(date +%s)

echo "▶ Starting application with JFR recording..."
java \
    -XX:StartFlightRecording=settings=profile,\
duration=600s,\
filename=$RESULTS_DIR/recording.jfr \
    -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
    > "$RESULTS_DIR/app.log" 2>&1 &
APP_PID=$!

sleep 10

if ! nc -z localhost 8080 2>/dev/null; then
    echo "✗ Application failed to start"
    cat "$RESULTS_DIR/app.log" | head -20
    exit 1
fi
echo "✓ Application started (PID: $APP_PID)"

echo "▶ Starting metrics collection..."
"$SCRIPT_DIR/collect-actuator-metrics.sh" localhost 8080 \
    "$RESULTS_DIR/metrics.jsonl" 5 120 &
METRICS_PID=$!
sleep 2
echo "✓ Metrics collection started"

echo "▶ Running load test (100 threads, 10 iterations)..."
"$SCRIPT_DIR/run-load-test.sh" localhost 8080 100 10 10 > "$RESULTS_DIR/load-test.log" 2>&1
echo "✓ Load test completed"

echo "▶ Collecting application logs..."
cp "$RESULTS_DIR/app.log" "$RESULTS_DIR/app-full.log"

echo "▶ Waiting for metrics and JFR..."
wait $METRICS_PID 2>/dev/null || true
sleep 10
kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true
echo "✓ Application stopped"

echo "▶ Converting JFR to JSON..."
if [ -f "$RESULTS_DIR/recording.jfr" ]; then
    jfr print --json "$RESULTS_DIR/recording.jfr" \
        > "$RESULTS_DIR/recording.json" 2>/dev/null || true
    echo "✓ JFR conversion completed"
fi

PHASE_TIME=$(($(date +%s) - PHASE_START))
echo "└─ Phase 3 completed in $PHASE_TIME seconds"
echo ""

# ============================================================================
# Phase 4: Analysis & Reporting (2 minutes)
# ============================================================================
echo "┌─ PHASE 4: Analysis & Reporting ───────────────────────────────────┐"
PHASE_START=$(date +%s)

echo "▶ Parsing JMeter results..."
JMETER_RESULTS="$RESULTS_DIR/jmeter-results.jtl"
if [ -f "$JMETER_RESULTS" ]; then
    TOTAL_REQUESTS=$(tail -n +2 "$JMETER_RESULTS" | wc -l)
    ERRORS=$(awk -F',' '$4 >= 400 {count++} END {print count+0}' "$JMETER_RESULTS")
    ERROR_RATE=$((ERRORS * 100 / TOTAL_REQUESTS))
    
    AVG_TIME=$(awk -F',' 'NR>1 {sum+=$2; count++} END {print int(sum/count)}' "$JMETER_RESULTS")
    
    echo "  Total Requests: $TOTAL_REQUESTS"
    echo "  Errors: $ERRORS ($ERROR_RATE%)"
    echo "  Avg Response Time: ${AVG_TIME}ms"
fi

echo "▶ Analyzing metrics..."
if [ -f "$RESULTS_DIR/metrics.jsonl" ]; then
    SAMPLE_COUNT=$(wc -l < "$RESULTS_DIR/metrics.jsonl")
    echo "  Samples collected: $SAMPLE_COUNT"
    
    # Average memory usage
    AVG_MEMORY=$(jq -s 'map(.jvm.memory.heap.used_bytes | select(. != null)) | add / length / 1048576 | round' \
        "$RESULTS_DIR/metrics.jsonl" 2>/dev/null || echo "N/A")
    echo "  Avg Memory (Heap): ${AVG_MEMORY}MB"
fi

# Create summary report
cat > "$RESULTS_DIR/RESULTS.txt" <<EOF
╔════════════════════════════════════════════════════════════════════╗
║              BENCHMARK RESULTS - $VARIANT
║              Timestamp: $(date)
║              Duration: $(($(date +%s) - START_TIME)) seconds
╚════════════════════════════════════════════════════════════════════╝

BENCHMARK CONFIGURATION
=======================
Java Version: $(java -version 2>&1 | head -1)
Variant: $VARIANT

EXECUTION RESULTS
=================
Build Time: ${BUILD_TIME}s
Test Suite Time: ${TEST_TIME}s
JFR + Load Test Time: $(($(date +%s) - PHASE_START))s

LOAD TEST METRICS
=================
Total Requests: ${TOTAL_REQUESTS:-N/A}
Errors: ${ERRORS:-N/A} (${ERROR_RATE:-N/A}%)
Avg Response Time: ${AVG_TIME:-N/A}ms

JVM METRICS (from Actuator)
===========================
Avg Memory Usage: ${AVG_MEMORY:-N/A}MB
Samples Collected: ${SAMPLE_COUNT:-N/A}

OUTPUT FILES
============
- metrics.jsonl: Actuator metrics timeseries
- recording.jfr: Java Flight Recorder output
- recording.json: JFR converted to JSON
- jmeter-results.jtl: Load test results
- surefire-reports/: Unit test results
- jacoco-report/: Code coverage report
- app.log: Application startup log

END-TO-END EXECUTION TIME
==========================
Total: $(($(date +%s) - START_TIME)) seconds (~$(( ($(date +%s) - START_TIME) / 60 )) minutes)
EOF

cat "$RESULTS_DIR/RESULTS.txt"

PHASE_TIME=$(($(date +%s) - PHASE_START))
echo "└─ Phase 4 completed in $PHASE_TIME seconds"
echo ""

# ============================================================================
# Summary
# ============================================================================
TOTAL_TIME=$(($(date +%s) - START_TIME))

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  BENCHMARK COMPLETE                                                ║"
echo "║  Total Time: $TOTAL_TIME seconds (~$(( TOTAL_TIME / 60 )) minutes)"
echo "║  Results: $RESULTS_DIR"
echo "╚════════════════════════════════════════════════════════════════════╝"
```

**Usage:**

```bash
chmod +x scripts/benchmark-complete.sh

# Run full benchmark
./scripts/benchmark-complete.sh

# Expected output:
# ✓ Benchmark completed in ~20-25 minutes
# Results saved to: benchmark-results/20240115_143022/
```

### 7.2 Timing Estimates per Phase

| Phase | Tool | Estimated Time | Notes |
|-------|------|-----------------|-------|
| **Preparation** | Maven Build | 3-5 min | Full clean build, downloads dependencies |
| **Unit Tests** | JUnit/Maven | 1-2 min | Fast in-memory tests |
| **Integration Tests** | JUnit/Docker | 2-5 min | Database-backed tests |
| **JFR Recording** | JFR | 10 min | Profiling with load test |
| **JMeter Load Test** | JMeter | 2-3 min | 100 threads, 10 iterations |
| **Metrics Collection** | Actuator/Polling | 10 min | 120 samples @ 5s intervals |
| **Analysis** | Scripts | 1-2 min | Parsing results, generating reports |
| **TOTAL** | - | **20-30 minutes** | Per single variant |

### 7.3 Multi-Variant Full Benchmark

**`scripts/benchmark-all-variants.sh`:**

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
MASTER_RESULTS="$PROJECT_ROOT/benchmark-results/MASTER_$(date +%Y%m%d_%H%M%S)"

mkdir -p "$MASTER_RESULTS"

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  Spring PetClinic - Multi-Variant Benchmark Suite                  ║"
echo "║  Master Results: $MASTER_RESULTS"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""

OVERALL_START=$(date +%s)

# Run Java 17 Baseline
if [ -n "$JAVA_17_HOME" ]; then
    echo "Starting Java 17 baseline benchmark..."
    export JAVA_HOME=$JAVA_17_HOME
    "$SCRIPT_DIR/benchmark-complete.sh" \
        | tee "$MASTER_RESULTS/java17-baseline.log"
    
    # Archive results
    LATEST_RESULTS=$(ls -td benchmark-results/*/ | head -1)
    mv "$LATEST_RESULTS" "$MASTER_RESULTS/java17-baseline"
    echo ""
fi

# Run Java 21 Variant A
if [ -n "$JAVA_21_HOME" ]; then
    echo "Starting Java 21 Variant A benchmark..."
    export JAVA_HOME=$JAVA_21_HOME
    "$SCRIPT_DIR/benchmark-complete.sh" \
        | tee "$MASTER_RESULTS/java21-variant-a.log"
    
    LATEST_RESULTS=$(ls -td benchmark-results/*/ | head -1)
    mv "$LATEST_RESULTS" "$MASTER_RESULTS/java21-variant-a"
    echo ""
    
    # Run Java 21 Variant B (Virtual Threads)
    echo "Starting Java 21 Variant B (Virtual Threads) benchmark..."
    export SPRING_PROFILES_ACTIVE=vthreads
    "$SCRIPT_DIR/benchmark-complete.sh" \
        | tee "$MASTER_RESULTS/java21-variant-b.log"
    
    LATEST_RESULTS=$(ls -td benchmark-results/*/ | head -1)
    mv "$LATEST_RESULTS" "$MASTER_RESULTS/java21-variant-b"
    unset SPRING_PROFILES_ACTIVE
    echo ""
fi

OVERALL_TIME=$(($(date +%s) - OVERALL_START))

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  ALL VARIANTS COMPLETE                                              ║"
echo "║  Total Time: $OVERALL_TIME seconds (~$(( OVERALL_TIME / 60 )) minutes)"
echo "║  Master Results: $MASTER_RESULTS"
echo "╚════════════════════════════════════════════════════════════════════╝"
```

---

## 8. Output File Reference

### 8.1 File Structure & Locations

```
benchmark-results/
├── 20240115_143022/                  # Timestamp directory
│   ├── RESULTS.txt                   # Summary report
│   │
│   ├── # Test Results
│   ├── surefire-reports/
│   │   ├── TEST-*.xml                # JUnit test results
│   │   └── TESTS-*.properties        # Test summary
│   ├── jacoco-report/
│   │   ├── index.html                # Coverage report
│   │   └── *.csv                     # Coverage data
│   │
│   ├── # Load Testing
│   ├── jmeter-results.jtl            # CSV: timestamp, elapsed, label, responseCode, responseMessage, threadName
│   ├── jmeter-summary.txt            # Parsed metrics (avg, p50, p95, p99)
│   └── load-test.log                 # JMeter log
│   │
│   ├── # JFR & Metrics
│   ├── recording.jfr                 # Binary JFR file
│   ├── recording.json                # JFR converted to JSON
│   ├── metrics.jsonl                 # JSONL: one metric object per line, one per 5-10 seconds
│   │
│   └── # Application Logs
│       └── app.log                   # Spring Boot startup/shutdown logs
```

### 8.2 File Format Descriptions

#### JMeter Results (JTL - CSV)

```
timestamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL
1705340400000,145,Home page,200,OK,User threads 1-1,text,true,,2345,512,1,1,http://localhost:8080/
1705340401000,123,List Vets,200,OK,User threads 1-2,text,true,,3456,512,1,2,http://localhost:8080/vets.html
```

**Key columns:**
- `elapsed`: Response time in ms
- `responseCode`: HTTP status (200, 404, 500, etc.)
- `success`: true/false
- `label`: Request name
- `threadName`: Identifies thread group

**Parsing examples:**
```bash
# Average response time
awk -F',' 'NR>1 {sum+=$2; count++} END {print sum/count}' results.jtl

# Count by response code
awk -F',' 'NR>1 {codes[$3]++} END {for (c in codes) print c, codes[c]}' results.jtl

# Success rate
awk -F',' '$5=="true" {success++} NR>1 {total++} END {print (success/total*100) "%"}' results.jtl
```

#### Metrics JSONL

```jsonl
{"timestamp":"2024-01-15T14:34:00.000Z","timestamp_unix":1705340040000,"http.requests.count":1450,"http.requests.avg_ms":145.2,"jvm.memory.heap.used_bytes":524288000,"jvm.threads.live":42,"process.cpu.usage":0.65}
{"timestamp":"2024-01-15T14:34:05.000Z","timestamp_unix":1705340045000,"http.requests.count":1520,"http.requests.avg_ms":148.5,"jvm.memory.heap.used_bytes":536870912,"jvm.threads.live":43,"process.cpu.usage":0.68}
```

**Parsing examples:**
```bash
# Extract only request latency timeseries
jq -r '[.timestamp, .http.requests.avg_ms] | @csv' metrics.jsonl > latency.csv

# Calculate min/max/avg memory usage
jq -s 'map(.jvm.memory.heap.used_bytes / 1048576) | {min: min, max: max, avg: (add/length)}' metrics.jsonl

# Find samples exceeding threshold
jq 'select(.process.cpu.usage > 0.7)' metrics.jsonl
```

#### JFR JSON Export

```json
[
  {
    "type": "jdk.ExecutionSample",
    "startTime": "2024-01-15T14:34:00.000Z",
    "duration": "10ms",
    "stackTrace": {...},
    "threadId": 42
  },
  {
    "type": "jdk.GarbageCollection",
    "startTime": "2024-01-15T14:34:02.000Z",
    "duration": "45ms",
    "gcAction": "end of major GC",
    "gcCause": "Ergonomics"
  }
]
```

**Parsing examples:**
```bash
# Extract GC events
jq '.[] | select(.type == "jdk.GarbageCollection") | {time: .startTime, duration: .duration, cause: .gcCause}' recording.json

# Calculate total GC time
jq '[.[] | select(.type == "jdk.GarbageCollection") | .duration | ltrimstr("ms") | tonumber] | add' recording.json

# Thread usage statistics
jq '[.[] | select(.type == "jdk.ThreadStatistics") | .peakCount] | {min: min, max: max, avg: (add/length)}' recording.json
```

### 8.3 Aggregation Script

**`scripts/aggregate-results.sh`:**

```bash
#!/bin/bash

RESULTS_DIR=${1:-.}

echo "=== Benchmark Results Aggregation ==="
echo "Source: $RESULTS_DIR"
echo ""

# Find all variants
for variant_dir in $RESULTS_DIR/*/; do
    VARIANT=$(basename "$variant_dir")
    echo "▶ $VARIANT"
    
    if [ -f "$variant_dir/RESULTS.txt" ]; then
        cat "$variant_dir/RESULTS.txt" | grep -E "(Requests|Response Time|Memory|Error)"
    fi
    echo ""
done

# Create comparison table
echo "=== Performance Comparison ==="
echo "Variant, Avg Response Time (ms), Throughput (req/s), Memory (MB)"

for variant_dir in $RESULTS_DIR/*/; do
    VARIANT=$(basename "$variant_dir")
    
    if [ -f "$variant_dir/jmeter-results.jtl" ]; then
        AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {printf "%.0f", sum/count}' "$variant_dir/jmeter-results.jtl")
        THROUGHPUT=$(awk -F',' 'NR>1 {count++} END {printf "%.1f", count/(NR/1000)}' "$variant_dir/jmeter-results.jtl")
    else
        AVG="N/A"
        THROUGHPUT="N/A"
    fi
    
    if [ -f "$variant_dir/metrics.jsonl" ]; then
        MEMORY=$(jq -s 'map(.jvm.memory.heap.used_bytes / 1048576) | add/length | round' "$variant_dir/metrics.jsonl")
    else
        MEMORY="N/A"
    fi
    
    echo "$VARIANT, $AVG, $THROUGHPUT, $MEMORY"
done
```

---

## Appendix: Command Reference

### Quick Start Commands

```bash
# Start application
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify

# Run load test
jmeter -n -t src/test/jmeter/petclinic_test_plan.jmx -l results.jtl

# Collect metrics (10 minutes)
./scripts/collect-actuator-metrics.sh

# Full benchmark (all variants)
./scripts/benchmark-all-variants.sh
```

### Troubleshooting

**Application won't start:**
```bash
# Check port 8080 is available
lsof -i :8080

# Check Java version
java -version

# Verify database connectivity
curl -s http://localhost:8080/actuator/health | jq '.'
```

**JMeter connection errors:**
```bash
# Verify app is running
nc -zv localhost 8080

# Check firewall
sudo ufw allow 8080

# Increase connection timeout
jmeter -t test.jmx -l results.jtl -Djava.net.connectTimeout=5000
```

**Metrics collection timeout:**
```bash
# Increase curl timeout
sed 's/--max-time 5/--max-time 10/g' scripts/collect-actuator-metrics.sh
```

---

## References

- [JMH Documentation](https://github.com/openjdk/jmh)
- [JFR Guide](https://docs.oracle.com/javase/jdk17/docs/technotes/guides/jfr/)
- [JMeter User Manual](https://jmeter.apache.org/usermanual/)
- [Gatling Documentation](https://gatling.io/docs/gatling/)
- [Spring Boot Actuator](https://spring.io/projects/spring-boot)

