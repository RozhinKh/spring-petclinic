# Benchmark Export Implementation

## Overview

This implementation provides comprehensive export functionality for benchmark results into machine-readable formats suitable for analysis by all stakeholders. Three output files are generated:

1. **benchmark-results.json** - Complete structured dataset with all metrics
2. **benchmark-results.csv** - Flat tabular format for Excel/Sheets
3. **benchmark-summary.json** - Executive summary with key metrics only

## Architecture

### Core Classes

#### 1. ExportFormatting
Utility class for consistent numeric formatting across all exports:
- `formatLatency(Double)` - 2 decimal places for ms/sec/us/ns
- `formatPercentage(Double)` - 1 decimal place for %
- `formatStdDev(Double)` - 1 decimal place for variance
- `formatByUnit(Double, String)` - Intelligent formatting based on unit
- `formatMemory(Double)` - 2 decimals for KB/MB/GB
- `formatThroughput(Double)` - Integer rounding for ops/sec
- `toCsvString(String)` - Escapes quotes and commas for CSV
- `toCsvDouble(Double, String)` - Formats number for CSV export

**Example:**
```java
ExportFormatting.formatLatency(45.2567);     // Returns 45.26
ExportFormatting.formatPercentage(12.567);   // Returns 12.6
ExportFormatting.formatByUnit(512.5, "MB");  // Returns 512.50
```

#### 2. ManualMetricsSection
Container for metrics not automatically captured, with support for formulas:

**Nested Classes:**
- `TestPassRate` - Test suite pass/fail counts and percentage
  - `totalTests`, `passedTests`, `failedTests`
  - Auto-calculated `passPercentage`

- `CloudCostAnalysis` - Cost per request calculations
  - `computeHourlyCost` ($/hour) - Editable
  - `throughputOpsPerSec` - From benchmark data
  - Auto-calculated `costPerRequest`
  - Formula: `$X.XX/request = ($cost/hour) / (ops/sec × 3600)`

- `InstancesRequired` - Infrastructure sizing
  - `peakLoadOpsPerSec` - From load test
  - `perInstanceCapacity` (ops/sec/instance) - Editable assumption
  - Auto-calculated `instancesRequired`
  - Formula: `N instances = peak_load ÷ per_instance_capacity`

- `EffortEstimate` - Modernization effort
  - `locRefactored` - Lines of code refactored
  - `developerHoursSaved` - Estimated hours saved per month
  - `assumptions_map` - Structured assumptions dict

**Example:**
```java
ManualMetricsSection.CloudCostAnalysis costAnalysis = 
    new ManualMetricsSection.CloudCostAnalysis(2.50, 8880.17);
// costPerRequest auto-calculated: 2.50 / (8880.17 × 3600) = 7.82e-8
// formula: "$0.0000 per request = ($2.50/hour) / (8880 ops/sec × 3600)"
```

#### 3. ExportMetadata
Metadata container for exports:
- `timestamp` - ISO 8601 export time
- `jdkVersions` - Map<String, String> variant → JDK version
- `toolVersions` - Map<String, String> tool → version
- `environmentInfo` - OS, CPU, memory, architecture
- `benchmarkDurationMinutes` - Total benchmark time

**Example:**
```java
ExportMetadata metadata = new ExportMetadata();
metadata.addJdkVersion("Java 17", "17.0.6");
metadata.addJdkVersion("Java 21 Virtual", "21.0.1");
metadata.addToolVersion("JMH", "1.35");
metadata.addEnvironmentInfo("OS", "Linux");
```

#### 4. BenchmarkExporter
Main exporter orchestrating JSON, CSV, and summary generation:

**Public Methods:**
- `export(List<AggregatedMetric>, String outputDir, ManualMetricsSection, ExportMetadata)` - Main export method
  - Generates all three output files
  - Organizes metrics by category
  - Includes metadata and statistics summaries

**Private Export Methods:**
- `exportFullJson()` - Complete dataset with all metrics and variants
- `exportCsv()` - Flat spreadsheet format with delta calculations
- `exportSummaryJson()` - Executive summary with 15-20 key metrics per category

**Data Organization:**
- Metrics grouped by 9 categories: startup, latency, throughput, memory, gc, threading, blocking, test_suite, modernization
- Variants for each metric (Java 17, Java 21 Trad, Java 21 Virtual)
- Statistics: average, min, max, std_dev, sample_count, coefficient_of_variation
- Deltas calculated between all variant pairs

### Integration with ResultAggregator

#### New Methods in ResultAggregator:
```java
// Export with custom metadata
void exportResults(List<AggregatedMetric> aggregatedMetrics, 
                   String outputDir,
                   ManualMetricsSection manualMetrics, 
                   ExportMetadata metadata) throws IOException

// Export with default metadata
void exportResults(List<AggregatedMetric> aggregatedMetrics, 
                   String outputDir) throws IOException
```

#### Updated AggregatorCli:
```bash
Usage: AggregatorCli <results_dir> <variants> <runs> [output_dir]

Example:
  AggregatorCli /benchmark/results 'Java 17,Java 21 Trad,Java 21 Virtual' 1,2,3 /exports
```

Output directory defaults to `<results_dir>/exports` if not specified.

## Output Files

### 1. benchmark-results.json (Complete Dataset)

**Structure:**
```json
{
  "metadata": {
    "timestamp": "ISO-8601",
    "jdk_versions": { "variant": "version" },
    "tool_versions": { "tool": "version" },
    "environment_info": { "key": "value" },
    "benchmark_duration_minutes": 45
  },
  "metrics": {
    "category": [
      {
        "metric_name": "string",
        "unit": "string",
        "data_source": "jmh|jfr|test_suite|load_test|actuator|blocking|modernization",
        "variants": [
          {
            "name": "Java 17",
            "average": 2450.50,
            "min": 2420.00,
            "max": 2480.00,
            "std_dev": 18.50,
            "sample_count": 10,
            "coefficient_of_variation": 0.0076
          }
        ],
        "statistics": { "average", "minimum", "maximum", "std_dev", "sample_count" }
      }
    ]
  },
  "statistics": {
    "total_metrics": 11,
    "variants": 3,
    "categories": 9,
    "data_sources": 7
  },
  "data_sources": {
    "jmh": 1,
    "jfr": 4,
    "test_suite": 2,
    ...
  }
}
```

**Validation:** Conforms to `src/main/resources/benchmark-export-schema.json`

**Precision:**
- Latencies: 2 decimal places
- Percentages: 1 decimal place
- Throughput: integer (rounded)
- Memory: 2 decimal places

### 2. benchmark-results.csv (Spreadsheet Format)

**Columns:**
```
Metric, Unit, Category, Data Source,
Java 17, Java 17 StdDev,
Java 21 Trad, Java 21 Trad StdDev,
Java 21 Virtual, Java 21 Virtual StdDev,
Delta Java 17→Java 21 Trad (%),
Delta Java 17→Java 21 Virtual (%),
Delta Java 21 Trad→Java 21 Virtual (%),
Notes
```

**Delta Calculation:**
```
Delta (%) = ((variant_value - baseline_value) / baseline_value) * 100
```

**Format:**
- One row per metric
- All variant values in same row
- Standard deviations follow each variant average
- All deltas calculated automatically
- Negative values indicate improvement for latency, positive for throughput

**Features:**
- Compatible with Excel, Google Sheets, Tableau
- Proper CSV escaping for special characters
- Empty cells for missing data
- Scientific notation for very large/small numbers

### 3. benchmark-summary.json (Executive Summary)

**Structure:**
```json
{
  "metadata": { ... },
  "key_metrics": {
    "startup": [
      { "metric_name", "unit", "variant", "average", "std_dev" }
    ],
    "latency": [
      { "metric_name": "P95 Latency", ... },
      { "metric_name": "P99 Latency", ... }
    ],
    "throughput": [ ... ],
    "memory": [ ... ],
    "gc": [ ... ],
    "threading": [ ... ],
    "blocking": [ ... ],
    "test_suite": [ ... ],
    "modernization": [ ... ]
  },
  "manual_metrics": {
    "test_pass_rate": {
      "total_tests": 520,
      "passed_tests": 520,
      "failed_tests": 0,
      "pass_percentage": 100.0
    },
    "cloud_cost_analysis": {
      "compute_hourly_cost": 2.50,
      "throughput_ops_per_sec": 8880.17,
      "requests_per_hour": 31968600.0,
      "cost_per_request": 7.816246e-8,
      "formula": "Human-readable formula"
    },
    "instances_required": {
      "peak_load_ops_per_sec": 12500.0,
      "per_instance_capacity": 10000.0,
      "instances_required": 2.0,
      "formula": "Human-readable formula"
    },
    "effort_estimate": {
      "loc_refactored": 2500,
      "developer_hours_saved": 40.0,
      "assumptions": { "key": "value" }
    }
  }
}
```

**Key Metrics Selected:**
- Startup: "Startup Time"
- Latency: "P95 Latency", "P99 Latency", "Average Latency"
- Throughput: "Throughput", "Throughput (ops/sec)"
- Memory: "Heap Memory", "Memory Footprint", "Max Memory"
- GC: "GC Pause Time", "GC Time Ratio", "Young GC Pause"
- Threading: "Thread Count", "Active Thread Count"
- Blocking: "Blocking Events", "Blocking Call Count", "Blocking Time Ratio"
- Test Suite: "Test Count", "Code Coverage %"
- Modernization: "Lines of Code", "Virtual Thread Usage Points"

## Usage Examples

### Basic Export with Default Metadata
```java
ResultAggregator aggregator = new ResultAggregator();
List<AggregatedMetric> metrics = // ... aggregated metrics
aggregator.exportResults(metrics, "/output/path");
```

### Export with Custom Metadata and Manual Metrics
```java
// Create metadata
ExportMetadata metadata = new ExportMetadata();
metadata.addJdkVersion("Java 17", "17.0.6");
metadata.addJdkVersion("Java 21 Virtual", "21.0.1");
metadata.addToolVersion("JMH", "1.35");
metadata.addEnvironmentInfo("OS", "Linux");
metadata.setBenchmarkDurationMinutes(45L);

// Create manual metrics
ManualMetricsSection manualMetrics = new ManualMetricsSection();

ManualMetricsSection.TestPassRate passRate = 
    new ManualMetricsSection.TestPassRate(520, 520, 0);
manualMetrics.setTestPassRate(passRate);

ManualMetricsSection.CloudCostAnalysis costAnalysis = 
    new ManualMetricsSection.CloudCostAnalysis(2.50, 8880.17);
manualMetrics.setCloudCostAnalysis(costAnalysis);

ManualMetricsSection.InstancesRequired instances = 
    new ManualMetricsSection.InstancesRequired(12500.0, 10000.0);
manualMetrics.setInstancesRequired(instances);

ManualMetricsSection.EffortEstimate effort = 
    new ManualMetricsSection.EffortEstimate(2500, 40.0);
effort.addAssumption("refactoring_rate_loc_per_hour", 100);
effort.addAssumption("maintenance_hours_per_month_before", 20);
manualMetrics.setEffortEstimate(effort);

// Export
aggregator.exportResults(metrics, "/output/path", manualMetrics, metadata);
```

### Via Command Line
```bash
# Basic usage
java AggregatorCli /benchmark/results 'Java 17,Java 21 Trad,Java 21 Virtual' 1,2,3

# With custom output directory
java AggregatorCli /benchmark/results 'Java 17,Java 21 Trad,Java 21 Virtual' 1,2,3 /exports
```

## Data Quality

### Numeric Precision
- Latencies: 2 decimals (ms, us, ns, sec)
- Percentages: 1 decimal (%)
- Standard deviations: 1 decimal
- Throughput: integers (ops/sec, requests/sec)
- Memory: 2 decimals (MB, GB, KB)
- Coefficient of variation: auto-calculated as stdDev / mean

### Missing Data Handling
- Null values for unavailable metrics
- "Not Captured" strings in display formats
- Empty CSV cells for missing data
- Continues processing if some sources unavailable

### Validation
- JSON files validate against schema
- CSV imports without type errors
- All numeric fields are numbers (not quoted)
- Plotly/Tableau compatible data types

## Compatibility

### Excel/Google Sheets
- RFC 4180 compliant CSV
- Proper quote escaping
- Single row per metric
- Column headers match standards

### Tableau
- Numeric columns stay numeric
- Category and variant columns support grouping
- Time-series data with run_number support
- Delta columns support calculation and visualization

### Plotly
- JSON structure directly compatible
- Hierarchical organization supports nested charts
- Variance data (std_dev) for error bars
- Multiple variants support grouped/overlay charts

### Pandas/Jupyter
```python
import json
import pandas as pd

# Load complete results
with open('benchmark-results.json') as f:
    data = json.load(f)

# Load summary
summary = pd.read_json('benchmark-summary.json')

# Load CSV
df = pd.read_csv('benchmark-results.csv')
```

## Performance

Typical export time:
- ~100 metrics: <1 second
- ~1000 metrics: <5 seconds
- I/O bound for large files

File sizes:
- Full JSON: ~500 KB per 100 metrics
- CSV: ~200 KB per 100 metrics
- Summary JSON: ~50 KB

## Future Extensions

1. **Additional export formats:** Parquet, Arrow, Excel with formatting
2. **Chart generation:** Automatic chart creation from summary data
3. **Report generation:** PDF/HTML reports with embedded charts
4. **Database export:** Direct writing to databases for BI tools
5. **Custom aggregations:** User-defined metric groupings and calculations

## Files Created

- `src/main/java/org/springframework/samples/petclinic/metrics/ExportFormatting.java` - Number formatting
- `src/main/java/org/springframework/samples/petclinic/metrics/ManualMetricsSection.java` - Manual metric containers
- `src/main/java/org/springframework/samples/petclinic/metrics/ExportMetadata.java` - Export metadata
- `src/main/java/org/springframework/samples/petclinic/metrics/BenchmarkExporter.java` - Main exporter
- `src/main/resources/benchmark-export-schema.json` - JSON schema for validation
- `benchmark-results.json` - Sample complete dataset
- `benchmark-results.csv` - Sample flat spreadsheet
- `benchmark-summary.json` - Sample executive summary
