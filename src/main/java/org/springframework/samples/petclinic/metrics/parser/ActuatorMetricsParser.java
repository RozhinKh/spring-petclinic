package org.springframework.samples.petclinic.metrics.parser;

import org.springframework.samples.petclinic.metrics.NormalizedMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Spring Boot Actuator metrics: HTTP, JVM, cache, database metrics
 */
public class ActuatorMetricsParser {

	private static final ObjectMapper mapper = new ObjectMapper();

	public List<NormalizedMetric> parse(File actuatorMetricsFile, String variant, Integer runNumber)
			throws IOException {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!actuatorMetricsFile.exists()) {
			return metrics;
		}

		JsonNode root = mapper.readTree(actuatorMetricsFile);

		// Parse HTTP metrics
		metrics.addAll(parseHttpMetrics(root, variant, runNumber));

		// Parse JVM metrics
		metrics.addAll(parseJvmMetrics(root, variant, runNumber));

		// Parse cache metrics
		metrics.addAll(parseCacheMetrics(root, variant, runNumber));

		// Parse database metrics
		metrics.addAll(parseDatabaseMetrics(root, variant, runNumber));

		return metrics;
	}

	private List<NormalizedMetric> parseHttpMetrics(JsonNode root, String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode httpNode = root.path("http");
		if (httpNode.isObject()) {
			Double requestCount = httpNode.path("request_count").asDouble(Double.NaN);
			if (!Double.isNaN(requestCount)) {
				metrics.add(new NormalizedMetric("http_request_count", requestCount, "count", variant, runNumber,
						"throughput", "actuator"));
			}

			Double avgResponse = httpNode.path("avg_response_time_ms").asDouble(Double.NaN);
			if (!Double.isNaN(avgResponse)) {
				metrics.add(new NormalizedMetric("http_avg_response_time", avgResponse, "ms", variant, runNumber,
						"latency", "actuator"));
			}

			Double maxResponse = httpNode.path("max_response_time_ms").asDouble(Double.NaN);
			if (!Double.isNaN(maxResponse)) {
				metrics.add(new NormalizedMetric("http_max_response_time", maxResponse, "ms", variant, runNumber,
						"latency", "actuator"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseJvmMetrics(JsonNode root, String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode jvmNode = root.path("jvm");
		if (jvmNode.isObject()) {
			// Memory metrics
			JsonNode memoryNode = jvmNode.path("memory");
			if (memoryNode.isObject()) {
				Double heapUsed = memoryNode.path("heap_used_mb").asDouble(Double.NaN);
				if (!Double.isNaN(heapUsed)) {
					metrics.add(new NormalizedMetric("jvm_memory_heap_used", heapUsed, "MB", variant, runNumber,
							"memory", "actuator"));
				}

				Double heapMax = memoryNode.path("heap_max_mb").asDouble(Double.NaN);
				if (!Double.isNaN(heapMax)) {
					metrics.add(new NormalizedMetric("jvm_memory_heap_max", heapMax, "MB", variant, runNumber, "memory",
							"actuator"));
				}
			}

			// GC metrics
			JsonNode gcNode = jvmNode.path("gc");
			if (gcNode.isObject()) {
				Double gcTime = gcNode.path("gc_time_ms").asDouble(Double.NaN);
				if (!Double.isNaN(gcTime)) {
					metrics
						.add(new NormalizedMetric("jvm_gc_time", gcTime, "ms", variant, runNumber, "gc", "actuator"));
				}

				Double gcCount = gcNode.path("gc_count").asDouble(Double.NaN);
				if (!Double.isNaN(gcCount)) {
					metrics.add(new NormalizedMetric("jvm_gc_count", gcCount, "count", variant, runNumber, "gc",
							"actuator"));
				}
			}

			// Thread metrics
			JsonNode threadNode = jvmNode.path("threads");
			if (threadNode.isObject()) {
				Double threadCount = threadNode.path("count").asDouble(Double.NaN);
				if (!Double.isNaN(threadCount)) {
					metrics.add(new NormalizedMetric("jvm_thread_count", threadCount, "count", variant, runNumber,
							"threading", "actuator"));
				}

				Double peakThreadCount = threadNode.path("peak_count").asDouble(Double.NaN);
				if (!Double.isNaN(peakThreadCount)) {
					metrics.add(new NormalizedMetric("jvm_thread_peak_count", peakThreadCount, "count", variant,
							runNumber, "threading", "actuator"));
				}
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseCacheMetrics(JsonNode root, String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode cacheNode = root.path("cache");
		if (cacheNode.isObject()) {
			Double hitCount = cacheNode.path("hit_count").asDouble(Double.NaN);
			if (!Double.isNaN(hitCount)) {
				metrics.add(new NormalizedMetric("cache_hit_count", hitCount, "count", variant, runNumber, "throughput",
						"actuator"));
			}

			Double missCount = cacheNode.path("miss_count").asDouble(Double.NaN);
			if (!Double.isNaN(missCount)) {
				metrics.add(new NormalizedMetric("cache_miss_count", missCount, "count", variant, runNumber,
						"throughput", "actuator"));
			}

			if (!Double.isNaN(hitCount) && !Double.isNaN(missCount)) {
				double total = hitCount + missCount;
				double hitRate = total > 0 ? (hitCount * 100.0 / total) : 0.0;
				metrics.add(new NormalizedMetric("cache_hit_rate", hitRate, "%", variant, runNumber, "throughput",
						"actuator"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseDatabaseMetrics(JsonNode root, String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode dbNode = root.path("database");
		if (dbNode.isObject()) {
			Double activeConnections = dbNode.path("active_connections").asDouble(Double.NaN);
			if (!Double.isNaN(activeConnections)) {
				metrics.add(new NormalizedMetric("db_active_connections", activeConnections, "count", variant,
						runNumber, "throughput", "actuator"));
			}

			Double poolSize = dbNode.path("pool_size").asDouble(Double.NaN);
			if (!Double.isNaN(poolSize)) {
				metrics.add(new NormalizedMetric("db_pool_size", poolSize, "count", variant, runNumber, "throughput",
						"actuator"));
			}

			Double queryTime = dbNode.path("avg_query_time_ms").asDouble(Double.NaN);
			if (!Double.isNaN(queryTime)) {
				metrics.add(new NormalizedMetric("db_avg_query_time", queryTime, "ms", variant, runNumber, "latency",
						"actuator"));
			}
		}

		return metrics;
	}

}
