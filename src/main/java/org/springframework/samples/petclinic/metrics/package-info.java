/**
 * Metrics aggregation framework for benchmark result analysis.
 *
 * <h2>Overview</h2> This package provides comprehensive metrics aggregation and
 * comparison functionality for analyzing benchmark results across multiple Java variants
 * (Java 17, Java 21 Traditional, Java 21 with Virtual Threads).
 *
 * <h2>Architecture</h2>
 *
 * <h3>Data Flow</h3> <pre>
 * Raw Data Sources
 *   ├── JMH JSON (startup, latency, throughput, memory)
 *   ├── JFR Metrics (GC, thread activity, memory allocation)
 *   ├── Test Suite (JUnit XML, JaCoCo coverage)
 *   ├── Load Test (latency percentiles, throughput, error rates)
 *   ├── Actuator Metrics (HTTP, JVM, cache, database)
 *   ├── Blocking Detection (static + runtime analysis)
 *   └── Modernization Metrics (LOC, constructs, virtual thread usage)
 *         ↓
 *     [Parsers] - Extract metrics from each source
 *         ↓
 *   NormalizedMetric (unified schema)
 *         ↓
 *     [MetricsNormalizer] - Handle missing data, unit conversion
 *         ↓
 *   Normalized Metrics List
 *         ↓
 *     [MetricsAggregator] - Calculate statistics across runs
 *         ↓
 *   AggregatedMetric (avg, min, max, stdDev per metric)
 *         ↓
 *     [ComparisonTableGenerator] - Create side-by-side comparisons
 *         ↓
 *   MetricComparison (with deltas and variance)
 *         ↓
 *   Final Output (formatted tables, JSON, CSV)
 * </pre>
 *
 * <h2>Key Components</h2>
 *
 * <h3>Data Models</h3>
 * <ul>
 * <li>{@link NormalizedMetric} - Unified metric schema with variant and run number</li>
 * <li>{@link AggregatedMetric} - Aggregated statistics (avg, min, max, stdDev) per
 * metric</li>
 * <li>{@link MetricComparison} - Side-by-side comparison with deltas and variance</li>
 * <li>{@link AggregationResult} - Container for complete aggregation results</li>
 * </ul>
 *
 * <h3>Parsers (in parser subpackage)</h3>
 * <ul>
 * <li>JmhJsonParser - Extracts JMH benchmark results from JSON</li>
 * <li>JfrMetricsParser - Extracts JFR (Java Flight Recorder) metrics</li>
 * <li>TestSuiteParser - Parses JUnit XML and JaCoCo coverage reports</li>
 * <li>LoadTestParser - Extracts load test metrics (latency, throughput)</li>
 * <li>ActuatorMetricsParser - Parses Spring Boot Actuator metrics</li>
 * <li>BlockingDetectionParser - Extracts blocking analysis results</li>
 * <li>ModernizationMetricsParser - Parses modernization/migration metrics</li>
 * </ul>
 *
 * <h3>Processing Components</h3>
 * <ul>
 * <li>{@link MetricsNormalizer} - Normalizes metrics (missing data, unit conversion)</li>
 * <li>{@link MetricsAggregator} - Calculates statistics across runs</li>
 * <li>{@link ComparisonTableGenerator} - Creates comparison tables with deltas</li>
 * <li>{@link ResultAggregator} - Orchestrates entire aggregation workflow</li>
 * <li>{@link StatisticalUtils} - Statistical calculations and significance testing</li>
 * </ul>
 *
 * <h3>Export Components</h3>
 * <ul>
 * <li>{@link BenchmarkExporter} - Generates JSON, CSV, and summary exports</li>
 * <li>{@link ExportFormatting} - Numeric formatting utilities</li>
 * <li>{@link ExportMetadata} - Metadata container for exports</li>
 * <li>{@link ManualMetricsSection} - Manual metrics (cost, effort, instances)</li>
 * </ul>
 *
 * <h2>Export Formats</h2>
 *
 * Three output files are generated:
 * <ul>
 * <li><b>benchmark-results.json</b> - Complete structured dataset with all metrics,
 * variants, runs, and aggregated statistics</li>
 * <li><b>benchmark-results.csv</b> - Flat spreadsheet format with one row per metric, all
 * variants in columns</li>
 * <li><b>benchmark-summary.json</b> - Executive summary with 15-20 key metrics suitable
 * for leadership review</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create aggregator
 * ResultAggregator aggregator = new ResultAggregator();
 *
 * // Aggregate single run
 * AggregationResult java17Result = aggregator.aggregate(
 *     "/path/to/results/java17", "Java 17", 1);
 *
 * AggregationResult java21TradResult = aggregator.aggregate(
 *     "/path/to/results/java21-trad", "Java 21 Trad", 1);
 *
 * AggregationResult java21VirtualResult = aggregator.aggregate(
 *     "/path/to/results/java21-virtual", "Java 21 Virtual", 1);
 *
 * // Generate comparisons across variants
 * List<AggregationResult> results = Arrays.asList(
 *     java17Result, java21TradResult, java21VirtualResult);
 * Map<String, List<MetricComparison>> comparisons =
 *     aggregator.generateComparisons(results);
 *
 * // Generate formatted output
 * String formattedTable = aggregator.generateFormattedComparisons(comparisons);
 * System.out.println(formattedTable);
 * }</pre>
 *
 * <h2>Normalization Process</h2>
 *
 * <h3>Common Schema</h3> All metrics are normalized to:
 * <ul>
 * <li>metric_name: String identifier</li>
 * <li>value: Double numeric value</li>
 * <li>unit: String (ms, MB, req/sec, %, etc.)</li>
 * <li>variant: String (Java 17, Java 21 Trad, Java 21 Virtual)</li>
 * <li>run_number: Integer run identifier</li>
 * <li>category: String (startup, latency, throughput, memory, gc, threading, blocking,
 * test_suite, modernization)</li>
 * <li>data_source: String (jmh, jfr, test_suite, load_test, actuator, blocking,
 * modernization)</li>
 * </ul>
 *
 * <h3>Missing Data Handling</h3>
 * <ul>
 * <li>Metrics with null/NaN values are either skipped or marked as "Not Captured"</li>
 * <li>Aggregation gracefully handles missing values from some runs</li>
 * <li>Comparison tables show "Not Captured" when data unavailable for a variant</li>
 * </ul>
 *
 * <h2>Aggregation Process</h2>
 *
 * For each unique (metric_name, variant, unit) combination:
 * <ul>
 * <li><b>Average</b>: Mean of all values across runs</li>
 * <li><b>Minimum</b>: Smallest value observed</li>
 * <li><b>Maximum</b>: Largest value observed</li>
 * <li><b>Std Dev</b>: Standard deviation (sqrt of sum of squared differences / n-1)</li>
 * </ul>
 *
 * <h2>Comparison Format</h2>
 *
 * Side-by-side comparison table: <pre>
 * | Metric | Unit | Java 17 | Java 21 Trad | Java 21 Virtual | Delta 17→21T (%) | Delta 17→21V (%) |
 * |--------|------|---------|--------------|-----------------|------------------|------------------|
 * | latency_p50 | ms | 12.5 (±0.3) | 11.8 (±0.2) | 10.2 (±0.4) | -5.6% | -18.4% |
 * | throughput | req/sec | 950 (±45) | 975 (±40) | 1280 (±60) | +2.6% | +34.7% |
 * </pre>
 *
 * Variance is shown in parentheses as ±stdDev Deltas calculated as: ((variant_value -
 * baseline_value) / baseline_value) * 100
 *
 * <h2>Statistical Significance</h2>
 *
 * Uses variance-based thresholds (from METRICS-INTERPRETATION-GUIDE.md):
 * <ul>
 * <li>Latency (low variance): 3% threshold</li>
 * <li>Throughput (medium variance): 8% threshold</li>
 * <li>GC metrics (high variance): 25% threshold</li>
 * <li>Memory: 12% threshold</li>
 * <li>Startup: 5% threshold</li>
 * </ul>
 *
 * <h2>Category Organization</h2>
 *
 * Metrics are categorized for organized reporting:
 * <ul>
 * <li><b>startup</b>: Application initialization time (cold/warm)</li>
 * <li><b>latency</b>: Request-response latency, percentiles</li>
 * <li><b>throughput</b>: Requests per second, transaction rate</li>
 * <li><b>memory</b>: Heap usage, allocation rates</li>
 * <li><b>gc</b>: GC pause times, collection counts</li>
 * <li><b>threading</b>: Thread counts, virtual thread usage</li>
 * <li><b>blocking</b>: Blocking event frequency, duration</li>
 * <li><b>test_suite</b>: Test pass rates, coverage percentages</li>
 * <li><b>modernization</b>: Virtual thread adoption, LOC changes</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 *
 * Typical execution time:
 * <ul>
 * <li>Parsing: 100-500ms (depends on file sizes)</li>
 * <li>Normalization: 50-200ms</li>
 * <li>Aggregation: 100-300ms</li>
 * <li>Comparison generation: 50-150ms</li>
 * <li><b>Total: &lt;5 minutes for typical benchmark run set</b></li>
 * </ul>
 *
 * @since 4.0.0
 */
package org.springframework.samples.petclinic.metrics;
