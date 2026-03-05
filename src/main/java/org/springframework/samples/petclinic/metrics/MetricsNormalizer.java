package org.springframework.samples.petclinic.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Normalizes metrics by handling missing data and unit conversions.
 */
public class MetricsNormalizer {

	/**
	 * Marks missing values with a special marker value
	 */
	private static final Double MISSING_MARKER = Double.NaN;

	public List<NormalizedMetric> normalizeMissingData(List<NormalizedMetric> metrics) {
		return metrics.stream().filter(metric -> metric.getValue() != null && !Double.isNaN(metric.getValue()))
				.collect(Collectors.toList());
	}

	/**
	 * Converts metric values to a standard unit (e.g., bytes to MB, ms to seconds)
	 */
	public NormalizedMetric convertUnit(NormalizedMetric metric, String targetUnit) {
		if (metric.getValue() == null || Double.isNaN(metric.getValue())) {
			return metric;
		}

		String currentUnit = metric.getUnit();
		if (currentUnit.equals(targetUnit)) {
			return metric;
		}

		Double convertedValue = convertValue(metric.getValue(), currentUnit, targetUnit);

		NormalizedMetric converted = new NormalizedMetric(metric.getMetricName(), convertedValue,
				targetUnit, metric.getVariant(), metric.getRunNumber(), metric.getCategory(),
				metric.getDataSource());
		converted.setTimestamp(metric.getTimestamp());
		return converted;
	}

	/**
	 * Generic value conversion between units
	 */
	private Double convertValue(Double value, String fromUnit, String toUnit) {
		// Time conversions
		if (fromUnit.equals("ms") && toUnit.equals("s")) {
			return value / 1000.0;
		}
		if (fromUnit.equals("s") && toUnit.equals("ms")) {
			return value * 1000.0;
		}

		// Memory conversions
		if (fromUnit.equals("bytes") && toUnit.equals("MB")) {
			return value / (1024.0 * 1024.0);
		}
		if (fromUnit.equals("MB") && toUnit.equals("bytes")) {
			return value * 1024.0 * 1024.0;
		}
		if (fromUnit.equals("bytes") && toUnit.equals("GB")) {
			return value / (1024.0 * 1024.0 * 1024.0);
		}
		if (fromUnit.equals("GB") && toUnit.equals("bytes")) {
			return value * 1024.0 * 1024.0 * 1024.0;
		}

		// No conversion possible, return original value
		return value;
	}

	/**
	 * Handles missing data gracefully by marking or skipping incomplete metrics
	 */
	public List<NormalizedMetric> handleMissingData(List<NormalizedMetric> metrics,
			boolean skipMissing) {
		if (skipMissing) {
			return normalizeMissingData(metrics);
		}

		// Mark missing data with NaN
		for (NormalizedMetric metric : metrics) {
			if (metric.getValue() == null) {
				metric.setValue(MISSING_MARKER);
			}
		}

		return metrics;
	}

	/**
	 * Validates metric schema completeness
	 */
	public boolean isValidMetric(NormalizedMetric metric) {
		return metric.getMetricName() != null && !metric.getMetricName().isEmpty()
				&& metric.getUnit() != null && !metric.getUnit().isEmpty()
				&& metric.getVariant() != null && !metric.getVariant().isEmpty()
				&& metric.getRunNumber() != null && metric.getRunNumber() > 0;
	}

}
