# Advanced Setup Guide - Benchmark Suite

This guide provides advanced configuration options and optimization techniques for running the Spring PetClinic benchmark suite across all three Java variants.

---

## Table of Contents

1. [Multi-Variant Build Strategy](#multi-variant-build-strategy)
2. [Automated Testing Setup](#automated-testing-setup)
3. [Continuous Integration](#continuous-integration)
4. [Advanced JVM Tuning](#advanced-jvm-tuning)
5. [Performance Monitoring](#performance-monitoring)
6. [Docker Optimization](#docker-optimization)
7. [Benchmarking Best Practices](#benchmarking-best-practices)

---

## Multi-Variant Build Strategy

### Building All Three Variants Sequentially

Create a shell script to build all variants automatically:

**build-all-variants.sh** (Unix/Linux/macOS):
```bash
#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Building All PetClinic Variants"
echo "=========================================="

# Clean previous builds
echo "[1/6] Cleaning previous builds..."
./mvnw clean

# Build Variant 1: Java 17 Baseline
echo "[2/6] Building Java 17 Baseline..."
export JAVA_HOME=$JAVA_17_HOME
java -version
./mvnw package -DskipTests -q
mv target/spring-petclinic-4.0.0-SNAPSHOT.jar \
   target/spring-petclinic-java17-baseline.jar
echo "✓ Java 17 baseline built: target/spring-petclinic-java17-baseline.jar"

# Build Variant 2: Java 21 Traditional
echo "[3/6] Building Java 21 Variant A (Traditional)..."
./mvnw clean -q
export JAVA_HOME=$JAVA_21_HOME
java -version
./mvnw package -DskipTests -q
mv target/spring-petclinic-4.0.0-SNAPSHOT.jar \
   target/spring-petclinic-java21-traditional.jar
echo "✓ Java 21 traditional built: target/spring-petclinic-java21-traditional.jar"

# Build Variant 3: Java 21 Virtual Threads
echo "[4/6] Building Java 21 Variant B (Virtual Threads)..."
# Virtual threads version is the same JAR as Java 21 traditional,
# differentiated by runtime profile
cp target/spring-petclinic-java21-traditional.jar \
   target/spring-petclinic-java21-vthreads.jar
echo "✓ Java 21 virtual threads JAR prepared: target/spring-petclinic-java21-vthreads.jar"

# Verify all artifacts exist
echo "[5/6] Verifying artifacts..."
for jar in java17-baseline java21-traditional java21-vthreads; do
  if [ -f "target/spring-petclinic-${jar}.jar" ]; then
    size=$(ls -lh target/spring-petclinic-${jar}.jar | awk '{print $5}')
    echo "✓ spring-petclinic-${jar}.jar ($size)"
  else
    echo "✗ spring-petclinic-${jar}.jar NOT FOUND"
    exit 1
  fi
done

echo "[6/6] Build Summary"
echo "=========================================="
echo "Build completed successfully!"
echo "=========================================="
echo ""
echo "Artifacts ready in target/ directory:"
ls -1 target/spring-petclinic-*.jar | xargs -n1 basename | sed 's/^/  - /'
echo ""
echo "Run with:"
echo "  java -Xmx2g -jar target/spring-petclinic-java17-baseline.jar"
echo "  java -Xmx2g -jar target/spring-petclinic-java21-traditional.jar"
echo "  java -Xmx2g -jar target/spring-petclinic-java21-vthreads.jar \\"
echo "    --spring.profiles.active=vthreads"
```

**build-all-variants.bat** (Windows):
```batch
@echo off
setlocal enabledelayedexpansion

echo ==========================================
echo Building All PetClinic Variants
echo ==========================================

REM Clean previous builds
echo [1/6] Cleaning previous builds...
call mvnw clean

REM Build Variant 1: Java 17 Baseline
echo [2/6] Building Java 17 Baseline...
set JAVA_HOME=%JAVA_17_HOME%
java -version
call mvnw package -DskipTests -q
move target\spring-petclinic-4.0.0-SNAPSHOT.jar ^
     target\spring-petclinic-java17-baseline.jar
echo ✓ Java 17 baseline built

REM Build Variant 2: Java 21 Traditional
echo [3/6] Building Java 21 Variant A (Traditional)...
call mvnw clean -q
set JAVA_HOME=%JAVA_21_HOME%
java -version
call mvnw package -DskipTests -q
move target\spring-petclinic-4.0.0-SNAPSHOT.jar ^
     target\spring-petclinic-java21-traditional.jar
echo ✓ Java 21 traditional built

REM Build Variant 3: Java 21 Virtual Threads
echo [4/6] Building Java 21 Variant B (Virtual Threads)...
copy target\spring-petclinic-java21-traditional.jar ^
     target\spring-petclinic-java21-vthreads.jar
echo ✓ Java 21 virtual threads JAR prepared

echo [5/6] Verifying artifacts...
for %%F in (target\spring-petclinic-*.jar) do (
  echo ✓ %%~nxF
)

echo ==========================================
echo Build completed successfully!
echo ==========================================
```

### Using Gradle for Multi-Variant Builds

Create a Gradle build script for all variants:

**gradle-all-variants.sh**:
```bash
#!/bin/bash

set -e

echo "Building all variants with Gradle..."

# Java 17 Baseline
export JAVA_HOME=$JAVA_17_HOME
./gradlew clean build -x test
mv build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar \
   build/libs/spring-petclinic-java17-baseline.jar

# Java 21 Traditional
export JAVA_HOME=$JAVA_21_HOME
./gradlew clean build -x test
mv build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar \
   build/libs/spring-petclinic-java21-traditional.jar

# Java 21 Virtual Threads
cp build/libs/spring-petclinic-java21-traditional.jar \
   build/libs/spring-petclinic-java21-vthreads.jar

echo "All variants built successfully!"
ls -lh build/libs/spring-petclinic-*.jar
```

---

## Automated Testing Setup

### Running Integration Tests

**With H2 (In-Memory):**
```bash
./mvnw verify -DskipITs=false
```

**With MySQL (Testcontainers):**
```bash
# Requires Docker
./mvnw verify -DskipITs=false -Dspring.profiles.active=mysql
```

**With PostgreSQL (Docker Compose):**
```bash
# Requires Docker
docker compose up postgres &
./mvnw verify -DskipITs=false -Dspring.profiles.active=postgres
docker compose down postgres
```

### Creating Custom Integration Tests

Create `src/test/java/org/springframework/samples/petclinic/BenchmarkTest.java`:

```java
package org.springframework.samples.petclinic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BenchmarkTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testApplicationLoads() {
        // Verify application starts successfully
        ResponseEntity<String> response = 
            restTemplate.getForEntity("/", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    public void testApplicationHealth() {
        ResponseEntity<String> response = 
            restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    public void testActuatorMetrics() {
        ResponseEntity<String> response = 
            restTemplate.getForEntity("/actuator/metrics", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
```

---

## Continuous Integration

### GitHub Actions Workflow

Create `.github/workflows/benchmark-build.yml`:

```yaml
name: Benchmark Build Matrix

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-java17:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      
      - name: Build with Maven
        run: |
          ./mvnw clean package -DskipTests
          mv target/spring-petclinic-4.0.0-SNAPSHOT.jar \
             target/spring-petclinic-java17-baseline.jar
      
      - name: Run Tests
        run: ./mvnw test
      
      - name: Upload artifact
        uses: actions/upload-artifact@v3
        with:
          name: spring-petclinic-java17
          path: target/spring-petclinic-java17-baseline.jar

  build-java21:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Build with Maven
        run: |
          ./mvnw clean package -DskipTests
          mv target/spring-petclinic-4.0.0-SNAPSHOT.jar \
             target/spring-petclinic-java21-traditional.jar
      
      - name: Run Tests
        run: ./mvnw test
      
      - name: Upload artifact
        uses: actions/upload-artifact@v3
        with:
          name: spring-petclinic-java21
          path: target/spring-petclinic-java21-traditional.jar

  integration-tests:
    needs: [build-java17, build-java21]
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:9.5
        env:
          MYSQL_ROOT_PASSWORD: root
          MYSQL_DATABASE: petclinic
          MYSQL_USER: petclinic
          MYSQL_PASSWORD: petclinic
        options: >-
          --health-cmd="mysqladmin ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=3
      
      postgres:
        image: postgres:18.1
        env:
          POSTGRES_PASSWORD: petclinic
          POSTGRES_USER: petclinic
          POSTGRES_DB: petclinic
        options: >-
          --health-cmd pg_isready
          --health-interval=10s
          --health-timeout=5s
          --health-retries=3
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Run Integration Tests
        run: |
          ./mvnw verify -DskipITs=false -Dspring.profiles.active=mysql,postgres
```

---

## Advanced JVM Tuning

### Garbage Collection Tuning

#### G1GC Aggressive Tuning (Maximum Throughput)

```bash
java -Xms4g -Xmx8g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=75 \
  -XX:InitiatingHeapOccupancyPercent=20 \
  -XX:G1NewSizePercent=20 \
  -XX:G1MaxNewSizePercent=30 \
  -XX:G1PretenureSizeThreshold=256m \
  -XX:+ParallelRefProcEnabled \
  -XX:+DisableExplicitGC \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:gc-aggressive.log \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

#### G1GC Low-Latency Tuning

```bash
java -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=50 \
  -XX:InitiatingHeapOccupancyPercent=25 \
  -XX:G1NewSizePercent=25 \
  -XX:G1MaxNewSizePercent=35 \
  -XX:G1HeapRegionSize=8m \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:G1SummarizeRSetStatsPeriod=86400 \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

### Tiered Compilation

```bash
# Tiered compilation (default in modern Java, but can be fine-tuned)
java -Xms2g -Xmx4g \
  -XX:+TieredCompilation \
  -XX:TieredStopAtLevel=4 \
  -XX:CompileThreshold=10000 \
  -XX:+PrintCompilation \
  -XX:+PrintInlining \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

### NUMA (Non-Uniform Memory Access) Tuning

For systems with multiple NUMA nodes:

```bash
java -Xms4g -Xmx8g \
  -XX:+UseNUMA \
  -XX:+UseNUMAInterleaving \
  -XX:+UseG1GC \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

---

## Performance Monitoring

### JVM Metrics via Actuator

Create a monitoring script:

**monitor-benchmark.sh**:
```bash
#!/bin/bash

# Monitor running benchmark instance

HOST=${1:-localhost}
PORT=${2:-8080}
INTERVAL=${3:-5}

echo "Monitoring PetClinic at $HOST:$PORT (refreshing every ${INTERVAL}s)"
echo "Press Ctrl+C to stop"
echo ""

while true; do
  clear
  echo "=== PetClinic Benchmark Metrics ==="
  echo "Timestamp: $(date)"
  echo ""
  
  # JVM Memory
  echo "--- JVM Memory ---"
  curl -s http://$HOST:$PORT/actuator/metrics/jvm.memory.used | \
    jq '.measurements[0].value | . / 1048576 | "Heap: \(.|floor)MB"'
  
  # Active Threads
  echo "--- Thread Info ---"
  curl -s http://$HOST:$PORT/actuator/metrics/jvm.threads.live | \
    jq '.measurements[0].value | "Live threads: \(.)"'
  
  # HTTP Requests
  echo "--- HTTP Requests ---"
  curl -s http://$HOST:$PORT/actuator/metrics/http.server.requests.count | \
    jq '.measurements[0].value | "Total requests: \(.|floor)"'
  
  # GC Activity
  echo "--- Garbage Collection ---"
  curl -s http://$HOST:$PORT/actuator/metrics/jvm.gc.pause | \
    jq '.measurements | length | "GC collections: \(.)"'
  
  sleep $INTERVAL
done
```

### Custom JFR Events

Create application startup script with JFR:

**run-with-jfr.sh**:
```bash
#!/bin/bash

VARIANT=${1:-java17-baseline}
JAR_FILE="target/spring-petclinic-${VARIANT}.jar"

# Create JFR directory if needed
mkdir -p jfr

# Run with JFR profiling
java -Xms2g -Xmx4g \
  -XX:+UnlockCommercialFeatures \
  -XX:+FlightRecorder \
  -XX:FlightRecorderOptions=stackdepth=1024,samplingrate=97 \
  -XX:StartFlightRecording=\
name="benchmark-${VARIANT}",\
filename="jfr/recording-${VARIANT}.jfr",\
maxsize=2g,\
maxage=24h,\
dumponexit=true,\
disk=true \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -jar "$JAR_FILE" "$@"

echo "JFR recording saved to: jfr/recording-${VARIANT}.jfr"
echo "Analyze with: jdk.jcmd <PID> JFR.dump filename=output.jfr"
```

---

## Docker Optimization

### Optimized Docker Compose for Benchmarking

```yaml
version: '3.9'

services:
  app-java17:
    build:
      context: .
      dockerfile: Dockerfile.benchmark
      args:
        JAVA_VERSION: 17
    environment:
      SPRING_PROFILES_ACTIVE: h2
      JAVA_OPTS: >
        -Xms1g -Xmx2g
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=100
    ports:
      - "8080:8080"
    networks:
      - benchmark

  app-java21:
    build:
      context: .
      dockerfile: Dockerfile.benchmark
      args:
        JAVA_VERSION: 21
    environment:
      SPRING_PROFILES_ACTIVE: mysql
      JAVA_OPTS: >
        -Xms1g -Xmx2g
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=100
    ports:
      - "8081:8080"
    depends_on:
      - mysql
    networks:
      - benchmark

  app-java21-vthreads:
    build:
      context: .
      dockerfile: Dockerfile.benchmark
      args:
        JAVA_VERSION: 21
    environment:
      SPRING_PROFILES_ACTIVE: vthreads,postgres
      JAVA_OPTS: >
        -Xms512m -Xmx2g
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=150
    ports:
      - "8082:8080"
    depends_on:
      - postgres
    networks:
      - benchmark

  mysql:
    image: mysql:9.5
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_USER: petclinic
      MYSQL_PASSWORD: petclinic
      MYSQL_DATABASE: petclinic
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - benchmark

  postgres:
    image: postgres:18.1
    environment:
      POSTGRES_PASSWORD: petclinic
      POSTGRES_USER: petclinic
      POSTGRES_DB: petclinic
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - benchmark

networks:
  benchmark:
    driver: bridge

volumes:
  mysql-data:
  postgres-data:
```

**Dockerfile.benchmark**:
```dockerfile
ARG JAVA_VERSION=17

FROM eclipse-temurin:${JAVA_VERSION}-jdk as builder
WORKDIR /build
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:${JAVA_VERSION}-jdk
COPY --from=builder /build/target/spring-petclinic-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Run all variants with Docker Compose:**
```bash
docker compose up -d
# Access:
#   Java 17:        http://localhost:8080
#   Java 21:        http://localhost:8081
#   Java 21 vthreads: http://localhost:8082
```

---

## Benchmarking Best Practices

### Pre-Benchmark Warmup

```bash
# Warm up JIT compiler before benchmark
echo "Warming up application..."
for i in {1..100}; do
  curl -s http://localhost:8080/ > /dev/null
  curl -s http://localhost:8080/actuator/health > /dev/null
done
echo "Warmup complete"
```

### Consistent Benchmark Conditions

**benchmark-setup.sh**:
```bash
#!/bin/bash

echo "Setting up consistent benchmark environment..."

# Stop all Java processes (be careful!)
# pkill -f "java.*spring-petclinic"

# Clear caches
sync && echo 3 > /proc/sys/vm/drop_caches

# Disable power management
sudo cpupower frequency-set -g performance

# Monitor CPU/Memory
echo "Monitoring system resources..."
iostat -xz 1 > system-stats.log &
vmstat 1 > vmstat.log &

echo "Benchmark environment ready"
echo "System stats will be recorded to system-stats.log and vmstat.log"
```

### Comparison Framework

**compare-variants.sh**:
```bash
#!/bin/bash

VARIANTS=("java17-baseline" "java21-traditional" "java21-vthreads")
DURATION=60
CONCURRENCY=100

echo "Starting comparative benchmark of all variants"
echo "Duration: ${DURATION}s, Concurrency: ${CONCURRENCY}"
echo ""

for variant in "${VARIANTS[@]}"; do
  echo "=== Benchmarking: $variant ==="
  
  # Start variant
  java -Xms2g -Xmx4g -XX:+UseG1GC \
    -jar target/spring-petclinic-${variant}.jar &
  APP_PID=$!
  
  sleep 10  # Warmup
  
  # Run load test
  apache2-bench -t $DURATION -c $CONCURRENCY http://localhost:8080/ \
    > results-${variant}.txt 2>&1
  
  # Extract results
  echo "Results for $variant:"
  grep -E "Requests per second|Time per request|Failed requests" \
    results-${variant}.txt
  echo ""
  
  # Stop variant
  kill $APP_PID 2>/dev/null || true
  sleep 5
done

echo "Benchmark complete. Results saved to results-*.txt"
```

---

**Document Version:** 1.0 (Advanced Supplement)
**Pairs with:** SETUP.md
**For:** Spring PetClinic Benchmark Suite
