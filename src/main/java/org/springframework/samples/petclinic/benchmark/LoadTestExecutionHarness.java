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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Main orchestrator for multi-variant load testing harness.
 * Coordinates the complete lifecycle: build, start, test, shutdown, results collection.
 *
 * Usage:
 *
 * <pre>
 * LoadTestExecutionHarness harness = new LoadTestExecutionHarness(
 *     "src/main/resources/load-test-harness-config.properties"
 * );
 * harness.executeAllVariants();
 * </pre>
 */
public class LoadTestExecutionHarness {

	private static final ObjectMapper mapper = new ObjectMapper();
	private final Properties config;
	private final String configPath;
	private final String masterResultsDir;
	private final List<VariantResult> allResults;

	public LoadTestExecutionHarness(String configPath) throws IOException {
		this.configPath = configPath;
		this.config = loadConfiguration(configPath);
		this.masterResultsDir = initializeResultsDirectory();
		this.allResults = new ArrayList<>();

		System.out.println("╔════════════════════════════════════════════════════════════════════╗");
		System.out.println("║  PetClinic Multi-Variant Load Testing Harness                      ║");
		System.out.println("║  Master Results: " + masterResultsDir);
		System.out.println("╚════════════════════════════════════════════════════════════════════╝");
	}

	/**
	 * Execute load tests for all configured variants.
	 */
	public void executeAllVariants() throws Exception {
		// Verify JMeter installation
		JMeterTestRunner.verifyJMeterInstallation();

		// Get variants to run
		String variantsToRun = config.getProperty("variants.to_run",
				"java17-baseline,java21-traditional,java21-virtual");
		String[] variants = variantsToRun.split(",");

		for (String variant : variants) {
			variant = variant.trim();
			try {
				System.out.println("\n");
				System.out.println("╔════════════════════════════════════════════════════════════════════╗");
				System.out.println("║  Running variant: " + variant);
				System.out.println("╚════════════════════════════════════════════════════════════════════╝");

				executeVariant(variant);
			} catch (Exception e) {
				System.err.println("ERROR: Variant failed: " + variant);
				e.printStackTrace();

				if (Boolean.parseBoolean(config.getProperty("execution.cleanup_on_error", "true"))) {
					System.out.println("Cleaning up after error...");
					// Cleanup logic handled by finally blocks
				}
			}
		}

		// Generate master report
		generateMasterReport();

		System.out.println("\n");
		System.out.println("╔════════════════════════════════════════════════════════════════════╗");
		System.out.println("║  ALL VARIANTS COMPLETED                                            ║");
		System.out.println("║  Master Results: " + masterResultsDir);
		System.out.println("╚════════════════════════════════════════════════════════════════════╝");
	}

	/**
	 * Execute load test for a single variant.
	 */
	private void executeVariant(String variant) throws Exception {
		// Get variant configuration
		String port = config.getProperty("variant." + variant + ".port", "8080");
		String profile = config.getProperty("variant." + variant + ".profile", "default");
		String jvmOptions = config.getProperty("variant." + variant + ".jvm.options", "-Xms512m -Xmx2g");

		// Get database configuration
		String dbType = config.getProperty("database.type", "h2");
		String dbDriver = config.getProperty("database." + dbType + ".driver");
		String dbUrl = config.getProperty("database." + dbType + ".url");
		String dbUsername = config.getProperty("database." + dbType + ".username", "sa");
		String dbPassword = config.getProperty("database." + dbType + ".password", "");
		String resetScript = config.getProperty("database." + dbType + ".reset_script", "");

		// Initialize components
		ApplicationStarter appStarter = new ApplicationStarter(variant, port, profile, jvmOptions,
				Long.parseLong(config.getProperty("application.health_timeout_seconds", "30")),
				Long.parseLong(config.getProperty("application.startup_timeout_seconds", "60")),
				Long.parseLong(config.getProperty("application.warmup_seconds", "30")),
				Integer.parseInt(config.getProperty("application.warmup_requests", "50")));

		DatabaseSetupManager dbManager = new DatabaseSetupManager(dbType, dbDriver, dbUrl, dbUsername, dbPassword,
				resetScript);

		JMeterTestRunner jmeterRunner = new JMeterTestRunner(config.getProperty("jmeter.test_plan"),
				appStarter.getBaseUrl(), masterResultsDir + "/" + variant);

		LoadTestResultsProcessor resultsProcessor = new LoadTestResultsProcessor();

		VariantResult variantResult = new VariantResult(variant);

		try {
			// Database setup
			System.out.println("\n>>> Step 1: Database Setup");
			dbManager.verifyConnectivity();
			dbManager.resetDatabase();
			validateTestData(dbManager);

			// Build variant
			System.out.println("\n>>> Step 2: Build Variant");
			appStarter.buildVariant();

			// Start application
			System.out.println("\n>>> Step 3: Start Application");
			long buildStartTime = System.currentTimeMillis();
			appStarter.startApplication();
			variantResult.startupTimeMs = appStarter.getStartupTimeMs();
			variantResult.startupTimestamp = appStarter.getStartupTimestamp();
			System.out.println("  Startup time: " + variantResult.startupTimeMs + "ms");

			// Warm-up period
			System.out.println("\n>>> Step 4: Warm-up Period");
			appStarter.warmup();

			// Run load tests for each profile
			System.out.println("\n>>> Step 5: Load Testing");
			String[] profiles = { "light", "medium", "peak" };
			for (String testProfile : profiles) {
				int threads = Integer.parseInt(config.getProperty("test.profile." + testProfile + ".threads"));
				long rampup = Long.parseLong(config.getProperty("test.profile." + testProfile + ".rampup"));
				long duration = Long.parseLong(config.getProperty("test.profile." + testProfile + ".duration"));

				JMeterTestRunner.TestRunResult testResult = jmeterRunner.runTest(variant, testProfile, threads,
						rampup, duration);

				// Process results
				String jsonResultPath = resultsProcessor.processResults(testResult.getResultFilePath(), variant,
						testProfile, testResult.getStartTimestamp());

				variantResult.testResults.put(testProfile, jsonResultPath);
			}

			variantResult.success = true;

		} finally {
			// Graceful shutdown
			System.out.println("\n>>> Step 6: Shutdown");
			if (appStarter.isHealthy()) {
				appStarter.shutdown();
			}

			if (Boolean.parseBoolean(config.getProperty("execution.cleanup_on_exit", "true"))) {
				System.out.println("Cleanup completed");
			}
		}

		allResults.add(variantResult);
	}

	/**
	 * Validate test data consistency.
	 */
	private void validateTestData(DatabaseSetupManager dbManager) throws Exception {
		long minOwners = Long.parseLong(config.getProperty("data.min_owners", "100"));
		long minPets = Long.parseLong(config.getProperty("data.min_pets", "200"));

		long ownerCount = dbManager.getRecordCount("owners");
		long petCount = dbManager.getRecordCount("pets");

		System.out.println("  Owner count: " + ownerCount + " (required: " + minOwners + ")");
		System.out.println("  Pet count: " + petCount + " (required: " + minPets + ")");

		if (ownerCount < minOwners) {
			throw new RuntimeException("Insufficient test data: owners count " + ownerCount + " < required "
					+ minOwners);
		}
		if (petCount < minPets) {
			throw new RuntimeException(
					"Insufficient test data: pets count " + petCount + " < required " + minPets);
		}

		System.out.println("✓ Test data validation passed");
	}

	/**
	 * Generate master report combining all variant results.
	 */
	private void generateMasterReport() throws IOException {
		ObjectNode report = mapper.createObjectNode();

		report.put("timestamp", Instant.now().toString());
		report.put("masterResultsDirectory", masterResultsDir);
		report.put("totalVariants", allResults.size());

		ArrayNode variantsNode = report.putArray("variants");
		for (VariantResult result : allResults) {
			ObjectNode varNode = variantsNode.addObject();
			varNode.put("name", result.variant);
			varNode.put("success", result.success);
			varNode.put("startupTimeMs", result.startupTimeMs);
			varNode.put("startupTimestamp", result.startupTimestamp);

			ObjectNode testsNode = varNode.putObject("testProfiles");
			result.testResults.forEach((profile, jsonPath) -> {
				testsNode.put(profile, jsonPath);
			});
		}

		String reportPath = masterResultsDir + "/master-report.json";
		Files.writeString(Paths.get(reportPath),
				mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));

		System.out.println("\n✓ Master report generated: " + reportPath);
	}

	/**
	 * Load configuration from properties file.
	 */
	private Properties loadConfiguration(String path) throws IOException {
		Properties props = new Properties();
		props.load(Files.newInputStream(Paths.get(path)));
		return props;
	}

	/**
	 * Initialize master results directory.
	 */
	private String initializeResultsDirectory() throws IOException {
		String resultsBase = config.getProperty("results.directory", "target/load-test-results");

		String resultDir;
		if (Boolean.parseBoolean(config.getProperty("results.create_timestamp_subdirs", "true"))) {
			String timestamp = Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
			resultDir = resultsBase + "/run_" + timestamp;
		} else {
			resultDir = resultsBase;
		}

		Files.createDirectories(Paths.get(resultDir));
		return resultDir;
	}

	/**
	 * Container for individual variant results.
	 */
	private static class VariantResult {

		String variant;
		boolean success;
		long startupTimeMs;
		long startupTimestamp;
		Map<String, String> testResults;

		VariantResult(String variant) {
			this.variant = variant;
			this.success = false;
			this.testResults = new HashMap<>();
		}

	}

	/**
	 * Entry point for standalone execution.
	 */
	public static void main(String[] args) throws Exception {
		String configPath = "src/main/resources/load-test-harness-config.properties";

		if (args.length > 0) {
			configPath = args[0];
		}

		LoadTestExecutionHarness harness = new LoadTestExecutionHarness(configPath);
		harness.executeAllVariants();
	}

}
