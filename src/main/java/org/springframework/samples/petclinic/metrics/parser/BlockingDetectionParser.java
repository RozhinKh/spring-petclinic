package org.springframework.samples.petclinic.metrics.parser;

import org.springframework.samples.petclinic.metrics.NormalizedMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for blocking detection reports: static analysis findings and runtime blocking
 * analysis
 */
public class BlockingDetectionParser {

	private static final ObjectMapper mapper = new ObjectMapper();

	public List<NormalizedMetric> parseStaticFindings(File staticFindingsFile, String variant, Integer runNumber)
			throws IOException {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!staticFindingsFile.exists()) {
			return metrics;
		}

		JsonNode root = mapper.readTree(staticFindingsFile);

		JsonNode staticNode = root.path("static_analysis");
		if (staticNode.isObject()) {
			Double blockingCallCount = staticNode.path("blocking_call_count").asDouble(Double.NaN);
			if (!Double.isNaN(blockingCallCount)) {
				metrics.add(new NormalizedMetric("static_blocking_calls", blockingCallCount, "count", variant,
						runNumber, "blocking", "blocking_detection"));
			}

			Double synchronizedCount = staticNode.path("synchronized_count").asDouble(Double.NaN);
			if (!Double.isNaN(synchronizedCount)) {
				metrics.add(new NormalizedMetric("static_synchronized_usage", synchronizedCount, "count", variant,
						runNumber, "blocking", "blocking_detection"));
			}

			Double lockCount = staticNode.path("lock_usage_count").asDouble(Double.NaN);
			if (!Double.isNaN(lockCount)) {
				metrics.add(new NormalizedMetric("static_lock_usage", lockCount, "count", variant, runNumber,
						"blocking", "blocking_detection"));
			}

			// Parse locations array
			JsonNode locationsNode = staticNode.path("blocking_locations");
			if (locationsNode.isArray()) {
				int locationCount = locationsNode.size();
				metrics.add(new NormalizedMetric("static_blocking_locations", (double) locationCount, "count", variant,
						runNumber, "blocking", "blocking_detection"));
			}
		}

		return metrics;
	}

	public List<NormalizedMetric> parseRuntimeFindings(File runtimeFindingsFile, String variant, Integer runNumber)
			throws IOException {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!runtimeFindingsFile.exists()) {
			return metrics;
		}

		JsonNode root = mapper.readTree(runtimeFindingsFile);

		JsonNode runtimeNode = root.path("runtime_analysis");
		if (runtimeNode.isObject()) {
			Double blockingEventCount = runtimeNode.path("blocking_event_count").asDouble(Double.NaN);
			if (!Double.isNaN(blockingEventCount)) {
				metrics.add(new NormalizedMetric("runtime_blocking_event_count", blockingEventCount, "count", variant,
						runNumber, "blocking", "blocking_detection"));
			}

			Double blockingFrequency = runtimeNode.path("blocking_frequency_per_sec").asDouble(Double.NaN);
			if (!Double.isNaN(blockingFrequency)) {
				metrics.add(new NormalizedMetric("runtime_blocking_frequency", blockingFrequency, "events/sec", variant,
						runNumber, "blocking", "blocking_detection"));
			}

			Double blockingDuration = runtimeNode.path("avg_blocking_duration_ms").asDouble(Double.NaN);
			if (!Double.isNaN(blockingDuration)) {
				metrics.add(new NormalizedMetric("runtime_avg_blocking_duration", blockingDuration, "ms", variant,
						runNumber, "blocking", "blocking_detection"));
			}

			Double maxBlockingDuration = runtimeNode.path("max_blocking_duration_ms").asDouble(Double.NaN);
			if (!Double.isNaN(maxBlockingDuration)) {
				metrics.add(new NormalizedMetric("runtime_max_blocking_duration", maxBlockingDuration, "ms", variant,
						runNumber, "blocking", "blocking_detection"));
			}

			// Parse affected threads
			JsonNode threadsNode = runtimeNode.path("affected_threads");
			if (threadsNode.isArray()) {
				int threadCount = threadsNode.size();
				metrics.add(new NormalizedMetric("runtime_affected_threads", (double) threadCount, "count", variant,
						runNumber, "blocking", "blocking_detection"));
			}
		}

		return metrics;
	}

}
