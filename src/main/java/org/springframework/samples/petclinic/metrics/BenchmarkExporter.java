package org.springframework.samples.petclinic.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports benchmark results to JSON and CSV formats suitable for analysis. Generates
 * three output files: - benchmark-results.json: Complete dataset with all metrics and
 * statistics - benchmark-results.csv: Flat spreadsheet format for Excel/Sheets -
 * benchmark-summary.json: Executive summary with key metrics only
 */
public class BenchmarkExporter {

	private final ObjectMapper objectMapper;

	public BenchmarkExporter() {
		this.objectMapper = new ObjectMapper();
		this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
	}

	/**
	 * Exports aggregated metrics to JSON, CSV, and summary formats
	 */
	public void export(List<AggregatedMetric> aggregatedMetrics, String outputDir, ManualMetricsSection manualMetrics,
			ExportMetadata metadata) throws IOException {
		File outputDirectory = new File(outputDir);
		if (!outputDirectory.exists()) {
			outputDirectory.mkdirs();
		}

		exportFullJson(aggregatedMetrics, metadata, new File(outputDirectory, "benchmark-results.json"));
		exportCsv(aggregatedMetrics, new File(outputDirectory, "benchmark-results.csv"));
		exportSummaryJson(aggregatedMetrics, manualMetrics, metadata,
				new File(outputDirectory, "benchmark-summary.json"));
	}

	/**
	 * Exports complete benchmark dataset to JSON
	 */
	private void exportFullJson(List<AggregatedMetric> aggregatedMetrics, ExportMetadata metadata, File outputFile)
			throws IOException {
		Map<String, Object> root = new LinkedHashMap<>();

		// Add metadata
		root.put("metadata", buildMetadataMap(metadata));

		// Organize metrics by category
		Map<String, List<AggregatedMetric>> byCategory = aggregatedMetrics.stream()
			.collect(Collectors.groupingBy(AggregatedMetric::getCategory));

		Map<String, Object> metrics = new LinkedHashMap<>();
		for (String category : sortedCategories()) {
			if (byCategory.containsKey(category)) {
				metrics.put(category, convertMetricsToJson(byCategory.get(category)));
			}
		}

		root.put("metrics", metrics);

		// Statistics summary
		root.put("statistics", buildStatisticsSummary(aggregatedMetrics));

		// Data provenance
		root.put("data_sources", buildDataSourcesSummary(aggregatedMetrics));

		objectMapper.writeValue(outputFile, root);
		System.out.println("Exported full results to: " + outputFile.getAbsolutePath());
	}

	/**
	 * Exports metrics to CSV format
	 */
	private void exportCsv(List<AggregatedMetric> aggregatedMetrics, File outputFile) throws IOException {
		try (FileWriter writer = new FileWriter(outputFile)) {
			// Get all unique variants
			Set<String> variants = aggregatedMetrics.stream()
				.map(AggregatedMetric::getVariant)
				.collect(Collectors.toCollection(TreeSet::new));

			// Write header
			writer.append("Metric,Unit,Category,Data Source");
			for (String variant : variants) {
				writer.append(",").append(ExportFormatting.toCsvString(variant));
				writer.append(",").append(ExportFormatting.toCsvString(variant + " StdDev"));
			}
			// Add delta columns
			List<String> variantList = new ArrayList<>(variants);
			for (int i = 0; i < variantList.size() - 1; i++) {
				for (int j = i + 1; j < variantList.size(); j++) {
					writer.append(",")
						.append(ExportFormatting
							.toCsvString("Delta " + variantList.get(i) + "→" + variantList.get(j) + " (%)"));
				}
			}
			writer.append(",Notes\n");

			// Group metrics by name
			Map<String, List<AggregatedMetric>> byMetricName = aggregatedMetrics.stream()
				.collect(Collectors.groupingBy(AggregatedMetric::getMetricName));

			// Write data rows
			for (String metricName : byMetricName.keySet()) {
				List<AggregatedMetric> metricVariants = byMetricName.get(metricName);
				if (metricVariants.isEmpty()) {
					continue;
				}

				AggregatedMetric first = metricVariants.get(0);
				writer.append(ExportFormatting.toCsvString(first.getMetricName()))
					.append(",")
					.append(ExportFormatting.toCsvString(first.getUnit()))
					.append(",")
					.append(ExportFormatting.toCsvString(first.getCategory()))
					.append(",")
					.append(ExportFormatting.toCsvString(first.getDataSource()));

				// Group by variant
				Map<String, AggregatedMetric> byVariant = metricVariants.stream()
					.collect(Collectors.toMap(AggregatedMetric::getVariant, m -> m));

				// Write variant values and stdDev
				for (String variant : variants) {
					AggregatedMetric metric = byVariant.get(variant);
					if (metric != null) {
						writer.append(",").append(ExportFormatting.toCsvDouble(metric.getAverage(), first.getUnit()));
						writer.append(",").append(ExportFormatting.toCsvDouble(metric.getStdDev(), ""));
					}
					else {
						writer.append(",,");
					}
				}

				// Calculate and write deltas
				variantList = new ArrayList<>(variants);
				for (int i = 0; i < variantList.size() - 1; i++) {
					for (int j = i + 1; j < variantList.size(); j++) {
						AggregatedMetric baseMetric = byVariant.get(variantList.get(i));
						AggregatedMetric compMetric = byVariant.get(variantList.get(j));

						Double delta = null;
						if (baseMetric != null && baseMetric.getAverage() != null && compMetric != null
								&& compMetric.getAverage() != null && baseMetric.getAverage() != 0) {
							delta = ((compMetric.getAverage() - baseMetric.getAverage()) / baseMetric.getAverage())
									* 100;
						}

						if (delta != null) {
							writer.append(",").append(ExportFormatting.toCsvDouble(delta, "%"));
						}
						else {
							writer.append(",");
						}
					}
				}

				writer.append(",\n");
			}
		}
		System.out.println("Exported CSV results to: " + outputFile.getAbsolutePath());
	}

	/**
	 * Exports executive summary to JSON
	 */
	private void exportSummaryJson(List<AggregatedMetric> aggregatedMetrics, ManualMetricsSection manualMetrics,
			ExportMetadata metadata, File outputFile) throws IOException {
		Map<String, Object> root = new LinkedHashMap<>();

		// Add metadata
		root.put("metadata", buildMetadataMap(metadata));

		// Extract key metrics
		Map<String, Object> keyMetrics = new LinkedHashMap<>();

		// Define key metrics by category
		keyMetrics.put("startup",
				extractKeyMetricsFromCategory(aggregatedMetrics, "startup", Arrays.asList("Startup Time")));
		keyMetrics.put("latency", extractKeyMetricsFromCategory(aggregatedMetrics, "latency",
				Arrays.asList("P95 Latency", "P99 Latency", "Average Latency")));
		keyMetrics.put("throughput", extractKeyMetricsFromCategory(aggregatedMetrics, "throughput",
				Arrays.asList("Throughput", "Throughput (ops/sec)")));
		keyMetrics.put("memory", extractKeyMetricsFromCategory(aggregatedMetrics, "memory",
				Arrays.asList("Heap Memory", "Memory Footprint", "Max Memory")));
		keyMetrics.put("gc", extractKeyMetricsFromCategory(aggregatedMetrics, "gc",
				Arrays.asList("GC Pause Time", "GC Time Ratio", "Young GC Pause")));
		keyMetrics.put("threading", extractKeyMetricsFromCategory(aggregatedMetrics, "threading",
				Arrays.asList("Thread Count", "Active Thread Count")));
		keyMetrics.put("blocking", extractKeyMetricsFromCategory(aggregatedMetrics, "blocking",
				Arrays.asList("Blocking Events", "Blocking Call Count", "Blocking Time Ratio")));
		keyMetrics.put("test_suite", extractKeyMetricsFromCategory(aggregatedMetrics, "test_suite",
				Arrays.asList("Test Count", "Code Coverage %")));
		keyMetrics.put("modernization", extractKeyMetricsFromCategory(aggregatedMetrics, "modernization",
				Arrays.asList("Lines of Code", "Virtual Thread Usage Points")));

		root.put("key_metrics", keyMetrics);

		// Add manual metrics if provided
		if (manualMetrics != null) {
			Map<String, Object> manual = new LinkedHashMap<>();
			if (manualMetrics.getTestPassRate() != null) {
				manual.put("test_pass_rate", buildTestPassRateMap(manualMetrics.getTestPassRate()));
			}
			if (manualMetrics.getCloudCostAnalysis() != null) {
				manual.put("cloud_cost_analysis", buildCloudCostMap(manualMetrics.getCloudCostAnalysis()));
			}
			if (manualMetrics.getInstancesRequired() != null) {
				manual.put("instances_required", buildInstancesRequiredMap(manualMetrics.getInstancesRequired()));
			}
			if (manualMetrics.getEffortEstimate() != null) {
				manual.put("effort_estimate", buildEffortEstimateMap(manualMetrics.getEffortEstimate()));
			}
			root.put("manual_metrics", manual);
		}

		objectMapper.writeValue(outputFile, root);
		System.out.println("Exported summary to: " + outputFile.getAbsolutePath());
	}

	// ===== Helper Methods =====

	/**
	 * Converts aggregated metrics to JSON format
	 */
	private List<Map<String, Object>> convertMetricsToJson(List<AggregatedMetric> metrics) {
		return metrics.stream().map(metric -> {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("metric_name", metric.getMetricName());
			map.put("unit", metric.getUnit());
			map.put("data_source", metric.getDataSource());
			map.put("variants", buildVariantMap(metric));
			map.put("statistics", buildStatisticsMap(metric));
			return map;
		}).collect(Collectors.toList());
	}

	/**
	 * Builds variant map with average, min, max, stdDev
	 */
	private Map<String, Object> buildVariantMap(AggregatedMetric metric) {
		Map<String, Object> variantMap = new LinkedHashMap<>();
		variantMap.put("name", metric.getVariant());
		variantMap.put("average", metric.getAverage());
		variantMap.put("min", metric.getMinimum());
		variantMap.put("max", metric.getMaximum());
		variantMap.put("std_dev", metric.getStdDev());
		variantMap.put("sample_count", metric.getSampleCount());
		variantMap.put("coefficient_of_variation",
				metric.getStdDev() != null && metric.getAverage() != null && metric.getAverage() != 0
						? (metric.getStdDev() / metric.getAverage()) : null);
		return variantMap;
	}

	/**
	 * Builds statistics map for a metric
	 */
	private Map<String, Object> buildStatisticsMap(AggregatedMetric metric) {
		Map<String, Object> statsMap = new LinkedHashMap<>();
		statsMap.put("average", metric.getAverage());
		statsMap.put("minimum", metric.getMinimum());
		statsMap.put("maximum", metric.getMaximum());
		statsMap.put("std_dev", metric.getStdDev());
		statsMap.put("sample_count", metric.getSampleCount());
		return statsMap;
	}

	/**
	 * Builds metadata map
	 */
	private Map<String, Object> buildMetadataMap(ExportMetadata metadata) {
		Map<String, Object> map = new LinkedHashMap<>();
		if (metadata != null) {
			map.put("timestamp", metadata.getTimestamp());
			map.put("jdk_versions", metadata.getJdkVersions());
			map.put("tool_versions", metadata.getToolVersions());
			map.put("environment_info", metadata.getEnvironmentInfo());
			map.put("benchmark_duration_minutes", metadata.getBenchmarkDurationMinutes());
		}
		else {
			map.put("timestamp", Instant.now().toString());
			map.put("jdk_versions", new HashMap<>());
			map.put("tool_versions", new HashMap<>());
		}
		return map;
	}

	/**
	 * Builds overall statistics summary
	 */
	private Map<String, Object> buildStatisticsSummary(List<AggregatedMetric> metrics) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("total_metrics", metrics.size());
		summary.put("variants", metrics.stream().map(AggregatedMetric::getVariant).collect(Collectors.toSet()).size());
		summary.put("categories",
				metrics.stream().map(AggregatedMetric::getCategory).collect(Collectors.toSet()).size());
		summary.put("data_sources",
				metrics.stream().map(AggregatedMetric::getDataSource).collect(Collectors.toSet()).size());
		return summary;
	}

	/**
	 * Builds data sources summary
	 */
	private Map<String, Object> buildDataSourcesSummary(List<AggregatedMetric> metrics) {
		Map<String, Long> sources = metrics.stream()
			.collect(Collectors.groupingBy(AggregatedMetric::getDataSource, Collectors.counting()));
		return new LinkedHashMap<>(sources);
	}

	/**
	 * Extracts key metrics from a category
	 */
	private List<Map<String, Object>> extractKeyMetricsFromCategory(List<AggregatedMetric> metrics, String category,
			List<String> metricNames) {
		return metrics.stream()
			.filter(m -> category.equals(m.getCategory()) && metricNames.contains(m.getMetricName()))
			.map(m -> {
				Map<String, Object> map = new LinkedHashMap<>();
				map.put("metric_name", m.getMetricName());
				map.put("unit", m.getUnit());
				map.put("variant", m.getVariant());
				map.put("average", m.getAverage());
				map.put("std_dev", m.getStdDev());
				return map;
			})
			.collect(Collectors.toList());
	}

	/**
	 * Builds test pass rate map
	 */
	private Map<String, Object> buildTestPassRateMap(ManualMetricsSection.TestPassRate testPassRate) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("total_tests", testPassRate.getTotalTests());
		map.put("passed_tests", testPassRate.getPassedTests());
		map.put("failed_tests", testPassRate.getFailedTests());
		map.put("pass_percentage", testPassRate.getPassPercentage());
		return map;
	}

	/**
	 * Builds cloud cost analysis map
	 */
	private Map<String, Object> buildCloudCostMap(ManualMetricsSection.CloudCostAnalysis cloudCost) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("compute_hourly_cost", cloudCost.getComputeHourlyCost());
		map.put("throughput_ops_per_sec", cloudCost.getThroughputOpsPerSec());
		map.put("requests_per_hour", cloudCost.getRequestsPerHour());
		map.put("cost_per_request", cloudCost.getCostPerRequest());
		map.put("formula", cloudCost.getFormula());
		return map;
	}

	/**
	 * Builds instances required map
	 */
	private Map<String, Object> buildInstancesRequiredMap(ManualMetricsSection.InstancesRequired instancesRequired) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("peak_load_ops_per_sec", instancesRequired.getPeakLoadOpsPerSec());
		map.put("per_instance_capacity", instancesRequired.getPerInstanceCapacity());
		map.put("instances_required", instancesRequired.getInstancesRequired());
		map.put("formula", instancesRequired.getFormula());
		return map;
	}

	/**
	 * Builds effort estimate map
	 */
	private Map<String, Object> buildEffortEstimateMap(ManualMetricsSection.EffortEstimate effort) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("loc_refactored", effort.getLocRefactored());
		map.put("developer_hours_saved", effort.getDeveloperHoursSaved());
		map.put("assumptions", effort.getAssumptions_map());
		return map;
	}

	/**
	 * Returns sorted list of categories for consistent ordering
	 */
	private List<String> sortedCategories() {
		return Arrays.asList("startup", "latency", "throughput", "memory", "gc", "threading", "blocking", "test_suite",
				"modernization");
	}

}
