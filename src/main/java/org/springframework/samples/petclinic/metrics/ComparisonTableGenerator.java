package org.springframework.samples.petclinic.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates side-by-side comparison tables for metrics across variants.
 * Format: Metric | Unit | Java 17 | Java 21 Trad | Java 21 Virtual |
 * Delta 17→21T (%) | Delta 17→21V (%)
 */
public class ComparisonTableGenerator {

	/**
	 * Generates comparison tables for all metrics organized by category
	 */
	public Map<String, List<MetricComparison>> generateComparisonsByCategory(
			List<AggregatedMetric> aggregatedMetrics) {
		return aggregatedMetrics.stream()
				.collect(Collectors.groupingBy(AggregatedMetric::getCategory,
						Collectors.collectingAndThen(
								Collectors.toList(),
								metrics -> buildComparisonList(metrics))));
	}

	/**
	 * Builds comparison list for a group of metrics with the same category
	 */
	private List<MetricComparison> buildComparisonList(List<AggregatedMetric> metrics) {
		List<MetricComparison> comparisons = new ArrayList<>();

		// Group by metric name
		Map<String, List<AggregatedMetric>> byMetricName = metrics.stream()
				.collect(Collectors.groupingBy(AggregatedMetric::getMetricName));

		for (Map.Entry<String, List<AggregatedMetric>> entry : byMetricName.entrySet()) {
			MetricComparison comparison = buildSingleComparison(entry.getValue());
			if (comparison != null) {
				comparisons.add(comparison);
			}
		}

		return comparisons;
	}

	/**
	 * Builds a single metric comparison across variants
	 */
	private MetricComparison buildSingleComparison(List<AggregatedMetric> variantMetrics) {
		if (variantMetrics.isEmpty()) {
			return null;
		}

		AggregatedMetric first = variantMetrics.get(0);
		MetricComparison comparison = new MetricComparison(first.getMetricName(),
				first.getUnit(), first.getCategory());

		// Extract metrics by variant
		Map<String, AggregatedMetric> byVariant = variantMetrics.stream()
				.collect(Collectors.toMap(AggregatedMetric::getVariant, m -> m));

		// Build values with variance in parentheses
		AggregatedMetric java17 = byVariant.get("Java 17");
		if (java17 != null) {
			comparison.setJava17Value(formatMetricValue(java17));
			comparison.setJava17Numeric(java17.getAverage());
			comparison.setJava17StdDev(java17.getStdDev());
		} else {
			comparison.setJava17Value("Not Captured");
			comparison.setJava17Numeric(null);
		}

		AggregatedMetric java21Trad = byVariant.get("Java 21 Trad");
		if (java21Trad != null) {
			comparison.setJava21TradValue(formatMetricValue(java21Trad));
			comparison.setJava21TradNumeric(java21Trad.getAverage());
			comparison.setJava21TradStdDev(java21Trad.getStdDev());
		} else {
			comparison.setJava21TradValue("Not Captured");
			comparison.setJava21TradNumeric(null);
		}

		AggregatedMetric java21Virtual = byVariant.get("Java 21 Virtual");
		if (java21Virtual != null) {
			comparison.setJava21VirtualValue(formatMetricValue(java21Virtual));
			comparison.setJava21VirtualNumeric(java21Virtual.getAverage());
			comparison.setJava21VirtualStdDev(java21Virtual.getStdDev());
		} else {
			comparison.setJava21VirtualValue("Not Captured");
			comparison.setJava21VirtualNumeric(null);
		}

		// Calculate deltas
		if (comparison.getJava17Numeric() != null && comparison.getJava21TradNumeric() != null) {
			comparison.setDelta17To21Trad(calculateDeltaPercentage(
					comparison.getJava17Numeric(), comparison.getJava21TradNumeric()));
		}

		if (comparison.getJava17Numeric() != null && comparison.getJava21VirtualNumeric() != null) {
			comparison.setDelta17To21Virtual(calculateDeltaPercentage(
					comparison.getJava17Numeric(), comparison.getJava21VirtualNumeric()));
		}

		return comparison;
	}

	/**
	 * Formats a metric value with variance in parentheses
	 * Format: "12.5 (±0.3)" or "Not Captured"
	 */
	private String formatMetricValue(AggregatedMetric metric) {
		if (metric.getAverage() == null || Double.isNaN(metric.getAverage())) {
			return "Not Captured";
		}

		String value = formatNumber(metric.getAverage());
		String variance = "";

		if (metric.getStdDev() != null && !Double.isNaN(metric.getStdDev()) && metric.getStdDev() > 0) {
			variance = " (±" + formatNumber(metric.getStdDev()) + ")";
		}

		return value + variance;
	}

	/**
	 * Calculates percentage delta between two values
	 * Formula: ((new - old) / old) * 100
	 * Negative values indicate improvement (for latency, lower is better)
	 * Positive values indicate improvement (for throughput, higher is better)
	 */
	private Double calculateDeltaPercentage(Double baseline, Double current) {
		if (baseline == null || baseline == 0.0) {
			return 0.0;
		}

		return ((current - baseline) / baseline) * 100.0;
	}

	/**
	 * Formats a number to 2 decimal places
	 */
	private String formatNumber(Double value) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return "N/A";
		}

		// Use appropriate precision based on magnitude
		if (Math.abs(value) >= 1000 || Math.abs(value) < 0.01) {
			return String.format("%.2e", value);
		}

		return String.format("%.2f", value);
	}

	/**
	 * Generates a formatted table string for display or export
	 */
	public String generateFormattedTable(Map<String, List<MetricComparison>> comparisons) {
		StringBuilder sb = new StringBuilder();

		for (Map.Entry<String, List<MetricComparison>> categoryEntry : comparisons.entrySet()) {
			sb.append("\n=== ").append(categoryEntry.getKey().toUpperCase()).append(" ===\n");
			sb.append(String.format("%-40s | %-10s | %-20s | %-20s | %-20s | %-15s | %-15s\n",
					"Metric", "Unit", "Java 17", "Java 21 Trad", "Java 21 Virtual",
					"Delta 17→21T", "Delta 17→21V"));
			sb.append(String.join("", java.util.Collections.nCopies(
					40 + 10 + 20 + 20 + 20 + 15 + 15 + 30, "-"))).append("\n");

			for (MetricComparison comparison : categoryEntry.getValue()) {
				sb.append(String.format("%-40s | %-10s | %-20s | %-20s | %-20s | %-15s | %-15s\n",
						comparison.getMetricName(),
						comparison.getUnit(),
						comparison.getJava17Value() != null ? comparison.getJava17Value() : "N/A",
						comparison.getJava21TradValue() != null ? comparison.getJava21TradValue() : "N/A",
						comparison.getJava21VirtualValue() != null ? comparison.getJava21VirtualValue() : "N/A",
						comparison.getDelta17To21Trad() != null ? formatDelta(comparison.getDelta17To21Trad()) : "N/A",
						comparison.getDelta17To21Virtual() != null ? formatDelta(comparison.getDelta17To21Virtual()) : "N/A"));
			}
		}

		return sb.toString();
	}

	/**
	 * Formats delta for display with directional indicator
	 */
	private String formatDelta(Double delta) {
		if (delta == null || Double.isNaN(delta)) {
			return "N/A";
		}

		String sign = delta >= 0 ? "+" : "";
		return String.format("%s%.2f%%", sign, delta);
	}

}
