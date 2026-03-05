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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes JMeter load test plan against running application instance.
 * Supports configurable concurrency profiles and result collection.
 */
public class JMeterTestRunner {

	private final String testPlanPath;
	private final String applicationBaseUrl;
	private final String resultDirectory;

	public JMeterTestRunner(String testPlanPath, String applicationBaseUrl, String resultDirectory) {
		this.testPlanPath = testPlanPath;
		this.applicationBaseUrl = applicationBaseUrl;
		this.resultDirectory = resultDirectory;
	}

	/**
	 * Run JMeter load test with specified profile.
	 *
	 * @param variant Variant identifier (e.g., java17-baseline)
	 * @param profile Profile name (light, medium, peak)
	 * @param threads Number of concurrent threads
	 * @param rampup Ramp-up time in seconds
	 * @param duration Steady-state duration in seconds
	 * @return Test result with metrics
	 */
	public TestRunResult runTest(String variant, String profile, int threads, long rampup, long duration)
			throws Exception {
		System.out.println(">>> Running JMeter test: " + profile + " profile (" + threads + " threads)");

		long testStartTime = System.currentTimeMillis();
		long testStartTimestamp = Instant.now().getEpochSecond();

		String resultFileName = String.format("jmeter-results-%s-%s-%d.csv", variant, profile, threads);
		String resultFilePath = resultDirectory + "/" + resultFileName;

		// Ensure result directory exists
		Files.createDirectories(Paths.get(resultDirectory));

		List<String> command = new ArrayList<>();
		command.add("jmeter");
		command.add("-n"); // Non-GUI mode
		command.add("-t");
		command.add(testPlanPath); // Test plan path

		// Result file
		command.add("-l");
		command.add(resultFilePath);

		// JMeter properties - test parameters
		command.add("-Jpetclinic.host=localhost");
		command.add("-Jpetclinic.port=" + extractPort(applicationBaseUrl));
		command.add("-Jpetclinic.threads=" + threads);
		command.add("-Jpetclinic.rampup=" + rampup);
		command.add("-Jpetclinic.duration=" + duration);

		// Additional JMeter settings
		command.add("-Jsummariser.out=true");
		command.add("-Jsummariser.interval=10");

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.inheritIO();

		Process jmeterProcess = pb.start();

		// Wait for JMeter to complete
		long totalDuration = rampup + duration + 30; // Add buffer for startup and shutdown
		if (!jmeterProcess.waitFor(totalDuration * 2, TimeUnit.SECONDS)) {
			jmeterProcess.destroyForcibly();
			throw new RuntimeException("JMeter test timeout for profile: " + profile);
		}

		int exitCode = jmeterProcess.exitValue();
		if (exitCode != 0) {
			System.err.println("JMeter exited with code: " + exitCode);
		}

		long testEndTime = System.currentTimeMillis();
		long actualTestDuration = testEndTime - testStartTime;

		System.out.println("✓ JMeter test completed in " + (actualTestDuration / 1000) + " seconds");

		return new TestRunResult(variant, profile, threads, testStartTimestamp, resultFilePath, actualTestDuration);
	}

	/**
	 * Extract port number from URL.
	 */
	private String extractPort(String url) {
		// Extract port from URL like http://localhost:8080
		if (url.contains(":")) {
			return url.substring(url.lastIndexOf(":") + 1);
		}
		return "8080";
	}

	/**
	 * Verify JMeter installation.
	 */
	public static void verifyJMeterInstallation() throws Exception {
		ProcessBuilder pb = new ProcessBuilder("jmeter", "--version");
		pb.redirectErrorStream(true);

		try {
			Process process = pb.start();
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new RuntimeException("JMeter version check timeout");
			}

			int exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new RuntimeException("JMeter is not installed or not in PATH");
			}

			System.out.println("✓ JMeter installation verified");
		} catch (Exception e) {
			throw new RuntimeException("Failed to verify JMeter installation: " + e.getMessage());
		}
	}

	/**
	 * Result container for test execution.
	 */
	public static class TestRunResult {

		private final String variant;
		private final String profile;
		private final int threads;
		private final long startTimestamp;
		private final String resultFilePath;
		private final long executionTimeMs;

		public TestRunResult(String variant, String profile, int threads, long startTimestamp, String resultFilePath,
				long executionTimeMs) {
			this.variant = variant;
			this.profile = profile;
			this.threads = threads;
			this.startTimestamp = startTimestamp;
			this.resultFilePath = resultFilePath;
			this.executionTimeMs = executionTimeMs;
		}

		public String getVariant() {
			return variant;
		}

		public String getProfile() {
			return profile;
		}

		public int getThreads() {
			return threads;
		}

		public long getStartTimestamp() {
			return startTimestamp;
		}

		public String getResultFilePath() {
			return resultFilePath;
		}

		public long getExecutionTimeMs() {
			return executionTimeMs;
		}

	}

}
