# Metrics Aggregator & Result Comparison Framework

A comprehensive framework for aggregating benchmark metrics from multiple data sources, normalizing them into a unified schema, and generating side-by-side variant comparisons with statistical aggregation.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Data Sources](#data-sources)
4. [Unified Schema](#unified-schema)
5. [Components](#components)
6. [Usage](#usage)
7. [Output Format](#output-format)
8. [Examples](#examples)
9. [API Reference](#api-reference)

---

## Overview

The metrics aggregator processes benchmark results from 7 different data sources, normalizes them into a consistent format, calculates aggregate statistics across multiple runs, and generates comparison tables showing performance deltas across Java variants (Java 17, Java 21 Traditional, Java 21 with Virtual Threads).

### Key Features

- **Multi-source parsing**: JMH, JFR, JUnit, JaCoCo, load tests, Actuator, blocking detection, modernization metrics
- **Unified normalization**: Consistent schema with automatic handling of missing data
- **Statistical aggregation**: Calculates average, min, max, standard deviation per metric
- **Comparison tables**: Side-by-side variant comparisons with percentage deltas and variance
- **Graceful degradation**: Continues processing if some data sources are unavailable
- **Performance**: Complete aggregation <5 minutes for typical benchmark sets

---

## Architecture

### High-Level Data Flow

```
Raw Data Sources
  ├── JMH JSON Files (*.json)
  ├── JFR Metrics (*.json)
  ├── Test Results (*.xml)
  ├── Load Test Results (*.json)
  ├── Actuator Metrics (*.json)
  ├── Blocking Detection (*.json)
  └── Modernization Metrics (*.json)
          ↓
      [Parsers]
    Extract metrics from each source format
          ↓
      NormalizedMetric[]
   (unified schema: metric_name, value, unit, variant, run_number)
          ↓
    [MetricsNormalizer]
  Handle missing data & unit conversions
          ↓
      Normalized Metrics[]
          ↓
     [MetricsAggregator]
  Calculate stats across runs (avg, min, max, stdDev)
          ↓
      AggregatedMetric[]
   (statistics per metric per variant)
          ↓
[ComparisonTableGenerator]
   Generate side-by-side comparisons with deltas
          ↓
      MetricComparison[]
   (with variance and percentage changes)
          ↓
    Final Output
  (Formatted tables, JSON, CSV)
```

### Package Structure

```
org.springframework.samples.petclinic.metrics/
├── NormalizedMetric.java          # Unified metric schema
├── AggregatedMetric.java          # Aggregated statistics
├── MetricComparison.java          # Side-by-side comparison
├── AggregationResult.java         # Container for results
├── MetricsNormalizer.java         # Normalization & validation
├── MetricsAggregator.java         # Statistical aggregation
├── ComparisonTableGenerator.java  # Comparison table generation
├── StatisticalUtils.java          # Statistical utilities
├── ResultAggregator.java          # Main orchestrator
├── parser/
│   ├── JmhJsonParser.java         # JMH results parser
│   ├── JfrMetricsParser.java      # JFR metrics parser
│   ├── TestSuiteParser.java       # JUnit & JaCoCo parser
│   ├── LoadTestParser.java        # Load test results parser
│   ├── ActuatorMetricsParser.java # Actuator metrics parser
│   ├── BlockingDetectionParser.java # Blocking detection parser
│   └── ModernizationMetricsParser.java # Modernization metrics parser
└── package-info.java              # Package documentation
```

---

## Data Sources

### 1. JMH JSON Output

**Format**: JSON produced by JMH with `-rf json -rff output.json`

**Metrics Extracted**:
- Application startup (cold/warm)
- Latency (average time per operation)
- Throughput (operations per second)
- Memory allocation/usage

**Expected Fields**:
```json
[
  {
    "benchmark": "org.example.Bench.method",
    "mode": "thrpt",
    "primaryMetric": {
      "score": 1000.0,
      "scoreUnit": "ops/sec"
    }
  }
]
```

### 2. JFR (Java Flight Recorder) Metrics

**Format**: JFR metrics exported as JSON

**Metrics Extracted**:
- GC pause times (avg/max)
- GC collection count
- Thread counts (current, peak, virtual)
- Memory metrics (heap used/max, allocation rate)
- Blocking events

**Expected Fields**:
```json
{
  "gc": {
    "pause_time_avg_ms": 25.5,
    "collection_count": 45
  },
  "threads": {
    "count": 42,
    "virtual_thread_count": 100
  }
}
```

### 3. Test Suite Results

**Format**: JUnit XML and JaCoCo XML

**Metrics Extracted**:
- Test counts (total, passed, failed, skipped)
- Pass rate percentage
- Execution time
- Code coverage (line and branch)

**Expected JUnit Structure**:
```xml
<testsuite tests="100" failures="5" skipped="0" time="45.5">
  <testcase name="test1" time="0.123"/>
</testsuite>
```

### 4. Load Test Results

**Format**: JSON from load testing tools (JMeter, k6, Gatling)

**Metrics Extracted**:
- Latency percentiles (P50-P99.9)
- Throughput (requests/sec)
- Error rates
- Total request count

**Expected Structure**:
```json
{
  "latency": {
    "p50_ms": 12.5,
    "p95_ms": 45.0,
    "p99_ms": 120.0
  },
  "throughput": {
    "requests_per_sec": 950.0
  }
}
```

### 5. Spring Boot Actuator Metrics

**Format**: JSON from Actuator metrics endpoint

**Metrics Extracted**:
- HTTP request metrics (count, response time)
- JVM metrics (memory, GC, threads)
- Cache hit/miss rates
- Database connection pool stats

**Expected Fields**:
```json
{
  "http": {
    "request_count": 1000,
    "avg_response_time_ms": 25.0
  },
  "jvm": {
    "memory": {"heap_used_mb": 256},
    "gc": {"gc_time_ms": 1200}
  }
}
```

### 6. Blocking Detection Reports

**Format**: Static and runtime analysis JSON

**Static Analysis**:
- Blocking call count
- Synchronized usage count
- Lock usage count
- Locations of blocking operations

**Runtime Analysis**:
- Blocking event count
- Blocking frequency (events/sec)
- Average/max blocking duration
- Affected thread count

### 7. Modernization Metrics

**Format**: Code analysis JSON

**Metrics Extracted**:
- Lines of code (total, modified, new)
- Construct counts (synchronized, locks, atomics, concurrent collections)
- Virtual thread adoption metrics
- Usage locations

---

## Unified Schema

All metrics are normalized to a common schema: `NormalizedMetric`

### NormalizedMetric Fields

| Field | Type | Description |
|-------|------|-------------|
| `metricName` | String | Identifier (e.g., "latency_p95") |
| `value` | Double | Numeric value |
| `unit` | String | Unit (ms, MB, req/sec, %, count, etc.) |
| `variant` | String | Java version (Java 17, Java 21 Trad, Java 21 Virtual) |
| `runNumber` | Integer | Run identifier (1, 2, 3, ...) |
| `category` | String | Category (startup, latency, throughput, memory, gc, threading, blocking, test_suite, modernization) |
| `dataSource` | String | Source (jmh, jfr, test_suite, load_test, actuator, blocking, modernization) |
| `timestamp` | Long | When captured (optional) |

### Example

```java
NormalizedMetric metric = new NormalizedMetric(
    "latency_p95",        // metricName
    45.2,                 // value
    "ms",                 // unit
    "Java 21 Virtual",    // variant
    1,                    // runNumber
    "latency",            // category
    "load_test"           // dataSource
);
```

---

## Components

### 1. MetricsNormalizer

Normalizes metrics by handling missing data and unit conversions.

```java
MetricsNormalizer normalizer = new MetricsNormalizer();

// Handle missing data
List<NormalizedMetric> normalized = 
    normalizer.handleMissingData(metrics, false);

// Convert units
NormalizedMetric inSeconds = 
    normalizer.convertUnit(metricInMs, "s");

// Validate schema
if (normalizer.isValidMetric(metric)) {
    // Process
}
```

### 2. MetricsAggregator

Calculates statistics across multiple runs.

```java
MetricsAggregator aggregator = new MetricsAggregator();

// Aggregate all metrics
List<AggregatedMetric> aggregated = 
    aggregator.aggregate(normalizedMetrics);

// Group by category
Map<String, List<AggregatedMetric>> byCategory = 
    aggregator.aggregateByCategory(aggregated);

// Check variance
double cv = aggregator.calculateCoefficientOfVariation(metric);
boolean highVariance = aggregator.hasSignificantVariance(metric);
```

### 3. ComparisonTableGenerator

Creates side-by-side comparison tables.

```java
ComparisonTableGenerator generator = 
    new ComparisonTableGenerator();

// Generate comparisons by category
Map<String, List<MetricComparison>> comparisons =
    generator.generateComparisonsByCategory(aggregatedMetrics);

// Format for display
String table = generator.generateFormattedTable(comparisons);
System.out.println(table);
```

### 4. ResultAggregator

Main orchestrator for entire workflow.

```java
ResultAggregator aggregator = new ResultAggregator();

// Aggregate single run
AggregationResult result = aggregator.aggregate(
    "/path/to/results",
    "Java 21 Virtual",
    1
);

// Aggregate multiple runs
Map<String, List<AggregatedMetric>> multiRun =
    aggregator.aggregateMultipleRuns(
        "/path/to/results",
        "Java 21 Virtual",
        Arrays.asList(1, 2, 3)
    );

// Generate comparisons across variants
List<AggregationResult> variants = Arrays.asList(
    java17Result, java21TradResult, java21VirtualResult
);
Map<String, List<MetricComparison>> comparisons =
    aggregator.generateComparisons(variants);

// Format output
String output = aggregator.generateFormattedComparisons(comparisons);
```

### 5. Parsers

Each parser implements the same pattern:

```java
// JMH
JmhJsonParser jmhParser = new JmhJsonParser();
List<NormalizedMetric> metrics = 
    jmhParser.parse(new File("jmh-results.json"), "Java 17", 1);

// JFR
JfrMetricsParser jfrParser = new JfrMetricsParser();
List<NormalizedMetric> metrics = 
    jfrParser.parse(new File("jfr-metrics.json"), "Java 21 Virtual", 1);

// Test Suite
TestSuiteParser testParser = new TestSuiteParser();
List<NormalizedMetric> metrics = 
    testParser.parseJunitXml(new File("test-results.xml"), "Java 21 Trad", 1);
```

---

## Usage

### Basic Example

```java
// 1. Create aggregator
ResultAggregator aggregator = new ResultAggregator();

// 2. Parse results from each variant
AggregationResult java17Result = aggregator.aggregate(
    "/benchmark/results/java17", "Java 17", 1);

AggregationResult java21TradResult = aggregator.aggregate(
    "/benchmark/results/java21-trad", "Java 21 Trad", 1);

AggregationResult java21VirtualResult = aggregator.aggregate(
    "/benchmark/results/java21-virtual", "Java 21 Virtual", 1);

// 3. Generate comparisons
List<AggregationResult> results = Arrays.asList(
    java17Result, java21TradResult, java21VirtualResult);

Map<String, List<MetricComparison>> comparisons =
    aggregator.generateComparisons(results);

// 4. Output results
String formattedOutput = 
    aggregator.generateFormattedComparisons(comparisons);
System.out.println(formattedOutput);
```

### Multi-Run Aggregation

```java
// Aggregate multiple runs of the same variant
ResultAggregator aggregator = new ResultAggregator();

Map<String, List<AggregatedMetric>> java17MultiRun =
    aggregator.aggregateMultipleRuns(
        "/benchmark/results/java17",
        "Java 17",
        Arrays.asList(1, 2, 3, 4, 5) // 5 runs
    );

// Results now contain average, min, max, stdDev across all 5 runs
```

### Programmatic Access to Results

```java
AggregationResult result = aggregator.aggregate(
    "/path/to/results", "Java 21 Virtual", 1);

// Access normalized metrics
List<NormalizedMetric> normalized = 
    result.getNormalizedMetrics();

// Access aggregated metrics
List<AggregatedMetric> aggregated = 
    result.getAggregatedMetrics();

// Access by category
Map<String, List<AggregatedMetric>> byCategory = 
    result.getAggregatedByCategory();

// Process by category
for (Map.Entry<String, List<AggregatedMetric>> entry : 
     byCategory.entrySet()) {
    String category = entry.getKey();
    List<AggregatedMetric> metrics = entry.getValue();
    
    System.out.println("Category: " + category);
    for (AggregatedMetric metric : metrics) {
        System.out.printf(
            "  %s: %.2f ± %.2f %s\n",
            metric.getMetricName(),
            metric.getAverage(),
            metric.getStdDev(),
            metric.getUnit()
        );
    }
}
```

---

## Output Format

### Comparison Table Structure

```
=== LATENCY ===
Metric                                   | Unit       | Java 17              | Java 21 Trad         | Java 21 Virtual      | Delta 17→21T    | Delta 17→21V
------------------------------------------|------------|----------------------|----------------------|----------------------|-----------------|------------------
latency_p50                              | ms         | 12.50 (±0.30)        | 11.80 (±0.20)        | 10.20 (±0.40)        | -5.60%          | -18.40%
latency_p95                              | ms         | 45.00 (±2.00)        | 42.00 (±1.50)        | 35.00 (±3.00)        | -6.67%          | -22.22%
latency_p99                              | ms         | 120.00 (±5.00)       | 115.00 (±4.00)       | 95.00 (±6.00)        | -4.17%          | -20.83%

=== THROUGHPUT ===
throughput_rps                           | req/sec    | 950.00 (±45.00)      | 975.00 (±40.00)      | 1280.00 (±60.00)     | +2.63%          | +34.74%
```

### Variance Notation

Metrics display variance as: `value (±stdDev)`

Example: `12.5 (±0.3)` means value 12.5 with standard deviation 0.3

### Delta Calculation

Percentage delta: `((variant_value - baseline_value) / baseline_value) * 100`

- Negative delta in latency = improvement (lower is better)
- Positive delta in throughput = improvement (higher is better)

---

## Examples

### Example 1: Single Variant, Single Run

```java
ResultAggregator agg = new ResultAggregator();
AggregationResult result = agg.aggregate(
    "/data/java21-virtual-run1", 
    "Java 21 Virtual", 
    1
);

// Explore results
System.out.println("Parsed " + 
    result.getNormalizedMetrics().size() + 
    " metrics");

System.out.println("Aggregated into " + 
    result.getAggregatedMetrics().size() + 
    " statistics");
```

### Example 2: Three Variants, Multiple Runs Each

```java
ResultAggregator agg = new ResultAggregator();

// Aggregate multiple runs for each variant
Map<String, List<AggregatedMetric>> java17 =
    agg.aggregateMultipleRuns("/data/java17", "Java 17", 
        Arrays.asList(1, 2, 3));
Map<String, List<AggregatedMetric>> java21Trad =
    agg.aggregateMultipleRuns("/data/java21-trad", "Java 21 Trad",
        Arrays.asList(1, 2, 3));
Map<String, List<AggregatedMetric>> java21Virtual =
    agg.aggregateMultipleRuns("/data/java21-virtual", "Java 21 Virtual",
        Arrays.asList(1, 2, 3));

// Merge all results for comparison
List<AggregatedMetric> allAggregated = new ArrayList<>();
allAggregated.addAll(java17.values().stream()
    .flatMap(List::stream).collect(Collectors.toList()));
allAggregated.addAll(java21Trad.values().stream()
    .flatMap(List::stream).collect(Collectors.toList()));
allAggregated.addAll(java21Virtual.values().stream()
    .flatMap(List::stream).collect(Collectors.toList()));

// Generate comparisons
ComparisonTableGenerator gen = new ComparisonTableGenerator();
Map<String, List<MetricComparison>> comparisons =
    gen.generateComparisonsByCategory(allAggregated);

// Output
System.out.println(gen.generateFormattedTable(comparisons));
```

### Example 3: Statistical Significance Testing

```java
// Check if differences are statistically significant
double java17Latency = 12.5;
double java17StdDev = 0.5;
double java21VirtualLatency = 10.2;

boolean isSignificant = StatisticalUtils.isSignificantDifference(
    java17Latency,
    java21VirtualLatency,
    "latency",       // category
    java17StdDev     // baseline stdDev
);

System.out.println("Difference significant? " + isSignificant);

// Calculate confidence interval
double margin = StatisticalUtils.calculateConfidenceIntervalMargin(
    java17StdDev,
    3  // 3 runs
);
System.out.println("95% CI margin: ±" + margin + "ms");
```

---

## API Reference

### NormalizedMetric

```java
public class NormalizedMetric {
    public String getMetricName()
    public Double getValue()
    public String getUnit()
    public String getVariant()
    public Integer getRunNumber()
    public String getCategory()
    public String getDataSource()
}
```

### AggregatedMetric

```java
public class AggregatedMetric {
    public String getMetricName()
    public String getUnit()
    public String getCategory()
    public String getVariant()
    
    public Double getAverage()
    public Double getMinimum()
    public Double getMaximum()
    public Double getStdDev()
    public Integer getSampleCount()
}
```

### MetricComparison

```java
public class MetricComparison {
    public String getMetricName()
    public String getUnit()
    public String getCategory()
    
    public String getJava17Value()           // "12.5 (±0.3)"
    public String getJava21TradValue()       // "11.8 (±0.2)"
    public String getJava21VirtualValue()    // "10.2 (±0.4)"
    
    public Double getDelta17To21Trad()       // -5.6%
    public Double getDelta17To21Virtual()    // -18.4%
}
```

### ResultAggregator

```java
public class ResultAggregator {
    public AggregationResult aggregate(
        String baseDir, String variant, Integer runNumber)
        throws Exception
    
    public Map<String, List<AggregatedMetric>> aggregateMultipleRuns(
        String baseDir, String variant, List<Integer> runNumbers)
        throws Exception
    
    public Map<String, List<MetricComparison>> generateComparisons(
        List<AggregationResult> variantResults)
    
    public String generateFormattedComparisons(
        Map<String, List<MetricComparison>> comparisons)
}
```

---

## Integration with Export (Task 58)

The metrics aggregator output is designed to be easily exported:

### JSON Export Structure

```json
{
  "aggregation_metadata": {
    "timestamp": "2024-11-28T10:30:00Z",
    "variants": ["Java 17", "Java 21 Trad", "Java 21 Virtual"],
    "metric_count": 145,
    "categories": ["startup", "latency", "throughput", "memory", "gc", "threading", "blocking", "test_suite", "modernization"]
  },
  "aggregated_metrics": [
    {
      "metric_name": "latency_p95",
      "unit": "ms",
      "category": "latency",
      "java_17": { "average": 45.0, "min": 44.5, "max": 45.5, "std_dev": 0.3 },
      "java_21_trad": { "average": 42.0, "min": 41.5, "max": 42.5, "std_dev": 0.2 },
      "java_21_virtual": { "average": 35.0, "min": 34.5, "max": 35.5, "std_dev": 0.4 }
    }
  ],
  "comparisons": [
    {
      "metric_name": "latency_p95",
      "unit": "ms",
      "java_17": "45.00 (±0.30)",
      "java_21_trad": "42.00 (±0.20)",
      "java_21_virtual": "35.00 (±0.40)",
      "delta_17_to_21_trad": -6.67,
      "delta_17_to_21_virtual": -22.22
    }
  ]
}
```

---

## Performance Characteristics

Typical execution times for a benchmark set with 3 variants, 3 runs each:

| Step | Time | Notes |
|------|------|-------|
| Parsing | 200-500ms | Depends on file sizes |
| Normalization | 50-150ms | Unit conversion, validation |
| Aggregation | 100-300ms | Statistical calculations |
| Comparison | 50-100ms | Table generation |
| **Total** | **400ms - 1.05s** | Per complete aggregation |
| **Full workflow (3 variants × 3 runs)** | **1.2 - 3.2s** | Parallel parsing recommended |

---

## Success Criteria

✅ Aggregator successfully parses all required input files without manual intervention
✅ All metrics are normalized into unified schema with consistent units
✅ Comparison table shows correct delta calculations
✅ Missing data is handled gracefully without breaking aggregation
✅ Variance/std_dev calculations are statistically correct
✅ Output is suitable for downstream export to JSON/CSV
✅ Aggregator completes within reasonable time (<5 minutes for typical run set)

