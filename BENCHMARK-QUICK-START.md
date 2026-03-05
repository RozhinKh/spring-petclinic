# JMH Benchmarks - Quick Start Guide

## Prerequisites

- Java 17+ (for Java 17 baseline) and Java 21+ (for Java 21 variants)
- Maven 3.8+
- Application not running on ports 8080-8082

## One-Command Benchmark Execution

```bash
# Build benchmarks JAR and execute all variants
mvn clean package -DskipTests

# Run the benchmark orchestrator
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner
```

This will:
1. Build all 3 variants (Java 17, Java 21 traditional, Java 21 virtual)
2. Start each application for 10-second warm-up
3. Execute JMH benchmarks (5 forks, 5 warmup iterations, 10 measurement iterations)
4. Collect results and export to JSON/CSV

**Estimated Runtime**: 30-45 minutes (all variants)

## Output Files

After execution, you'll have:

```
benchmark-results.json      # Unified results (all variants)
benchmark-results.csv       # Summary in CSV format
jmh-results.json           # Raw JMH output (last variant)
```

## View Results

### JSON Results (Programmatic Analysis)
```bash
cat benchmark-results.json | jq '.variants[0].benchmarks[] | {name, score, unit, std_dev}'
```

### CSV Results (Spreadsheet Analysis)
```bash
# View in terminal
column -t -s, benchmark-results.csv

# Open in Excel/Sheets
open benchmark-results.csv
```

## Run Individual Benchmarks

### Startup Benchmarks Only
```bash
java -jar target/benchmarks.jar StartupBenchmark
```

### Latency Benchmarks Only
```bash
java -jar target/benchmarks.jar LatencyBenchmark
```

### Throughput Benchmarks Only
```bash
java -jar target/benchmarks.jar ThroughputBenchmark
```

### Memory Benchmarks Only
```bash
java -jar target/benchmarks.jar MemoryBenchmark
```

### Specific Endpoint Latency
```bash
java -jar target/benchmarks.jar LatencyBenchmark.getOwners
```

## Customize Benchmark Execution

### Change Fork Count
```bash
java -jar target/benchmarks.jar -f 10 LatencyBenchmark
# 10 forks = more stable but slower
```

### Change Iteration Count
```bash
java -jar target/benchmarks.jar -wi 10 -i 20 LatencyBenchmark
# 10 warmup iterations, 20 measurement iterations
```

### Change Measurement Time
```bash
java -jar target/benchmarks.jar -r 5s LatencyBenchmark
# 5 seconds per measurement iteration (default: 1s)
```

### List All Available Benchmarks
```bash
java -jar target/benchmarks.jar -l
```

### List Benchmarks with Parameters
```bash
java -jar target/benchmarks.jar -lp
```

## Performance Expectations

### Startup Time
- Java 17 Baseline: 2.0-2.5 seconds
- Java 21 Traditional: 1.9-2.4 seconds
- Java 21 Virtual: 1.8-2.3 seconds

### Endpoint Latency (P95, GET /owners)
- Java 17 Baseline: 50-100ms
- Java 21 Traditional: 45-90ms
- Java 21 Virtual: 40-85ms

### Throughput (requests/second)
- Java 17 Baseline: 200-250 req/s
- Java 21 Traditional: 220-280 req/s
- Java 21 Virtual: 250-320 req/s

### Idle Heap Usage
- Java 17 Baseline: 400-500 MB
- Java 21 Traditional: 400-500 MB
- Java 21 Virtual: 350-450 MB

## Interpreting Results

### Score
- **Latency**: milliseconds (lower is better)
- **Throughput**: operations/second (higher is better)
- **Startup**: milliseconds (lower is better)
- **Memory**: bytes used (lower is better)

### Std_dev (Standard Deviation)
- Indicates measurement consistency
- <10% of score = good stability
- >20% of score = needs more iterations/forks

### Confidence Interval (min/max)
- 95% confidence interval around the score
- Narrow range = stable measurement
- Wide range = variable performance

## Example Analysis

```json
{
  "name": "getOwners",
  "benchmark_type": "latency",
  "mode": "AverageTime",
  "unit": "ms",
  "value": 45.23,
  "std_dev": 3.45,
  "min": 42.10,
  "max": 48.50,
  "variant": "java17-baseline"
}
```

**Interpretation**:
- Average latency: 45.23ms
- Stability: std_dev=3.45 is 7.6% of mean (excellent)
- 95% confidence: between 42.10ms and 48.50ms
- Expected real-world latency: 95% of requests < 48.50ms

## Troubleshooting

### "Cannot connect to application"
- Check if previous process is still running: `lsof -i :8080`
- Kill it: `kill -9 <PID>`
- Wait 5 seconds before retrying

### "Port already in use"
- Change port in ApplicationStarter (line 161-162 in BenchmarkRunner)
- Ensure port 8080-8082 are available

### "Build failed"
- Run `mvn clean package -DskipTests` separately
- Check for compilation errors in benchmark classes
- Verify Java version matches Maven compiler config

### Extremely High Variance (>30%)
- Increase forks: `-f 10`
- Increase iterations: `-i 20`
- Increase measurement time: `-r 3s`
- Close other applications on the system

### Timeout Errors
- Increase timeout: increase warmup/measurement time
- Check application logs for exceptions
- Verify database connectivity

## Advanced Usage

### Compare Variants
```bash
# Extract scores for specific benchmark
grep "Latency.getOwners" benchmark-results.json | jq '.score'

# Calculate improvement
# Java 21 Virtual: 40.23ms
# Java 17 Baseline: 45.23ms
# Improvement = (45.23 - 40.23) / 45.23 = 11.1%
```

### Continuous Benchmarking
```bash
# Run benchmarks daily
(crontab -l 2>/dev/null; echo "0 2 * * * cd /path/to/petclinic && ./run-benchmarks.sh") | crontab -

# Create run-benchmarks.sh
#!/bin/bash
mvn clean package -DskipTests -q
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner > \
  benchmark-results-$(date +%Y%m%d-%H%M%S).json
```

### Generate Performance Report
```bash
# Extract key metrics
jq -r '.variants[] | 
  "\(.variant): " + (.benchmarks[] | 
  select(.name == "coldStartup" or .name == "getOwners" or .name == "getOwnersThroughput") | 
  "\(.name)=\(.value) \(.unit)") | @csv' benchmark-results.json
```

## Related Documentation

- [JMH Implementation Details](./BENCHMARK-JMH-IMPLEMENTATION.md)
- [Load Test Harness Guide](./LOAD-TEST-HARNESS-GUIDE.md)
- [Benchmark Workflows](./BENCHMARK-WORKFLOWS.md)
