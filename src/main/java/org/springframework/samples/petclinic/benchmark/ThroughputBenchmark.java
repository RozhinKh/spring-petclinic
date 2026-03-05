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
 * JMH benchmarks for throughput measurement. Measures requests per second under
 * single-threaded load with no concurrent requests.
 */
@Fork(value = 5)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ThroughputBenchmark {

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
	 * Throughput benchmark for GET /owners endpoint.
	 */
	@Benchmark
	public void getOwnersThroughput(Blackhole bh) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/owners").openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		conn.disconnect();

		if (responseCode != 200) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(responseCode);
	}

	/**
	 * Throughput benchmark for GET /vets endpoint.
	 */
	@Benchmark
	public void getVetsThroughput(Blackhole bh) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/vets").openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		conn.disconnect();

		if (responseCode != 200) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(responseCode);
	}

	/**
	 * Throughput benchmark for GET /owners/{id} endpoint.
	 */
	@Benchmark
	public void getOwnerByIdThroughput(Blackhole bh) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/owners/1").openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		conn.disconnect();

		if (responseCode != 200) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(responseCode);
	}

	/**
	 * Mixed workload throughput benchmark: alternates between GET /owners and GET
	 * /vets.
	 */
	@State(Scope.Benchmark)
	public static class MixedWorkloadState {

		private int requestCount = 0;

	}

	@Benchmark
	public void mixedWorkloadThroughput(Blackhole bh, MixedWorkloadState state) throws Exception {
		String endpoint = (state.requestCount++ % 2 == 0) ? "/owners" : "/vets";

		HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + endpoint).openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestMethod("GET");

		int responseCode = conn.getResponseCode();
		conn.disconnect();

		if (responseCode != 200) {
			throw new RuntimeException("Request failed with status: " + responseCode);
		}

		// Consume result to prevent dead code elimination
		bh.consume(responseCode);
	}

}
