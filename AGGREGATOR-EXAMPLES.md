# Metrics Aggregator - Usage Examples

## Command-Line Interface

### Basic Usage

```bash
# Simple single run comparison
java AggregatorCli \
  /path/to/benchmark/results \
  "Java 17,Java 21 Trad,Java 21 Virtual" \
  "1"

# Multiple runs per variant
java AggregatorCli \
  /path/to/benchmark/results \
  "Java 17,Java 21 Trad,Java 21 Virtual" \
  "1,2,3"
```

### Example Output

```
=== Metrics Aggregator ===
Results directory: /benchmark/results
Variants: Java 17, Java 21 Trad, Java 21 Virtual
Runs: 1

Processing variant: Java 17
  ✓ Aggregated 145 metrics
Processing variant: Java 21 Trad
  ✓ Aggregated 145 metrics
Processing variant: Java 21 Virtual
  ✓ Aggregated 142 metrics

Generating comparison tables...

=== LATENCY ===
Metric                                   | Unit       | Java 17              | Java 21 Trad         | Java 21 Virtual      | Delta 17→21T    | Delta 17→21V
------------------------------------------|------------|----------------------|----------------------|----------------------|-----------------|------------------
latency_p50                              | ms         | 12.50 (±0.30)        | 11.80 (±0.20)        | 10.20 (±0.40)        | -5.60%          | -18.40%
latency_p95                              | ms         | 45.00 (±2.00)        | 42.00 (±1.50)        | 35.00 (±3.00)        | -6.67%          | -22.22%
latency_p99                              | ms         | 120.00 (±5.00)       | 115.00 (±4.00)       | 95.00 (±6.00)        | -4.17%          | -20.83%

=== THROUGHPUT ===
throughput_rps                           | req/sec    | 950.00 (±45.00)      | 975.00 (±40.00)      | 1280.00 (±60.00)     | +2.63%          | +34.74%
http_request_count                       | count      | 5000.00 (±100.00)    | 5100.00 (±80.00)     | 6800.00 (±120.00)    | +2.00%          | +36.00%

=== STARTUP ===
applicationStartup                       | ms         | 2850.00 (±85.00)     | 2780.00 (±70.00)     | 2650.00 (±90.00)     | -2.46%          | -7.02%

=== MEMORY ===
memory_heap_used                         | MB         | 256.00 (±12.00)      | 245.00 (±10.00)      | 240.00 (±15.00)      | -4.30%          | -6.25%
memory_allocation_rate                   | MB/sec     | 128.00 (±8.00)       | 125.00 (±6.00)       | 110.00 (±10.00)      | -2.34%          | -14.06%

=== GC ===
gc_pause_time_avg                        | ms         | 25.50 (±3.00)        | 24.00 (±2.50)        | 18.00 (±4.00)        | -5.88%          | -29.41%
gc_pause_time_max                        | ms         | 45.00 (±8.00)        | 43.00 (±6.00)        | 32.00 (±10.00)       | -4.44%          | -28.89%
gc_collection_count                      | count      | 45.00 (±2.00)        | 42.00 (±1.50)        | 35.00 (±3.00)        | -6.67%          | -22.22%

=== THREADING ===
thread_count                             | count      | 42.00 (±2.00)        | 43.00 (±2.00)        | 52.00 (±3.00)        | +2.38%          | +23.81%
virtual_thread_count                     | count      | 0.00 (±0.00)         | 0.00 (±0.00)         | 3200.00 (±150.00)    | N/A             | N/A

=== TEST_SUITE ===
test_count                               | count      | 850.00 (±0.00)       | 850.00 (±0.00)       | 850.00 (±0.00)       | +0.00%          | +0.00%
test_pass_rate                           | %          | 100.00 (±0.00)       | 100.00 (±0.00)       | 100.00 (±0.00)       | +0.00%          | +0.00%
code_coverage_lines                      | %          | 82.50 (±0.50)        | 82.50 (±0.50)        | 82.50 (±0.50)        | +0.00%          | +0.00%

=== BLOCKING ===
runtime_blocking_event_count             | count      | 1250.00 (±50.00)     | 1200.00 (±45.00)     | 450.00 (±60.00)      | -4.00%          | -64.00%
runtime_avg_blocking_duration            | ms         | 8.50 (±1.00)         | 8.20 (±0.80)         | 2.50 (±1.50)         | -3.53%          | -70.59%
static_blocking_calls                    | count      | 12.00 (±0.00)        | 12.00 (±0.00)        | 8.00 (±0.00)         | +0.00%          | -33.33%

=== MODERNIZATION ===
modernization_total_loc                  | lines      | 8500.00 (±0.00)      | 8500.00 (±0.00)      | 8620.00 (±0.00)      | +0.00%          | +1.41%
modernization_modified_loc                | lines      | 0.00 (±0.00)         | 0.00 (±0.00)         | 220.00 (±0.00)       | N/A             | N/A
virtual_thread_construct_count           | count      | 0.00 (±0.00)         | 0.00 (±0.00)         | 12.00 (±0.00)        | N/A             | N/A

Aggregation completed in 1234ms
```

---

## Programmatic API Examples

### Example 1: Basic Aggregation

```java
import org.springframework.samples.petclinic.metrics.*;

public class AggregatorExample {
    public static void main(String[] args) throws Exception {
        // Create aggregator
        ResultAggregator agg = new ResultAggregator();
        
        // Parse a single variant/run
        AggregationResult result = agg.aggregate(
            "/data/java21-virtual/run1",
            "Java 21 Virtual",
            1
        );
        
        // Access normalized metrics
        System.out.println("Raw metrics: " + 
            result.getNormalizedMetrics().size());
        
        // Access aggregated metrics
        System.out.println("Aggregated metrics: " + 
            result.getAggregatedMetrics().size());
        
        // Iterate over categories
        for (Map.Entry<String, List<AggregatedMetric>> entry : 
             result.getAggregatedByCategory().entrySet()) {
            System.out.println("\n" + entry.getKey() + ":");
            for (AggregatedMetric m : entry.getValue()) {
                System.out.printf(
                    "  %s: %.2f ± %.2f %s\n",
                    m.getMetricName(),
                    m.getAverage(),
                    m.getStdDev(),
                    m.getUnit()
                );
            }
        }
    }
}
```

### Example 2: Multi-Variant Comparison

```java
import org.springframework.samples.petclinic.metrics.*;
import java.util.*;

public class ComparisonExample {
    public static void main(String[] args) throws Exception {
        ResultAggregator agg = new ResultAggregator();
        
        // Aggregate each variant
        AggregationResult java17 = agg.aggregate(
            "/data/java17", "Java 17", 1);
        AggregationResult java21Trad = agg.aggregate(
            "/data/java21-trad", "Java 21 Trad", 1);
        AggregationResult java21Virtual = agg.aggregate(
            "/data/java21-virtual", "Java 21 Virtual", 1);
        
        // Generate comparisons
        List<AggregationResult> results = Arrays.asList(
            java17, java21Trad, java21Virtual);
        
        Map<String, List<MetricComparison>> comparisons =
            agg.generateComparisons(results);
        
        // Generate formatted output
        String output = agg.generateFormattedComparisons(comparisons);
        System.out.println(output);
        
        // Or process programmatically
        for (Map.Entry<String, List<MetricComparison>> cat : 
             comparisons.entrySet()) {
            System.out.println("\n=== " + cat.getKey().toUpperCase() + " ===");
            
            for (MetricComparison comp : cat.getValue()) {
                System.out.printf(
                    "%s: J17=%-20s J21T=%-20s J21V=%-20s\n",
                    comp.getMetricName(),
                    comp.getJava17Value(),
                    comp.getJava21TradValue(),
                    comp.getJava21VirtualValue()
                );
                
                if (comp.getDelta17To21Virtual() != null) {
                    System.out.printf(
                        "  → Java 21 Virtual: %+.1f%%\n",
                        comp.getDelta17To21Virtual()
                    );
                }
            }
        }
    }
}
```

### Example 3: Multi-Run Aggregation

```java
import org.springframework.samples.petclinic.metrics.*;
import java.util.*;

public class MultiRunExample {
    public static void main(String[] args) throws Exception {
        ResultAggregator agg = new ResultAggregator();
        
        // Aggregate 5 runs of Java 17
        Map<String, List<AggregatedMetric>> java17Results =
            agg.aggregateMultipleRuns(
                "/data/java17",
                "Java 17",
                Arrays.asList(1, 2, 3, 4, 5)
            );
        
        // Aggregate 5 runs of Java 21 Virtual
        Map<String, List<AggregatedMetric>> java21VirtResults =
            agg.aggregateMultipleRuns(
                "/data/java21-virtual",
                "Java 21 Virtual",
                Arrays.asList(1, 2, 3, 4, 5)
            );
        
        // Analyze by category
        System.out.println("Java 17 Latency Metrics:");
        for (AggregatedMetric m : java17Results.get("latency")) {
            System.out.printf(
                "  %s: %.2f ms (CV: %.1f%%)\n",
                m.getMetricName(),
                m.getAverage(),
                (m.getStdDev() / m.getAverage()) * 100
            );
        }
        
        System.out.println("\nJava 21 Virtual Latency Metrics:");
        for (AggregatedMetric m : java21VirtResults.get("latency")) {
            System.out.printf(
                "  %s: %.2f ms (CV: %.1f%%)\n",
                m.getMetricName(),
                m.getAverage(),
                (m.getStdDev() / m.getAverage()) * 100
            );
        }
    }
}
```

### Example 4: Statistical Analysis

```java
import org.springframework.samples.petclinic.metrics.*;

public class StatisticalExample {
    public static void main(String[] args) throws Exception {
        ResultAggregator agg = new ResultAggregator();
        AggregationResult result = agg.aggregate(
            "/data/java21-virtual", "Java 21 Virtual", 1);
        
        // Analyze variance
        MetricsAggregator aggregator = new MetricsAggregator();
        for (AggregatedMetric metric : result.getAggregatedMetrics()) {
            double cv = aggregator
                .calculateCoefficientOfVariation(metric);
            boolean hasVariance = 
                aggregator.hasSignificantVariance(metric);
            
            System.out.printf(
                "%s: %.2f (CV: %.1f%%) - %s\n",
                metric.getMetricName(),
                metric.getAverage(),
                cv,
                hasVariance ? "HIGH VARIANCE" : "Low variance"
            );
        }
        
        // Check significance
        AggregatedMetric latency = result.getAggregatedMetrics().stream()
            .filter(m -> m.getMetricName().equals("latency_p95"))
            .findFirst().orElse(null);
        
        if (latency != null) {
            double baseline = latency.getAverage();
            double improved = baseline * 0.92; // 8% improvement
            
            boolean isSignificant = 
                StatisticalUtils.isSignificantDifference(
                    baseline,
                    improved,
                    "latency",
                    latency.getStdDev()
                );
            
            System.out.println("8% improvement is " +
                (isSignificant ? "" : "NOT ") +
                "statistically significant");
            
            // Calculate confidence interval
            double margin = 
                StatisticalUtils.calculateConfidenceIntervalMargin(
                    latency.getStdDev(),
                    3  // 3 runs
                );
            
            System.out.printf("95%% CI: %.2f ± %.2f ms\n",
                baseline, margin);
        }
    }
}
```

### Example 5: Custom Analysis

```java
import org.springframework.samples.petclinic.metrics.*;
import java.util.*;

public class CustomAnalysisExample {
    public static void main(String[] args) throws Exception {
        ResultAggregator agg = new ResultAggregator();
        
        AggregationResult java17 = agg.aggregate(
            "/data/java17", "Java 17", 1);
        AggregationResult java21Virtual = agg.aggregate(
            "/data/java21-virtual", "Java 21 Virtual", 1);
        
        // Find biggest improvements
        List<AggregatedMetric> java17Agg = java17.getAggregatedMetrics();
        List<AggregatedMetric> java21VirtAgg = java21Virtual.getAggregatedMetrics();
        
        Map<String, Double> improvements = new HashMap<>();
        
        for (AggregatedMetric m1 : java17Agg) {
            for (AggregatedMetric m2 : java21VirtAgg) {
                if (m1.getMetricName().equals(m2.getMetricName())) {
                    String direction = 
                        StatisticalUtils.getImprovementDirection(
                            m1.getMetricName());
                    
                    double delta = 
                        ((m2.getAverage() - m1.getAverage()) 
                        / m1.getAverage()) * 100;
                    
                    // Invert for latency (lower is better)
                    if (direction.equals("lower_is_better")) {
                        delta = -delta;
                    }
                    
                    improvements.put(m1.getMetricName(), delta);
                }
            }
        }
        
        // Display top 10 improvements
        improvements.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(10)
            .forEach(e -> 
                System.out.printf("%s: %+.1f%%\n", e.getKey(), e.getValue())
            );
    }
}
```

---

## Data Structure Examples

### NormalizedMetric

```java
// Created by parsers, represents raw extracted metric

NormalizedMetric metric = new NormalizedMetric(
    "latency_p95",              // metricName
    45.2,                       // value
    "ms",                       // unit
    "Java 21 Virtual",          // variant
    1,                          // runNumber
    "latency",                  // category
    "load_test"                 // dataSource
);

// Output
// NormalizedMetric{
//   metricName='latency_p95', value=45.2, unit='ms',
//   variant='Java 21 Virtual', runNumber=1, 
//   category='latency', dataSource='load_test', timestamp=null
// }
```

### AggregatedMetric

```java
// Created by aggregator, statistics across runs

AggregatedMetric stat = new AggregatedMetric(
    "latency_p95", "ms", "latency", "Java 21 Virtual", "load_test");
stat.setAverage(44.8);
stat.setMinimum(43.5);
stat.setMaximum(46.2);
stat.setStdDev(1.2);
stat.setSampleCount(5);

// Output
// AggregatedMetric{
//   metricName='latency_p95', unit='ms', category='latency',
//   variant='Java 21 Virtual', dataSource='load_test',
//   average=44.8, minimum=43.5, maximum=46.2,
//   stdDev=1.2, sampleCount=5
// }
```

### MetricComparison

```java
// Created by comparison generator, side-by-side comparison

MetricComparison comp = new MetricComparison(
    "latency_p95", "ms", "latency");

comp.setJava17Value("45.00 (±2.00)");
comp.setJava21TradValue("42.00 (±1.50)");
comp.setJava21VirtualValue("35.00 (±3.00)");

comp.setJava17Numeric(45.0);
comp.setJava21TradNumeric(42.0);
comp.setJava21VirtualNumeric(35.0);

comp.setDelta17To21Trad(-6.67);
comp.setDelta17To21Virtual(-22.22);

// Output
// MetricComparison{
//   metricName='latency_p95', unit='ms', category='latency',
//   java17Value='45.00 (±2.00)', java21TradValue='42.00 (±1.50)',
//   java21VirtualValue='35.00 (±3.00)',
//   delta17To21Trad=-6.67, delta17To21Virtual=-22.22
// }
```

---

## Integration with Export (Task 4)

### Export to JSON

```json
{
  "aggregation_metadata": {
    "timestamp": "2024-11-28T10:30:00Z",
    "variants": ["Java 17", "Java 21 Trad", "Java 21 Virtual"],
    "metric_count": 145
  },
  "comparisons": [
    {
      "metric_name": "latency_p95",
      "unit": "ms",
      "category": "latency",
      "java_17": {
        "value": 45.00,
        "std_dev": 2.00,
        "display": "45.00 (±2.00)"
      },
      "java_21_trad": {
        "value": 42.00,
        "std_dev": 1.50,
        "display": "42.00 (±1.50)"
      },
      "java_21_virtual": {
        "value": 35.00,
        "std_dev": 3.00,
        "display": "35.00 (±3.00)"
      },
      "delta_17_to_21_trad_percent": -6.67,
      "delta_17_to_21_virtual_percent": -22.22
    }
  ]
}
```

### Export to CSV

```csv
Metric,Unit,Category,Java 17,Java 17 StdDev,Java 21 Trad,Java 21 Trad StdDev,Java 21 Virtual,Java 21 Virtual StdDev,Delta 17→21T %,Delta 17→21V %
latency_p95,ms,latency,45.00,2.00,42.00,1.50,35.00,3.00,-6.67,-22.22
latency_p99,ms,latency,120.00,5.00,115.00,4.00,95.00,6.00,-4.17,-20.83
throughput_rps,req/sec,throughput,950.00,45.00,975.00,40.00,1280.00,60.00,2.63,34.74
memory_heap_used,MB,memory,256.00,12.00,245.00,10.00,240.00,15.00,-4.30,-6.25
```

