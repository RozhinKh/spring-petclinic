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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

/**
 * Tests for load test harness components.
 * Tests database setup, configuration loading, and results processing.
 */
public class LoadTestHarnessTests {

	private static final String TEST_RESULTS_DIR = "target/test-results";
	private static final String TEST_CONFIG_PATH = "src/main/resources/load-test-harness-config.properties";

	@BeforeEach
	public void setup() throws IOException {
		Files.createDirectories(Paths.get(TEST_RESULTS_DIR));
	}

	// ============================================
	// DatabaseSetupManager Tests
	// ============================================

	@Test
	public void testDatabaseTypeEnum() {
		// Test enum creation from name
		assertThat(DatabaseSetupManager.DatabaseType.fromName("h2")).isEqualTo(
				DatabaseSetupManager.DatabaseType.H2);
		assertThat(DatabaseSetupManager.DatabaseType.fromName("mysql")).isEqualTo(
				DatabaseSetupManager.DatabaseType.MYSQL);
		assertThat(DatabaseSetupManager.DatabaseType.fromName("postgres")).isEqualTo(
				DatabaseSetupManager.DatabaseType.POSTGRES);

		// Test case insensitivity
		assertThat(DatabaseSetupManager.DatabaseType.fromName("H2")).isEqualTo(
				DatabaseSetupManager.DatabaseType.H2);
		assertThat(DatabaseSetupManager.DatabaseType.fromName("MySQL")).isEqualTo(
				DatabaseSetupManager.DatabaseType.MYSQL);
	}

	@Test
	public void testDatabaseTypeProperties() {
		DatabaseSetupManager.DatabaseType h2Type = DatabaseSetupManager.DatabaseType.H2;
		assertThat(h2Type.getName()).isEqualTo("h2");
		assertThat(h2Type.getDriver()).isEqualTo("org.h2.Driver");

		DatabaseSetupManager.DatabaseType mysqlType = DatabaseSetupManager.DatabaseType.MYSQL;
		assertThat(mysqlType.getName()).isEqualTo("mysql");
		assertThat(mysqlType.getDriver()).isEqualTo("com.mysql.cj.jdbc.Driver");

		DatabaseSetupManager.DatabaseType postgresType = DatabaseSetupManager.DatabaseType.POSTGRES;
		assertThat(postgresType.getName()).isEqualTo("postgres");
		assertThat(postgresType.getDriver()).isEqualTo("org.postgresql.Driver");
	}

	@Test
	public void testDatabaseTypeInvalidName() {
		assertThatException().isThrownBy(() -> DatabaseSetupManager.DatabaseType.fromName("invalid"))
				.withMessageContaining("Unknown database type");
	}

	// ============================================
	// ApplicationStarter Tests
	// ============================================

	@Test
	public void testApplicationStarterConfiguration() {
		ApplicationStarter starter = new ApplicationStarter("test-variant", "8080", "default", "-Xms512m",
				30L, 60L, 30L, 50);

		assertThat(starter.getVariant()).isEqualTo("test-variant");
		assertThat(starter.getPort()).isEqualTo("8080");
		assertThat(starter.getBaseUrl()).isEqualTo("http://localhost:8080");
	}

	@Test
	public void testApplicationStarterPortVariations() {
		ApplicationStarter starter8080 = new ApplicationStarter("v1", "8080", "default", "-Xms512m",
				30L, 60L, 30L, 50);
		assertThat(starter8080.getBaseUrl()).isEqualTo("http://localhost:8080");

		ApplicationStarter starter8081 = new ApplicationStarter("v2", "8081", "default", "-Xms512m",
				30L, 60L, 30L, 50);
		assertThat(starter8081.getBaseUrl()).isEqualTo("http://localhost:8081");
	}

	// ============================================
	// JMeterTestRunner Tests
	// ============================================

	@Test
	public void testJMeterTestRunResultContainer() {
		JMeterTestRunner.TestRunResult result = new JMeterTestRunner.TestRunResult("java17-baseline", "light",
				100, 1704067200L, "/path/to/results.csv", 60000L);

		assertThat(result.getVariant()).isEqualTo("java17-baseline");
		assertThat(result.getProfile()).isEqualTo("light");
		assertThat(result.getThreads()).isEqualTo(100);
		assertThat(result.getStartTimestamp()).isEqualTo(1704067200L);
		assertThat(result.getResultFilePath()).isEqualTo("/path/to/results.csv");
		assertThat(result.getExecutionTimeMs()).isEqualTo(60000L);
	}

	// ============================================
	// LoadTestResultsProcessor Tests
	// ============================================

	@Test
	public void testLoadTestResultsProcessorInitialization() {
		LoadTestResultsProcessor processor = new LoadTestResultsProcessor();
		assertThat(processor).isNotNull();
	}

	// ============================================
	// Configuration Tests
	// ============================================

	@Test
	public void testConfigurationFileExists() {
		assertThat(Files.exists(Paths.get(TEST_CONFIG_PATH)))
				.as("Configuration file should exist at " + TEST_CONFIG_PATH).isTrue();
	}

	@Test
	public void testConfigurationFileReadable() throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(TEST_CONFIG_PATH));
		assertThat(lines).isNotEmpty();
		assertThat(lines.stream().anyMatch(line -> line.contains("variant.java17.name")))
				.as("Configuration should contain java17 variant").isTrue();
	}

	// ============================================
	// Database Reset Script Tests
	// ============================================

	@Test
	public void testH2ResetScriptExists() {
		assertThat(Files.exists(Paths.get("src/main/resources/db/h2/reset.sql")))
				.as("H2 reset script should exist").isTrue();
	}

	@Test
	public void testMySQLResetScriptExists() {
		assertThat(Files.exists(Paths.get("src/main/resources/db/mysql/reset.sql")))
				.as("MySQL reset script should exist").isTrue();
	}

	@Test
	public void testPostgresResetScriptExists() {
		assertThat(Files.exists(Paths.get("src/main/resources/db/postgres/reset.sql")))
				.as("PostgreSQL reset script should exist").isTrue();
	}

	@Test
	public void testH2ResetScriptContent() throws IOException {
		String content = Files.readString(Paths.get("src/main/resources/db/h2/reset.sql"));
		assertThat(content).contains("DROP TABLE IF EXISTS owners");
		assertThat(content).contains("CREATE TABLE owners");
		assertThat(content).contains("INSERT INTO types");
	}

	@Test
	public void testMySQLResetScriptContent() throws IOException {
		String content = Files.readString(Paths.get("src/main/resources/db/mysql/reset.sql"));
		assertThat(content).contains("DROP TABLE IF EXISTS owners");
		assertThat(content).contains("CREATE TABLE owners");
		assertThat(content).contains("CHARACTER SET utf8mb4");
	}

	@Test
	public void testPostgresResetScriptContent() throws IOException {
		String content = Files.readString(Paths.get("src/main/resources/db/postgres/reset.sql"));
		assertThat(content).contains("DROP TABLE IF EXISTS owners CASCADE");
		assertThat(content).contains("CREATE TABLE owners");
		assertThat(content).contains("CREATE SEQUENCE");
	}

	// ============================================
	// Results Directory Tests
	// ============================================

	@Test
	public void testResultsDirectoryCreation() throws IOException {
		String testDir = TEST_RESULTS_DIR + "/test-variant";
		Files.createDirectories(Paths.get(testDir));

		assertThat(Files.exists(Paths.get(testDir))).isTrue();
		assertThat(Files.isDirectory(Paths.get(testDir))).isTrue();

		// Cleanup
		Files.deleteIfExists(Paths.get(testDir));
	}

	// ============================================
	// Variant Profile Tests
	// ============================================

	@Test
	public void testVariantPortConfiguration() {
		// Ports should be distinct to avoid conflicts
		String java17Port = "8080";
		String java21TraditionalPort = "8081";
		String java21VirtualPort = "8082";

		assertThat(java17Port).isNotEqualTo(java21TraditionalPort);
		assertThat(java21TraditionalPort).isNotEqualTo(java21VirtualPort);
		assertThat(java17Port).isNotEqualTo(java21VirtualPort);
	}

	// ============================================
	// Load Profile Tests
	// ============================================

	@Test
	public void testLoadProfileConfiguration() throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(TEST_CONFIG_PATH));

		// Verify light profile
		assertThat(lines.stream().anyMatch(line -> line.contains("test.profile.light.threads=100")))
				.as("Light profile should have 100 threads").isTrue();

		// Verify medium profile
		assertThat(lines.stream().anyMatch(line -> line.contains("test.profile.medium.threads=250")))
				.as("Medium profile should have 250 threads").isTrue();

		// Verify peak profile
		assertThat(lines.stream().anyMatch(line -> line.contains("test.profile.peak.threads=500")))
				.as("Peak profile should have 500 threads").isTrue();
	}

	// ============================================
	// File Path Tests
	// ============================================

	@Test
	public void testJMeterTestPlanPathConfiguration() throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(TEST_CONFIG_PATH));

		boolean foundTestPlan = lines.stream()
				.anyMatch(line -> line.contains("jmeter.test_plan=") && line.contains(".jmx"));

		assertThat(foundTestPlan).as("Configuration should specify JMeter test plan path").isTrue();
	}

	// ============================================
	// Timeout Configuration Tests
	// ============================================

	@Test
	public void testApplicationTimeoutConfiguration() throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(TEST_CONFIG_PATH));

		assertThat(lines.stream().anyMatch(line -> line.contains("application.health_timeout_seconds")))
				.as("Health check timeout should be configured").isTrue();

		assertThat(lines.stream().anyMatch(line -> line.contains("application.startup_timeout_seconds")))
				.as("Startup timeout should be configured").isTrue();

		assertThat(lines.stream().anyMatch(line -> line.contains("application.warmup_seconds")))
				.as("Warm-up period should be configured").isTrue();
	}

}
