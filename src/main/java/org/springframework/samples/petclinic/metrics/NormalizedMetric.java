package org.springframework.samples.petclinic.metrics;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a normalized metric with consistent schema across all data sources.
 * Metric_name, value, unit, variant, run_number
 */
public class NormalizedMetric implements Serializable, Comparable<NormalizedMetric> {

	private String metricName;

	private Double value;

	private String unit;

	private String variant; // e.g., "Java 17", "Java 21 Trad", "Java 21 Virtual"

	private Integer runNumber;

	private String category; // e.g., "startup", "latency", "throughput", "memory", "gc",
								// "threading",
								// "blocking", "test_suite", "modernization"

	private String dataSource; // Where metric came from: "jmh", "jfr", "test_suite",
								// "load_test",
								// "actuator", "blocking", "modernization"

	private Long timestamp; // When this metric was captured

	public NormalizedMetric() {
	}

	public NormalizedMetric(String metricName, Double value, String unit, String variant, Integer runNumber) {
		this.metricName = metricName;
		this.value = value;
		this.unit = unit;
		this.variant = variant;
		this.runNumber = runNumber;
	}

	public NormalizedMetric(String metricName, Double value, String unit, String variant, Integer runNumber,
			String category, String dataSource) {
		this.metricName = metricName;
		this.value = value;
		this.unit = unit;
		this.variant = variant;
		this.runNumber = runNumber;
		this.category = category;
		this.dataSource = dataSource;
	}

	// Getters and Setters
	public String getMetricName() {
		return metricName;
	}

	public void setMetricName(String metricName) {
		this.metricName = metricName;
	}

	public Double getValue() {
		return value;
	}

	public void setValue(Double value) {
		this.value = value;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getVariant() {
		return variant;
	}

	public void setVariant(String variant) {
		this.variant = variant;
	}

	public Integer getRunNumber() {
		return runNumber;
	}

	public void setRunNumber(Integer runNumber) {
		this.runNumber = runNumber;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getDataSource() {
		return dataSource;
	}

	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public int compareTo(NormalizedMetric other) {
		if (!this.metricName.equals(other.metricName)) {
			return this.metricName.compareTo(other.metricName);
		}
		if (!this.variant.equals(other.variant)) {
			return this.variant.compareTo(other.variant);
		}
		return this.runNumber.compareTo(other.runNumber);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		NormalizedMetric that = (NormalizedMetric) o;
		return Objects.equals(metricName, that.metricName) && Objects.equals(value, that.value)
				&& Objects.equals(unit, that.unit) && Objects.equals(variant, that.variant)
				&& Objects.equals(runNumber, that.runNumber);
	}

	@Override
	public int hashCode() {
		return Objects.hash(metricName, value, unit, variant, runNumber);
	}

	@Override
	public String toString() {
		return "NormalizedMetric{" + "metricName='" + metricName + '\'' + ", value=" + value + ", unit='" + unit + '\''
				+ ", variant='" + variant + '\'' + ", runNumber=" + runNumber + ", category='" + category + '\''
				+ ", dataSource='" + dataSource + '\'' + ", timestamp=" + timestamp + '}';
	}

}
