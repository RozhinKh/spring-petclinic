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
package org.springframework.samples.petclinic.metrics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Exports collected metrics to JSON format with timestamped files.
 * Stores metrics in target/metrics/ directory for correlation with JMH/JFR events.
 */
public class MetricsExporter {

	private static final String DEFAULT_METRICS_DIR = "target/metrics";

	private static final ObjectMapper objectMapper;

	static {
		objectMapper = new ObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	private final String metricsDirectory;

	/**
	 * Create exporter with default metrics directory (target/metrics)
	 */
	public MetricsExporter() {
		this(DEFAULT_METRICS_DIR);
	}

	/**
	 * Create exporter with custom metrics directory
	 */
	public MetricsExporter(String metricsDirectory) {
		this.metricsDirectory = metricsDirectory;
		ensureDirectoryExists();
	}

	/**
	 * Export metrics snapshot to JSON file
	 *
	 * @param metrics the metrics to export
	 * @param variant the variant name (e.g., "java17", "java21-traditional",
	 *                "java21-virtual")
	 * @return the exported file path
	 * @throws IOException if export fails
	 */
	public String exportMetrics(Map<String, Object> metrics, String variant) throws IOException {
		String filename = generateFilename(variant);
		String filePath = metricsDirectory + File.separator + filename;

		// Create metrics export object with schema
		Map<String, Object> exportData = new HashMap<>();
		exportData.put("timestamp", Instant.now().toString());
		exportData.put("variant", variant);
		exportData.put("metrics", metrics);

		// Write to file
		Files.createDirectories(Paths.get(metricsDirectory));
		objectMapper.writeValue(new File(filePath), exportData);

		return filePath;
	}

	/**
	 * Export batch of metrics snapshots
	 *
	 * @param metricsBatch list of metric snapshots
	 * @param variant the variant name
	 * @return the exported file path
	 * @throws IOException if export fails
	 */
	public String exportMetricsBatch(List<Map<String, Object>> metricsBatch, String variant)
			throws IOException {
		String filename = generateBatchFilename(variant);
		String filePath = metricsDirectory + File.separator + filename;

		// Create batch export object
		Map<String, Object> exportData = new HashMap<>();
		exportData.put("export_timestamp", Instant.now().toString());
		exportData.put("variant", variant);
		exportData.put("snapshot_count", metricsBatch.size());
		exportData.put("snapshots", metricsBatch);

		// Write to file
		Files.createDirectories(Paths.get(metricsDirectory));
		objectMapper.writeValue(new File(filePath), exportData);

		return filePath;
	}

	/**
	 * Generate timestamped filename for single metrics snapshot
	 */
	private String generateFilename(String variant) {
		long timestamp = System.currentTimeMillis();
		return String.format("metrics-%s-%d.json", sanitizeVariant(variant), timestamp);
	}

	/**
	 * Generate timestamped filename for batch export
	 */
	private String generateBatchFilename(String variant) {
		long timestamp = System.currentTimeMillis();
		return String.format("metrics-batch-%s-%d.json", sanitizeVariant(variant), timestamp);
	}

	/**
	 * Sanitize variant name for use in filename
	 */
	private String sanitizeVariant(String variant) {
		return variant.toLowerCase().replaceAll("[^a-z0-9-]", "-");
	}

	/**
	 * Ensure metrics directory exists
	 */
	private void ensureDirectoryExists() {
		try {
			Files.createDirectories(Paths.get(metricsDirectory));
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to create metrics directory: " + metricsDirectory, e);
		}
	}

	/**
	 * Create a metrics snapshot from raw actuator data
	 *
	 * @param timestamp snapshot timestamp
	 * @param httpMetrics HTTP metrics
	 * @param jvmMetrics JVM metrics
	 * @param threadMetrics thread pool metrics
	 * @param cacheMetrics cache metrics
	 * @param dbMetrics database metrics
	 * @return metrics snapshot map
	 */
	public static Map<String, Object> createMetricsSnapshot(Long timestamp,
			Map<String, Object> httpMetrics, Map<String, Object> jvmMetrics,
			Map<String, Object> threadMetrics, Map<String, Object> cacheMetrics,
			Map<String, Object> dbMetrics) {

		Map<String, Object> snapshot = new HashMap<>();
		snapshot.put("timestamp_ms", timestamp);
		snapshot.put("timestamp_iso", Instant.ofEpochMilli(timestamp).toString());

		// Add categorized metrics
		Map<String, Object> categories = new HashMap<>();
		if (httpMetrics != null && !httpMetrics.isEmpty()) {
			categories.put("http", httpMetrics);
		}
		if (jvmMetrics != null && !jvmMetrics.isEmpty()) {
			categories.put("jvm", jvmMetrics);
		}
		if (threadMetrics != null && !threadMetrics.isEmpty()) {
			categories.put("threads", threadMetrics);
		}
		if (cacheMetrics != null && !cacheMetrics.isEmpty()) {
			categories.put("cache", cacheMetrics);
		}
		if (dbMetrics != null && !dbMetrics.isEmpty()) {
			categories.put("database", dbMetrics);
		}

		snapshot.put("metrics", categories);
		return snapshot;
	}

}
