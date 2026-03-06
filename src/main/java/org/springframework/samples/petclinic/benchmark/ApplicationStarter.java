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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages application startup, health checks, and shutdown for benchmark variants.
 * Handles application process lifecycle including warm-up period.
 */
public class ApplicationStarter {

	private final String variant;

	private final String port;

	private final String profile;

	private final String jvmOptions;

	private final long healthCheckTimeoutSeconds;

	private final long startupTimeoutSeconds;

	private final long warmupSeconds;

	private final int warmupRequests;

	private final String baseUrl;

	private Process applicationProcess;

	private long startupTime;

	private long startupTimestamp;

	public ApplicationStarter(String variant, String port, String profile, String jvmOptions,
			long healthCheckTimeoutSeconds, long startupTimeoutSeconds, long warmupSeconds, int warmupRequests) {
		this.variant = variant;
		this.port = port;
		this.profile = profile;
		this.jvmOptions = jvmOptions;
		this.healthCheckTimeoutSeconds = healthCheckTimeoutSeconds;
		this.startupTimeoutSeconds = startupTimeoutSeconds;
		this.warmupSeconds = warmupSeconds;
		this.warmupRequests = warmupRequests;
		this.baseUrl = "http://localhost:" + port;
	}

	/**
	 * Build the application using Maven for the specific variant.
	 */
	public void buildVariant() throws Exception {
		System.out.println(">>> Building variant: " + variant);

		List<String> command = new ArrayList<>();
		command.add("mvn");
		command.add("clean");
		command.add("package");
		command.add("-q"); // Quiet mode
		command.add("-DskipTests");

		// Add profile if not default
		if (profile != null && !profile.equalsIgnoreCase("default")) {
			command.add("-P" + profile);
		}

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.inheritIO();

		long buildStartTime = System.currentTimeMillis();
		Process buildProcess = pb.start();

		if (!buildProcess.waitFor(10, TimeUnit.MINUTES)) {
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
	 * Start the application instance on the configured port.
	 */
	public void startApplication() throws Exception {
		System.out.println(">>> Starting application: " + variant + " on port " + port);

		List<String> command = new ArrayList<>();
		command.add("java");

		// Add JVM options
		if (jvmOptions != null && !jvmOptions.isEmpty()) {
			String[] options = jvmOptions.split("\\s+");
			for (String option : options) {
				command.add(option);
			}
		}

		// Spring profile and port configuration
		command.add("-Dserver.port=" + port);
		if (profile != null && !profile.equalsIgnoreCase("default")) {
			command.add("-Dspring.profiles.active=" + profile);
		}
		command.add("-Dspring.profiles.active=benchmark");

		// Add application jar
		String jarPath = "target/spring-petclinic-4.0.0-SNAPSHOT.jar";
		command.add("-jar");
		command.add(jarPath);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(new File("."));
		pb.inheritIO();

		startupTimestamp = System.currentTimeMillis();
		startupTime = Instant.now().getEpochSecond();
		applicationProcess = pb.start();

		// Wait for health check
		waitForHealthCheck();

		System.out.println("✓ Application started: " + variant);
	}

	/**
	 * Wait for application to become healthy (health check passes).
	 */
	private void waitForHealthCheck() throws Exception {
		long startTime = System.currentTimeMillis();
		long timeoutMs = healthCheckTimeoutSeconds * 1000;
		String healthUrl = baseUrl + "/actuator/health";

		while (System.currentTimeMillis() - startTime < timeoutMs) {
			try {
				HttpURLConnection conn = (HttpURLConnection) new URL(healthUrl).openConnection();
				conn.setConnectTimeout(2000);
				conn.setReadTimeout(2000);
				conn.setRequestMethod("GET");

				int responseCode = conn.getResponseCode();
				if (responseCode == 200) {
					System.out.println("✓ Health check passed");
					return;
				}
			}
			catch (Exception e) {
				// Ignore - application not ready yet
			}

			Thread.sleep(500);
		}

		throw new RuntimeException("Application failed to start within " + healthCheckTimeoutSeconds
				+ " seconds (variant: " + variant + ")");
	}

	/**
	 * Run warm-up requests to allow JIT compilation and cache population.
	 */
	public void warmup() throws Exception {
		System.out.println(">>> Warm-up period: " + warmupSeconds + " seconds, " + warmupRequests + " requests");

		long warmupStart = System.currentTimeMillis();
		long warmupEndTime = warmupStart + (warmupSeconds * 1000);
		int requestCount = 0;

		while (System.currentTimeMillis() < warmupEndTime && requestCount < warmupRequests) {
			try {
				// Make diverse requests to exercise different code paths
				String[] endpoints = { "/owners", "/vets", "/owners/find", "/owners/1", "/owners/1/edit" };

				for (String endpoint : endpoints) {
					if (System.currentTimeMillis() >= warmupEndTime) {
						break;
					}

					HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + endpoint).openConnection();
					conn.setConnectTimeout(5000);
					conn.setReadTimeout(5000);
					conn.setRequestMethod("GET");
					conn.getResponseCode();
					conn.disconnect();

					requestCount++;
					if (requestCount >= warmupRequests) {
						break;
					}
				}
			}
			catch (Exception e) {
				System.err.println("Warm-up request failed: " + e.getMessage());
			}

			Thread.sleep(100);
		}

		System.out.println("✓ Warm-up completed: " + requestCount + " requests in " + warmupSeconds + " seconds");
	}

	/**
	 * Gracefully shutdown the application.
	 */
	public void shutdown() {
		if (applicationProcess != null && applicationProcess.isAlive()) {
			System.out.println(">>> Shutting down application: " + variant);

			try {
				// Try graceful shutdown first
				HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/actuator/shutdown").openConnection();
				conn.setConnectTimeout(5000);
				conn.setRequestMethod("POST");
				conn.getResponseCode();
				conn.disconnect();

				// Wait for graceful shutdown
				if (applicationProcess.waitFor(10, TimeUnit.SECONDS)) {
					System.out.println("✓ Application shutdown cleanly");
					return;
				}
			}
			catch (Exception e) {
				// Graceful shutdown failed, force kill
			}

			// Force kill if still running
			applicationProcess.destroyForcibly();
			System.out.println("✓ Application force-killed");
		}
	}

	/**
	 * Check if application is running and healthy.
	 */
	public boolean isHealthy() {
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/actuator/health").openConnection();
			conn.setConnectTimeout(2000);
			conn.setReadTimeout(2000);
			conn.setRequestMethod("GET");
			return conn.getResponseCode() == 200;
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * Get startup time in milliseconds.
	 */
	public long getStartupTimeMs() {
		return System.currentTimeMillis() - startupTimestamp;
	}

	/**
	 * Get startup timestamp (epoch seconds).
	 */
	public long getStartupTimestamp() {
		return startupTime;
	}

	/**
	 * Get application base URL.
	 */
	public String getBaseUrl() {
		return baseUrl;
	}

	/**
	 * Get variant identifier.
	 */
	public String getVariant() {
		return variant;
	}

	/**
	 * Get port number.
	 */
	public String getPort() {
		return port;
	}

}
