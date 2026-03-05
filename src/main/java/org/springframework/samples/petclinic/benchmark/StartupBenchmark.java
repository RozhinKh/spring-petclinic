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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.boot.SpringApplication;
import org.springframework.samples.petclinic.PetClinicApplication;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for application startup time measurement. Measures cold start
 * (fresh JVM) and warm start (with JIT compilation cache) performance.
 */
@Fork(value = 5, warmups = 1)
@Warmup(iterations = 0)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class StartupBenchmark {

	private static final String BASE_URL = "http://localhost:8080";
	private static final String HEALTH_URL = BASE_URL + "/actuator/health";
	private static final long HEALTH_TIMEOUT_MS = 60_000;

	/**
	 * Measures cold start time: fresh JVM start to application responding to HTTP
	 * requests.
	 */
	@Benchmark
	public void coldStartup(Blackhole bh) throws Exception {
		long startTime = System.currentTimeMillis();

		// Start the application with minimal JVM options for startup measurement
		ProcessBuilder pb = new ProcessBuilder("java", "-jar", "target/spring-petclinic-4.0.0-SNAPSHOT.jar",
				"-Dserver.port=8080", "-Dspring.profiles.active=benchmark");

		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		Process process = pb.start();

		try {
			// Wait for application to become healthy
			boolean healthy = waitForHealthCheck();
			if (!healthy) {
				throw new RuntimeException("Application failed to start within timeout");
			}

			long endTime = System.currentTimeMillis();
			long startupTime = endTime - startTime;

			// Consume result to prevent dead code elimination
			bh.consume(startupTime);
		} finally {
			// Shutdown application
			if (process.isAlive()) {
				process.destroyForcibly();
			}
		}
	}

	/**
	 * Measures warm start time: application start with pre-compiled bytecode cache,
	 * allowing JIT-compiled code to be reused.
	 */
	@Benchmark
	public void warmStartup(Blackhole bh) throws Exception {
		// First, start the application once to populate bytecode cache
		ProcessBuilder pb = new ProcessBuilder("java", "-jar", "target/spring-petclinic-4.0.0-SNAPSHOT.jar",
				"-Dserver.port=8080", "-Dspring.profiles.active=benchmark");

		Process warmupProcess = pb.start();
		waitForHealthCheck();

		// Run some warmup requests to trigger JIT compilation
		for (int i = 0; i < 20; i++) {
			try {
				HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/owners").openConnection();
				conn.setConnectTimeout(5000);
				conn.setReadTimeout(5000);
				conn.getResponseCode();
				conn.disconnect();
			} catch (Exception e) {
				// Ignore
			}
		}

		// Shutdown warmup process
		if (warmupProcess.isAlive()) {
			warmupProcess.destroyForcibly();
		}

		// Now measure warm start
		long startTime = System.currentTimeMillis();
		ProcessBuilder pb2 = new ProcessBuilder("java", "-jar", "target/spring-petclinic-4.0.0-SNAPSHOT.jar",
				"-Dserver.port=8081", "-Dspring.profiles.active=benchmark");

		Process process = pb2.start();

		try {
			// Wait for application to become healthy
			boolean healthy = waitForHealthCheckPort(8081);
			if (!healthy) {
				throw new RuntimeException("Application failed to start within timeout");
			}

			long endTime = System.currentTimeMillis();
			long startupTime = endTime - startTime;

			// Consume result to prevent dead code elimination
			bh.consume(startupTime);
		} finally {
			// Shutdown application
			if (process.isAlive()) {
				process.destroyForcibly();
			}
		}
	}

	/**
	 * Wait for application health check on default port 8080.
	 */
	private boolean waitForHealthCheck() throws Exception {
		long startTime = System.currentTimeMillis();

		while (System.currentTimeMillis() - startTime < HEALTH_TIMEOUT_MS) {
			try {
				HttpURLConnection conn = (HttpURLConnection) new URL(HEALTH_URL).openConnection();
				conn.setConnectTimeout(2000);
				conn.setReadTimeout(2000);
				conn.setRequestMethod("GET");

				int responseCode = conn.getResponseCode();
				if (responseCode == 200) {
					return true;
				}
			} catch (Exception e) {
				// Ignore - application not ready yet
			}

			Thread.sleep(500);
		}

		return false;
	}

	/**
	 * Wait for application health check on specified port.
	 */
	private boolean waitForHealthCheckPort(int port) throws Exception {
		long startTime = System.currentTimeMillis();
		String healthUrl = "http://localhost:" + port + "/actuator/health";

		while (System.currentTimeMillis() - startTime < HEALTH_TIMEOUT_MS) {
			try {
				HttpURLConnection conn = (HttpURLConnection) new URL(healthUrl).openConnection();
				conn.setConnectTimeout(2000);
				conn.setReadTimeout(2000);
				conn.setRequestMethod("GET");

				int responseCode = conn.getResponseCode();
				if (responseCode == 200) {
					return true;
				}
			} catch (Exception e) {
				// Ignore - application not ready yet
			}

			Thread.sleep(500);
		}

		return false;
	}

}
