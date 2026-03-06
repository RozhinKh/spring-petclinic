package org.springframework.samples.petclinic.metrics.parser;

import org.springframework.samples.petclinic.metrics.NormalizedMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for JFR (Java Flight Recorder) parsed metrics. Extracts: GC pause times, thread
 * counts, memory allocation, blocking event data
 */
public class JfrMetricsParser {

	private static final ObjectMapper mapper = new ObjectMapper();

	public List<NormalizedMetric> parse(File jfrMetricsFile, String variant, Integer runNumber) throws IOException {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!jfrMetricsFile.exists()) {
			return metrics;
		}

		JsonNode root = mapper.readTree(jfrMetricsFile);

		// Parse GC metrics
		metrics.addAll(parseGcMetrics(root, variant, runNumber));

		// Parse thread metrics
		metrics.addAll(parseThreadMetrics(root, variant, runNumber));

		// Parse memory metrics
		metrics.addAll(parseMemoryMetrics(root, variant, runNumber));

		// Parse blocking metrics
		metrics.addAll(parseBlockingMetrics(root, variant, runNumber));

		return metrics;
	}

	private List<NormalizedMetric> parseGcMetrics(JsonNode root, String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode gcNode = root.path("gc");
		if (gcNode.isObject()) {
			// GC pause times
			Double gcPauseAvg = gcNode.path("pause_time_avg_ms").asDouble(Double.NaN);
			if (!Double.isNaN(gcPauseAvg)) {
				metrics
					.add(new NormalizedMetric("gc_pause_time_avg", gcPauseAvg, "ms", variant, runNumber, "gc", "jfr"));
			}

			Double gcPauseMax = gcNode.path("pause_time_max_ms").asDouble(Double.NaN);
			if (!Double.isNaN(gcPauseMax)) {
				metrics
					.add(new NormalizedMetric("gc_pause_time_max", gcPauseMax, "ms", variant, runNumber, "gc", "jfr"));
			}

			Double gcCount = gcNode.path("collection_count").asDouble(Double.NaN);
			if (!Double.isNaN(gcCount)) {
				metrics.add(
						new NormalizedMetric("gc_collection_count", gcCount, "count", variant, runNumber, "gc", "jfr"));
			}

			Double gcDuration = gcNode.path("total_pause_duration_ms").asDouble(Double.NaN);
			if (!Double.isNaN(gcDuration)) {
				metrics.add(new NormalizedMetric("gc_total_pause_duration", gcDuration, "ms", variant, runNumber, "gc",
						"jfr"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseThreadMetrics(JsonNode root, String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode threadNode = root.path("threads");
		if (threadNode.isObject()) {
			Double threadCount = threadNode.path("count").asDouble(Double.NaN);
			if (!Double.isNaN(threadCount)) {
				metrics.add(new NormalizedMetric("thread_count", threadCount, "count", variant, runNumber, "threading",
						"jfr"));
			}

			Double peakThreadCount = threadNode.path("peak_count").asDouble(Double.NaN);
			if (!Double.isNaN(peakThreadCount)) {
				metrics.add(new NormalizedMetric("thread_peak_count", peakThreadCount, "count", variant, runNumber,
						"threading", "jfr"));
			}

			Double virtualThreadCount = threadNode.path("virtual_thread_count").asDouble(Double.NaN);
			if (!Double.isNaN(virtualThreadCount)) {
				metrics.add(new NormalizedMetric("virtual_thread_count", virtualThreadCount, "count", variant,
						runNumber, "threading", "jfr"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseMemoryMetrics(JsonNode root, String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode memoryNode = root.path("memory");
		if (memoryNode.isObject()) {
			Double heapUsed = memoryNode.path("heap_used_mb").asDouble(Double.NaN);
			if (!Double.isNaN(heapUsed)) {
				metrics
					.add(new NormalizedMetric("memory_heap_used", heapUsed, "MB", variant, runNumber, "memory", "jfr"));
			}

			Double heapMax = memoryNode.path("heap_max_mb").asDouble(Double.NaN);
			if (!Double.isNaN(heapMax)) {
				metrics
					.add(new NormalizedMetric("memory_heap_max", heapMax, "MB", variant, runNumber, "memory", "jfr"));
			}

			Double allocRate = memoryNode.path("allocation_rate_mb_per_sec").asDouble(Double.NaN);
			if (!Double.isNaN(allocRate)) {
				metrics.add(new NormalizedMetric("memory_allocation_rate", allocRate, "MB/sec", variant, runNumber,
						"memory", "jfr"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseBlockingMetrics(JsonNode root, String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode blockingNode = root.path("blocking");
		if (blockingNode.isObject()) {
			Double blockingCount = blockingNode.path("event_count").asDouble(Double.NaN);
			if (!Double.isNaN(blockingCount)) {
				metrics.add(new NormalizedMetric("blocking_event_count", blockingCount, "count", variant, runNumber,
						"blocking", "jfr"));
			}

			Double blockingDuration = blockingNode.path("total_duration_ms").asDouble(Double.NaN);
			if (!Double.isNaN(blockingDuration)) {
				metrics.add(new NormalizedMetric("blocking_total_duration", blockingDuration, "ms", variant, runNumber,
						"blocking", "jfr"));
			}
		}

		return metrics;
	}

}
