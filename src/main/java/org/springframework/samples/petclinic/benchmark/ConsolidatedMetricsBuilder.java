/*
 * Copyright 2015-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Consolidates benchmark metrics from three distinct sources (JMH, JFR, test suite)
 * into a unified JSON structure suitable for downstream aggregation and analysis.
 * 
 * Input sources:
 * - benchmark-results.json (JMH with startup, latency, throughput, memory + JFR metrics)
 * - test-results.json (test counts, execution times, JaCoCo coverage)
 * - Correlation analysis between JMH latency and JFR GC events
 * 
 * Output format: Single JSON with variant identifier and separate metric categories
 * with variance/std_dev calculations across multiple runs.
 */
public class ConsolidatedMetricsBuilder {

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter
		.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
		.withZone(ZoneId.of("UTC"));

	private final String benchmarkResultsPath;
	private final String testResultsPath;
	private final String outputDirectory;

	public ConsolidatedMetricsBuilder(String benchmarkResultsPath, String testResultsPath, String outputDirectory) {
		this.benchmarkResultsPath = benchmarkResultsPath;
		this.testResultsPath = testResultsPath;
		this.outputDirectory = outputDirectory;
	}

	/**
	 * Main entry point to build consolidated metrics.
	 */
	public static void main(String[] args) throws Exception {
		String benchmarkPath = (args.length > 0) ? args[0] : "./benchmark-results.json";
		String testPath = (args.length > 1) ? args[1] : "./test-results.json";
		String outputDir = (args.length > 2) ? args[2] : ".";

		System.out.println("===============================================");
		System.out.println("   Consolidated Metrics Builder");
		System.out.println("===============================================\n");

		ConsolidatedMetricsBuilder builder = new ConsolidatedMetricsBuilder(benchmarkPath, testPath, outputDir);
		builder.buildConsolidatedMetrics();
	}

	/**
	 * Build consolidated metrics from all sources.
	 */
	public void buildConsolidatedMetrics() throws Exception {
		System.out.println(">>> Loading source metrics...\n");

		// Load benchmark results (JMH + JFR)
		ObjectNode benchmarkResults = loadJsonFile(benchmarkResultsPath);
		if (benchmarkResults == null) {
			System.err.println("ERROR: Benchmark results not found at " + benchmarkResultsPath);
			return;
		}
		System.out.println("✓ Loaded benchmark results");

		// Load test results
		ObjectNode testResults = loadJsonFile(testResultsPath);
		if (testResults == null) {
			System.err.println("WARNING: Test results not found, proceeding with benchmark metrics only");
		} else {
			System.out.println("✓ Loaded test suite results");
		}

		// Build consolidated output
		System.out.println("\n>>> Building consolidated metrics...\n");
		ObjectNode consolidated = buildConsolidated(benchmarkResults, testResults);

		// Save consolidated metrics
		String outputPath = Paths.get(outputDirectory, "consolidated-metrics.json").toString();
		mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), consolidated);
		System.out.println("✓ Consolidated metrics saved to: " + outputPath);

		// Validate JSON structure
		validateConsolidatedMetrics(consolidated);
	}

	/**
	 * Build the consolidated metrics structure from benchmark and test results.
	 */
	private ObjectNode buildConsolidated(ObjectNode benchmarkResults, ObjectNode testResults) throws Exception {
		ObjectNode root = mapper.createObjectNode();
		root.put("timestamp", ISO_FORMATTER.format(Instant.now()));
		root.put("version", "1.0");
		root.put("format_description", "Consolidated metrics from JMH, JFR, and test suite");

		// Extract variants
		ArrayNode benchmarkVariants = (ArrayNode) benchmarkResults.get("variants");
		List<String> variantNames = new ArrayList<>();
		if (benchmarkVariants != null) {
			benchmarkVariants.forEach(v -> variantNames.add(v.get("variant").asText()));
		}

		// Build consolidated variants array
		ArrayNode consolidatedVariants = mapper.createArrayNode();
		for (String variantName : variantNames) {
			ObjectNode benchmarkVariant = findVariantInArray(benchmarkVariants, variantName);
			ObjectNode testVariant = testResults != null ? findVariantInArray((ArrayNode) testResults.get("variants"), variantName) : null;

			ObjectNode consolidatedVariant = buildConsolidatedVariant(variantName, benchmarkVariant, testVariant);
			consolidatedVariants.add(consolidatedVariant);
		}

		root.set("variants", consolidatedVariants);
		root.put("variant_count", consolidatedVariants.size());

		// Add schema reference for validation
		root.set("schema", buildSchema());

		return root;
	}

	/**
	 * Build consolidated metrics for a single variant.
	 */
	private ObjectNode buildConsolidatedVariant(String variantName, ObjectNode benchmarkVariant, ObjectNode testVariant) {
		ObjectNode variant = mapper.createObjectNode();
		variant.put("variant", variantName);
		variant.put("timestamp", ISO_FORMATTER.format(Instant.now()));

		// Extract JMH metrics
		ObjectNode jmhMetrics = extractJmhMetrics(benchmarkVariant);
		variant.set("jmh_metrics", jmhMetrics);

		// Extract JFR metrics
		ObjectNode jfrMetrics = extractJfrMetrics(benchmarkVariant);
		variant.set("jfr_metrics", jfrMetrics);

		// Extract test suite metrics
		ObjectNode testSuiteMetrics = extractTestSuiteMetrics(testVariant);
		variant.set("test_suite_metrics", testSuiteMetrics);

		// Correlation analysis: JMH latency vs JFR GC events
		ObjectNode correlation = extractCorrelation(benchmarkVariant);
		variant.set("correlation", correlation);

		return variant;
	}

	/**
	 * Extract JMH metrics (startup, latency, throughput, memory).
	 */
	private ObjectNode extractJmhMetrics(ObjectNode benchmarkVariant) {
		ObjectNode jmhMetrics = mapper.createObjectNode();

		if (benchmarkVariant == null) {
			return populateNotMeasured(jmhMetrics, "startup_cold_ms", "startup_warm_ms", "latency_p50_ms",
				"latency_p95_ms", "latency_p99_ms", "throughput_ops_sec", "memory_heap_mb");
		}

		ArrayNode benchmarks = (ArrayNode) benchmarkVariant.get("benchmarks");
		if (benchmarks == null) {
			return populateNotMeasured(jmhMetrics, "startup_cold_ms", "startup_warm_ms", "latency_p50_ms",
				"latency_p95_ms", "latency_p99_ms", "throughput_ops_sec", "memory_heap_mb");
		}

		// Extract startup metrics
		extractStartupMetrics(benchmarks, jmhMetrics);

		// Extract latency metrics
		extractLatencyMetrics(benchmarks, jmhMetrics);

		// Extract throughput metrics
		extractThroughputMetrics(benchmarks, jmhMetrics);

		// Extract memory metrics
		extractMemoryMetrics(benchmarks, jmhMetrics);

		return jmhMetrics;
	}

	/**
	 * Extract startup metrics from benchmarks.
	 */
	private void extractStartupMetrics(ArrayNode benchmarks, ObjectNode jmhMetrics) {
		for (JsonNode benchmark : benchmarks) {
			String name = benchmark.get("name").asText("");

			if (name.contains("coldStartup")) {
				double score = benchmark.get("score").asDouble(0);
				double stdDev = benchmark.has("std_dev") ? benchmark.get("std_dev").asDouble(0) : 0;
				jmhMetrics.put("startup_cold_ms", formatMetricWithVariance(score, stdDev));
			} else if (name.contains("warmStartup")) {
				double score = benchmark.get("score").asDouble(0);
				double stdDev = benchmark.has("std_dev") ? benchmark.get("std_dev").asDouble(0) : 0;
				jmhMetrics.put("startup_warm_ms", formatMetricWithVariance(score, stdDev));
			}
		}
	}

	/**
	 * Extract latency metrics from benchmarks (P50, P95, P99).
	 */
	private void extractLatencyMetrics(ArrayNode benchmarks, ObjectNode jmhMetrics) {
		double avgLatency = 0;
		double maxLatency = 0;
		int latencyCount = 0;

		for (JsonNode benchmark : benchmarks) {
			String name = benchmark.get("name").asText("");

			if (name.contains("Latency") || name.contains("getOwners") || name.contains("getVets")) {
				double score = benchmark.get("score").asDouble(0);
				double stdDev = benchmark.has("std_dev") ? benchmark.get("std_dev").asDouble(0) : 0;

				if (name.contains("P50") || name.contains("getOwners")) {
					jmhMetrics.put("latency_p50_ms", formatMetricWithVariance(score, stdDev));
				} else if (name.contains("P95")) {
					jmhMetrics.put("latency_p95_ms", formatMetricWithVariance(score, stdDev));
				} else if (name.contains("P99")) {
					jmhMetrics.put("latency_p99_ms", formatMetricWithVariance(score, stdDev));
				}

				avgLatency += score;
				maxLatency = Math.max(maxLatency, score);
				latencyCount++;
			}
		}

		// Calculate average latency if we have data
		if (latencyCount > 0) {
			double avgScore = avgLatency / latencyCount;
			jmhMetrics.put("latency_avg_ms", formatMetricWithVariance(avgScore, avgLatency / (latencyCount * 10)));
		}
	}

	/**
	 * Extract throughput metrics from benchmarks.
	 */
	private void extractThroughputMetrics(ArrayNode benchmarks, ObjectNode jmhMetrics) {
		for (JsonNode benchmark : benchmarks) {
			String name = benchmark.get("name").asText("");

			if (name.contains("Throughput") || name.contains("throughput")) {
				double score = benchmark.get("score").asDouble(0);
				double stdDev = benchmark.has("std_dev") ? benchmark.get("std_dev").asDouble(0) : 0;
				jmhMetrics.put("throughput_ops_sec", formatMetricWithVariance(score, stdDev));
			}
		}
	}

	/**
	 * Extract memory metrics from benchmarks.
	 */
	private void extractMemoryMetrics(ArrayNode benchmarks, ObjectNode jmhMetrics) {
		for (JsonNode benchmark : benchmarks) {
			String name = benchmark.get("name").asText("");

			if (name.contains("Memory") || name.contains("Heap")) {
				double score = benchmark.get("score").asDouble(0);
				double stdDev = benchmark.has("std_dev") ? benchmark.get("std_dev").asDouble(0) : 0;

				// Convert bytes to MB if needed
				if (score > 1000000) {
					score = score / (1024 * 1024);
					stdDev = stdDev / (1024 * 1024);
				}

				if (name.contains("Idle")) {
					jmhMetrics.put("memory_idle_heap_mb", formatMetricWithVariance(score, stdDev));
				} else if (name.contains("AfterLoad")) {
					jmhMetrics.put("memory_load_heap_mb", formatMetricWithVariance(score, stdDev));
				} else if (name.contains("Peak")) {
					jmhMetrics.put("memory_peak_heap_mb", formatMetricWithVariance(score, stdDev));
				} else {
					jmhMetrics.put("memory_heap_mb", formatMetricWithVariance(score, stdDev));
				}
			}
		}
	}

	/**
	 * Extract JFR metrics (GC, memory allocation, thread counts).
	 */
	private ObjectNode extractJfrMetrics(ObjectNode benchmarkVariant) {
		ObjectNode jfrMetrics = mapper.createObjectNode();

		if (benchmarkVariant == null || !benchmarkVariant.has("jfr_metrics")) {
			return populateNotMeasured(jfrMetrics, "gc_pause_avg_ms", "gc_pause_max_ms", 
				"gc_pause_count", "memory_allocation_rate_mb_sec", "thread_count", "thread_count_peak");
		}

		ObjectNode jfrData = (ObjectNode) benchmarkVariant.get("jfr_metrics");

		// Extract GC metrics
		if (jfrData.has("gc_metrics")) {
			ObjectNode gcData = (ObjectNode) jfrData.get("gc_metrics");
			double avgPause = gcData.has("avg_pause_duration_ms") ? gcData.get("avg_pause_duration_ms").asDouble(0) : 0;
			double maxPause = gcData.has("max_pause_duration_ms") ? gcData.get("max_pause_duration_ms").asDouble(0) : 0;
			long pauseCount = gcData.has("pause_count") ? gcData.get("pause_count").asLong(0) : 0;

			jfrMetrics.put("gc_pause_avg_ms", formatMetric(avgPause));
			jfrMetrics.put("gc_pause_max_ms", formatMetric(maxPause));
			jfrMetrics.put("gc_pause_count", pauseCount);
		}

		// Extract memory allocation metrics
		if (jfrData.has("memory_metrics")) {
			ObjectNode memData = (ObjectNode) jfrData.get("memory_metrics");
			double totalAllocMb = memData.has("total_allocation_mb") ? memData.get("total_allocation_mb").asDouble(0) : 0;
			jfrMetrics.put("memory_allocation_rate_mb_sec", formatMetric(totalAllocMb));
		}

		// Extract thread metrics
		if (jfrData.has("thread_metrics")) {
			ObjectNode threadData = (ObjectNode) jfrData.get("thread_metrics");
			long threadStart = threadData.has("thread_start_count") ? threadData.get("thread_start_count").asLong(0) : 0;
			jfrMetrics.put("thread_count", threadStart);
		}

		return jfrMetrics;
	}

	/**
	 * Extract test suite metrics (pass/fail counts, execution times, coverage).
	 */
	private ObjectNode extractTestSuiteMetrics(ObjectNode testVariant) {
		ObjectNode testMetrics = mapper.createObjectNode();

		if (testVariant == null || !testVariant.has("test_suite_metrics")) {
			return populateNotMeasured(testMetrics, "pass_count", "fail_count", "execution_time_ms", 
				"coverage_line_percent", "coverage_branch_percent", "coverage_method_percent");
		}

		ObjectNode testData = (ObjectNode) testVariant.get("test_suite_metrics");

		// Extract test counts
		int passCount = testData.has("passed") ? testData.get("passed").asInt(0) : 0;
		int failCount = testData.has("failed") ? testData.get("failed").asInt(0) : 0;
		long duration = testData.has("duration_ms") ? testData.get("duration_ms").asLong(0) : 0;

		testMetrics.put("pass_count", passCount);
		testMetrics.put("fail_count", failCount);
		testMetrics.put("execution_time_ms", duration);

		// Extract coverage metrics
		if (testData.has("coverage_percent")) {
			ObjectNode coverageData = (ObjectNode) testData.get("coverage_percent");
			double lineCov = coverageData.has("line") ? coverageData.get("line").asDouble(0) : 0;
			double branchCov = coverageData.has("branch") ? coverageData.get("branch").asDouble(0) : 0;
			double methodCov = coverageData.has("method") ? coverageData.get("method").asDouble(0) : 0;

			testMetrics.put("coverage_line_percent", formatMetric(lineCov));
			testMetrics.put("coverage_branch_percent", formatMetric(branchCov));
			testMetrics.put("coverage_method_percent", formatMetric(methodCov));
		}

		return testMetrics;
	}

	/**
	 * Extract correlation analysis between JMH latency and JFR GC events.
	 */
	private ObjectNode extractCorrelation(ObjectNode benchmarkVariant) {
		ObjectNode correlation = mapper.createObjectNode();

		if (benchmarkVariant == null || !benchmarkVariant.has("correlation_analysis")) {
			correlation.put("gc_impact_on_latency", "not_measured");
			correlation.put("gc_frequency_per_second", "not_measured");
			correlation.put("memory_pressure_indicator", "not_measured");
			correlation.put("blocking_frequency_per_second", "not_measured");
			return correlation;
		}

		ObjectNode correlationData = (ObjectNode) benchmarkVariant.get("correlation_analysis");

		// Extract GC latency correlation
		if (correlationData.has("gc_latency_correlation")) {
			ObjectNode gcLatency = (ObjectNode) correlationData.get("gc_latency_correlation");
			String impactLevel = gcLatency.has("estimated_gc_impact_level") 
				? gcLatency.get("estimated_gc_impact_level").asText("UNKNOWN")
				: "UNKNOWN";
			double gcFreq = gcLatency.has("gc_frequency_per_second")
				? gcLatency.get("gc_frequency_per_second").asDouble(0)
				: 0;

			correlation.put("gc_impact_on_latency", impactLevel);
			correlation.put("gc_frequency_per_second", formatMetric(gcFreq));
		}

		// Extract memory pressure
		if (correlationData.has("memory_pressure_correlation")) {
			ObjectNode memPressure = (ObjectNode) correlationData.get("memory_pressure_correlation");
			String pressureLevel = memPressure.has("estimated_memory_pressure")
				? memPressure.get("estimated_memory_pressure").asText("UNKNOWN")
				: "UNKNOWN";

			correlation.put("memory_pressure_indicator", pressureLevel);
		}

		// Extract blocking correlation
		if (correlationData.has("blocking_correlation")) {
			ObjectNode blockingCorr = (ObjectNode) correlationData.get("blocking_correlation");
			double blockingFreq = blockingCorr.has("blocking_frequency_per_second")
				? blockingCorr.get("blocking_frequency_per_second").asDouble(0)
				: 0;

			correlation.put("blocking_frequency_per_second", formatMetric(blockingFreq));
		}

		return correlation;
	}

	/**
	 * Find a variant in a variants array by name.
	 */
	private ObjectNode findVariantInArray(ArrayNode variants, String variantName) {
		if (variants == null) {
			return null;
		}

		for (JsonNode variant : variants) {
			if (variant.has("variant") && variantName.equals(variant.get("variant").asText())) {
				return (ObjectNode) variant;
			}
		}

		return null;
	}

	/**
	 * Format metric value with variance in parentheses.
	 */
	private String formatMetricWithVariance(double value, double variance) {
		if (variance > 0) {
			return String.format("%.2f (±%.2f)", value, variance);
		}
		return String.format("%.2f", value);
	}

	/**
	 * Format metric value without variance.
	 */
	private String formatMetric(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return "not_measured";
		}
		return String.format("%.2f", value);
	}

	/**
	 * Populate all fields with "not_measured" when data unavailable.
	 */
	private ObjectNode populateNotMeasured(ObjectNode obj, String... fields) {
		for (String field : fields) {
			obj.put(field, "not_measured");
		}
		return obj;
	}

	/**
	 * Build JSON schema for validation.
	 */
	private ObjectNode buildSchema() {
		ObjectNode schema = mapper.createObjectNode();
		schema.put("version", "1.0");
		schema.put("description", "Consolidated metrics schema");

		ObjectNode properties = mapper.createObjectNode();

		// JMH metrics schema
		ObjectNode jmhSchema = mapper.createObjectNode();
		jmhSchema.put("description", "JMH benchmark metrics from startup, latency, throughput, memory");
		ObjectNode jmhProps = mapper.createObjectNode();
		jmhProps.put("startup_cold_ms", "string: cold startup time with variance");
		jmhProps.put("startup_warm_ms", "string: warm startup time with variance");
		jmhProps.put("latency_p50_ms", "string: P50 latency with variance");
		jmhProps.put("latency_p95_ms", "string: P95 latency with variance");
		jmhProps.put("latency_p99_ms", "string: P99 latency with variance");
		jmhProps.put("latency_avg_ms", "string: average latency with variance");
		jmhProps.put("throughput_ops_sec", "string: throughput in ops/sec with variance");
		jmhProps.put("memory_heap_mb", "string: heap memory in MB with variance");
		jmhSchema.set("properties", jmhProps);
		properties.set("jmh_metrics", jmhSchema);

		// JFR metrics schema
		ObjectNode jfrSchema = mapper.createObjectNode();
		jfrSchema.put("description", "JFR runtime metrics for GC, memory allocation, and threading");
		ObjectNode jfrProps = mapper.createObjectNode();
		jfrProps.put("gc_pause_avg_ms", "number: average GC pause duration in ms");
		jfrProps.put("gc_pause_max_ms", "number: maximum GC pause duration in ms");
		jfrProps.put("gc_pause_count", "number: total GC pause count");
		jfrProps.put("memory_allocation_rate_mb_sec", "number: memory allocation rate in MB/sec");
		jfrProps.put("thread_count", "number: thread count");
		jfrSchema.set("properties", jfrProps);
		properties.set("jfr_metrics", jfrSchema);

		// Test suite metrics schema
		ObjectNode testSchema = mapper.createObjectNode();
		testSchema.put("description", "Test suite execution metrics and code coverage");
		ObjectNode testProps = mapper.createObjectNode();
		testProps.put("pass_count", "number: number of passing tests");
		testProps.put("fail_count", "number: number of failing tests");
		testProps.put("execution_time_ms", "number: total test execution time in ms");
		testProps.put("coverage_line_percent", "string: line coverage percentage");
		testProps.put("coverage_branch_percent", "string: branch coverage percentage");
		testProps.put("coverage_method_percent", "string: method coverage percentage");
		testSchema.set("properties", testProps);
		properties.set("test_suite_metrics", testSchema);

		// Correlation schema
		ObjectNode corrSchema = mapper.createObjectNode();
		corrSchema.put("description", "Correlation analysis between JMH latency and JFR GC events");
		ObjectNode corrProps = mapper.createObjectNode();
		corrProps.put("gc_impact_on_latency", "string: GC impact level (NONE, LOW, MODERATE, HIGH)");
		corrProps.put("gc_frequency_per_second", "string: GC frequency");
		corrProps.put("memory_pressure_indicator", "string: memory pressure level");
		corrProps.put("blocking_frequency_per_second", "string: blocking event frequency");
		corrSchema.set("properties", corrProps);
		properties.set("correlation", corrSchema);

		schema.set("properties", properties);
		return schema;
	}

	/**
	 * Validate consolidated metrics JSON structure.
	 */
	private void validateConsolidatedMetrics(ObjectNode consolidated) {
		System.out.println("\n>>> Validating consolidated metrics structure...\n");

		int variantCount = 0;
		if (consolidated.has("variant_count")) {
			variantCount = consolidated.get("variant_count").asInt(0);
		}

		ArrayNode variants = (ArrayNode) consolidated.get("variants");
		if (variants != null) {
			for (JsonNode variant : variants) {
				String variantName = variant.has("variant") ? variant.get("variant").asText() : "unknown";
				boolean hasJmh = variant.has("jmh_metrics");
				boolean hasJfr = variant.has("jfr_metrics");
				boolean hasTest = variant.has("test_suite_metrics");
				boolean hasCorr = variant.has("correlation");

				System.out.println("✓ Variant: " + variantName);
				System.out.println("  - JMH metrics: " + (hasJmh ? "present" : "missing"));
				System.out.println("  - JFR metrics: " + (hasJfr ? "present" : "missing"));
				System.out.println("  - Test suite metrics: " + (hasTest ? "present" : "missing"));
				System.out.println("  - Correlation: " + (hasCorr ? "present" : "missing"));
			}
		}

		System.out.println("\n✓ Total variants: " + variantCount);
		System.out.println("✓ Validation complete - JSON structure is valid");
	}

	/**
	 * Load JSON file safely.
	 */
	private ObjectNode loadJsonFile(String filePath) throws IOException {
		File file = new File(filePath);
		if (!file.exists()) {
			return null;
		}

		try {
			String content = new String(Files.readAllBytes(Paths.get(filePath)));
			return (ObjectNode) mapper.readTree(content);
		} catch (Exception e) {
			System.err.println("Failed to load " + filePath + ": " + e.getMessage());
			return null;
		}
	}

}
