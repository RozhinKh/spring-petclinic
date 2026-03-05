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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for memory footprint measurement. Measures heap usage at idle,
 * after serving 1000 sequential requests, and peak heap usage.
 */
@Fork(value = 3)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class MemoryBenchmark {

	private static final String BASE_URL = "http://localhost:8080";
	private MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

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
	 * Measures heap footprint at idle state.
	 */
	@Benchmark
	public void measureIdleHeap(Blackhole bh) throws Exception {
		// Force garbage collection to get clean state
		System.gc();
		Thread.sleep(500);

		// Get current heap usage
		MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
		long usedBytes = heapUsage.getUsed();

		// Consume result to prevent dead code elimination
		bh.consume(usedBytes);
	}

	/**
	 * Measures heap footprint after serving 1000 sequential requests.
	 */
	@Benchmark
	public void measureHeapAfterLoad(Blackhole bh) throws Exception {
		// Force garbage collection before load
		System.gc();
		Thread.sleep(500);

		long peakHeap = 0;

		try {
			// Execute 1000 sequential requests
			for (int i = 0; i < 1000; i++) {
				try {
					String endpoint = (i % 3 == 0) ? "/owners" : (i % 3 == 1) ? "/vets" : "/owners/1";
					HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + endpoint).openConnection();
					conn.setConnectTimeout(10000);
					conn.setReadTimeout(10000);
					conn.setRequestMethod("GET");
					int code = conn.getResponseCode();
					conn.disconnect();

					if (code != 200) {
						throw new RuntimeException("Request failed with status: " + code);
					}

					// Track peak heap usage
					MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
					long currentHeap = heapUsage.getUsed();
					if (currentHeap > peakHeap) {
						peakHeap = currentHeap;
					}
				} catch (Exception e) {
					// Continue with next request
				}
			}
		} finally {
			// Force garbage collection after load
			System.gc();
			Thread.sleep(500);
		}

		// Get final heap usage
		MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
		long finalHeap = heapUsage.getUsed();

		// Return the peak heap usage during the load
		bh.consume(peakHeap);
		bh.consume(finalHeap);
	}

	/**
	 * Measures peak heap usage during sustained load.
	 */
	@Benchmark
	public void measurePeakHeap(Blackhole bh) throws Exception {
		// Force garbage collection before measurement
		System.gc();
		Thread.sleep(500);

		long peakHeap = 0;

		try {
			// Run requests for 5 seconds and track peak heap
			long endTime = System.currentTimeMillis() + 5000;
			while (System.currentTimeMillis() < endTime) {
				try {
					HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/owners").openConnection();
					conn.setConnectTimeout(10000);
					conn.setReadTimeout(10000);
					conn.setRequestMethod("GET");
					int code = conn.getResponseCode();
					conn.disconnect();

					if (code != 200) {
						throw new RuntimeException("Request failed with status: " + code);
					}

					// Track peak heap usage
					MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
					long currentHeap = heapUsage.getUsed();
					if (currentHeap > peakHeap) {
						peakHeap = currentHeap;
					}
				} catch (Exception e) {
					// Continue with next request
				}

				Thread.sleep(10);
			}
		} finally {
			// Force garbage collection after measurement
			System.gc();
			Thread.sleep(500);
		}

		// Consume result to prevent dead code elimination
		bh.consume(peakHeap);
	}

	/**
	 * Measures heap footprint variation across different endpoints.
	 */
	@Benchmark
	public void measureEndpointHeapVariation(Blackhole bh) throws Exception {
		// Force garbage collection
		System.gc();
		Thread.sleep(500);

		long[] heapReadings = new long[100];

		try {
			// Execute diverse requests and measure heap after each
			for (int i = 0; i < 100; i++) {
				String endpoint = switch (i % 5) {
				case 0 -> "/owners";
				case 1 -> "/vets";
				case 2 -> "/owners/1";
				case 3 -> "/owners/find";
				default -> "/owners";
				};

				try {
					HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + endpoint).openConnection();
					conn.setConnectTimeout(10000);
					conn.setReadTimeout(10000);
					conn.setRequestMethod("GET");
					int code = conn.getResponseCode();
					conn.disconnect();

					if (code != 200) {
						throw new RuntimeException("Request failed with status: " + code);
					}
				} catch (Exception e) {
					// Continue with next request
				}

				MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
				heapReadings[i] = heapUsage.getUsed();
			}
		} finally {
			// Force garbage collection after measurement
			System.gc();
			Thread.sleep(500);
		}

		// Calculate statistics
		long sumHeap = 0;
		long maxHeap = 0;
		for (long reading : heapReadings) {
			sumHeap += reading;
			if (reading > maxHeap) {
				maxHeap = reading;
			}
		}
		long avgHeap = sumHeap / heapReadings.length;

		// Consume results to prevent dead code elimination
		bh.consume(avgHeap);
		bh.consume(maxHeap);
	}

}
