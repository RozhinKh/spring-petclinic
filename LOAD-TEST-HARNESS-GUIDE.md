# Load Test Execution Harness Guide

## Overview

The Load Test Execution Harness is a comprehensive Java-based orchestration system that manages the complete lifecycle of multi-variant load testing across three code variants (Java 17 baseline, Java 21 traditional, Java 21 virtual threads). It automates the entire process: building variants, starting applications, executing JMeter tests, collecting results, and generating normalized JSON outputs.

## Architecture

The harness consists of five main components working together:

```
┌─────────────────────────────────────────────────────────────┐
│         LoadTestExecutionHarness (Main Orchestrator)       │
└─────────────────────────────────────────────────────────────┘
         │           │              │            │
         ▼           ▼              ▼            ▼
    ApplicationStar  DatabaseSetupM  JMeterTest  LoadTestResults
    ter (Lifecycle)  anager (Setup)  Runner      Processor (Export)
         │           │              │            │
    ┌────┴───────────┴──────────────┴────────────┴────┐
    │                                                 │
    │  Maven Profiles  Database Reset Scripts         │
    │  (Build variants) (H2/MySQL/PostgreSQL)         │
    │                                                 │
    └─────────────────────────────────────────────────┘
```

## Components

### 1. LoadTestExecutionHarness

**File**: `src/main/java/org/springframework/samples/petclinic/benchmark/LoadTestExecutionHarness.java`

Main orchestrator that coordinates the complete load test workflow.

**Key Features**:
- Reads configuration from properties file
- Iterates through all configured variants
- Manages complete lifecycle for each variant
- Generates master report combining all results
- Handles graceful shutdown and cleanup

**Entry Point**:
```bash
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.LoadTestExecutionHarness \
  src/main/resources/load-test-harness-config.properties
```

### 2. ApplicationStarter

**File**: `src/main/java/org/springframework/samples/petclinic/benchmark/ApplicationStarter.java`

Manages application lifecycle: build, startup, health checks, warm-up, shutdown.

**Key Responsibilities**:
- Builds variant with Maven (`mvn clean package`)
- Starts JAR with variant-specific JVM options and Spring profiles
- Waits for application health check (`/actuator/health`)
- Runs warm-up requests to trigger JIT compilation
- Gracefully shuts down application

**Health Check Flow**:
```
Start Application
    ↓
Poll /actuator/health every 500ms
    ↓
Response == 200 (UP)
    ↓
✓ Application Ready
```

### 3. DatabaseSetupManager

**File**: `src/main/java/org/springframework/samples/petclinic/benchmark/DatabaseSetupManager.java`

Handles database lifecycle: connectivity verification, initialization, reset.

**Key Responsibilities**:
- Verify database driver is installed
- Verify database connectivity
- Execute schema initialization SQL
- Execute reset SQL to clear tables
- Validate test data consistency

**Supported Databases**:
- **H2**: In-memory (auto-initialized)
- **MySQL**: `jdbc:mysql://localhost:3306/petclinic`
- **PostgreSQL**: `jdbc:postgresql://localhost:5432/petclinic`

**Reset Strategy**: Drops all tables and re-creates from reset SQL scripts

### 4. JMeterTestRunner

**File**: `src/main/java/org/springframework/samples/petclinic/benchmark/JMeterTestRunner.java`

Executes JMeter test plan against running application.

**Key Responsibilities**:
- Verify JMeter installation
- Execute JMeter in non-GUI mode
- Pass variant-specific parameters (threads, ramp-up, duration)
- Collect results to CSV file
- Handle timeouts and failures

**Parameters Passed**:
```
-Jpetclinic.host=localhost
-Jpetclinic.port=8080
-Jpetclinic.threads=100
-Jpetclinic.rampup=300
-Jpetclinic.duration=600
```

### 5. LoadTestResultsProcessor

**File**: `src/main/java/org/springframework/samples/petclinic/benchmark/LoadTestResultsProcessor.java`

Parses JMeter CSV results and exports normalized JSON metrics.

**Key Responsibilities**:
- Parse JMeter CSV output format
- Calculate latency percentiles (P50, P75, P90, P95, P99, P99.9)
- Calculate throughput (requests/second)
- Calculate error rates and response distribution
- Group metrics by endpoint
- Export to timestamped JSON file

**Output Schema**:
```json
{
  "timestamp": "2024-12-15T10:30:00Z",
  "variant": "java17-baseline",
  "profile": "light",
  "metrics": {
    "totalRequests": 15000,
    "successfulRequests": 14850,
    "failedRequests": 150,
    "errorRate": 1.0,
    "minLatency": 10,
    "maxLatency": 5000,
    "meanLatency": 125,
    "p50": 85,
    "p75": 150,
    "p90": 300,
    "p95": 450,
    "p99": 2000,
    "p99_9": 4500,
    "throughput": 250.5,
    "distribution": {
      "0-100ms": 7000,
      "100-250ms": 4500,
      "250-500ms": 2000,
      "500-1000ms": 800,
      ">1000ms": 550
    }
  },
  "endpointMetrics": {
    "GET /owners": {
      "requests": 2500,
      "minLatency": 20,
      "maxLatency": 1000,
      "meanLatency": 100,
      "p95Latency": 250,
      "p99Latency": 800
    }
  }
}
```

## Configuration

### Configuration File

**File**: `src/main/resources/load-test-harness-config.properties`

Central configuration for all harness aspects.

#### Variant Definition

```properties
# Each variant has:
# - name: identifier
# - port: unique port for application instance
# - profile: Spring profile (default or vthreads)
# - jvm.options: JVM arguments

variant.java17.name=java17-baseline
variant.java17.port=8080
variant.java17.profile=default
variant.java17.jvm.options=-Xms512m -Xmx2g

variant.java21-traditional.name=java21-traditional
variant.java21-traditional.port=8081
variant.java21-traditional.profile=default
variant.java21-traditional.jvm.options=-Xms512m -Xmx2g

variant.java21-virtual.name=java21-virtual
variant.java21-virtual.port=8082
variant.java21-virtual.profile=vthreads
variant.java21-virtual.jvm.options=-Xms512m -Xmx2g
```

#### Database Configuration

```properties
# Database type selection
database.type=h2  # or mysql, postgres

# Per-database settings
database.h2.driver=org.h2.Driver
database.h2.url=jdbc:h2:mem:petclinic;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE

database.mysql.driver=com.mysql.cj.jdbc.Driver
database.mysql.url=jdbc:mysql://localhost:3306/petclinic
database.mysql.username=root
database.mysql.password=root

database.postgres.driver=org.postgresql.Driver
database.postgres.url=jdbc:postgresql://localhost:5432/petclinic
database.postgres.username=postgres
database.postgres.password=postgres
```

#### Test Profiles

```properties
# Define concurrency levels
test.profile.light.threads=100
test.profile.light.rampup=300        # seconds
test.profile.light.duration=600      # steady-state seconds

test.profile.medium.threads=250
test.profile.medium.rampup=300
test.profile.medium.duration=600

test.profile.peak.threads=500
test.profile.peak.rampup=300
test.profile.peak.duration=600
```

#### Application Configuration

```properties
application.health_url=/actuator/health
application.health_timeout_seconds=30
application.startup_timeout_seconds=60
application.warmup_seconds=30
application.warmup_requests=50

jmeter.test_plan=src/test/jmeter/petclinic_test_plan.jmx
```

#### Data Validation

```properties
data.min_owners=100
data.min_pets=200
```

## Usage

### Quick Start

```bash
# Run all variants with default configuration
bash scripts/load-test-runner.sh
```

### Standalone Java Execution

```bash
# Build the project
mvn clean package

# Run harness
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.LoadTestExecutionHarness

# Or with custom configuration
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.LoadTestExecutionHarness \
  src/main/resources/load-test-harness-config.properties
```

### Via Maven Exec Plugin

```bash
mvn test-compile exec:java \
  -Dexec.mainClass="org.springframework.samples.petclinic.benchmark.LoadTestExecutionHarness" \
  -Dexec.args="src/main/resources/load-test-harness-config.properties"
```

## Execution Workflow

For each variant, the harness follows this sequence:

### Phase 1: Preparation

```
Step 1: Database Setup
  ├─ Verify database connectivity
  ├─ Drop existing tables
  ├─ Re-create schema
  ├─ Validate test data (100+ owners, 200+ pets)
  └─ ✓ Ready for testing

Step 2: Build Variant
  ├─ Run: mvn clean package -DskipTests
  ├─ Generate: target/spring-petclinic-4.0.0-SNAPSHOT.jar
  └─ ✓ Build complete
```

### Phase 2: Application Startup

```
Step 3: Start Application
  ├─ Java process with JVM options
  ├─ Spring profile activation
  ├─ Port binding
  ├─ Wait for /actuator/health → 200
  └─ ✓ Application ready

Step 4: Warm-up Period
  ├─ Duration: 30 seconds
  ├─ Requests: 50 concurrent
  ├─ Exercises: /owners, /vets, /owners/find, /owners/1
  ├─ Allows JIT compilation and cache population
  └─ ✓ Warm-up complete
```

### Phase 3: Load Testing

```
Step 5: Load Testing (3 profiles)
  ├─ Light: 100 threads, 5min ramp, 10min steady
  ├─ Medium: 250 threads, 5min ramp, 10min steady
  ├─ Peak: 500 threads, 5min ramp, 10min steady
  └─ ✓ Tests complete
```

### Phase 4: Results Processing

```
Step 6: Results Export
  ├─ Parse JMeter CSV output
  ├─ Calculate latency percentiles
  ├─ Calculate throughput
  ├─ Calculate error rates
  ├─ Export JSON results
  └─ ✓ Results saved

Step 7: Shutdown
  ├─ Graceful application shutdown
  ├─ Process cleanup
  └─ ✓ Ready for next variant
```

## Results Structure

Results are organized hierarchically:

```
target/load-test-results/
├── run_20241215_103000/           # Master results directory (timestamp)
│   ├── master-report.json          # Aggregated report of all variants
│   ├── harness.log                # Complete execution log
│   │
│   ├── java17-baseline/           # Variant 1 results
│   │   ├── jmeter-results-java17-baseline-light-100.csv
│   │   ├── load-test-results-java17-baseline-light-...json
│   │   ├── jmeter-results-java17-baseline-medium-250.csv
│   │   ├── load-test-results-java17-baseline-medium-...json
│   │   ├── jmeter-results-java17-baseline-peak-500.csv
│   │   └── load-test-results-java17-baseline-peak-...json
│   │
│   ├── java21-traditional/        # Variant 2 results
│   │   └── ... (same structure as variant 1)
│   │
│   └── java21-virtual/            # Variant 3 results
│       └── ... (same structure as variant 1)
```

## Master Report

The `master-report.json` provides a high-level summary:

```json
{
  "timestamp": "2024-12-15T10:30:00Z",
  "masterResultsDirectory": "target/load-test-results/run_20241215_103000",
  "totalVariants": 3,
  "variants": [
    {
      "name": "java17-baseline",
      "success": true,
      "startupTimeMs": 5234,
      "startupTimestamp": 1702646400,
      "testProfiles": {
        "light": "run_20241215_103000/java17-baseline/load-test-results-java17-baseline-light-....json",
        "medium": "run_20241215_103000/java17-baseline/load-test-results-java17-baseline-medium-....json",
        "peak": "run_20241215_103000/java17-baseline/load-test-results-java17-baseline-peak-....json"
      }
    },
    ...
  ]
}
```

## Database Reset Scripts

### H2 (src/main/resources/db/h2/reset.sql)

- Drops all tables with CASCADE behavior
- Re-creates schema with auto-increment IDs
- Inserts default data (5 vets, 3 specialties, 6 pet types)

### MySQL (src/main/resources/db/mysql/reset.sql)

- Drops tables with proper foreign key handling
- Creates tables with UTF-8MB4 charset
- Sets SQL strict mode
- Inserts default data

### PostgreSQL (src/main/resources/db/postgres/reset.sql)

- Drops tables with CASCADE
- Creates sequences for ID generation
- Resets sequence counters
- Inserts default data

## Maven Profiles

Three profiles enable variant-specific builds:

```bash
# Java 17 Baseline
mvn clean package -Pjava17-baseline

# Java 21 Traditional
mvn clean package -Pjava21-traditional

# Java 21 Virtual Threads
mvn clean package -Pjava21-virtual -Dvthreads=true
```

## Prerequisites

### Required Software

1. **Java 17+**: For baseline and Java 21 compilation
2. **Maven 3.9+**: For building variants
3. **JMeter 5.5+**: For running load tests
4. **Database**: H2 (included), MySQL, or PostgreSQL (external)

### Installation

```bash
# Install JMeter (macOS with Homebrew)
brew install jmeter

# Or download from: https://jmeter.apache.org/download_jmeter.cgi
# Add JMETER_HOME/bin to PATH

# Verify installation
jmeter --version
```

### Database Setup (External Databases)

**MySQL**:
```bash
# Create database
mysql -u root -p -e "CREATE DATABASE petclinic;"

# Verify connectivity
mysql -u root -p petclinic -e "SELECT 1;"
```

**PostgreSQL**:
```bash
# Create database
createdb -U postgres petclinic

# Verify connectivity
psql -U postgres -d petclinic -c "SELECT 1;"
```

## Troubleshooting

### Common Issues

**1. "JMeter not found in PATH"**
- Solution: Install JMeter and add to PATH, or modify script to specify full path

**2. "Application failed to start within 30 seconds"**
- Solution: Increase `application.startup_timeout_seconds` in config
- Check disk space and available memory

**3. "Database connection refused"**
- Solution: Verify database is running, check connection parameters in config
- For MySQL: `mysql -u root -p petclinic -e "SELECT 1;"`
- For PostgreSQL: `psql -U postgres -d petclinic -c "SELECT 1;"`

**4. "Insufficient test data: owners count 0 < required 100"**
- Solution: Verify database reset script executed, check database connectivity
- Manually execute reset script: `mysql -u root -p petclinic < src/main/resources/db/mysql/reset.sql`

**5. "Build timeout"**
- Solution: Increase Maven build timeout or check for network issues
- Run build manually: `mvn clean package -DskipTests`

### Debug Mode

Enable debug logging in load-test-harness-config.properties:

```properties
logging.level=DEBUG
```

Check logs:
- `target/load-test-harness.log` - Main harness log
- `target/load-test-results/run_*/harness.log` - Execution log per run

## Performance Expectations

### Light Profile (100 users)

- **Duration**: 15 minutes (5min ramp + 10min steady)
- **Expected Throughput**: 20-30 req/sec
- **Expected P99 Latency**: < 500ms
- **Error Rate**: < 1%

### Medium Profile (250 users)

- **Duration**: 15 minutes
- **Expected Throughput**: 50-75 req/sec
- **Expected P99 Latency**: 500-1000ms
- **Error Rate**: < 1%

### Peak Profile (500 users)

- **Duration**: 15 minutes
- **Expected Throughput**: 100-150 req/sec
- **Expected P99 Latency**: 1000-3000ms
- **Error Rate**: < 2%

### Variant Comparison

Expected performance characteristics:

**Java 17 Baseline**
- Startup: ~5-6 seconds
- Warm-up: 30 seconds
- Stable throughput

**Java 21 Traditional**
- Startup: ~5-6 seconds (similar to Java 17)
- Warm-up: 30 seconds
- Similar throughput to Java 17

**Java 21 Virtual Threads**
- Startup: ~5-6 seconds
- Warm-up: 30 seconds
- 10-20% higher throughput (expected)
- Lower P99 latency under peak load

## Integration with Task 5

The Load Test Harness is designed to work with the Metrics Collection framework:

- Startup times recorded for correlation with metrics
- Timestamps enable matching with Actuator metrics snapshots
- Test profiles correspond to load profiles in metrics collection
- Results JSON format compatible with aggregation pipeline

## Files Summary

### Java Implementation (5 files)

| File | Purpose |
|------|---------|
| LoadTestExecutionHarness.java | Main orchestrator |
| ApplicationStarter.java | Application lifecycle |
| DatabaseSetupManager.java | Database setup/reset |
| JMeterTestRunner.java | JMeter execution |
| LoadTestResultsProcessor.java | Results parsing & export |

### Configuration & Scripts (3 files)

| File | Purpose |
|------|---------|
| load-test-harness-config.properties | Configuration |
| load-test-runner.sh | Shell orchestrator |
| LoadTestHarnessTests.java | Unit tests |

### Database Scripts (3 files)

| File | Purpose |
|------|---------|
| src/main/resources/db/h2/reset.sql | H2 reset |
| src/main/resources/db/mysql/reset.sql | MySQL reset |
| src/main/resources/db/postgres/reset.sql | PostgreSQL reset |

### Maven Profiles (Updated pom.xml)

- java17-baseline
- java21-traditional
- java21-virtual

## Success Criteria Met

✅ **Build Integration**: Maven profiles for each variant with clean rebuild
✅ **Application Lifecycle**: Start, health check, warm-up, shutdown
✅ **Database Setup**: Init/reset for H2, MySQL, PostgreSQL
✅ **Load Test Execution**: JMeter integration with variant parameters
✅ **Result Collection**: JSON output with all required metrics
✅ **Error Handling**: Graceful shutdown, cleanup, timeout handling
✅ **Multi-database Support**: H2, MySQL, PostgreSQL
✅ **Process Cleanup**: No orphaned processes
✅ **Data Consistency**: Validation between variants

---

**Last Updated**: December 2024
**Version**: 1.0
**Status**: Production Ready
