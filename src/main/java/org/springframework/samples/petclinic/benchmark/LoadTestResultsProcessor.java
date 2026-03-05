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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Processes JMeter load test results and exports to structured JSON format.
 * Parses CSV results, calculates percentiles and throughput metrics.
 */
public class LoadTestResultsProcessor {

	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Process JMeter CSV results file and generate JSON output.
	 *
	 * @param csvResultsPath Path to JMeter results CSV file
	 * @param variant Variant identifier
	 * @param profile Load profile name
	 * @param startTimestamp Test start timestamp (epoch seconds)
	 * @return Path to exported JSON file
	 */
	public String processResults(String csvResultsPath, String variant, String profile, long startTimestamp)
			throws IOException {
		System.out.println(">>> Processing JMeter results: " + csvResultsPath);

		// Parse CSV results
		List<RequestResult> results = parseJMeterCsv(csvResultsPath);

		if (results.isEmpty()) {
			throw new IOException("No results found in CSV file: " + csvResultsPath);
		}

		// Calculate metrics
		Map<String, Object> metrics = calculateMetrics(results);

		// Calculate per-endpoint metrics
		Map<String, Object> endpointMetrics = calculateEndpointMetrics(results);

		// Generate JSON output
		return exportToJson(variant, profile, startTimestamp, metrics, endpointMetrics);
	}

	/**
	 * Parse JMeter CSV results file.
	 * Format: timeStamp,elapsed,label,responseCode,responseMessage,success,...
	 */
	private List<RequestResult> parseJMeterCsv(String csvPath) throws IOException {
		List<RequestResult> results = new ArrayList<>();

		List<String> lines = Files.readAllLines(Paths.get(csvPath));

		// Skip header
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.isEmpty()) {
				continue;
			}

			String[] fields = line.split(",");
			if (fields.length < 6) {
				continue;
			}

			try {
				long timestamp = Long.parseLong(fields[0]);
				long elapsed = Long.parseLong(fields[1]);
				String label = fields[2];
				String responseCode = fields[3];
				String responseMessage = fields[4];
				boolean success = Boolean.parseBoolean(fields[5]);

				results.add(new RequestResult(timestamp, elapsed, label, responseCode, success));
			} catch (Exception e) {
				System.err.println("Failed to parse line: " + line + ", error: " + e.getMessage());
			}
		}

		System.out.println("✓ Parsed " + results.size() + " results from CSV");
		return results;
	}

	/**
	 * Calculate overall metrics from results.
	 */
	private Map<String, Object> calculateMetrics(List<RequestResult> results) {
		Map<String, Object> metrics = new HashMap<>();

		List<Long> latencies = new ArrayList<>();
		int successCount = 0;
		long minLatency = Long.MAX_VALUE;
		long maxLatency = 0;
		long totalLatency = 0;

		for (RequestResult result : results) {
			latencies.add(result.elapsed);
			totalLatency += result.elapsed;

			if (result.elapsed < minLatency) {
				minLatency = result.elapsed;
			}
			if (result.elapsed > maxLatency) {
				maxLatency = result.elapsed;
			}

			if (result.success) {
				successCount++;
			}
		}

		long failureCount = results.size() - successCount;
		double errorRate = (double) failureCount / results.size() * 100;

		// Sort latencies for percentile calculation
		Collections.sort(latencies);

		// Calculate percentiles
		metrics.put("totalRequests", results.size());
		metrics.put("successfulRequests", successCount);
		metrics.put("failedRequests", failureCount);
		metrics.put("errorRate", Math.round(errorRate * 100.0) / 100.0);

		// Latency metrics (in milliseconds)
		metrics.put("minLatency", minLatency);
		metrics.put("maxLatency", maxLatency);
		metrics.put("meanLatency", Math.round((double) totalLatency / results.size() * 100.0) / 100.0);
		metrics.put("p50", calculatePercentile(latencies, 50));
		metrics.put("p75", calculatePercentile(latencies, 75));
		metrics.put("p90", calculatePercentile(latencies, 90));
		metrics.put("p95", calculatePercentile(latencies, 95));
		metrics.put("p99", calculatePercentile(latencies, 99));
		metrics.put("p99_9", calculatePercentile(latencies, 99.9));

		// Throughput (requests per second) - calculate from first and last timestamp
		if (results.size() > 1) {
			RequestResult first = results.get(0);
			RequestResult last = results.get(results.size() - 1);
			long durationSeconds = (last.timestamp - first.timestamp) / 1000;
			if (durationSeconds > 0) {
				double throughput = (double) results.size() / durationSeconds;
				metrics.put("throughput", Math.round(throughput * 100.0) / 100.0);
			}
		}

		// Response time distribution
		Map<String, Long> distribution = new HashMap<>();
		distribution.put("0-100ms", 0L);
		distribution.put("100-250ms", 0L);
		distribution.put("250-500ms", 0L);
		distribution.put("500-1000ms", 0L);
		distribution.put(">1000ms", 0L);

		for (RequestResult result : results) {
			if (result.elapsed <= 100) {
				distribution.put("0-100ms", distribution.get("0-100ms") + 1);
			} else if (result.elapsed <= 250) {
				distribution.put("100-250ms", distribution.get("100-250ms") + 1);
			} else if (result.elapsed <= 500) {
				distribution.put("250-500ms", distribution.get("250-500ms") + 1);
			} else if (result.elapsed <= 1000) {
				distribution.put("500-1000ms", distribution.get("500-1000ms") + 1);
			} else {
				distribution.put(">1000ms", distribution.get(">1000ms") + 1);
			}
		}

		metrics.put("distribution", distribution);

		return metrics;
	}

	/**
	 * Calculate per-endpoint metrics.
	 */
	private Map<String, Object> calculateEndpointMetrics(List<RequestResult> results) {
		Map<String, List<RequestResult>> byEndpoint = new HashMap<>();

		// Group by endpoint
		for (RequestResult result : results) {
			byEndpoint.computeIfAbsent(result.label, k -> new ArrayList<>()).add(result);
		}

		Map<String, Object> endpointMetrics = new HashMap<>();

		// Calculate metrics per endpoint
		for (String endpoint : byEndpoint.keySet()) {
			List<RequestResult> endpointResults = byEndpoint.get(endpoint);
			Map<String, Object> metrics = new HashMap<>();

			List<Long> latencies = new ArrayList<>();
			long minLatency = Long.MAX_VALUE;
			long maxLatency = 0;
			long totalLatency = 0;
			int successCount = 0;

			for (RequestResult result : endpointResults) {
				latencies.add(result.elapsed);
				totalLatency += result.elapsed;

				if (result.elapsed < minLatency) {
					minLatency = result.elapsed;
				}
				if (result.elapsed > maxLatency) {
					maxLatency = result.elapsed;
				}

				if (result.success) {
					successCount++;
				}
			}

			Collections.sort(latencies);

			metrics.put("requests", endpointResults.size());
			metrics.put("minLatency", minLatency);
			metrics.put("maxLatency", maxLatency);
			metrics.put("meanLatency", Math.round((double) totalLatency / endpointResults.size() * 100.0) / 100.0);
			metrics.put("p95Latency", calculatePercentile(latencies, 95));
			metrics.put("p99Latency", calculatePercentile(latencies, 99));
			metrics.put("successRate", Math.round((double) successCount / endpointResults.size() * 10000.0) / 100.0);

			endpointMetrics.put(endpoint, metrics);
		}

		return endpointMetrics;
	}

	/**
	 * Calculate percentile value.
	 */
	private long calculatePercentile(List<Long> sortedValues, double percentile) {
		if (sortedValues.isEmpty()) {
			return 0;
		}

		int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
		index = Math.max(0, Math.min(index, sortedValues.size() - 1));

		return sortedValues.get(index);
	}

	/**
	 * Export metrics to JSON file.
	 */
	private String exportToJson(String variant, String profile, long startTimestamp, Map<String, Object> metrics,
			Map<String, Object> endpointMetrics) throws IOException {
		// Create output directory if needed
		String outputDir = "target/load-test-results";
		Files.createDirectories(Paths.get(outputDir));

		// Generate filename with timestamp
		String timestamp = Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)
				.replace(":", "-").replace("Z", "");
		String filename = String.format("load-test-results-%s-%s-%s.json", variant, profile, timestamp);
		String filepath = outputDir + "/" + filename;

		// Build JSON object
		ObjectNode root = mapper.createObjectNode();
		root.put("timestamp", Instant.now().toString());
		root.put("variant", variant);
		root.put("profile", profile);
		root.put("startTimestamp", startTimestamp);

		// Add metrics
		ObjectNode metricsNode = root.putObject("metrics");
		metrics.forEach((key, value) -> {
			if (value instanceof Integer || value instanceof Long) {
				metricsNode.put(key, ((Number) value).longValue());
			} else if (value instanceof Double || value instanceof Float) {
				metricsNode.put(key, ((Number) value).doubleValue());
			} else if (value instanceof Map) {
				// Distribution map
				ObjectNode distNode = metricsNode.putObject(key);
				@SuppressWarnings("unchecked")
				Map<String, Long> distMap = (Map<String, Long>) value;
				distMap.forEach(distNode::put);
			} else {
				metricsNode.put(key, String.valueOf(value));
			}
		});

		// Add endpoint metrics
		ObjectNode endpointNode = root.putObject("endpointMetrics");
		endpointMetrics.forEach((endpoint, metrics_) -> {
			ObjectNode epNode = endpointNode.putObject(endpoint);
			@SuppressWarnings("unchecked")
			Map<String, Object> epMetrics = (Map<String, Object>) metrics_;
			epMetrics.forEach((key, value) -> {
				if (value instanceof Integer || value instanceof Long) {
					epNode.put(key, ((Number) value).longValue());
				} else if (value instanceof Double || value instanceof Float) {
					epNode.put(key, ((Number) value).doubleValue());
				} else {
					epNode.put(key, String.valueOf(value));
				}
			});
		});

		// Write JSON file
		Files.writeString(Paths.get(filepath), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));

		System.out.println("✓ Results exported to: " + filepath);
		return filepath;
	}

	/**
	 * Internal class to hold a single request result.
	 */
	private static class RequestResult {

		long timestamp;
		long elapsed;
		String label;
		String responseCode;
		boolean success;

		RequestResult(long timestamp, long elapsed, String label, String responseCode, boolean success) {
			this.timestamp = timestamp;
			this.elapsed = elapsed;
			this.label = label;
			this.responseCode = responseCode;
			this.success = success;
		}

	}

}
