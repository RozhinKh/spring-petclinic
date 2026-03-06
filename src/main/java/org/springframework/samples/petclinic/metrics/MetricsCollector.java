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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Collects metrics from Spring Boot Actuator /actuator/metrics endpoint at configurable
 * intervals. Supports polling during benchmark execution for correlation with JMH/JFR
 * events. Handles HTTP, JVM, thread pool, cache, and database metrics.
 */
@Component
public class MetricsCollector {

	private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

	private static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;

	private static final String ACTUATOR_METRICS_URL = "http://localhost:8080/actuator/metrics";

	private final RestTemplate restTemplate;

	private final MetricsExporter exporter;

	private final ScheduledExecutorService scheduler;

	private final AtomicBoolean collecting;

	private final List<Map<String, Object>> collectedSnapshots;

	private String currentVariant;

	private int pollIntervalSeconds;

	private final ObjectMapper objectMapper;

	public MetricsCollector() {
		this.restTemplate = new RestTemplate();
		this.exporter = new MetricsExporter();
		this.scheduler = Executors.newScheduledThreadPool(1, r -> {
			Thread t = new Thread(r, "MetricsCollectorThread");
			t.setDaemon(true);
			return t;
		});
		this.collecting = new AtomicBoolean(false);
		this.collectedSnapshots = new ArrayList<>();
		this.currentVariant = "default";
		this.pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS;
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * Start metrics collection with default interval (5 seconds)
	 * @param variant variant name for labeling metrics
	 */
	public void start(String variant) {
		start(variant, DEFAULT_POLL_INTERVAL_SECONDS);
	}

	/**
	 * Start metrics collection with custom interval
	 * @param variant variant name for labeling metrics
	 * @param pollIntervalSeconds interval between polls in seconds
	 */
	public void start(String variant, int pollIntervalSeconds) {
		if (collecting.getAndSet(true)) {
			logger.warn("Metrics collection already in progress");
			return;
		}

		this.currentVariant = variant;
		this.pollIntervalSeconds = pollIntervalSeconds;
		this.collectedSnapshots.clear();

		logger.info("Starting metrics collection for variant: {} with interval: {}s", variant, pollIntervalSeconds);

		// Schedule periodic polling
		scheduler.scheduleAtFixedRate(this::pollMetrics, 0, pollIntervalSeconds, TimeUnit.SECONDS);
	}

	/**
	 * Stop metrics collection and export to JSON
	 * @return path to exported metrics file
	 */
	public String stop() {
		if (!collecting.getAndSet(false)) {
			logger.warn("Metrics collection not in progress");
			return null;
		}

		// Stop the scheduled task
		scheduler.shutdown();

		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduler.shutdownNow();
			}
		}
		catch (InterruptedException e) {
			scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}

		logger.info("Stopped metrics collection. Collected {} snapshots", collectedSnapshots.size());

		// Export collected metrics
		try {
			String filePath = exporter.exportMetricsBatch(collectedSnapshots, currentVariant);
			logger.info("Exported metrics to: {}", filePath);
			return filePath;
		}
		catch (IOException e) {
			logger.error("Failed to export metrics", e);
			return null;
		}
	}

	/**
	 * Poll metrics from Actuator endpoint
	 */
	private void pollMetrics() {
		try {
			long timestamp = System.currentTimeMillis();

			// Extract metrics from endpoint
			Map<String, Object> httpMetrics = extractHttpMetrics();
			Map<String, Object> jvmMetrics = extractJvmMetrics();
			Map<String, Object> threadMetrics = extractThreadMetrics();
			Map<String, Object> cacheMetrics = extractCacheMetrics();
			Map<String, Object> dbMetrics = extractDatabaseMetrics();

			// Create snapshot
			Map<String, Object> snapshot = MetricsExporter.createMetricsSnapshot(timestamp, httpMetrics, jvmMetrics,
					threadMetrics, cacheMetrics, dbMetrics);

			// Store snapshot
			synchronized (collectedSnapshots) {
				collectedSnapshots.add(snapshot);
			}

		}
		catch (Exception e) {
			logger.debug("Failed to poll metrics: {}", e.getMessage());
		}
	}

	/**
	 * Extract HTTP metrics from Actuator endpoint
	 */
	private Map<String, Object> extractHttpMetrics() {
		Map<String, Object> httpMetrics = new HashMap<>();

		try {
			// Fetch http.server.requests metric
			String url = ACTUATOR_METRICS_URL + "/http.server.requests";
			Map response = restTemplate.getForObject(url, Map.class);

			if (response != null) {
				// Extract mean value
				Map measurements = (Map) response.get("measurements");
				if (measurements != null) {
					List<Map<String, Object>> measurementList = (List<Map<String, Object>>) measurements;
					for (Map<String, Object> m : measurementList) {
						String statistic = (String) m.get("statistic");
						Double value = ((Number) m.get("value")).doubleValue();

						if ("MEAN".equals(statistic)) {
							httpMetrics.put("mean_response_time_ms", value);
						}
						else if ("MAX".equals(statistic)) {
							httpMetrics.put("max_response_time_ms", value);
						}
						else if ("COUNT".equals(statistic)) {
							httpMetrics.put("request_count", value);
						}
					}
				}

				// Extract tags (status codes, methods)
				List tags = (List) response.get("availableTags");
				if (tags != null) {
					httpMetrics.put("available_tags", tags);
				}
			}
		}
		catch (Exception e) {
			logger.debug("Failed to extract HTTP metrics: {}", e.getMessage());
		}

		return httpMetrics;
	}

	/**
	 * Extract JVM metrics from Actuator endpoint
	 */
	private Map<String, Object> extractJvmMetrics() {
		Map<String, Object> jvmMetrics = new HashMap<>();

		try {
			// Memory metrics
			jvmMetrics.putAll(extractMemoryMetrics());

			// GC metrics
			jvmMetrics.putAll(extractGcMetrics());

			// Thread count
			jvmMetrics.putAll(extractThreadCountMetrics());

		}
		catch (Exception e) {
			logger.debug("Failed to extract JVM metrics: {}", e.getMessage());
		}

		return jvmMetrics;
	}

	/**
	 * Extract JVM memory metrics
	 */
	private Map<String, Object> extractMemoryMetrics() {
		Map<String, Object> memoryMetrics = new HashMap<>();

		try {
			// Heap memory
			String heapUrl = ACTUATOR_METRICS_URL + "/jvm.memory.used?tag=area:heap";
			Map heapResponse = restTemplate.getForObject(heapUrl, Map.class);
			if (heapResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) heapResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					memoryMetrics.put("heap_memory_used_bytes", value);
				}
			}

			// Heap max
			String heapMaxUrl = ACTUATOR_METRICS_URL + "/jvm.memory.max?tag=area:heap";
			Map heapMaxResponse = restTemplate.getForObject(heapMaxUrl, Map.class);
			if (heapMaxResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) heapMaxResponse
					.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					memoryMetrics.put("heap_memory_max_bytes", value);
				}
			}

			// Non-heap memory
			String nonHeapUrl = ACTUATOR_METRICS_URL + "/jvm.memory.used?tag=area:nonheap";
			Map nonHeapResponse = restTemplate.getForObject(nonHeapUrl, Map.class);
			if (nonHeapResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) nonHeapResponse
					.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					memoryMetrics.put("nonheap_memory_used_bytes", value);
				}
			}

		}
		catch (Exception e) {
			logger.debug("Failed to extract memory metrics: {}", e.getMessage());
		}

		return memoryMetrics;
	}

	/**
	 * Extract GC metrics
	 */
	private Map<String, Object> extractGcMetrics() {
		Map<String, Object> gcMetrics = new HashMap<>();

		try {
			// GC pause time
			String pauseUrl = ACTUATOR_METRICS_URL + "/jvm.gc.pause";
			Map pauseResponse = restTemplate.getForObject(pauseUrl, Map.class);
			if (pauseResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) pauseResponse.get("measurements");
				if (measurements != null) {
					for (Map<String, Object> m : measurements) {
						String statistic = (String) m.get("statistic");
						Double value = ((Number) m.get("value")).doubleValue();

						if ("MEAN".equals(statistic)) {
							gcMetrics.put("gc_pause_mean_ms", value);
						}
						else if ("MAX".equals(statistic)) {
							gcMetrics.put("gc_pause_max_ms", value);
						}
						else if ("COUNT".equals(statistic)) {
							gcMetrics.put("gc_pause_count", value);
						}
					}
				}
			}

			// GC max data size
			String maxDataSizeUrl = ACTUATOR_METRICS_URL + "/jvm.gc.max.data.size";
			Map maxDataSizeResponse = restTemplate.getForObject(maxDataSizeUrl, Map.class);
			if (maxDataSizeResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) maxDataSizeResponse
					.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					gcMetrics.put("gc_max_data_size_bytes", value);
				}
			}

		}
		catch (Exception e) {
			logger.debug("Failed to extract GC metrics: {}", e.getMessage());
		}

		return gcMetrics;
	}

	/**
	 * Extract thread count metrics
	 */
	private Map<String, Object> extractThreadCountMetrics() {
		Map<String, Object> threadMetrics = new HashMap<>();

		try {
			// Live threads
			String liveUrl = ACTUATOR_METRICS_URL + "/jvm.threads.live";
			Map liveResponse = restTemplate.getForObject(liveUrl, Map.class);
			if (liveResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) liveResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					threadMetrics.put("live_threads", value);
				}
			}

			// Peak threads
			String peakUrl = ACTUATOR_METRICS_URL + "/jvm.threads.peak";
			Map peakResponse = restTemplate.getForObject(peakUrl, Map.class);
			if (peakResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) peakResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					threadMetrics.put("peak_threads", value);
				}
			}

			// Daemon threads
			String daemonUrl = ACTUATOR_METRICS_URL + "/jvm.threads.daemon";
			Map daemonResponse = restTemplate.getForObject(daemonUrl, Map.class);
			if (daemonResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) daemonResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					threadMetrics.put("daemon_threads", value);
				}
			}

		}
		catch (Exception e) {
			logger.debug("Failed to extract thread count metrics: {}", e.getMessage());
		}

		return threadMetrics;
	}

	/**
	 * Extract thread pool metrics (for both platform and virtual threads)
	 */
	private Map<String, Object> extractThreadMetrics() {
		Map<String, Object> threadMetrics = new HashMap<>();

		try {
			// Thread pool active count
			String activeUrl = ACTUATOR_METRICS_URL + "/executor.active";
			Map activeResponse = restTemplate.getForObject(activeUrl, Map.class);
			if (activeResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) activeResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					threadMetrics.put("executor_active_threads", value);
				}
			}

			// Thread pool queued
			String queuedUrl = ACTUATOR_METRICS_URL + "/executor.queued";
			Map queuedResponse = restTemplate.getForObject(queuedUrl, Map.class);
			if (queuedResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) queuedResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					threadMetrics.put("executor_queued_tasks", value);
				}
			}

			// Thread pool size
			String poolSizeUrl = ACTUATOR_METRICS_URL + "/executor.pool.size";
			Map poolSizeResponse = restTemplate.getForObject(poolSizeUrl, Map.class);
			if (poolSizeResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) poolSizeResponse
					.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					threadMetrics.put("executor_pool_size", value);
				}
			}

			// Thread pool max
			String poolMaxUrl = ACTUATOR_METRICS_URL + "/executor.pool.max";
			Map poolMaxResponse = restTemplate.getForObject(poolMaxUrl, Map.class);
			if (poolMaxResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) poolMaxResponse
					.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					threadMetrics.put("executor_pool_max", value);
				}
			}

		}
		catch (Exception e) {
			logger.debug("Failed to extract thread pool metrics: {}", e.getMessage());
		}

		return threadMetrics;
	}

	/**
	 * Extract cache metrics for Caffeine backend
	 */
	private Map<String, Object> extractCacheMetrics() {
		Map<String, Object> cacheMetrics = new HashMap<>();

		try {
			// Get cache metrics (vets cache)
			String getsUrl = ACTUATOR_METRICS_URL + "/cache.gets?tag=cache:vets";
			Map getsResponse = restTemplate.getForObject(getsUrl, Map.class);
			if (getsResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) getsResponse.get("measurements");
				if (measurements != null) {
					for (Map<String, Object> m : measurements) {
						String statistic = (String) m.get("statistic");
						Double value = ((Number) m.get("value")).doubleValue();

						if ("COUNT".equals(statistic)) {
							cacheMetrics.put("cache_gets_count", value);
						}
					}
				}

				// Extract tags to track hit/miss
				List tags = (List) getsResponse.get("availableTags");
				if (tags != null) {
					cacheMetrics.put("cache_available_tags", tags);
				}
			}

			// Cache puts
			String putsUrl = ACTUATOR_METRICS_URL + "/cache.puts?tag=cache:vets";
			Map putsResponse = restTemplate.getForObject(putsUrl, Map.class);
			if (putsResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) putsResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					cacheMetrics.put("cache_puts_count", value);
				}
			}

			// Cache evictions
			String evictionsUrl = ACTUATOR_METRICS_URL + "/cache.evictions?tag=cache:vets";
			Map evictionsResponse = restTemplate.getForObject(evictionsUrl, Map.class);
			if (evictionsResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) evictionsResponse
					.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					cacheMetrics.put("cache_evictions_count", value);
				}
			}

		}
		catch (Exception e) {
			logger.debug("Failed to extract cache metrics: {}", e.getMessage());
		}

		return cacheMetrics;
	}

	/**
	 * Extract database connection pool metrics (HikariCP)
	 */
	private Map<String, Object> extractDatabaseMetrics() {
		Map<String, Object> dbMetrics = new HashMap<>();

		try {
			// Active connections
			String activeUrl = ACTUATOR_METRICS_URL + "/hikaricp.connections.active";
			Map activeResponse = restTemplate.getForObject(activeUrl, Map.class);
			if (activeResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) activeResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					dbMetrics.put("hikari_active_connections", value);
				}
			}

			// Idle connections
			String idleUrl = ACTUATOR_METRICS_URL + "/hikaricp.connections.idle";
			Map idleResponse = restTemplate.getForObject(idleUrl, Map.class);
			if (idleResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) idleResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					dbMetrics.put("hikari_idle_connections", value);
				}
			}

			// Pending connections
			String pendingUrl = ACTUATOR_METRICS_URL + "/hikaricp.connections.pending";
			Map pendingResponse = restTemplate.getForObject(pendingUrl, Map.class);
			if (pendingResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) pendingResponse
					.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					dbMetrics.put("hikari_pending_connections", value);
				}
			}

			// Max connections
			String maxUrl = ACTUATOR_METRICS_URL + "/hikaricp.connections.max";
			Map maxResponse = restTemplate.getForObject(maxUrl, Map.class);
			if (maxResponse != null) {
				List<Map<String, Object>> measurements = (List<Map<String, Object>>) maxResponse.get("measurements");
				if (measurements != null && !measurements.isEmpty()) {
					Double value = ((Number) measurements.get(0).get("value")).doubleValue();
					dbMetrics.put("hikari_max_connections", value);
				}
			}

		}
		catch (Exception e) {
			logger.debug("Failed to extract database metrics: {}", e.getMessage());
		}

		return dbMetrics;
	}

	/**
	 * Get current collected snapshots (for testing)
	 */
	public List<Map<String, Object>> getCollectedSnapshots() {
		synchronized (collectedSnapshots) {
			return new ArrayList<>(collectedSnapshots);
		}
	}

	/**
	 * Clear collected snapshots
	 */
	public void clearSnapshots() {
		synchronized (collectedSnapshots) {
			collectedSnapshots.clear();
		}
	}

	/**
	 * Check if collection is in progress
	 */
	public boolean isCollecting() {
		return collecting.get();
	}

}
