# Consolidated Metrics Validation & Testing

## Validation Checklist

This document provides validation criteria and testing procedures for the Consolidated Metrics Builder implementation.

### Requirements Verification

#### ✅ Input Sources Parsing
- [x] Parse JMH JSON output files (one per variant)
  - Extract startup cold/warm times
  - Extract latency percentiles (P50, P95, P99)
  - Extract throughput metrics (ops/sec)
  - Extract memory metrics (heap usage)

- [x] Parse JFR metrics data
  - Extract GC pause times (avg, max, count)
  - Extract memory allocation rates
  - Extract thread counts

- [x] Parse test suite results (JUnit XML and JaCoCo coverage XML)
  - Extract pass/fail counts
  - Extract per-test execution times
  - Extract coverage percentages (line, branch, method)

#### ✅ Data Aggregation
- [x] Support multiple benchmark runs with variance/std_dev calculations
  - Average across runs
  - Standard deviation calculation
  - Min/max confidence intervals
  - Coefficient of variation

#### ✅ Variant Handling
- [x] Support three distinct variants
  - Java 17 baseline
  - Java 21 traditional threads
  - Java 21 virtual threads
  - Consistent metric names across variants

#### ✅ JSON Schema & Output
- [x] Build unified JSON schema with proper structure
  - variant identifier
  - jmh_metrics category
  - jfr_metrics category
  - test_suite_metrics category
  - correlation category

- [x] Include variance/std_dev in parentheses for each metric
  - Format: `value (±std_dev)`
  - Example: `"1250 (±50)"` for startup time

- [x] Handle missing/incomplete data gracefully
  - Use "not_measured" marker for unavailable metrics
  - Continue processing if some data sources unavailable
  - Validation output reports completeness

#### ✅ Correlation Analysis
- [x] Correlate JMH latency with JFR GC pause events
  - Identify GC-caused latency spikes
  - Impact level assessment (NONE, LOW, MODERATE, HIGH)
  - Frequency metrics (GC/sec, blocking/sec)

#### ✅ Validation & Quality
- [x] Validate JSON structure against schema
  - No parsing errors
  - All required fields present or marked "not_measured"
  - Proper data types
  - Machine-parseable format

## Implementation Validation

### ConsolidatedMetricsBuilder Class

**Location**: `src/main/java/org/springframework/samples/petclinic/benchmark/ConsolidatedMetricsBuilder.java`

**Key Methods**:
1. `buildConsolidatedMetrics()` - Main entry point
2. `buildConsolidated()` - Core consolidation logic
3. `extractJmhMetrics()` - JMH metric extraction
4. `extractJfrMetrics()` - JFR metric extraction
5. `extractTestSuiteMetrics()` - Test suite metric extraction
6. `extractCorrelation()` - Correlation analysis extraction
7. `validateConsolidatedMetrics()` - Structure validation

**Quality Checks**:
- Exception handling for missing files
- Null safety for optional data
- Graceful degradation when sources unavailable
- Informative error messages

### Integration Points

#### MetricsAggregator.java Changes
```java
public void aggregateAllMetrics(String outputDir) throws IOException {
    // Uses ConsolidatedMetricsBuilder for metric consolidation
    ConsolidatedMetricsBuilder builder = new ConsolidatedMetricsBuilder(
        benchmarkPath, testPath, outputDir);
    builder.buildConsolidatedMetrics();
}
```

#### BenchmarkRunner.java Changes
```java
// After test suite execution, build consolidated metrics
ConsolidatedMetricsBuilder builder = new ConsolidatedMetricsBuilder(
    benchmarkPath, testPath, outputDir);
builder.buildConsolidatedMetrics();
```

## Test Scenarios

### Scenario 1: All Data Available
**Input**: Complete benchmark-results.json and test-results.json
**Expected Output**: Fully populated consolidated-metrics.json with all metrics
**Validation**: All variants have JMH, JFR, test suite, and correlation data

### Scenario 2: Only Benchmark Data
**Input**: benchmark-results.json only (no test-results.json)
**Expected Output**: Consolidated metrics with test suite metrics marked "not_measured"
**Validation**: JMH and JFR data present, test metrics show "not_measured"

### Scenario 3: Missing Optional JFR Data
**Input**: Benchmark results without JFR metrics block
**Expected Output**: All JFR metrics marked "not_measured"
**Validation**: Builder continues without failure, output shows graceful degradation

### Scenario 4: Corrupt/Invalid JSON
**Input**: Malformed JSON in source files
**Expected Output**: Error message with fallback to available data
**Validation**: Graceful error handling, partial results if possible

## Success Criteria Testing

### Test 1: Multiple Variants Consolidated
```
✓ Verify 3 variants in output: java17-baseline, java21-traditional, java21-virtual
✓ Each variant has unique timestamp
✓ Metric values differ appropriately (Java 21 typically better)
```

### Test 2: Variance Representation
```
✓ All JMH metrics include std_dev: e.g., "2450.50 (±18.50)"
✓ JFR metrics are numeric: e.g., "25.50"
✓ Test metrics include coverage: e.g., "87.50"
```

### Test 3: Metric Extraction Completeness
```
✓ JMH: startup (cold/warm), latency (P50/P95/P99/avg), throughput, memory
✓ JFR: GC pauses, memory allocation, thread counts
✓ Test: pass/fail counts, execution time, coverage %
✓ Correlation: GC impact, memory pressure, blocking frequency
```

### Test 4: Missing Data Handling
```
✓ Missing metrics marked as "not_measured"
✓ No null pointer exceptions
✓ Validation completes successfully
✓ Output still valid JSON
```

### Test 5: Schema Validation
```
✓ Output JSON validates against embedded schema
✓ All required properties present
✓ Data types match schema (strings vs numbers)
✓ No extraneous fields
```

### Test 6: GC-Latency Correlation
```
✓ Correlation data present in output
✓ Impact levels correct (NONE/LOW/MODERATE/HIGH)
✓ Frequency metrics present
✓ Memory pressure indicator present
```

### Test 7: JSON Machine Parseable
```
✓ Valid JSON syntax (no parsing errors)
✓ Can be parsed by any JSON parser (jq, Python json, etc.)
✓ Suitable for downstream analysis tools
✓ No encoding issues (UTF-8 clean)
```

## Execution Testing

### Basic Execution
```bash
# Standalone builder execution
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.ConsolidatedMetricsBuilder \
  ./benchmark-results.json \
  ./test-results.json \
  .

# Expected output:
# ===============================================
#    Consolidated Metrics Builder
# ===============================================
# 
# >>> Loading source metrics...
# ✓ Loaded benchmark results
# ✓ Loaded test suite results
# 
# >>> Building consolidated metrics...
# ✓ Consolidated metrics saved to: ./consolidated-metrics.json
# 
# >>> Validating consolidated metrics structure...
# ✓ Variant: java17-baseline
#   - JMH metrics: present
#   - JFR metrics: present
#   - Test suite metrics: present
#   - Correlation: present
# ✓ Total variants: 3
# ✓ Validation complete - JSON structure is valid
```

### Integrated Execution
```bash
# With test suite execution
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner \
  . include-tests

# This will:
# 1. Run JMH benchmarks → benchmark-results.json
# 2. Run test suite → test-results.json
# 3. Build consolidated metrics → consolidated-metrics.json
```

## Output Validation

### Consolidated JSON Structure Check
```json
{
  "timestamp": "ISO-8601 timestamp",
  "version": "1.0",
  "variant_count": 3,
  "variants": [
    {
      "variant": "java17-baseline|java21-traditional|java21-virtual",
      "timestamp": "ISO-8601 timestamp",
      "jmh_metrics": {
        "startup_cold_ms": "number (±std_dev) or 'not_measured'",
        "latency_p50_ms": "number (±std_dev) or 'not_measured'",
        "latency_p95_ms": "number (±std_dev) or 'not_measured'",
        "latency_p99_ms": "number (±std_dev) or 'not_measured'",
        "throughput_ops_sec": "number (±std_dev) or 'not_measured'",
        "memory_heap_mb": "number (±std_dev) or 'not_measured'"
      },
      "jfr_metrics": {
        "gc_pause_avg_ms": "number or 'not_measured'",
        "gc_pause_count": "number or 'not_measured'",
        "memory_allocation_rate_mb_sec": "number or 'not_measured'",
        "thread_count": "number or 'not_measured'"
      },
      "test_suite_metrics": {
        "pass_count": "number or 'not_measured'",
        "fail_count": "number or 'not_measured'",
        "execution_time_ms": "number or 'not_measured'",
        "coverage_line_percent": "number or 'not_measured'"
      },
      "correlation": {
        "gc_impact_on_latency": "NONE|LOW|MODERATE|HIGH or 'not_measured'",
        "gc_frequency_per_second": "number or 'not_measured'",
        "memory_pressure_indicator": "LOW|MODERATE|HIGH or 'not_measured'",
        "blocking_frequency_per_second": "number or 'not_measured'"
      }
    }
  ],
  "schema": { /* schema definition */ }
}
```

### Validation Commands

#### Using jq (Command Line)
```bash
# Verify JSON is valid
jq . consolidated-metrics.json > /dev/null && echo "Valid JSON"

# Count variants
jq '.variant_count' consolidated-metrics.json

# Check Java 17 metrics
jq '.variants[] | select(.variant == "java17-baseline") | .jmh_metrics' consolidated-metrics.json

# Verify all variants present
jq '.variants[].variant' consolidated-metrics.json
```

#### Using Python
```python
import json

with open('consolidated-metrics.json') as f:
    metrics = json.load(f)

# Validate structure
assert 'variants' in metrics
assert metrics['variant_count'] == len(metrics['variants'])

# Validate variant data
for variant in metrics['variants']:
    assert 'jmh_metrics' in variant
    assert 'jfr_metrics' in variant
    assert 'test_suite_metrics' in variant
    assert 'correlation' in variant
    print(f"✓ {variant['variant']} - all categories present")

print(f"\n✓ Validation complete - {metrics['variant_count']} variants")
```

## Performance Metrics

**Consolidation Time**: <1 second for typical benchmark set
**Memory Usage**: <50MB peak
**File Size**: 100-500KB depending on metric detail level
**JSON Parsing**: Compatible with all standard JSON parsers

## Documentation

- **CONSOLIDATED-METRICS-GUIDE.md**: Complete usage guide
- **consolidated-metrics-example.json**: Example output with sample data
- **This document**: Validation and testing procedures

## Regression Testing

### After Code Changes
1. Regenerate consolidated metrics
2. Run validation script
3. Compare output structure with previous version
4. Verify all metrics present
5. Check no new "not_measured" markers unexpectedly

### Expected Metric Trends
- Java 21 Virtual typically shows:
  - Lower startup time than Java 17
  - Lower latency percentiles
  - Higher throughput
  - Fewer GC pauses
  - Lower thread count (virtual threads are lightweight)
  - Better memory efficiency

- Java 21 Traditional typically shows:
  - Similar to Java 17 for most metrics
  - Slight improvements from newer JDK
  - Similar thread counts and GC behavior

## Troubleshooting Validation Failures

### "Benchmark results not found"
- Ensure benchmark-results.json exists and is valid JSON
- Check BenchmarkRunner completed successfully

### "Missing metrics in output"
- Expected behavior if source data incomplete
- Check individual benchmark/test runs completed
- Verify source JSON files have required data

### "Validation shows missing variants"
- Ensure all three variants built and executed
- Check output directory has correct variant data
- Review benchmark/test runner logs

### "JSON parsing error"
- Validate source JSON files with jq or online validator
- Check for encoding issues (UTF-8 expected)
- Ensure no special characters in metric values

## References

- [CONSOLIDATED-METRICS-GUIDE.md](./CONSOLIDATED-METRICS-GUIDE.md)
- [ConsolidatedMetricsBuilder.java](./src/main/java/org/springframework/samples/petclinic/benchmark/ConsolidatedMetricsBuilder.java)
- [consolidated-metrics-example.json](./consolidated-metrics-example.json)
