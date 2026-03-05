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
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Aggregates metrics from multiple sources (JMH, Test Suite, JaCoCo, JFR)
 * into a unified JSON report for comparison and analysis.
 */
public class MetricsAggregator {

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
			.withZone(ZoneId.of("UTC"));

	/**
	 * Main entry point to aggregate all available metrics.
	 */
	public static void main(String[] args) throws Exception {
		String outputDir = (args.length > 0) ? args[0] : ".";

		System.out.println("===============================================");
		System.out.println("   Metrics Aggregator");
		System.out.println("===============================================\n");

		MetricsAggregator aggregator = new MetricsAggregator();
		aggregator.aggregateAllMetrics(outputDir);
	}

	/**
	 * Aggregate all available metrics from benchmark and test results.
	 */
	public void aggregateAllMetrics(String outputDir) throws IOException {
		// Use ConsolidatedMetricsBuilder for unified metric consolidation
		String benchmarkPath = outputDir + "/benchmark-results.json";
		String testPath = outputDir + "/test-results.json";

		System.out.println(">>> Using ConsolidatedMetricsBuilder for metric aggregation...\n");

		try {
			ConsolidatedMetricsBuilder builder = new ConsolidatedMetricsBuilder(benchmarkPath, testPath, outputDir);
			builder.buildConsolidatedMetrics();
		} catch (Exception e) {
			System.err.println("ERROR: Failed to build consolidated metrics: " + e.getMessage());
			e.printStackTrace();

			// Fallback to simple consolidation if builder fails
			System.out.println("\n>>> Falling back to simple metric consolidation...\n");
			fallbackConsolidation(outputDir);
		}
	}

	/**
	 * Fallback consolidation if ConsolidatedMetricsBuilder fails.
	 */
	private void fallbackConsolidation(String outputDir) throws IOException {
		ObjectNode consolidated = mapper.createObjectNode();
		consolidated.put("timestamp", ISO_FORMATTER.format(Instant.now()));

		// Load benchmark results if available
		ObjectNode benchmarkResults = loadJsonFile(outputDir + "/benchmark-results.json");
		if (benchmarkResults != null) {
			consolidated.set("benchmark_results", benchmarkResults);
			System.out.println("✓ Loaded benchmark results");
		} else {
			System.out.println("! Benchmark results not found");
		}

		// Load test results if available
		ObjectNode testResults = loadJsonFile(outputDir + "/test-results.json");
		if (testResults != null) {
			consolidated.set("test_results", testResults);
			System.out.println("✓ Loaded test results");
		} else {
			System.out.println("! Test results not found");
		}

		// Save consolidated metrics
		String outputPath = outputDir + "/consolidated-metrics.json";
		mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), consolidated);
		System.out.println("\n✓ Consolidated metrics saved to: " + outputPath);
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
