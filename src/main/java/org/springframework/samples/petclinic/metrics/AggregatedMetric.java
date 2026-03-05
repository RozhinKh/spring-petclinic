package org.springframework.samples.petclinic.metrics;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents aggregated statistics for a metric across multiple runs.
 * Contains average, min, max, and standard deviation.
 */
public class AggregatedMetric implements Serializable, Comparable<AggregatedMetric> {

	private String metricName;
	private String unit;
	private String category;
	private String variant;
	private String dataSource;

	private Double average;
	private Double minimum;
	private Double maximum;
	private Double stdDev;
	private Integer sampleCount;

	public AggregatedMetric() {
	}

	public AggregatedMetric(String metricName, String unit, String category, String variant,
			String dataSource) {
		this.metricName = metricName;
		this.unit = unit;
		this.category = category;
		this.variant = variant;
		this.dataSource = dataSource;
	}

	// Getters and Setters
	public String getMetricName() {
		return metricName;
	}

	public void setMetricName(String metricName) {
		this.metricName = metricName;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getVariant() {
		return variant;
	}

	public void setVariant(String variant) {
		this.variant = variant;
	}

	public String getDataSource() {
		return dataSource;
	}

	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	public Double getAverage() {
		return average;
	}

	public void setAverage(Double average) {
		this.average = average;
	}

	public Double getMinimum() {
		return minimum;
	}

	public void setMinimum(Double minimum) {
		this.minimum = minimum;
	}

	public Double getMaximum() {
		return maximum;
	}

	public void setMaximum(Double maximum) {
		this.maximum = maximum;
	}

	public Double getStdDev() {
		return stdDev;
	}

	public void setStdDev(Double stdDev) {
		this.stdDev = stdDev;
	}

	public Integer getSampleCount() {
		return sampleCount;
	}

	public void setSampleCount(Integer sampleCount) {
		this.sampleCount = sampleCount;
	}

	@Override
	public int compareTo(AggregatedMetric other) {
		if (!this.category.equals(other.category)) {
			return this.category.compareTo(other.category);
		}
		if (!this.metricName.equals(other.metricName)) {
			return this.metricName.compareTo(other.metricName);
		}
		return this.variant.compareTo(other.variant);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		AggregatedMetric that = (AggregatedMetric) o;
		return Objects.equals(metricName, that.metricName)
				&& Objects.equals(unit, that.unit) && Objects.equals(category, that.category)
				&& Objects.equals(variant, that.variant)
				&& Objects.equals(dataSource, that.dataSource);
	}

	@Override
	public int hashCode() {
		return Objects.hash(metricName, unit, category, variant, dataSource);
	}

	@Override
	public String toString() {
		return "AggregatedMetric{" + "metricName='" + metricName + '\'' + ", unit='" + unit
				+ '\'' + ", category='" + category + '\'' + ", variant='" + variant + '\''
				+ ", dataSource='" + dataSource + '\'' + ", average=" + average + ", minimum="
				+ minimum + ", maximum=" + maximum + ", stdDev=" + stdDev + ", sampleCount="
				+ sampleCount + '}';
	}

}
