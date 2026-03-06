package org.springframework.samples.petclinic.metrics;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Container for aggregation results from a single variant/run combination.
 */
public class AggregationResult implements Serializable {

	private List<NormalizedMetric> normalizedMetrics;

	private List<AggregatedMetric> aggregatedMetrics;

	private Map<String, List<AggregatedMetric>> aggregatedByCategory;

	private Long processingTimeMillis;

	public AggregationResult() {
	}

	public AggregationResult(List<NormalizedMetric> normalizedMetrics, List<AggregatedMetric> aggregatedMetrics,
			Map<String, List<AggregatedMetric>> aggregatedByCategory) {
		this.normalizedMetrics = normalizedMetrics;
		this.aggregatedMetrics = aggregatedMetrics;
		this.aggregatedByCategory = aggregatedByCategory;
	}

	// Getters and Setters
	public List<NormalizedMetric> getNormalizedMetrics() {
		return normalizedMetrics;
	}

	public void setNormalizedMetrics(List<NormalizedMetric> normalizedMetrics) {
		this.normalizedMetrics = normalizedMetrics;
	}

	public List<AggregatedMetric> getAggregatedMetrics() {
		return aggregatedMetrics;
	}

	public void setAggregatedMetrics(List<AggregatedMetric> aggregatedMetrics) {
		this.aggregatedMetrics = aggregatedMetrics;
	}

	public Map<String, List<AggregatedMetric>> getAggregatedByCategory() {
		return aggregatedByCategory;
	}

	public void setAggregatedByCategory(Map<String, List<AggregatedMetric>> aggregatedByCategory) {
		this.aggregatedByCategory = aggregatedByCategory;
	}

	public Long getProcessingTimeMillis() {
		return processingTimeMillis;
	}

	public void setProcessingTimeMillis(Long processingTimeMillis) {
		this.processingTimeMillis = processingTimeMillis;
	}

}
