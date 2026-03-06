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

import java.io.IOException;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Multi-variant benchmark orchestrator. Builds each variant, runs JMH benchmarks,
 * collects results, and generates unified JSON output.
 *
 * Execution flow: 1. Read configuration 2. For each variant: - Build variant with Maven -
 * Execute JMH benchmarks - Start application for acceptance - Run benchmarks - Collect
 * results 3. Aggregate results to JSON 4. Generate summary report
 */
public class BenchmarkRunner {

	private static final ObjectMapper mapper = new ObjectMapper();

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
		.withZone(ZoneId.of("UTC"));

	private static final String JAR_PATH = "target/spring-petclinic-4.0.0-SNAPSHOT.jar";

	private static final String BENCHMARKS_JAR_PATH = "target/benchmarks.jar";

	private final List<BenchmarkResult> allResults = new ArrayList<>();

	private final String outputDirectory;

	public BenchmarkRunner(String outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	/**
	 * Get the ObjectMapper instance for JSON operations. This is used by JFREventParser
	 * and JFRCorrelator.
	 */
	public static ObjectMapper getObjectMapper() {
		return mapper;
	}

	public static void main(String[] args) throws Exception {
		String outputDir = (args.length > 0) ? args[0] : ".";
		boolean includeTests = args.length > 1 && "include-tests".equals(args[1]);

		System.out.println("===============================================");
		System.out.println("   JMH Multi-Variant Benchmark Runner");
		if (includeTests) {
			System.out.println("   (Including Test Suite Execution)");
		}
		System.out.println("===============================================\n");

		BenchmarkRunner runner = new BenchmarkRunner(outputDir);
		runner.executeAllVariants();

		// Execute test suite if requested
		if (includeTests) {
			System.out.println("\n\n===============================================");
			System.out.println("   Test Suite Execution & Metrics Capture");
			System.out.println("===============================================\n");

			TestSuiteRunner testRunner = new TestSuiteRunner(outputDir);
			testRunner.executeAllVariants();

			// Build consolidated metrics from benchmark + test results
			System.out.println("\n\n===============================================");
			System.out.println("   Consolidated Metrics Builder");
			System.out.println("===============================================\n");

			try {
				ConsolidatedMetricsBuilder builder = new ConsolidatedMetricsBuilder(
						Paths.get(outputDir, "benchmark-results.json").toString(),
						Paths.get(outputDir, "test-results.json").toString(), outputDir);
				builder.buildConsolidatedMetrics();
			}
			catch (Exception e) {
				System.err.println("WARNING: Failed to build consolidated metrics: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	/**
	 * Execute benchmarks for all variants.
	 */
	public void executeAllVariants() throws Exception {
		List<String> variants = List.of("java17-baseline", "java21-traditional", "java21-virtual");

		// Build all variants once
		System.out.println(">>> Building all variants...\n");
		for (String variant : variants) {
			buildVariant(variant);
		}

		// Run benchmarks for each variant
		System.out.println("\n>>> Running benchmarks for all variants...\n");
		for (String variant : variants) {
			try {
				System.out.println("\n" + "=".repeat(50));
				System.out.println("Variant: " + variant);
				System.out.println("=".repeat(50));

				executeVariantBenchmarks(variant);
			}
			catch (Exception e) {
				System.err
					.println("ERROR: Failed to execute benchmarks for variant " + variant + ": " + e.getMessage());
				e.printStackTrace();
			}
		}

		// Generate unified JSON output
		System.out.println("\n>>> Generating unified results...\n");
		exportUnifiedResults();

		System.out.println("\n✓ Benchmark execution completed");
		System.out.println("Results exported to: benchmark-results.json");
	}

	/**
	 * Build a specific variant with Maven.
	 */
	private void buildVariant(String variant) throws Exception {
		System.out.println(">>> Building variant: " + variant);

		List<String> command = new ArrayList<>();
		command.add("mvn");
		command.add("clean");
		command.add("package");
		command.add("-q");
		command.add("-DskipTests");

		// Add profile if not default
		if (!variant.equals("java17-baseline")) {
			if (variant.equals("java21-virtual")) {
				command.add("-Pjava21-virtual");
			}
			else if (variant.equals("java21-traditional")) {
				command.add("-Pjava21-traditional");
			}
		}

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.inheritIO();

		long buildStartTime = System.currentTimeMillis();
		Process buildProcess = pb.start();

		if (!buildProcess.waitFor(15, TimeUnit.MINUTES)) {
			buildProcess.destroyForcibly();
			throw new RuntimeException("Build timeout for variant: " + variant);
		}

		int exitCode = buildProcess.exitValue();
		if (exitCode != 0) {
			throw new RuntimeException("Build failed for variant: " + variant + " (exit code: " + exitCode + ")");
		}

		long buildTime = System.currentTimeMillis() - buildStartTime;
		System.out.println("✓ Build completed in " + (buildTime / 1000) + " seconds");
	}

	/**
	 * Execute JMH benchmarks for a specific variant.
	 */
	private void executeVariantBenchmarks(String variant) throws Exception {
		// First, start the application for warm-up
		System.out.println(">>> Starting application for warm-up...");
		ApplicationStarter starter = new ApplicationStarter(variant, "8080", getProfileForVariant(variant),
				getJvmOptionsForVariant(variant), 30, 60, 10, 20);

		JFRHarness jfrHarness = new JFRHarness();

		try {
			starter.startApplication();
			starter.warmup();

			// Start JFR recording before benchmarks
			System.out.println("\n>>> Starting JFR recording...");
			jfrHarness.startRecording();

			// Execute JMH benchmarks
			System.out.println("\n>>> Running JMH benchmarks...");
			long benchmarkStartTimeMs = System.currentTimeMillis();
			Map<String, Object> benchmarkResults = runJmhBenchmarks();
			long benchmarkDurationMs = System.currentTimeMillis() - benchmarkStartTimeMs;

			// Stop JFR recording after benchmarks
			System.out.println("\n>>> Stopping JFR recording...");
			Path jfrFilePath = jfrHarness.stopRecording();

			// Parse JFR metrics
			System.out.println(">>> Parsing JFR metrics...");
			JFREventParser jfrParser = new JFREventParser(jfrFilePath);
			ObjectNode jfrMetrics = jfrParser.parseJFRFile();

			// Correlate JFR with JMH results
			System.out.println(">>> Correlating JFR and JMH results...");
			JFRCorrelator correlator = new JFRCorrelator(jfrMetrics,
					mapper.convertValue(benchmarkResults, ObjectNode.class), benchmarkStartTimeMs, benchmarkDurationMs,
					jfrHarness.getRecordingStartTimeMs(), jfrHarness.getRecordingEndTimeMs());
			ObjectNode correlationAnalysis = correlator.correlate();

			// Process and store results with JFR data
			BenchmarkResult result = new BenchmarkResult(variant, benchmarkResults, jfrMetrics, correlationAnalysis);
			allResults.add(result);

			System.out.println("✓ Benchmarks and JFR analysis completed for variant: " + variant);
		}
		finally {
			starter.shutdown();
		}
	}

	/**
	 * Execute JMH benchmarks using the compiled benchmarks JAR.
	 */
	private Map<String, Object> runJmhBenchmarks() throws Exception {
		List<String> command = new ArrayList<>();
		command.add("java");
		command.add("-jar");
		command.add(BENCHMARKS_JAR_PATH);

		// JMH options for high-quality measurements
		command.add("-f");
		command.add("5"); // 5 forks for stable results
		command.add("-wi");
		command.add("5"); // warmup iterations
		command.add("-i");
		command.add("10"); // measurement iterations
		command.add("-r");
		command.add("2s"); // measurement time per iteration
		command.add("-rf");
		command.add("json"); // JSON output format
		command.add("-rff");
		command.add("jmh-results.json"); // output file

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.inheritIO();

		Process process = pb.start();

		if (!process.waitFor(60, TimeUnit.MINUTES)) {
			process.destroyForcibly();
			throw new RuntimeException("JMH benchmark timeout");
		}

		int exitCode = process.exitValue();
		if (exitCode != 0) {
			throw new RuntimeException("JMH benchmark failed with exit code: " + exitCode);
		}

		// Parse JMH JSON output
		return parseJmhJsonOutput("jmh-results.json");
	}

	/**
	 * Parse JMH JSON output file.
	 */
	private Map<String, Object> parseJmhJsonOutput(String jsonFile) {
		Map<String, Object> results = new HashMap<>();
		List<Map<String, Object>> benchmarks = new ArrayList<>();

		try {
			String content = new String(Files.readAllBytes(Paths.get(jsonFile)));
			JsonNode root = mapper.readTree(content);

			// Process each benchmark result
			if (root.isArray()) {
				for (JsonNode node : root) {
					Map<String, Object> benchmark = new HashMap<>();

					// Extract benchmark metadata
					String benchmarkName = node.get("benchmark").asText();
					benchmark.put("name", extractBenchmarkName(benchmarkName));
					benchmark.put("benchmark_type", extractBenchmarkType(benchmarkName));
					benchmark.put("full_name", benchmarkName);

					// Extract mode and unit
					String mode = node.get("mode").asText();
					benchmark.put("mode", mode);
					String unit = node.get("unit").asText();
					benchmark.put("unit", unit);

					// Extract score and error statistics
					double score = node.get("score").asDouble();
					benchmark.put("value", score);
					benchmark.put("score", score);

					// Extract additional statistics
					JsonNode scoreError = node.get("scoreError");
					if (scoreError != null) {
						benchmark.put("std_dev", scoreError.asDouble());
					}

					// Try to extract min/max from confidence interval
					JsonNode scoreConfidence = node.get("scoreConfidence");
					if (scoreConfidence != null && scoreConfidence.isArray() && scoreConfidence.size() == 2) {
						benchmark.put("min", scoreConfidence.get(0).asDouble());
						benchmark.put("max", scoreConfidence.get(1).asDouble());
					}

					benchmarks.add(benchmark);
				}
			}

		}
		catch (Exception e) {
			System.err.println("Warning: Could not parse JMH JSON output: " + e.getMessage());
			// Fall back to empty results
		}

		results.put("benchmarks", benchmarks);
		results.put("timestamp", ISO_FORMATTER.format(Instant.now()));
		results.put("executionCount", benchmarks.size());

		return results;
	}

	/**
	 * Extract short benchmark name from full name.
	 */
	private String extractBenchmarkName(String fullName) {
		// Full name format: package.Class.methodName
		int lastDot = fullName.lastIndexOf(".");
		if (lastDot > 0) {
			return fullName.substring(lastDot + 1);
		}
		return fullName;
	}

	/**
	 * Export unified results to JSON file.
	 */
	private void exportUnifiedResults() throws Exception {
		ObjectNode root = mapper.createObjectNode();

		// Add metadata
		root.put("timestamp", ISO_FORMATTER.format(Instant.now()));
		root.put("variantCount", allResults.size());

		// Add results for each variant
		ArrayNode variants = mapper.createArrayNode();
		for (BenchmarkResult result : allResults) {
			ObjectNode variantNode = mapper.createObjectNode();
			variantNode.put("variant", result.variant);
			variantNode.put("timestamp", result.timestamp);

			// Add benchmark results
			ArrayNode benchmarks = mapper.createArrayNode();
			for (Map<String, Object> benchmark : result.benchmarks) {
				ObjectNode benchmarkNode = mapper.convertValue(benchmark, ObjectNode.class);
				benchmarkNode.put("variant", result.variant);
				benchmarkNode.put("benchmark_type", extractBenchmarkType(benchmark));
				benchmarks.add(benchmarkNode);
			}
			variantNode.set("benchmarks", benchmarks);

			// Add JFR metrics if available
			if (result.jfrMetrics != null) {
				variantNode.set("jfr_metrics", result.jfrMetrics);
			}

			// Add correlation analysis if available
			if (result.correlationAnalysis != null) {
				variantNode.set("correlation_analysis", result.correlationAnalysis);
			}

			variants.add(variantNode);
		}
		root.set("variants", variants);

		// Write to JSON file
		String outputPath = "benchmark-results.json";
		mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), root);

		System.out.println("✓ Results exported to " + outputPath);

		// Also generate CSV summary
		exportCsvSummary();
	}

	/**
	 * Extract benchmark type from benchmark map.
	 */
	private String extractBenchmarkType(Map<String, Object> benchmark) {
		String name = (String) benchmark.get("name");
		if (name == null) {
			return "unknown";
		}

		return extractBenchmarkType(name);
	}

	/**
	 * Extract benchmark type from benchmark name string.
	 */
	private String extractBenchmarkType(String benchmarkName) {
		if (benchmarkName == null) {
			return "unknown";
		}

		if (benchmarkName.contains("Startup")) {
			return "startup";
		}
		else if (benchmarkName.contains("Latency")) {
			return "latency";
		}
		else if (benchmarkName.contains("Throughput")) {
			return "throughput";
		}
		else if (benchmarkName.contains("Memory") || benchmarkName.contains("Heap")) {
			return "memory";
		}

		return "other";
	}

	/**
	 * Export CSV summary of results.
	 */
	private void exportCsvSummary() throws Exception {
		StringBuilder csv = new StringBuilder();
		csv.append("variant,benchmark_name,score,unit,timestamp\n");

		for (BenchmarkResult result : allResults) {
			for (Map<String, Object> benchmark : result.benchmarks) {
				String variant = result.variant;
				String name = (String) benchmark.get("name");
				Object score = benchmark.get("score");
				String unit = (String) benchmark.getOrDefault("unit", "ops/s");

				csv.append(variant)
					.append(",")
					.append(name != null ? name : "")
					.append(",")
					.append(score != null ? score : "")
					.append(",")
					.append(unit)
					.append(",")
					.append(result.timestamp)
					.append("\n");
			}
		}

		Files.write(Paths.get("benchmark-results.csv"), csv.toString().getBytes(), StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

		System.out.println("✓ CSV summary exported to benchmark-results.csv");
	}

	/**
	 * Get Spring profile for variant.
	 */
	private String getProfileForVariant(String variant) {
		if (variant.equals("java21-virtual")) {
			return "vthreads";
		}
		return "default";
	}

	/**
	 * Get JVM options for variant.
	 */
	private String getJvmOptionsForVariant(String variant) {
		return "-Xms512m -Xmx2g";
	}

	/**
	 * Wrapper for benchmark results.
	 */
	private static class BenchmarkResult {

		String variant;

		String timestamp;

		List<Map<String, Object>> benchmarks;

		ObjectNode jfrMetrics;

		ObjectNode correlationAnalysis;

		BenchmarkResult(String variant, Map<String, Object> results) {
			this.variant = variant;
			this.timestamp = ISO_FORMATTER.format(Instant.now());

			@SuppressWarnings("unchecked")
			List<Map<String, Object>> benchmarks = (List<Map<String, Object>>) results.get("benchmarks");
			this.benchmarks = benchmarks != null ? benchmarks : new ArrayList<>();
			this.jfrMetrics = null;
			this.correlationAnalysis = null;
		}

		BenchmarkResult(String variant, Map<String, Object> results, ObjectNode jfrMetrics,
				ObjectNode correlationAnalysis) {
			this.variant = variant;
			this.timestamp = ISO_FORMATTER.format(Instant.now());

			@SuppressWarnings("unchecked")
			List<Map<String, Object>> benchmarks = (List<Map<String, Object>>) results.get("benchmarks");
			this.benchmarks = benchmarks != null ? benchmarks : new ArrayList<>();
			this.jfrMetrics = jfrMetrics;
			this.correlationAnalysis = correlationAnalysis;
		}

	}

}
