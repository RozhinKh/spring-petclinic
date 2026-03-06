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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates test suite execution across code variants. Runs Maven/Gradle tests,
 * captures JUnit and JaCoCo reports, aggregates metrics.
 *
 * Execution flow: 1. For each variant: - Build variant - Run `mvn clean verify` with
 * JaCoCo - Parse test results (JUnit XML) - Parse coverage results (JaCoCo XML) - Detect
 * regressions (test failures) 2. Aggregate results into JSON 3. Export for comparison and
 * dashboards
 */
public class TestSuiteRunner {

	private static final ObjectMapper mapper = new ObjectMapper();

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
		.withZone(ZoneId.of("UTC"));

	private final String outputDirectory;

	private final List<TestResult> allResults = new ArrayList<>();

	public TestSuiteRunner(String outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	/**
	 * Main entry point for test suite execution.
	 */
	public static void main(String[] args) throws Exception {
		String outputDir = (args.length > 0) ? args[0] : ".";

		System.out.println("===============================================");
		System.out.println("   Test Suite Execution & Metrics Capture");
		System.out.println("===============================================\n");

		TestSuiteRunner runner = new TestSuiteRunner(outputDir);
		runner.executeAllVariants();
	}

	/**
	 * Execute test suites for all variants.
	 */
	public void executeAllVariants() throws Exception {
		List<String> variants = List.of("java17-baseline", "java21-traditional", "java21-virtual");

		System.out.println(">>> Building all variants...\n");
		for (String variant : variants) {
			try {
				buildVariant(variant);
			}
			catch (Exception e) {
				System.err.println("ERROR: Failed to build variant " + variant + ": " + e.getMessage());
			}
		}

		System.out.println("\n>>> Running tests for all variants...\n");
		for (String variant : variants) {
			try {
				System.out.println("\n" + "=".repeat(60));
				System.out.println("Variant: " + variant);
				System.out.println("=".repeat(60));

				executeVariantTests(variant);
			}
			catch (Exception e) {
				System.err.println("ERROR: Failed to execute tests for variant " + variant + ": " + e.getMessage());
				e.printStackTrace();
			}
		}

		System.out.println("\n>>> Generating unified results...\n");
		exportUnifiedResults();

		System.out.println("\n✓ Test suite execution completed");
		System.out.println("Results exported to: test-results.json");
	}

	/**
	 * Build a specific variant.
	 */
	private void buildVariant(String variant) throws Exception {
		System.out.println(">>> Building variant: " + variant);

		List<String> command = new ArrayList<>();
		command.add("mvn");
		command.add("clean");
		command.add("package");
		command.add("-q");
		command.add("-DskipTests");

		// Add profile for Java 21 variants
		if (variant.equals("java21-traditional")) {
			command.add("-Pjava21-traditional");
		}
		else if (variant.equals("java21-virtual")) {
			command.add("-Pjava21-virtual");
		}

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.inheritIO();

		long startTime = System.currentTimeMillis();
		Process process = pb.start();

		if (!process.waitFor(15, TimeUnit.MINUTES)) {
			process.destroyForcibly();
			throw new RuntimeException("Build timeout for variant: " + variant);
		}

		int exitCode = process.exitValue();
		if (exitCode != 0) {
			throw new RuntimeException("Build failed for variant: " + variant + " (exit code: " + exitCode + ")");
		}

		long buildTime = System.currentTimeMillis() - startTime;
		System.out.println("✓ Build completed in " + (buildTime / 1000) + " seconds");
	}

	/**
	 * Execute tests for a specific variant using Maven.
	 */
	private void executeVariantTests(String variant) throws Exception {
		long testStartTime = System.currentTimeMillis();
		long testStartTimestamp = Instant.now().getEpochSecond();

		System.out.println(">>> Running tests with Maven...");

		// Build test execution command
		List<String> command = new ArrayList<>();
		command.add("mvn");
		command.add("clean");
		command.add("verify");
		command.add("-DskipITs=false");

		// Add profile for Java 21 variants
		if (variant.equals("java21-traditional")) {
			command.add("-Pjava21-traditional");
		}
		else if (variant.equals("java21-virtual")) {
			command.add("-Pjava21-virtual");
		}

		// Configure JaCoCo output file
		String jacocoExecFile = "target/jacoco-" + variant + ".exec";
		command.add("-Djacoco.destFile=" + jacocoExecFile);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.inheritIO();

		Process process = pb.start();

		if (!process.waitFor(30, TimeUnit.MINUTES)) {
			process.destroyForcibly();
			throw new RuntimeException("Test execution timeout for variant: " + variant);
		}

		int exitCode = process.exitValue();
		// Note: We continue even if tests fail to capture results

		long testDuration = System.currentTimeMillis() - testStartTime;
		System.out.println("✓ Tests completed in " + (testDuration / 1000) + " seconds");
		System.out.println("  (Exit code: " + exitCode + " - continue with result parsing)");

		// Parse test results
		String surefileReportsDir = "target/surefire-reports";
		JunitReportParser junitParser = new JunitReportParser();
		ObjectNode testMetrics = junitParser.parseTestReports(surefileReportsDir);

		// Parse JaCoCo coverage
		String jacocoReportFile = "target/site/jacoco/index.xml";
		JaCoCoReportParser jacocoParser = new JaCoCoReportParser();
		ObjectNode coverageMetrics;

		try {
			coverageMetrics = jacocoParser.parseJaCoCoReport(jacocoReportFile);
		}
		catch (Exception e) {
			System.err.println("Warning: Failed to parse JaCoCo report: " + e.getMessage());
			coverageMetrics = mapper.createObjectNode();
			coverageMetrics.put("line_coverage_percent", 0.0);
			coverageMetrics.put("branch_coverage_percent", 0.0);
			coverageMetrics.put("method_coverage_percent", 0.0);
		}

		// Detect regressions
		boolean hasRegressions = testMetrics.get("failed").asInt() > 0;
		ArrayNode failedTests = (ArrayNode) testMetrics.get("failed_tests");
		List<String> regressionDetails = new ArrayList<>();

		if (hasRegressions && failedTests != null) {
			failedTests.forEach(test -> {
				String testClass = test.get("class").asText();
				String testMethod = test.get("method").asText();
				String errorMsg = test.has("error_message") ? test.get("error_message").asText() : "Unknown error";
				regressionDetails.add(testClass + "." + testMethod + " - " + errorMsg);
			});
		}

		// Store results
		TestResult result = new TestResult(variant, testStartTimestamp, testMetrics, coverageMetrics, hasRegressions,
				regressionDetails, testDuration);
		allResults.add(result);

		System.out.println("✓ Test results parsed for variant: " + variant);
		System.out.println("  Tests: " + testMetrics.get("total_tests").asInt() + " | Passed: "
				+ testMetrics.get("passed").asInt() + " | Failed: " + testMetrics.get("failed").asInt()
				+ " | Coverage: " + String.format("%.2f%%", coverageMetrics.get("line_coverage_percent").asDouble()));
	}

	/**
	 * Export unified results to JSON file.
	 */
	private void exportUnifiedResults() throws Exception {
		ObjectNode rootNode = mapper.createObjectNode();
		rootNode.put("timestamp", ISO_FORMATTER.format(Instant.now()));
		rootNode.put("variant_count", allResults.size());

		ArrayNode variantsArray = mapper.createArrayNode();

		// Add results for each variant
		for (TestResult result : allResults) {
			ObjectNode variantNode = mapper.createObjectNode();
			variantNode.put("variant", result.variant);
			variantNode.put("timestamp", ISO_FORMATTER.format(Instant.ofEpochSecond(result.timestamp)));

			// Add test metrics
			ObjectNode testSuiteMetrics = mapper.createObjectNode();
			testSuiteMetrics.put("total_tests", result.testMetrics.get("total_tests").asInt());
			testSuiteMetrics.put("passed", result.testMetrics.get("passed").asInt());
			testSuiteMetrics.put("failed", result.testMetrics.get("failed").asInt());
			testSuiteMetrics.put("skipped", result.testMetrics.get("skipped").asInt());
			testSuiteMetrics.put("duration_ms", result.testDuration);
			testSuiteMetrics.put("pass_rate", result.testMetrics.get("pass_rate").asDouble());

			// Add coverage metrics
			ObjectNode coveragePercent = mapper.createObjectNode();
			coveragePercent.put("line", result.coverageMetrics.get("line_coverage_percent").asDouble());
			coveragePercent.put("branch", result.coverageMetrics.get("branch_coverage_percent").asDouble());
			coveragePercent.put("method", result.coverageMetrics.get("method_coverage_percent").asDouble());

			testSuiteMetrics.set("coverage_percent", coveragePercent);

			variantNode.set("test_suite_metrics", testSuiteMetrics);

			// Add failed tests
			variantNode.set("failed_tests", result.testMetrics.get("failed_tests"));

			// Add regression detection
			ObjectNode regressionNode = mapper.createObjectNode();
			regressionNode.put("has_regressions", result.hasRegressions);
			ArrayNode detailsArray = mapper.createArrayNode();
			for (String detail : result.regressionDetails) {
				detailsArray.add(detail);
			}
			regressionNode.set("details", detailsArray);
			variantNode.set("regression_detection", regressionNode);

			variantsArray.add(variantNode);
		}

		rootNode.set("variants", variantsArray);

		// Add summary
		ObjectNode summary = mapper.createObjectNode();
		int totalTests = 0;
		int totalPassed = 0;
		int totalFailed = 0;
		double avgLineCoverage = 0.0;

		for (TestResult result : allResults) {
			totalTests += result.testMetrics.get("total_tests").asInt();
			totalPassed += result.testMetrics.get("passed").asInt();
			totalFailed += result.testMetrics.get("failed").asInt();
			avgLineCoverage += result.coverageMetrics.get("line_coverage_percent").asDouble();
		}

		summary.put("total_tests_across_variants", totalTests);
		summary.put("total_passed", totalPassed);
		summary.put("total_failed", totalFailed);
		if (allResults.size() > 0) {
			summary.put("avg_line_coverage", Math.round((avgLineCoverage / allResults.size()) * 100.0) / 100.0);
		}

		// Check for regressions
		boolean hasAnyRegressions = allResults.stream().anyMatch(r -> r.hasRegressions);
		summary.put("has_regressions", hasAnyRegressions);

		rootNode.set("summary", summary);

		// Write to file
		String outputPath = Paths.get(outputDirectory, "test-results.json").toString();
		mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), rootNode);

		System.out.println("✓ Results exported to: " + outputPath);

		// Also export as CSV for quick analysis
		exportAsCSV();
	}

	/**
	 * Export test results as CSV for easy comparison.
	 */
	private void exportAsCSV() throws Exception {
		StringBuilder csv = new StringBuilder();
		csv.append(
				"Variant,Total Tests,Passed,Failed,Skipped,Pass Rate,Line Coverage,Branch Coverage,Method Coverage,Duration (ms),Has Regressions\n");

		for (TestResult result : allResults) {
			csv.append(result.variant).append(",");
			csv.append(result.testMetrics.get("total_tests").asInt()).append(",");
			csv.append(result.testMetrics.get("passed").asInt()).append(",");
			csv.append(result.testMetrics.get("failed").asInt()).append(",");
			csv.append(result.testMetrics.get("skipped").asInt()).append(",");
			csv.append(String.format("%.2f%%", result.testMetrics.get("pass_rate").asDouble())).append(",");
			csv.append(String.format("%.2f%%", result.coverageMetrics.get("line_coverage_percent").asDouble()))
				.append(",");
			csv.append(String.format("%.2f%%", result.coverageMetrics.get("branch_coverage_percent").asDouble()))
				.append(",");
			csv.append(String.format("%.2f%%", result.coverageMetrics.get("method_coverage_percent").asDouble()))
				.append(",");
			csv.append(result.testDuration).append(",");
			csv.append(result.hasRegressions ? "YES" : "NO").append("\n");
		}

		String csvPath = Paths.get(outputDirectory, "test-results.csv").toString();
		Files.write(Paths.get(csvPath), csv.toString().getBytes());

		System.out.println("✓ CSV exported to: " + csvPath);
	}

	/**
	 * Helper class to store test results for a variant.
	 */
	private static class TestResult {

		String variant;

		long timestamp;

		ObjectNode testMetrics;

		ObjectNode coverageMetrics;

		boolean hasRegressions;

		List<String> regressionDetails;

		long testDuration;

		TestResult(String variant, long timestamp, ObjectNode testMetrics, ObjectNode coverageMetrics,
				boolean hasRegressions, List<String> regressionDetails, long testDuration) {
			this.variant = variant;
			this.timestamp = timestamp;
			this.testMetrics = testMetrics;
			this.coverageMetrics = coverageMetrics;
			this.hasRegressions = hasRegressions;
			this.regressionDetails = regressionDetails;
			this.testDuration = testDuration;
		}

	}

}
