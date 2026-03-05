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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Benchmark metrics harness for integrating with JMH/JFR benchmark execution.
 * Provides methods to start/stop metrics collection during benchmark phases.
 * Supports correlation of metrics with JMH benchmark windows using matching timestamps.
 */
@Component
public class BenchmarkMetricsHarness {

	private static final Logger logger = LoggerFactory.getLogger(BenchmarkMetricsHarness.class);

	private final MetricsCollector metricsCollector;

	private final CaffeineCacheMetricsCollector cacheMetricsCollector;

	private final HttpMetricsListener httpMetricsListener;

	private final List<String> exportedMetricsFiles = new ArrayList<>();

	private final Map<String, Long> benchmarkStartTimes = new HashMap<>();

	@Autowired
	public BenchmarkMetricsHarness(MetricsCollector metricsCollector,
			CaffeineCacheMetricsCollector cacheMetricsCollector,
			HttpMetricsListener httpMetricsListener) {
		this.metricsCollector = metricsCollector;
		this.cacheMetricsCollector = cacheMetricsCollector;
		this.httpMetricsListener = httpMetricsListener;
	}

	/**
	 * Start metrics collection for a benchmark phase
	 *
	 * @param benchmarkName name of the benchmark (e.g., "startup", "throughput",
	 *                      "latency")
	 * @param variant variant name (e.g., "java17", "java21-traditional",
	 *                "java21-virtual")
	 * @param pollIntervalSeconds metrics polling interval in seconds
	 */
	public void startBenchmark(String benchmarkName, String variant, int pollIntervalSeconds) {
		logger.info("Starting metrics collection for benchmark: {} (variant: {})", benchmarkName,
				variant);

		// Record start time for correlation
		String key = benchmarkName + "-" + variant;
		benchmarkStartTimes.put(key, System.currentTimeMillis());

		// Reset metrics
		httpMetricsListener.reset();
		cacheMetricsCollector.resetAll();
		metricsCollector.clearSnapshots();

		// Start metrics collection
		metricsCollector.start(variant, pollIntervalSeconds);
	}

	/**
	 * Stop metrics collection for a benchmark phase
	 *
	 * @param benchmarkName name of the benchmark
	 * @param variant variant name
	 * @return exported file path
	 */
	public String stopBenchmark(String benchmarkName, String variant) {
		logger.info("Stopping metrics collection for benchmark: {} (variant: {})", benchmarkName,
				variant);

		// Stop collection
		String exportedFile = metricsCollector.stop();

		if (exportedFile != null) {
			exportedMetricsFiles.add(exportedFile);
			logger.info("Metrics exported to: {}", exportedFile);

			// Log cache metrics
			logCacheMetrics();

			// Log HTTP metrics
			logHttpMetrics();

			return exportedFile;
		}

		return null;
	}

	/**
	 * Log cache metrics summary
	 */
	private void logCacheMetrics() {
		Map<String, CaffeineCacheMetricsCollector.CacheMetrics> allMetrics = cacheMetricsCollector
				.getAllMetrics();

		if (!allMetrics.isEmpty()) {
			logger.info("=== Cache Metrics Summary ===");
			for (Map.Entry<String, CaffeineCacheMetricsCollector.CacheMetrics> entry : allMetrics
					.entrySet()) {
				CaffeineCacheMetricsCollector.CacheMetrics metrics = entry.getValue();
				logger.info("Cache: {}", metrics.getCacheName());
				logger.info("  Hits: {}, Misses: {}, Hit Rate: {:.2f}%", metrics.getHits(),
						metrics.getMisses(), metrics.getHitRate());
				logger.info("  Evictions: {}, Removals: {}, Size: {}", metrics.getEvictions(),
						metrics.getRemovals(), metrics.getSize());
			}
		}
	}

	/**
	 * Log HTTP metrics summary
	 */
	private void logHttpMetrics() {
		logger.info("=== HTTP Metrics Summary ===");
		logger.info("Total Requests: {}", httpMetricsListener.getTotalRequests());
		logger.info("Total Errors: {}", httpMetricsListener.getTotalErrors());

		Map<Integer, Long> errorsByStatus = httpMetricsListener.getErrorCountByStatus();
		if (!errorsByStatus.isEmpty()) {
			logger.info("Errors by Status Code:");
			errorsByStatus.forEach((status, count) -> logger.info("  {}: {}", status, count));
		}
	}

	/**
	 * Get list of exported metrics files
	 */
	public List<String> getExportedMetricsFiles() {
		return new ArrayList<>(exportedMetricsFiles);
	}

	/**
	 * Get cached metrics summary as map
	 */
	public Map<String, Map<String, Object>> getCacheMetricsSummary() {
		Map<String, Map<String, Object>> summary = new HashMap<>();

		Map<String, CaffeineCacheMetricsCollector.CacheMetrics> allMetrics = cacheMetricsCollector
				.getAllMetrics();
		for (Map.Entry<String, CaffeineCacheMetricsCollector.CacheMetrics> entry : allMetrics
				.entrySet()) {
			summary.put(entry.getKey(), entry.getValue().toMap());
		}

		return summary;
	}

	/**
	 * Get HTTP metrics summary as map
	 */
	public Map<String, Object> getHttpMetricsSummary() {
		Map<String, Object> summary = new HashMap<>();
		summary.put("total_requests", httpMetricsListener.getTotalRequests());
		summary.put("total_errors", httpMetricsListener.getTotalErrors());
		summary.put("errors_by_status", httpMetricsListener.getErrorCountByStatus());
		return summary;
	}

	/**
	 * Get collected snapshots
	 */
	public List<Map<String, Object>> getCollectedSnapshots() {
		return metricsCollector.getCollectedSnapshots();
	}

	/**
	 * Check if metrics collection is in progress
	 */
	public boolean isCollecting() {
		return metricsCollector.isCollecting();
	}

}
