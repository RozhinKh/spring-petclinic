/**
 * Parser implementations for various benchmark data sources.
 *
 * <h2>Overview</h2> This package contains parsers for extracting metrics from different
 * benchmark and monitoring tools. Each parser converts tool-specific output formats into
 * the unified {@link org.springframework.samples.petclinic.metrics.NormalizedMetric}
 * schema.
 *
 * <h2>Available Parsers</h2>
 *
 * <h3>JmhJsonParser</h3> Parses Java Microbenchmark Harness (JMH) output in JSON format.
 * <ul>
 * <li><b>Input format</b>: JMH JSON file (produced with -rf json -rff output.json)</li>
 * <li><b>Metrics extracted</b>:</li>
 * <ul>
 * <li>Startup: cold/warm startup times</li>
 * <li>Latency: average time per operation</li>
 * <li>Throughput: operations per second</li>
 * <li>Memory: heap usage, allocation rates</li>
 * </ul>
 * <li><b>Example fields</b>: primaryMetric.score, primaryMetric.scoreUnit,
 * secondaryMetrics</li>
 * </ul>
 *
 * <h3>JfrMetricsParser</h3> Parses Java Flight Recorder (JFR) metrics exported as JSON.
 * <ul>
 * <li><b>Input format</b>: JFR parsed metrics JSON file</li>
 * <li><b>Metrics extracted</b>:</li>
 * <ul>
 * <li>GC metrics: pause times, collection counts, total pause duration</li>
 * <li>Thread metrics: thread count, peak count, virtual thread count</li>
 * <li>Memory metrics: heap used/max, allocation rate</li>
 * <li>Blocking metrics: blocking events, total duration</li>
 * </ul>
 * <li><b>Example fields</b>: gc.pause_time_avg_ms, threads.count,
 * memory.heap_used_mb</li>
 * </ul>
 *
 * <h3>TestSuiteParser</h3> Parses test suite results from JUnit XML and JaCoCo coverage
 * reports.
 * <ul>
 * <li><b>Input format</b>: JUnit XML (junit-report.xml) and JaCoCo XML (report.xml)</li>
 * <li><b>JUnit metrics extracted</b>:</li>
 * <ul>
 * <li>Test counts: total, passed, failed, skipped</li>
 * <li>Pass rate percentage</li>
 * <li>Total execution time</li>
 * </ul>
 * <li><b>JaCoCo metrics extracted</b>:</li>
 * <ul>
 * <li>Line coverage percentage</li>
 * <li>Branch coverage percentage</li>
 * </ul>
 * <li><b>Example XML structure</b>: &lt;testsuite tests="100" failures="0" skipped="0"
 * time="45.5"&gt;</li>
 * </ul>
 *
 * <h3>LoadTestParser</h3> Parses load testing results (Apache JMeter, k6, Gatling, etc.).
 * <ul>
 * <li><b>Input format</b>: Load test results JSON file</li>
 * <li><b>Metrics extracted</b>:</li>
 * <ul>
 * <li>Latency percentiles: P50, P95, P99, P99.9, avg, min, max</li>
 * <li>Throughput: requests per second, total request count</li>
 * <li>Error metrics: error count, error rate percentage</li>
 * </ul>
 * <li><b>Example fields</b>: latency.p50_ms, throughput.requests_per_sec,
 * errors.error_rate_percent</li>
 * </ul>
 *
 * <h3>ActuatorMetricsParser</h3> Parses Spring Boot Actuator metrics endpoint output.
 * <ul>
 * <li><b>Input format</b>: Actuator metrics JSON</li>
 * <li><b>Metrics extracted</b>:</li>
 * <ul>
 * <li>HTTP metrics: request count, response time (avg/max)</li>
 * <li>JVM metrics: memory (heap used/max), GC (time/count), threads</li>
 * <li>Cache metrics: hits, misses, hit rate</li>
 * <li>Database metrics: active connections, pool size, query time</li>
 * </ul>
 * <li><b>Example fields</b>: http.request_count, jvm.memory.heap_used_mb,
 * cache.hit_rate</li>
 * </ul>
 *
 * <h3>BlockingDetectionParser</h3> Parses blocking detection analysis results (static and
 * runtime).
 * <ul>
 * <li><b>Input format</b>: Blocking detection JSON reports</li>
 * <li><b>Static analysis metrics extracted</b>:</li>
 * <ul>
 * <li>Blocking call count</li>
 * <li>Synchronized keyword usage count</li>
 * <li>Lock usage count</li>
 * <li>Locations of blocking operations</li>
 * </ul>
 * <li><b>Runtime analysis metrics extracted</b>:</li>
 * <ul>
 * <li>Blocking event count during execution</li>
 * <li>Blocking frequency (events/sec)</li>
 * <li>Average and max blocking duration</li>
 * <li>Affected thread count</li>
 * </ul>
 * </ul>
 *
 * <h3>ModernizationMetricsParser</h3> Parses modernization and code migration metrics.
 * <ul>
 * <li><b>Input format</b>: Modernization metrics JSON</li>
 * <li><b>Metrics extracted</b>:</li>
 * <ul>
 * <li>Lines of code: total, modified, new</li>
 * <li>Construct counts: synchronized usage, lock usage, atomic operations, concurrent
 * collections</li>
 * <li>Virtual thread adoption: construct count, structured concurrency usage, executor
 * usage, reactive usage</li>
 * <li>Usage locations</li>
 * </ul>
 * </ul>
 *
 * <h2>Parser Usage Pattern</h2>
 *
 * Each parser follows the same pattern: <pre>{@code
 * // Create parser
 * JmhJsonParser parser = new JmhJsonParser();
 *
 * // Parse file
 * List<NormalizedMetric> metrics = parser.parse(
 *     new File("/path/to/results.json"),
 *     "Java 21 Virtual",  // variant
 *     1                    // run number
 * );
 *
 * // Metrics now in unified schema for further processing
 * }</pre>
 *
 * <h2>Error Handling</h2>
 *
 * All parsers:
 * <ul>
 * <li>Check if input files exist before parsing</li>
 * <li>Return empty list if file not found (graceful degradation)</li>
 * <li>Skip metrics with null or NaN values</li>
 * <li>Print error messages to stderr if parsing fails</li>
 * <li>Continue aggregation even if some data sources are unavailable</li>
 * </ul>
 *
 * <h2>Input File Format Requirements</h2>
 *
 * <h3>JMH JSON</h3> Array of benchmark results with structure: <pre>{@code
 * [
 *   {
 *     "benchmark": "org.example.Bench.method",
 *     "mode": "thrpt",
 *     "primaryMetric": {
 *       "score": 1000.0,
 *       "scoreUnit": "ops/sec"
 *     },
 *     "secondaryMetrics": { ... }
 *   }
 * ]
 * }</pre>
 *
 * <h3>JFR Metrics</h3> Object with top-level keys for different metric categories:
 * <pre>{@code
 * {
 *   "gc": {
 *     "pause_time_avg_ms": 25.5,
 *     "pause_time_max_ms": 45.2,
 *     ...
 *   },
 *   "threads": {
 *     "count": 42,
 *     "peak_count": 50,
 *     ...
 *   },
 *   ...
 * }
 * }</pre>
 *
 * <h3>Test Suite Results</h3> Standard JUnit XML format with attributes on testsuite
 * element: <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <testsuite tests="100" failures="5" skipped="2" time="45.5">
 *   <testcase name="test1" time="0.123"/>
 *   ...
 * </testsuite>
 * }</pre>
 *
 * <h3>Load Test Results</h3> Structured JSON with latency, throughput, and error
 * sections: <pre>{@code
 * {
 *   "latency": {
 *     "p50_ms": 12.5,
 *     "p95_ms": 45.0,
 *     "p99_ms": 120.0,
 *     ...
 *   },
 *   "throughput": {
 *     "requests_per_sec": 950.0,
 *     ...
 *   },
 *   "errors": {
 *     "error_rate_percent": 0.5,
 *     ...
 *   }
 * }
 * }</pre>
 *
 * @since 4.0.0
 */
package org.springframework.samples.petclinic.metrics.parser;
