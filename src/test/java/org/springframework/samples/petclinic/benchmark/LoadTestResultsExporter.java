/*
 * Copyright 2012-2025 the original author or authors.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Exports load test results in JSON format with latency percentiles and performance metrics.
 * Processes JMeter results and creates aggregated metrics for analysis.
 *
 * @author Load Test Framework
 */
public class LoadTestResultsExporter {

	private static final String EXPORT_DIR = "target/load-test-results";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
		.enable(SerializationFeature.INDENT_OUTPUT);

	/**
	 * Exports JMeter test results to JSON format.
	 * @param csvResultsPath path to JMeter CSV results file
	 * @param testProfile concurrent user profile (e.g., "light-100", "medium-250", "peak-500")
	 * @throws IOException if export fails
	 */
	public void exportResults(String csvResultsPath, String testProfile) throws IOException {
		File csvFile = new File(csvResultsPath);
		if (!csvFile.exists()) {
			throw new IOException("CSV results file not found: " + csvResultsPath);
		}

		List<String> lines = Files.readAllLines(csvFile.toPath());
		if (lines.isEmpty()) {
			throw new IOException("CSV results file is empty");
		}

		// Parse CSV header and results
		Map<String, Integer> headers = parseHeader(lines.get(0));
		List<TestResult> results = parseResults(lines, headers);

		// Calculate metrics
		LoadTestMetrics metrics = calculateMetrics(results);

		// Export to JSON
		exportToJson(metrics, testProfile);
	}

	/**
	 * Parses CSV header to create column index map.
	 */
	private Map<String, Integer> parseHeader(String headerLine) {
		Map<String, Integer> headers = new HashMap<>();
		String[] columns = headerLine.split(",");
		for (int i = 0; i < columns.length; i++) {
			headers.put(columns[i].trim(), i);
		}
		return headers;
	}

	/**
	 * Parses CSV results lines into TestResult objects.
	 */
	private List<TestResult> parseResults(List<String> lines, Map<String, Integer> headers) {
		List<TestResult> results = new ArrayList<>();

		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.isEmpty()) {
				continue;
			}

			String[] values = line.split(",");
			try {
				TestResult result = new TestResult();
				result.timestamp = Long.parseLong(getValue(values, headers, "timeStamp", "0"));
				result.elapsed = Long.parseLong(getValue(values, headers, "elapsed", "0"));
				result.label = getValue(values, headers, "label", "Unknown");
				result.responseCode = getValue(values, headers, "responseCode", "");
				result.responseMessage = getValue(values, headers, "responseMessage", "");
				result.success = Boolean.parseBoolean(getValue(values, headers, "success", "true"));

				results.add(result);
			} catch (NumberFormatException e) {
				// Skip malformed lines
			}
		}

		return results;
	}

	/**
	 * Gets value from array using header mapping with fallback.
	 */
	private String getValue(String[] values, Map<String, Integer> headers, String columnName, String defaultValue) {
		Integer index = headers.get(columnName);
		if (index != null && index < values.length) {
			return values[index].trim();
		}
		return defaultValue;
	}

	/**
	 * Calculates performance metrics from test results.
	 */
	private LoadTestMetrics calculateMetrics(List<TestResult> results) {
		LoadTestMetrics metrics = new LoadTestMetrics();
		metrics.totalRequests = results.size();
		metrics.timestamp = Instant.now().toString();

		if (results.isEmpty()) {
			return metrics;
		}

		// Separate successful and failed requests
		List<Long> successfulLatencies = new ArrayList<>();
		int failedCount = 0;

		Map<String, List<Long>> latenciesByEndpoint = new HashMap<>();

		for (TestResult result : results) {
			if (result.success) {
				successfulLatencies.add(result.elapsed);

				latenciesByEndpoint.computeIfAbsent(result.label, k -> new ArrayList<>()).add(result.elapsed);
			} else {
				failedCount++;
			}
		}

		metrics.successfulRequests = successfulLatencies.size();
		metrics.failedRequests = failedCount;
		metrics.errorRate = (double) failedCount / metrics.totalRequests * 100;
		metrics.throughput = calculateThroughput(results);

		if (!successfulLatencies.isEmpty()) {
			// Calculate overall latency percentiles
			Collections.sort(successfulLatencies);
			metrics.latency = new LatencyMetrics();
			metrics.latency.min = successfulLatencies.get(0);
			metrics.latency.max = successfulLatencies.get(successfulLatencies.size() - 1);
			metrics.latency.mean = (long) successfulLatencies.stream().mapToLong(Long::longValue).average()
				.orElse(0);
			metrics.latency.p50 = calculatePercentile(successfulLatencies, 50);
			metrics.latency.p75 = calculatePercentile(successfulLatencies, 75);
			metrics.latency.p90 = calculatePercentile(successfulLatencies, 90);
			metrics.latency.p95 = calculatePercentile(successfulLatencies, 95);
			metrics.latency.p99 = calculatePercentile(successfulLatencies, 99);
			metrics.latency.p99_9 = calculatePercentile(successfulLatencies, 99.9);

			// Calculate per-endpoint metrics
			metrics.endpointMetrics = new HashMap<>();
			for (Map.Entry<String, List<Long>> entry : latenciesByEndpoint.entrySet()) {
				List<Long> latencies = entry.getValue();
				Collections.sort(latencies);

				EndpointMetrics epMetrics = new EndpointMetrics();
				epMetrics.requests = latencies.size();
				epMetrics.minLatency = latencies.get(0);
				epMetrics.maxLatency = latencies.get(latencies.size() - 1);
				epMetrics.meanLatency = (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);
				epMetrics.p95Latency = calculatePercentile(latencies, 95);
				epMetrics.p99Latency = calculatePercentile(latencies, 99);

				metrics.endpointMetrics.put(entry.getKey(), epMetrics);
			}

			// Calculate response time distribution
			metrics.distribution = calculateDistribution(successfulLatencies);
		}

		return metrics;
	}

	/**
	 * Calculates percentile value from sorted list.
	 */
	private long calculatePercentile(List<Long> sortedValues, double percentile) {
		if (sortedValues.isEmpty()) {
			return 0;
		}
		int index = (int) Math.ceil((percentile / 100) * sortedValues.size()) - 1;
		index = Math.max(0, Math.min(index, sortedValues.size() - 1));
		return sortedValues.get(index);
	}

	/**
	 * Calculates response time distribution buckets.
	 */
	private Map<String, Long> calculateDistribution(List<Long> latencies) {
		Map<String, Long> distribution = new HashMap<>();

		long bucket0_100 = latencies.stream().filter(l -> l < 100).count();
		long bucket100_250 = latencies.stream().filter(l -> l >= 100 && l < 250).count();
		long bucket250_500 = latencies.stream().filter(l -> l >= 250 && l < 500).count();
		long bucket500_1000 = latencies.stream().filter(l -> l >= 500 && l < 1000).count();
		long bucketOver1000 = latencies.stream().filter(l -> l >= 1000).count();

		distribution.put("0-100ms", bucket0_100);
		distribution.put("100-250ms", bucket100_250);
		distribution.put("250-500ms", bucket250_500);
		distribution.put("500-1000ms", bucket500_1000);
		distribution.put(">1000ms", bucketOver1000);

		return distribution;
	}

	/**
	 * Calculates throughput (requests per second).
	 */
	private double calculateThroughput(List<TestResult> results) {
		if (results.isEmpty()) {
			return 0;
		}

		long minTimestamp = results.stream().mapToLong(r -> r.timestamp).min().orElse(0);
		long maxTimestamp = results.stream().mapToLong(r -> r.timestamp).max().orElse(0);

		if (minTimestamp == maxTimestamp) {
			return 0;
		}

		long durationSeconds = (maxTimestamp - minTimestamp) / 1000;
		if (durationSeconds == 0) {
			return 0;
		}

		return (double) results.size() / durationSeconds;
	}

	/**
	 * Exports metrics to JSON file.
	 */
	private void exportToJson(LoadTestMetrics metrics, String testProfile) throws IOException {
		Path exportPath = Paths.get(EXPORT_DIR);
		Files.createDirectories(exportPath);

		String filename = String.format("load-test-results-%s-%d.json", testProfile,
				System.currentTimeMillis());
		Path jsonFile = exportPath.resolve(filename);

		String jsonContent = OBJECT_MAPPER.writeValueAsString(metrics);
		Files.write(jsonFile, jsonContent.getBytes());

		System.out.println("Load test results exported to: " + jsonFile);
	}

	/**
	 * Internal class for test result record.
	 */
	private static class TestResult {

		long timestamp;

		long elapsed;

		String label;

		String responseCode;

		String responseMessage;

		boolean success;

	}

	/**
	 * Data class for load test metrics.
	 */
	public static class LoadTestMetrics {

		public String timestamp;

		public long totalRequests;

		public long successfulRequests;

		public long failedRequests;

		public double errorRate;

		public double throughput;

		public LatencyMetrics latency;

		public Map<String, EndpointMetrics> endpointMetrics;

		public Map<String, Long> distribution;

	}

	/**
	 * Data class for latency metrics.
	 */
	public static class LatencyMetrics {

		public long min;

		public long max;

		public long mean;

		public long p50;

		public long p75;

		public long p90;

		public long p95;

		public long p99;

		public long p99_9;

	}

	/**
	 * Data class for per-endpoint metrics.
	 */
	public static class EndpointMetrics {

		public long requests;

		public long minLatency;

		public long maxLatency;

		public long meanLatency;

		public long p95Latency;

		public long p99Latency;

	}

}
