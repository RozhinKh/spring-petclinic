package org.springframework.samples.petclinic.metrics.parser;

import org.springframework.samples.petclinic.metrics.NormalizedMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for JMH JSON output files.
 * Extracts: startup (cold/warm), latency, throughput, memory metrics
 */
public class JmhJsonParser {

	private static final ObjectMapper mapper = new ObjectMapper();

	public List<NormalizedMetric> parse(File jmhJsonFile, String variant, Integer runNumber)
			throws IOException {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!jmhJsonFile.exists()) {
			return metrics;
		}

		JsonNode root = mapper.readTree(jmhJsonFile);

		if (root.isArray()) {
			for (JsonNode benchmarkNode : root) {
				metrics.addAll(extractMetricsFromBenchmark(benchmarkNode, variant, runNumber));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> extractMetricsFromBenchmark(JsonNode benchmarkNode,
			String variant, Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		String benchmark = benchmarkNode.path("benchmark").asText("");
		Double score = benchmarkNode.path("primaryMetric").path("score").asDouble(Double.NaN);
		String unit = benchmarkNode.path("primaryMetric").path("scoreUnit").asText("ops/sec");
		String mode = benchmarkNode.path("mode").asText("thrpt");

		if (Double.isNaN(score)) {
			return metrics;
		}

		// Determine metric name and category from benchmark name
		String metricName = extractMetricName(benchmark);
		String category = determineCategory(benchmark, mode);

		NormalizedMetric metric = new NormalizedMetric(metricName, score, unit, variant,
				runNumber, category, "jmh");
		metrics.add(metric);

		// Extract secondary metrics (min, max, p50, p95, p99)
		JsonNode secondaryMetrics = benchmarkNode.path("secondaryMetrics");
		if (secondaryMetrics.isObject()) {
			for (var it = secondaryMetrics.fields(); it.hasNext();) {
				var field = it.next();
				String metricKey = field.getKey();
				JsonNode metricValue = field.getValue();

				if (metricValue.has("score")) {
					Double value = metricValue.path("score").asDouble(Double.NaN);
					String metricUnit = metricValue.path("scoreUnit").asText(unit);

					if (!Double.isNaN(value)) {
						String secondaryMetricName = metricName + "_" + metricKey;
						NormalizedMetric secondaryMetric = new NormalizedMetric(
								secondaryMetricName, value, metricUnit, variant, runNumber,
								category, "jmh");
						metrics.add(secondaryMetric);
					}
				}
			}
		}

		return metrics;
	}

	private String extractMetricName(String benchmark) {
		// Extract the method name from benchmark string
		// E.g., "org.springframework.samples.petclinic.bench.PetClinicStartupBench.applicationStartup"
		// -> "applicationStartup"
		int lastDot = benchmark.lastIndexOf('.');
		if (lastDot >= 0 && lastDot < benchmark.length() - 1) {
			return benchmark.substring(lastDot + 1);
		}
		return benchmark;
	}

	private String determineCategory(String benchmark, String mode) {
		String lower = benchmark.toLowerCase();

		if (lower.contains("startup")) {
			return "startup";
		}
		if (lower.contains("latency") || mode.equals("avgt") || mode.equals("sampletime")) {
			return "latency";
		}
		if (lower.contains("throughput") || mode.equals("thrpt")) {
			return "throughput";
		}
		if (lower.contains("memory") || lower.contains("alloc")) {
			return "memory";
		}

		return "latency"; // default
	}

}
