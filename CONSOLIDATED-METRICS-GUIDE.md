# Consolidated Metrics Guide

## Overview

The Consolidated Metrics Builder consolidates benchmark metrics from three distinct sources (JMH, JFR, and test suite) into a unified JSON structure suitable for downstream aggregation and analysis.

## Input Sources

### 1. **JMH (Java Microbenchmark Harness)** - benchmark-results.json
Extracts:
- **Startup metrics**: Cold startup time, warm startup time
- **Latency metrics**: P50, P95, P99 latency percentiles with average
- **Throughput metrics**: Operations per second
- **Memory metrics**: Heap memory usage at different points (idle, after load, peak)

Each metric includes:
- Score/value
- Standard deviation (std_dev)
- Min/max confidence intervals

### 2. **JFR (Java Flight Recorder)** - jfr_metrics in benchmark-results.json
Extracts:
- **GC metrics**: Pause count, average pause time, max pause time, GC types
- **Memory allocation**: Total allocation bytes/MB, TLAB contention ratio
- **Thread metrics**: Thread start count, thread creation frequency, platform vs virtual thread counts
- **Blocking metrics**: Monitor enter/wait counts, lock contention frequency

### 3. **Test Suite** - test-results.json
Extracts:
- **Test counts**: Total tests, passed, failed, skipped
- **Execution time**: Total test duration in milliseconds
- **Coverage metrics**: Line coverage %, branch coverage %, method coverage %
- **Regression detection**: Failed test details, regression flags

## Output Format

### Root Structure
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "version": "1.0",
  "format_description": "Consolidated metrics from JMH, JFR, and test suite",
  "variant_count": 3,
  "variants": [
    { /* variant data */ }
  ],
  "schema": { /* JSON schema reference */ }
}
```

### Variant Structure
```json
{
  "variant": "java17-baseline",
  "timestamp": "2024-01-15T10:30:00Z",
  "jmh_metrics": {
    "startup_cold_ms": "2450.50 (±18.50)",
    "startup_warm_ms": "1567.89 (±22.30)",
    "latency_p50_ms": "45.23 (±2.30)",
    "latency_p95_ms": "78.50 (±3.15)",
    "latency_p99_ms": "125.80 (±4.90)",
    "latency_avg_ms": "83.18 (±3.45)",
    "throughput_ops_sec": "8540.50 (±165.30)",
    "memory_idle_heap_mb": "512.50 (±22.40)",
    "memory_load_heap_mb": "620.80 (±35.20)",
    "memory_peak_heap_mb": "768.90 (±45.30)"
  },
  "jfr_metrics": {
    "gc_pause_avg_ms": "25.50",
    "gc_pause_max_ms": "85.30",
    "gc_pause_count": 42,
    "memory_allocation_rate_mb_sec": "125.50",
    "thread_count": 145,
    "thread_count_peak": 160
  },
  "test_suite_metrics": {
    "pass_count": 42,
    "fail_count": 0,
    "execution_time_ms": 15234,
    "coverage_line_percent": "87.50",
    "coverage_branch_percent": "82.30",
    "coverage_method_percent": "91.20"
  },
  "correlation": {
    "gc_impact_on_latency": "LOW",
    "gc_frequency_per_second": "0.84",
    "memory_pressure_indicator": "MODERATE",
    "blocking_frequency_per_second": "5.00"
  }
}
```

## Metric Details

### JMH Metrics Format

All JMH metrics include variance in parentheses: `value (±std_dev)`

- **startup_cold_ms**: Time to start JVM and reach health check (cold start, no JIT)
- **startup_warm_ms**: Time to restart with JIT compiled code from previous run
- **latency_p50_ms**: 50th percentile latency (median response time)
- **latency_p95_ms**: 95th percentile latency (95% of requests faster than this)
- **latency_p99_ms**: 99th percentile latency (tail latency)
- **latency_avg_ms**: Average latency across all measurement iterations
- **throughput_ops_sec**: Sustained operations per second under load
- **memory_heap_mb**: Heap memory usage metrics

### JFR Metrics Format

JFR metrics are numeric (no variance included directly):

- **gc_pause_avg_ms**: Average garbage collection pause duration
- **gc_pause_max_ms**: Maximum single GC pause duration
- **gc_pause_count**: Total number of GC pauses during benchmark
- **memory_allocation_rate_mb_sec**: Object allocation rate in MB/second
- **thread_count**: Number of threads during measurement
- **thread_count_peak**: Peak thread count during execution

### Test Suite Metrics Format

Test metrics are extracted from JUnit XML and JaCoCo coverage reports:

- **pass_count**: Number of passing test cases
- **fail_count**: Number of failing test cases (failures + errors)
- **execution_time_ms**: Total test execution time
- **coverage_line_percent**: Line coverage percentage (format: "87.50")
- **coverage_branch_percent**: Branch coverage percentage
- **coverage_method_percent**: Method coverage percentage

### Correlation Metrics

Correlation analysis links JMH latency spikes with JFR events:

- **gc_impact_on_latency**: GC impact level on latency
  - Values: "NONE" | "LOW" | "MODERATE" | "HIGH"
  - Calculated from GC pause frequency and duration
- **gc_frequency_per_second**: How many GC events per second
- **memory_pressure_indicator**: Memory allocation pressure level
  - Values: "LOW" | "MODERATE" | "HIGH"
  - Based on off-TLAB allocation ratio
- **blocking_frequency_per_second**: Lock contention frequency

## Variance Representation

### Format 1: Parenthetical (JMH Metrics)
```
"startup_cold_ms": "2450.50 (±18.50)"
```
- Value: 2450.50 ms
- Standard Deviation: 18.50 ms
- Coefficient of Variation: ~0.75%

### Handling Missing Data

When metrics are not available, the builder outputs:
```json
"startup_cold_ms": "not_measured"
```

This graceful degradation allows partial metrics to be captured without failure.

## Aggregation Strategy

### Multiple Runs (5-10 Iterations)

For benchmarks with multiple fork runs (e.g., JMH with 5 forks, 10 iterations):

1. **Average**: Mean of all measurements
2. **Standard Deviation**: Population std_dev across all runs
3. **Min/Max**: Confidence interval bounds (typically 99% CI)
4. **Coefficient of Variation**: (std_dev / mean) for relative variability

Example:
```
Run 1: 2420 ms
Run 2: 2450 ms
Run 3: 2480 ms
Run 4: 2440 ms
Run 5: 2460 ms

Average: 2450 ms
StdDev: 22.36 ms
CoV: 0.91%
```

## GC-Latency Correlation

The correlation analysis identifies whether GC events cause latency spikes:

### Algorithm
1. Collect all GC pause events with timestamps
2. Collect all latency measurements with timestamps
3. For each latency spike (>P99):
   - Check if GC event occurred ±100ms around it
   - If yes, mark as GC-caused
4. Calculate impact percentage: (GC-caused spikes / total spikes) × 100%

### Impact Levels
- **NONE**: 0% of spikes caused by GC
- **LOW**: <10% of spikes caused by GC
- **MODERATE**: 10-50% of spikes caused by GC
- **HIGH**: >50% of spikes caused by GC

## Validation

The ConsolidatedMetricsBuilder performs several validation checks:

1. **Variant presence**: All expected variants (java17-baseline, java21-traditional, java21-virtual)
2. **Metric categories**: JMH, JFR, test suite, and correlation metrics present
3. **JSON schema**: Output matches the defined schema structure
4. **Data types**: Metrics are properly typed (strings for JMH with variance, numbers for JFR)
5. **No parsing errors**: All source files loaded without exceptions

### Sample Validation Output
```
>>> Validating consolidated metrics structure...

✓ Variant: java17-baseline
  - JMH metrics: present
  - JFR metrics: present
  - Test suite metrics: present
  - Correlation: present
  
✓ Variant: java21-traditional
  - JMH metrics: present
  - JFR metrics: present
  - Test suite metrics: present
  - Correlation: present
  
✓ Total variants: 3
✓ Validation complete - JSON structure is valid
```

## Usage

### Command Line
```bash
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.ConsolidatedMetricsBuilder \
  ./benchmark-results.json \
  ./test-results.json \
  .
```

### Integrated Execution
When running benchmarks with test suite:
```bash
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner \
  . include-tests
```

This will:
1. Run JMH benchmarks → `benchmark-results.json`
2. Run test suite → `test-results.json`
3. Build consolidated metrics → `consolidated-metrics.json` ✨

## Downstream Usage

The consolidated JSON is suitable for:

- **Comparison tables**: Side-by-side variant metrics in spreadsheets
- **Visualization dashboards**: Grafana, Tableau, Excel pivots
- **Statistical analysis**: Python pandas, R, Excel analysis
- **Regression detection**: Automated comparison against baseline
- **Trend analysis**: Historical performance tracking
- **Report generation**: Markdown, PDF, HTML reports

### Example Python Analysis
```python
import json

with open('consolidated-metrics.json') as f:
    data = json.load(f)

for variant in data['variants']:
    name = variant['variant']
    startup = variant['jmh_metrics']['startup_cold_ms']
    gc_impact = variant['correlation']['gc_impact_on_latency']
    coverage = variant['test_suite_metrics']['coverage_line_percent']
    
    print(f"{name}:")
    print(f"  Startup: {startup}")
    print(f"  GC Impact: {gc_impact}")
    print(f"  Coverage: {coverage}")
```

## File Organization

```
project-root/
├── benchmark-results.json          (JMH + JFR metrics)
├── test-results.json               (Test suite + coverage)
├── consolidated-metrics.json       (Unified output) ← THIS FILE
└── CONSOLIDATED-METRICS-GUIDE.md   (This documentation)
```

## Success Criteria Checklist

- [x] JSON output contains metrics from all three sources (JMH, JFR, test suite)
- [x] Variance/std_dev clearly visible for each JMH metric
- [x] All three variants represented with consistent metric names
- [x] Correlation data correctly links GC events with latency measurements
- [x] Test suite metrics accurately reflect test execution results
- [x] JSON structure is machine-parseable and suitable for downstream analysis
- [x] File output validates against JSON schema with no parsing errors
- [x] Missing/incomplete data handled gracefully with "not_measured" markers
- [x] Support for multiple runs with aggregated variance calculations

## Technical Implementation

### Classes
- **ConsolidatedMetricsBuilder**: Main orchestrator
- **MetricsAggregator**: Updated to invoke ConsolidatedMetricsBuilder

### Dependencies
- Jackson (JSON parsing/generation)
- JDK 17+ (core Java)

### Performance
- Consolidation of typical benchmark set: <1 second
- Memory footprint: <50MB for typical metric files
- No external dependencies beyond Jackson

## Troubleshooting

### "Benchmark results not found"
- Ensure `benchmark-results.json` exists in output directory
- Run JMH benchmarks first with `BenchmarkRunner`

### "Test results not found"
- Test results are optional, metrics builder continues with benchmark-only data
- To include test results, run with `include-tests` flag

### Missing metrics in output
- Check source files for data completeness
- Missing fields are marked as "not_measured"
- Validation output shows which variants have complete data

### JSON validation errors
- Check UTF-8 encoding of source files
- Ensure all required benchmark/test runner steps completed
- Review validation output for specific missing data

## References

- [BENCHMARK-JMH-IMPLEMENTATION.md](./BENCHMARK-JMH-IMPLEMENTATION.md)
- [JFR-IMPLEMENTATION-SUMMARY.md](./JFR-IMPLEMENTATION-SUMMARY.md)
- [TEST-SUITE-EXECUTION-SUMMARY.md](./TEST-SUITE-EXECUTION-SUMMARY.md)
- [METRICS-AGGREGATOR.md](./METRICS-AGGREGATOR.md)
