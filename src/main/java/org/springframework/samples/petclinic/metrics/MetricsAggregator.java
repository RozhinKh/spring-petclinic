package org.springframework.samples.petclinic.metrics;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates normalized metrics across runs and calculates statistics.
 * Provides average, min, max, and standard deviation.
 */
public class MetricsAggregator {

	/**
	 * Aggregates metrics by metric name, variant, and category.
	 * Calculates: average, min, max, std_dev, sample_count
	 */
	public List<AggregatedMetric> aggregate(List<NormalizedMetric> normalizedMetrics) {
		List<AggregatedMetric> aggregated = new ArrayList<>();

		// Group by metric name, variant, and unit
		Map<String, List<NormalizedMetric>> grouped = normalizedMetrics.stream()
				.filter(m -> m.getValue() != null && !Double.isNaN(m.getValue()))
				.collect(Collectors.groupingBy(
						m -> m.getMetricName() + "|" + m.getVariant() + "|" + m.getUnit()));

		// Process each group
		for (Map.Entry<String, List<NormalizedMetric>> entry : grouped.entrySet()) {
			List<NormalizedMetric> group = entry.getValue();

			if (group.isEmpty()) {
				continue;
			}

			// Get metadata from first metric in group
			NormalizedMetric first = group.get(0);

			// Calculate statistics
			DoubleSummaryStatistics stats = group.stream()
					.mapToDouble(NormalizedMetric::getValue)
					.summaryStatistics();

			double stdDev = calculateStandardDeviation(group, stats.getAverage());

			// Create aggregated metric
			AggregatedMetric agg = new AggregatedMetric(first.getMetricName(), first.getUnit(),
					first.getCategory(), first.getVariant(), first.getDataSource());

			agg.setAverage(stats.getAverage());
			agg.setMinimum(stats.getMin());
			agg.setMaximum(stats.getMax());
			agg.setStdDev(stdDev);
			agg.setSampleCount((int) stats.getCount());

			aggregated.add(agg);
		}

		return aggregated;
	}

	/**
	 * Calculates standard deviation for a group of metrics
	 */
	private double calculateStandardDeviation(List<NormalizedMetric> metrics, double mean) {
		if (metrics.size() < 2) {
			return 0.0;
		}

		double sumOfSquaredDifferences = metrics.stream()
				.mapToDouble(m -> Math.pow(m.getValue() - mean, 2))
				.sum();

		return Math.sqrt(sumOfSquaredDifferences / (metrics.size() - 1));
	}

	/**
	 * Aggregates metrics by category
	 */
	public Map<String, List<AggregatedMetric>> aggregateByCategory(
			List<AggregatedMetric> aggregatedMetrics) {
		return aggregatedMetrics.stream()
				.collect(Collectors.groupingBy(AggregatedMetric::getCategory));
	}

	/**
	 * Calculates coefficient of variation (CV) = stdDev / mean
	 * Useful for comparing variance across metrics with different scales
	 */
	public double calculateCoefficientOfVariation(AggregatedMetric metric) {
		if (metric.getAverage() == null || metric.getAverage() == 0.0) {
			return 0.0;
		}
		return (metric.getStdDev() / metric.getAverage()) * 100.0;
	}

	/**
	 * Determines if a metric has significant variance (>10% CV)
	 */
	public boolean hasSignificantVariance(AggregatedMetric metric) {
		return calculateCoefficientOfVariation(metric) > 10.0;
	}

}
