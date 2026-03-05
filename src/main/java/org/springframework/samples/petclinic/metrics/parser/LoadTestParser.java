package org.springframework.samples.petclinic.metrics.parser;

import org.springframework.samples.petclinic.metrics.NormalizedMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for load test results: latency percentiles, throughput, error rates
 */
public class LoadTestParser {

	private static final ObjectMapper mapper = new ObjectMapper();

	public List<NormalizedMetric> parse(File loadTestFile, String variant, Integer runNumber)
			throws IOException {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!loadTestFile.exists()) {
			return metrics;
		}

		JsonNode root = mapper.readTree(loadTestFile);

		// Parse latency metrics
		metrics.addAll(parseLatencyMetrics(root, variant, runNumber));

		// Parse throughput metrics
		metrics.addAll(parseThroughputMetrics(root, variant, runNumber));

		// Parse error metrics
		metrics.addAll(parseErrorMetrics(root, variant, runNumber));

		return metrics;
	}

	private List<NormalizedMetric> parseLatencyMetrics(JsonNode root, String variant,
			Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode latencyNode = root.path("latency");
		if (latencyNode.isObject()) {
			// Parse percentiles
			Double p50 = latencyNode.path("p50_ms").asDouble(Double.NaN);
			if (!Double.isNaN(p50)) {
				metrics.add(new NormalizedMetric("latency_p50", p50, "ms", variant, runNumber,
						"latency", "load_test"));
			}

			Double p95 = latencyNode.path("p95_ms").asDouble(Double.NaN);
			if (!Double.isNaN(p95)) {
				metrics.add(new NormalizedMetric("latency_p95", p95, "ms", variant, runNumber,
						"latency", "load_test"));
			}

			Double p99 = latencyNode.path("p99_ms").asDouble(Double.NaN);
			if (!Double.isNaN(p99)) {
				metrics.add(new NormalizedMetric("latency_p99", p99, "ms", variant, runNumber,
						"latency", "load_test"));
			}

			Double p999 = latencyNode.path("p99_9_ms").asDouble(Double.NaN);
			if (!Double.isNaN(p999)) {
				metrics.add(new NormalizedMetric("latency_p99_9", p999, "ms", variant,
						runNumber, "latency", "load_test"));
			}

			Double avg = latencyNode.path("avg_ms").asDouble(Double.NaN);
			if (!Double.isNaN(avg)) {
				metrics.add(new NormalizedMetric("latency_avg", avg, "ms", variant, runNumber,
						"latency", "load_test"));
			}

			Double min = latencyNode.path("min_ms").asDouble(Double.NaN);
			if (!Double.isNaN(min)) {
				metrics.add(new NormalizedMetric("latency_min", min, "ms", variant, runNumber,
						"latency", "load_test"));
			}

			Double max = latencyNode.path("max_ms").asDouble(Double.NaN);
			if (!Double.isNaN(max)) {
				metrics.add(new NormalizedMetric("latency_max", max, "ms", variant, runNumber,
						"latency", "load_test"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseThroughputMetrics(JsonNode root, String variant,
			Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode throughputNode = root.path("throughput");
		if (throughputNode.isObject()) {
			Double rps = throughputNode.path("requests_per_sec").asDouble(Double.NaN);
			if (!Double.isNaN(rps)) {
				metrics.add(new NormalizedMetric("throughput_rps", rps, "req/sec", variant,
						runNumber, "throughput", "load_test"));
			}

			Double totalRequests = throughputNode.path("total_requests").asDouble(Double.NaN);
			if (!Double.isNaN(totalRequests)) {
				metrics.add(new NormalizedMetric("load_test_total_requests", totalRequests,
						"count", variant, runNumber, "throughput", "load_test"));
			}
		}

		return metrics;
	}

	private List<NormalizedMetric> parseErrorMetrics(JsonNode root, String variant,
			Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();

		JsonNode errorNode = root.path("errors");
		if (errorNode.isObject()) {
			Double errorCount = errorNode.path("total_errors").asDouble(Double.NaN);
			if (!Double.isNaN(errorCount)) {
				metrics.add(new NormalizedMetric("error_count", errorCount, "count", variant,
						runNumber, "throughput", "load_test"));
			}

			Double errorRate = errorNode.path("error_rate_percent").asDouble(Double.NaN);
			if (!Double.isNaN(errorRate)) {
				metrics.add(new NormalizedMetric("error_rate", errorRate, "%", variant,
						runNumber, "throughput", "load_test"));
			}
		}

		return metrics;
	}

}
