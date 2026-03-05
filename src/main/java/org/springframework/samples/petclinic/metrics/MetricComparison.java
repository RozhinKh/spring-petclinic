package org.springframework.samples.petclinic.metrics;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a side-by-side comparison of a metric across variants.
 * Format: Metric | Unit | Java 17 | Java 21 Trad | Java 21 Virtual |
 * Delta 17→21T (%) | Delta 17→21V (%)
 */
public class MetricComparison implements Serializable {

	private String metricName;
	private String unit;
	private String category;

	// Variant values with variance (e.g., "12.5 (±0.3)")
	private String java17Value; // includes variance
	private String java21TradValue; // includes variance
	private String java21VirtualValue; // includes variance

	// Deltas
	private Double delta17To21Trad; // percentage change
	private Double delta17To21Virtual; // percentage change

	// Raw numeric values for calculation purposes
	private Double java17Numeric;
	private Double java21TradNumeric;
	private Double java21VirtualNumeric;

	// Standard deviations
	private Double java17StdDev;
	private Double java21TradStdDev;
	private Double java21VirtualStdDev;

	public MetricComparison() {
	}

	public MetricComparison(String metricName, String unit, String category) {
		this.metricName = metricName;
		this.unit = unit;
		this.category = category;
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

	public String getJava17Value() {
		return java17Value;
	}

	public void setJava17Value(String java17Value) {
		this.java17Value = java17Value;
	}

	public String getJava21TradValue() {
		return java21TradValue;
	}

	public void setJava21TradValue(String java21TradValue) {
		this.java21TradValue = java21TradValue;
	}

	public String getJava21VirtualValue() {
		return java21VirtualValue;
	}

	public void setJava21VirtualValue(String java21VirtualValue) {
		this.java21VirtualValue = java21VirtualValue;
	}

	public Double getDelta17To21Trad() {
		return delta17To21Trad;
	}

	public void setDelta17To21Trad(Double delta17To21Trad) {
		this.delta17To21Trad = delta17To21Trad;
	}

	public Double getDelta17To21Virtual() {
		return delta17To21Virtual;
	}

	public void setDelta17To21Virtual(Double delta17To21Virtual) {
		this.delta17To21Virtual = delta17To21Virtual;
	}

	public Double getJava17Numeric() {
		return java17Numeric;
	}

	public void setJava17Numeric(Double java17Numeric) {
		this.java17Numeric = java17Numeric;
	}

	public Double getJava21TradNumeric() {
		return java21TradNumeric;
	}

	public void setJava21TradNumeric(Double java21TradNumeric) {
		this.java21TradNumeric = java21TradNumeric;
	}

	public Double getJava21VirtualNumeric() {
		return java21VirtualNumeric;
	}

	public void setJava21VirtualNumeric(Double java21VirtualNumeric) {
		this.java21VirtualNumeric = java21VirtualNumeric;
	}

	public Double getJava17StdDev() {
		return java17StdDev;
	}

	public void setJava17StdDev(Double java17StdDev) {
		this.java17StdDev = java17StdDev;
	}

	public Double getJava21TradStdDev() {
		return java21TradStdDev;
	}

	public void setJava21TradStdDev(Double java21TradStdDev) {
		this.java21TradStdDev = java21TradStdDev;
	}

	public Double getJava21VirtualStdDev() {
		return java21VirtualStdDev;
	}

	public void setJava21VirtualStdDev(Double java21VirtualStdDev) {
		this.java21VirtualStdDev = java21VirtualStdDev;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MetricComparison that = (MetricComparison) o;
		return Objects.equals(metricName, that.metricName)
				&& Objects.equals(unit, that.unit) && Objects.equals(category, that.category);
	}

	@Override
	public int hashCode() {
		return Objects.hash(metricName, unit, category);
	}

	@Override
	public String toString() {
		return "MetricComparison{" + "metricName='" + metricName + '\'' + ", unit='" + unit
				+ '\'' + ", category='" + category + '\'' + ", java17Value='" + java17Value
				+ '\'' + ", java21TradValue='" + java21TradValue + '\''
				+ ", java21VirtualValue='" + java21VirtualValue + '\'' + ", delta17To21Trad="
				+ delta17To21Trad + ", delta17To21Virtual=" + delta17To21Virtual + '}';
	}

}
