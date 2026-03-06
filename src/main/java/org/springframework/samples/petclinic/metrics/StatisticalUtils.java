package org.springframework.samples.petclinic.metrics;

import java.util.List;

/**
 * Utility class for statistical calculations on metrics.
 */
public class StatisticalUtils {

	/**
	 * Determines if a difference is statistically significant based on variance. Uses the
	 * rule: difference > threshold based on metric type and variance.
	 */
	public static boolean isSignificantDifference(Double baseline, Double current, String metricCategory,
			Double baselineStdDev) {
		if (baseline == null || baseline == 0.0 || current == null) {
			return false;
		}

		double percentChange = Math.abs((current - baseline) / baseline) * 100.0;

		// Thresholds based on variance patterns from the metrics interpretation guide
		double threshold = getThresholdForCategory(metricCategory, baselineStdDev);

		return percentChange > threshold;
	}

	/**
	 * Gets threshold for statistical significance based on metric category and variance
	 */
	private static double getThresholdForCategory(String category, Double stdDev) {
		double cv = stdDev != null ? stdDev : 0.0; // Coefficient of variation

		return switch (category) {
			case "latency" -> 3.0; // Low variance - threshold 3%
			case "throughput" -> 8.0; // Medium variance - threshold 8%
			case "gc" -> 25.0; // High variance - threshold 25%
			case "memory" -> 12.0; // Medium-high variance
			case "startup" -> 5.0; // Relatively stable
			case "threading" -> 10.0; // Variable based on thread scheduling
			case "blocking" -> 15.0; // Depends on workload
			case "test_suite" -> 10.0; // Depends on test variance
			case "modernization" -> 5.0; // Stable count metrics
			default -> 10.0; // Default threshold
		};
	}

	/**
	 * Calculates confidence interval (95%) around a mean
	 */
	public static double calculateConfidenceIntervalMargin(Double stdDev, Integer sampleCount) {
		if (stdDev == null || sampleCount == null || sampleCount < 2) {
			return 0.0;
		}

		// For small samples, use t-distribution critical value
		// For now, use simple z-score approximation: 1.96 * stdDev / sqrt(n)
		double standardError = stdDev / Math.sqrt(sampleCount);
		return 1.96 * standardError; // 95% confidence level
	}

	/**
	 * Determines improvement direction based on metric category Some metrics: lower is
	 * better (latency), others: higher is better (throughput)
	 */
	public static String getImprovementDirection(String metricName) {
		String lower = metricName.toLowerCase();

		if (lower.contains("latency") || lower.contains("time") || lower.contains("pause") || lower.contains("duration")
				|| lower.contains("error")) {
			return "lower_is_better";
		}

		return "higher_is_better";
	}

	/**
	 * Formats a metric value with units for display
	 */
	public static String formatMetricWithUnit(Double value, String unit) {
		if (value == null || Double.isNaN(value)) {
			return "N/A";
		}

		String formatted = String.format("%.2f", value);
		return formatted + " " + unit;
	}

	/**
	 * Calculates percent change from baseline to current Respects improvement direction
	 * (some metrics lower is better)
	 */
	public static Double calculatePercentChange(Double baseline, Double current, String improvementDirection) {
		if (baseline == null || baseline == 0.0 || current == null) {
			return 0.0;
		}

		double percentChange = ((current - baseline) / baseline) * 100.0;

		// For metrics where lower is better, negative change is improvement
		// For metrics where higher is better, positive change is improvement
		return percentChange;
	}

	/**
	 * Calculates z-score for outlier detection
	 */
	public static double calculateZScore(Double value, Double mean, Double stdDev) {
		if (stdDev == null || stdDev == 0.0 || mean == null) {
			return 0.0;
		}
		return (value - mean) / stdDev;
	}

	/**
	 * Determines if a value is an outlier (|z-score| > 3)
	 */
	public static boolean isOutlier(Double value, Double mean, Double stdDev) {
		double zScore = calculateZScore(value, mean, stdDev);
		return Math.abs(zScore) > 3.0;
	}

}
