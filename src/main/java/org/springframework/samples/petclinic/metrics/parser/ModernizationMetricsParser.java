package org.springframework.samples.petclinic.metrics.parser;

import org.springframework.samples.petclinic.metrics.NormalizedMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for modernization metrics: LOC changes, construct counts, virtual thread usage
 */
public class ModernizationMetricsParser {

	private static final ObjectMapper mapper = new ObjectMapper();

	public List<NormalizedMetric> parse(File modernizationFile, String variant,
			Integer runNumber) throws IOException {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!modernizationFile.exists()) {
			return metrics;
		}

		JsonNode root = mapper.readTree(modernizationFile);

		// Parse LOC metrics
		metrics.addAll(parseLinesOfCode(root, variant, runNumber));

		// Parse construct metrics
		metrics.addAll(parseConstructMetrics(root, variant, runNumber));

		// Parse virtual thread usage
		metrics.addAll(parseVirtualThreadMetrics(root, variant, runNumber));

		return metrics;
	}

	private List<NormalizedMetric> parseLinesOfCode(JsonNode root, String variant,
			Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode locNode = root.path("lines_of_code");
		if (locNode.isObject()) {
			Double totalLoc = locNode.path("total_loc").asDouble(Double.NaN);
			if (!Double.isNaN(totalLoc)) {
				metrics.add(new NormalizedMetric("modernization_total_loc", totalLoc, "lines",
						variant, runNumber, "modernization", "modernization"));
			}

			Double modifiedLoc = locNode.path("modified_loc").asDouble(Double.NaN);
			if (!Double.isNaN(modifiedLoc)) {
				metrics.add(new NormalizedMetric("modernization_modified_loc", modifiedLoc,
						"lines", variant, runNumber, "modernization", "modernization"));
			}

			Double newLoc = locNode.path("new_loc").asDouble(Double.NaN);
			if (!Double.isNaN(newLoc)) {
				metrics.add(new NormalizedMetric("modernization_new_loc", newLoc, "lines",
						variant, runNumber, "modernization", "modernization"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseConstructMetrics(JsonNode root, String variant,
			Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode constructNode = root.path("constructs");
		if (constructNode.isObject()) {
			Double synchronizedCount = constructNode.path("synchronized_keyword_usage")
					.asDouble(Double.NaN);
			if (!Double.isNaN(synchronizedCount)) {
				metrics.add(new NormalizedMetric("modernization_synchronized_usage",
						synchronizedCount, "count", variant, runNumber, "modernization",
						"modernization"));
			}

			Double lockCount = constructNode.path("lock_usage").asDouble(Double.NaN);
			if (!Double.isNaN(lockCount)) {
				metrics.add(new NormalizedMetric("modernization_lock_usage", lockCount, "count",
						variant, runNumber, "modernization", "modernization"));
			}

			Double atomicCount = constructNode.path("atomic_usage").asDouble(Double.NaN);
			if (!Double.isNaN(atomicCount)) {
				metrics.add(new NormalizedMetric("modernization_atomic_usage", atomicCount,
						"count", variant, runNumber, "modernization", "modernization"));
			}

			Double concurrentHashmapCount = constructNode.path("concurrent_hashmap_usage")
					.asDouble(Double.NaN);
			if (!Double.isNaN(concurrentHashmapCount)) {
				metrics.add(new NormalizedMetric("modernization_concurrent_hashmap_usage",
						concurrentHashmapCount, "count", variant, runNumber, "modernization",
						"modernization"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseVirtualThreadMetrics(JsonNode root, String variant,
			Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode vtNode = root.path("virtual_threads");
		if (vtNode.isObject()) {
			Double vtConstructCount = vtNode.path("construct_count").asDouble(Double.NaN);
			if (!Double.isNaN(vtConstructCount)) {
				metrics.add(new NormalizedMetric("virtual_thread_construct_count",
						vtConstructCount, "count", variant, runNumber, "modernization",
						"modernization"));
			}

			Double structuredConcurrencyCount = vtNode.path("structured_concurrency_usage")
					.asDouble(Double.NaN);
			if (!Double.isNaN(structuredConcurrencyCount)) {
				metrics.add(new NormalizedMetric("virtual_thread_structured_concurrency",
						structuredConcurrencyCount, "count", variant, runNumber,
						"modernization", "modernization"));
			}

			Double virtualThreadExecutorCount = vtNode
					.path("virtual_thread_executor_usage").asDouble(Double.NaN);
			if (!Double.isNaN(virtualThreadExecutorCount)) {
				metrics.add(new NormalizedMetric("virtual_thread_executor_usage",
						virtualThreadExecutorCount, "count", variant, runNumber,
						"modernization", "modernization"));
			}

			Double reactiveCount = vtNode.path("reactive_usage").asDouble(Double.NaN);
			if (!Double.isNaN(reactiveCount)) {
				metrics.add(new NormalizedMetric("virtual_thread_reactive_usage", reactiveCount,
						"count", variant, runNumber, "modernization", "modernization"));
			}

			// Parse locations array
			JsonNode locationsNode = vtNode.path("usage_locations");
			if (locationsNode.isArray()) {
				int locationCount = locationsNode.size();
				metrics.add(new NormalizedMetric("virtual_thread_usage_locations",
						(double) locationCount, "count", variant, runNumber, "modernization",
						"modernization"));
			}
		}

		return metrics;
	}

}
