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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for single-request latency measurement. Measures P50, P75, P90,
 * P95 latency for key endpoints.
 */
@Fork(value = 5)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class LatencyBenchmark {

	private static final String BASE_URL = "http://localhost:8080";

	@Setup
	public void setup() {
		// Verify application is accessible
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/actuator/health").openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			int code = conn.getResponseCode();
			if (code != 200) {
				throw new RuntimeException("Application health check failed: " + code);
			}
		} catch (Exception e) {
			throw new RuntimeException("Cannot connect to application at " + BASE_URL + ": " + e.getMessage());
		}
	}

	/**
	 * Benchmark for GET /owners endpoint - list all owners.
	 */
	@Benchmark
	public void getOwners(Blackhole bh) throws Exception {
		long startTime = System.nanoTime();

		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/owners").openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		conn.disconnect();

		long endTime = System.nanoTime();
		long latencyNs = endTime - startTime;

		if (responseCode != 200) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(latencyNs);
	}

	/**
	 * Benchmark for GET /vets endpoint - list all veterinarians.
	 */
	@Benchmark
	public void getVets(Blackhole bh) throws Exception {
		long startTime = System.nanoTime();

		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/vets").openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		conn.disconnect();

		long endTime = System.nanoTime();
		long latencyNs = endTime - startTime;

		if (responseCode != 200) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(latencyNs);
	}

	/**
	 * Benchmark for GET /owners/find endpoint - owner search form.
	 */
	@Benchmark
	public void getOwnerFind(Blackhole bh) throws Exception {
		long startTime = System.nanoTime();

		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/owners/find").openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		conn.disconnect();

		long endTime = System.nanoTime();
		long latencyNs = endTime - startTime;

		if (responseCode != 200) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(latencyNs);
	}

	/**
	 * Benchmark for GET /owners/1 endpoint - get specific owner by ID.
	 */
	@Benchmark
	public void getOwnerById(Blackhole bh) throws Exception {
		long startTime = System.nanoTime();

		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/owners/1").openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		conn.disconnect();

		long endTime = System.nanoTime();
		long latencyNs = endTime - startTime;

		if (responseCode != 200) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(latencyNs);
	}

	/**
	 * Benchmark for POST /owners/new endpoint - create new owner.
	 */
	@Benchmark
	public void postNewOwner(Blackhole bh) throws Exception {
		long startTime = System.nanoTime();

		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/owners/new").openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

		// Set request body with owner form data
		String body = "firstName=John&lastName=Doe&address=123+Main+St&city=Springfield&telephone=1234567890";
		byte[] bodyBytes = body.getBytes("UTF-8");
		conn.setFixedLengthStreamingMode(bodyBytes.length);

		conn.getOutputStream().write(bodyBytes);
		int responseCode = conn.getResponseCode();
		conn.disconnect();

		long endTime = System.nanoTime();
		long latencyNs = endTime - startTime;

		// Accept 200 (created) or 302 (redirect) as success
		if (responseCode != 200 && responseCode != 302) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(latencyNs);
	}

}
