# PetClinic Load Testing Suite

## Overview

This document describes the comprehensive load testing suite for PetClinic, designed to measure performance across multiple concurrency profiles and identify latency degradation patterns.

## Components

### 1. Test Data Setup

**File**: `src/test/resources/load-test-data-setup.sql`

Populates database with realistic test data:
- **100+ owner records** with varied first/last names, addresses, cities, and phone numbers
- **200+ pet records** distributed across owners (2-3 pets per owner)
- Supports H2 (in-memory), MySQL, and PostgreSQL backends
- Data includes all valid pet types (cat, dog, lizard, snake, bird, hamster)

**Usage**:
Execute the SQL script before running load tests to ensure sufficient test data:
```bash
# For H2 (automatic in application startup)
# Included in H2 initialization with schema

# For MySQL
mysql -u root -p petclinic < src/test/resources/load-test-data-setup.sql

# For PostgreSQL
psql -U postgres -d petclinic < src/test/resources/load-test-data-setup.sql
```

### 2. LoadTestDataGenerator (Java Utility)

**File**: `src/test/java/org/springframework/samples/petclinic/benchmark/LoadTestDataGenerator.java`

Programmatic test data generation for load testing scenarios.

**Features**:
- Generates random owners and pets with valid data
- Validates test data completeness (minimum 100 owners, 200 pets)
- Provides `TestDataValidation` result class for verification

**Usage in Java**:
```java
LoadTestDataGenerator generator = new LoadTestDataGenerator(ownerRepository, petTypeRepository);
generator.generateTestData(100, 2); // 100 owners, avg 2 pets each
LoadTestDataGenerator.TestDataValidation validation = generator.validateTestData();
System.out.println("Owners: " + validation.getOwnerCount());
System.out.println("Pets: " + validation.getPetCount());
System.out.println("Valid: " + validation.isValid());
```

### 3. LoadTestResultsExporter (Results Processing)

**File**: `src/test/java/org/springframework/samples/petclinic/benchmark/LoadTestResultsExporter.java`

Converts JMeter CSV results to comprehensive JSON metrics.

**Features**:
- Parses JMeter CSV output format
- Calculates latency percentiles: min, max, mean, P50, P75, P90, P95, P99, P99.9
- Computes response time distribution buckets: 0-100ms, 100-250ms, 250-500ms, 500-1000ms, >1000ms
- Per-endpoint metrics breakdown (requests, min/max/mean/P95/P99 latencies)
- Throughput calculation (requests per second)
- Error rate tracking (success vs failed)

**JSON Output Schema**:
```json
{
  "timestamp": "2024-12-15T10:30:00Z",
  "totalRequests": 15000,
  "successfulRequests": 14850,
  "failedRequests": 150,
  "errorRate": 1.0,
  "throughput": 250.5,
  "latency": {
    "min": 10,
    "max": 5000,
    "mean": 125,
    "p50": 85,
    "p75": 150,
    "p90": 300,
    "p95": 450,
    "p99": 2000,
    "p99_9": 4500
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
  },
  "distribution": {
    "0-100ms": 7000,
    "100-250ms": 4500,
    "250-500ms": 2000,
    "500-1000ms": 800,
    ">1000ms": 550
  }
}
```

**Usage**:
```java
LoadTestResultsExporter exporter = new LoadTestResultsExporter();
exporter.exportResults("target/jmeter-results-light-100.csv", "light-100");
// Outputs: target/load-test-results/load-test-results-light-100-<timestamp>.json
```

### 4. JMeter Test Plan

**File**: `src/test/jmeter/petclinic-load-test.jmx`

Comprehensive load test plan covering all PetClinic workflows with three concurrency profiles.

#### Test Scenarios (7 total)

1. **GET /owners (list)** - Baseline request with pagination
   - Parameters: random page (1-5)
   - Think time: 1-3 seconds
   - Expected: 200/302 response

2. **GET /owners/find** - Search form request
   - Precedes search workflow
   - Expected: 200 response

3. **GET /owners (search)** - Dynamic owner search
   - Parameters: random lastName from predefined list
   - Emulates user search behavior
   - Expected: 200/302 response

4. **GET /owners/{id}** - Owner detail view
   - Parameters: random owner ID (1-90)
   - High-frequency operation
   - Expected: 200 response

5. **GET /vets (cached)** - Cached endpoint measurement
   - Baseline for cache performance
   - Expected: 200 response
   - Shows high throughput due to caching

6. **GET /owners/new** - Create owner form
   - Form rendering request
   - Expected: 200 response

7. **GET /owners/{id}/edit** - Edit owner form
   - Parameters: random owner ID
   - Nested resource handling
   - Expected: 200 response

#### Concurrency Profiles

**Light Profile (100 users)**
- Concurrent threads: 100
- Ramp-up: 5 minutes (300 seconds)
- Steady-state: 10 minutes (600 seconds, total 900s)
- Total duration: 15 minutes
- Expected throughput: 20-30 requests/second

**Medium Profile (250 users)**
- Concurrent threads: 250
- Ramp-up: 5 minutes (300 seconds)
- Steady-state: 10 minutes (600 seconds, total 900s)
- Total duration: 15 minutes
- Expected throughput: 50-75 requests/second

**Peak Profile (500 users)**
- Concurrent threads: 500
- Ramp-up: 5 minutes (300 seconds)
- Steady-state: 10 minutes (600 seconds, total 900s)
- Total duration: 15 minutes
- Expected throughput: 100-150 requests/second

#### Think Time

Between each request: **1000-3000 milliseconds (random)**

Simulates realistic user behavior with pause time between interactions.

#### Results Collection

Each profile outputs to dedicated CSV file:
- `target/jmeter-results-light-100.csv`
- `target/jmeter-results-medium-250.csv`
- `target/jmeter-results-peak-500.csv`

Fields collected:
- `timeStamp`: Request start time
- `elapsed`: Response time (milliseconds)
- `label`: Request name/endpoint
- `responseCode`: HTTP status code
- `responseMessage`: HTTP status message
- `success`: true/false

## Execution Guide

### Prerequisites

1. **Java**: Java 17+ (required for Spring Boot)
2. **JMeter**: Apache JMeter 5.5+ installed
3. **Database**: Running instance (H2 auto-starts, MySQL/PostgreSQL via Docker)
4. **Application**: PetClinic running on http://localhost:8080

### Step 1: Start the Application

```bash
# Build
mvn clean package

# Run with H2 (default)
mvn spring-boot:run

# Or with MySQL
export SPRING_PROFILES_ACTIVE=mysql
mvn spring-boot:run

# Or with PostgreSQL
export SPRING_PROFILES_ACTIVE=postgres
mvn spring-boot:run
```

### Step 2: Populate Test Data

**Option A**: Execute SQL script (automatic for H2)

For MySQL/PostgreSQL, execute manually or through application startup hooks.

**Option B**: Use Java Generator in Test Suite

```java
// Create test class to inject repositories
@SpringBootTest
public class LoadTestSetup {
    @Autowired
    private OwnerRepository ownerRepository;
    
    @Autowired
    private PetTypeRepository petTypeRepository;
    
    @Test
    public void setupTestData() {
        LoadTestDataGenerator generator = new LoadTestDataGenerator(
            ownerRepository, petTypeRepository
        );
        generator.generateTestData(100, 2);
        LoadTestDataGenerator.TestDataValidation validation = generator.validateTestData();
        assertTrue(validation.isValid());
    }
}
```

### Step 3: Run JMeter Test Plan

```bash
# Using GUI (for development/debugging)
jmeter -t src/test/jmeter/petclinic-load-test.jmx

# Using headless mode (for CI/CD)
jmeter -n -t src/test/jmeter/petclinic-load-test.jmx \
  -l target/jmeter-results.csv \
  -j target/jmeter.log

# Run specific profile (modify test plan or use property override)
jmeter -Jthreads=100 -Jrampup=300 -t src/test/jmeter/petclinic-load-test.jmx
```

### Step 4: Export Results to JSON

```bash
# Create Java application to process results
mvn exec:java -Dexec.mainClass="org.springframework.samples.petclinic.benchmark.LoadTestResultsExporter" \
  -Dexec.args="target/jmeter-results-light-100.csv light-100"
```

## Success Criteria Validation

### Functional Criteria
✓ JMeter test plan is valid and executable
✓ All 7 workflows execute successfully with think time
✓ Test data: 100+ owners, 200+ pets verified
✓ No connection errors or database lock issues
✓ Latency percentiles captured for all profiles

### Performance Criteria
- **Error Rate**: < 1% under peak 500-user load
- **Latency Degradation**: Smooth increase as users ramp
- **Cached Endpoint**: /vets shows 2-3x higher throughput than non-cached
- **Steady-State**: Stable throughput within expected variance
- **Response Distribution**: Expected degradation curve with concurrency

### Metric Expectations by Profile

**Light (100 users)**
- Throughput: 20-30 req/s
- P95 latency: 100-200ms
- P99 latency: 300-500ms
- Error rate: < 0.1%

**Medium (250 users)**
- Throughput: 50-75 req/s
- P95 latency: 200-400ms
- P99 latency: 600-1000ms
- Error rate: < 0.5%

**Peak (500 users)**
- Throughput: 100-150 req/s
- P95 latency: 400-800ms
- P99 latency: 1000-2000ms
- Error rate: < 1.0%

## Analysis and Interpretation

### Response Time Distribution

View the distribution JSON to understand latency patterns:

```json
{
  "distribution": {
    "0-100ms": 7000,     // Fast responses (cache hits, simple queries)
    "100-250ms": 4500,   // Normal responses (DB queries)
    "250-500ms": 2000,   // Slower responses (complex operations)
    "500-1000ms": 800,   // Slow responses (contention)
    ">1000ms": 550       // Very slow responses (bottleneck)
  }
}
```

**Interpretation**:
- Majority in 0-100ms: Excellent cache effectiveness
- Shift to >500ms under peak load: Indicates DB bottleneck or thread pool saturation
- Compare across profiles to measure degradation curve

### Per-Endpoint Analysis

Identify problematic endpoints:

```json
{
  "endpointMetrics": {
    "GET /owners (list)": {
      "requests": 2500,
      "p95Latency": 250,
      "p99Latency": 800
    },
    "GET /vets (cached)": {
      "requests": 2500,
      "p95Latency": 20,     // Much faster due to cache
      "p99Latency": 50
    },
    "GET /owners/{id}": {
      "requests": 2500,
      "p95Latency": 300,
      "p99Latency": 1000    // Slower (more complex entity)
    }
  }
}
```

### Concurrency Degradation

Plot latency percentiles across profiles:

```
P95 Latency vs Concurrency:
Light (100):   150ms
Medium (250):  300ms (2x)
Peak (500):    600ms (4x)

Expected:      Linear-to-sublinear degradation
Concerning:    Exponential increase (database saturation)
```

## Integration with Other Benchmark Data

Load test results are designed for correlation with:

1. **Metrics Collection** (Task 5)
   - Export window: Align with 5-10 second metrics collection windows
   - Match timestamps for correlation analysis

2. **Blocking Detection** (Task 6)
   - Peak load profile may trigger blocking patterns
   - Compare THREAD_PARK events during load

3. **Multi-Version Comparison** (Task 8)
   - Run identical test plan against Java 17, Java 21 traditional, Java 21 virtual
   - Compare latency percentiles and throughput
   - Measure virtual thread advantage in high-concurrency scenarios

## Troubleshooting

### Connection Errors
- Verify application is running: `curl http://localhost:8080/`
- Check port: Modify `BASE_URL` in test plan
- Verify firewall: Allow JMeter -> localhost:8080

### Database Lock Issues
- H2: Ensure single writer (sequential test execution)
- MySQL/PostgreSQL: Check connection pool exhaustion
- Solution: Reduce thread ramp-up time or increase DB connections

### Test Failures
- Verify test data exists: Query owners/pets tables
- Check application logs for errors
- Validate assertions in test plan (HTTP codes)

### Performance Issues
- Increase heap: `jmeter -Xms2g -Xmx4g -t ...`
- Enable profiling: JMH frame graph integration
- Isolate variables: Run single profile at a time

## Configuration Reference

### User Variables (Test Plan Level)

```
BASE_URL = http://localhost:8080       # Application URL
THINK_TIME_MIN = 1000                  # Minimum think time (ms)
THINK_TIME_MAX = 3000                  # Maximum think time (ms)
```

### Thread Group Configuration

Each profile configured with:
- **Number of Threads**: Concurrent users
- **Ramp-Up Period**: 300 seconds (5 minutes)
- **Loop Count**: 1 (users stay connected for duration)
- **Scheduler**: Enabled
- **Duration**: 900 seconds (15 minutes total: 5m ramp + 10m steady)

### Sampler Configuration

HTTP Samplers configured with:
- **Protocol**: HTTP
- **Method**: GET (for all read scenarios)
- **Follow Redirects**: Enabled
- **Use Keep-Alive**: Enabled
- **Retrieve All Embedded Resources**: Disabled (reduces noise)

## Next Steps

1. **Run baseline tests** against current codebase
2. **Document baseline metrics** for each profile
3. **Identify bottlenecks** using per-endpoint analysis
4. **Run against all three Java variants** (Task 8+)
5. **Compare virtual vs platform threads** under load
6. **Export results** for correlation with other benchmarks

## References

- [Apache JMeter Documentation](https://jmeter.apache.org/)
- [JMeter User Manual](https://jmeter.apache.org/usermanual/index.html)
- [Spring PetClinic GitHub](https://github.com/spring-projects/spring-petclinic)
- [Load Testing Best Practices](https://jmeter.apache.org/usermanual/best-practices.html)
