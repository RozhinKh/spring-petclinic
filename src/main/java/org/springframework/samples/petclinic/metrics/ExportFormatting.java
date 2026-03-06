package org.springframework.samples.petclinic.metrics;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utility class for formatting numbers in exports with proper precision. Ensures
 * consistency across JSON, CSV, and other export formats.
 */
public class ExportFormatting {

	/**
	 * Formats latencies to 2 decimal places
	 */
	public static Double formatLatency(Double value) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return null;
		}
		return Math.round(value * 100.0) / 100.0;
	}

	/**
	 * Formats percentages to 1 decimal place
	 */
	public static Double formatPercentage(Double value) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return null;
		}
		return Math.round(value * 10.0) / 10.0;
	}

	/**
	 * Formats standard deviation to 1 decimal place
	 */
	public static Double formatStdDev(Double value) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return null;
		}
		return Math.round(value * 10.0) / 10.0;
	}

	/**
	 * Formats a value based on its unit and magnitude - Latency/Time: 2 decimals -
	 * Percentage: 1 decimal - Throughput/Count: 0 decimals - Memory: 2 decimals
	 */
	public static Double formatByUnit(Double value, String unit) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return null;
		}

		if (unit == null) {
			return value;
		}

		String lowerUnit = unit.toLowerCase();

		if (lowerUnit.contains("ms") || lowerUnit.contains("sec") || lowerUnit.contains("us")
				|| lowerUnit.contains("ns")) {
			return formatLatency(value);
		}
		else if (lowerUnit.contains("%")) {
			return formatPercentage(value);
		}
		else if (lowerUnit.contains("kb") || lowerUnit.contains("mb") || lowerUnit.contains("gb")
				|| lowerUnit.contains("byte")) {
			return formatMemory(value);
		}
		else if (lowerUnit.contains("ops") || lowerUnit.contains("request") || lowerUnit.contains("call")) {
			return formatThroughput(value);
		}

		return value;
	}

	/**
	 * Formats memory values to 2 decimal places
	 */
	public static Double formatMemory(Double value) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return null;
		}
		return Math.round(value * 100.0) / 100.0;
	}

	/**
	 * Formats throughput values to nearest integer (ops/sec)
	 */
	public static Double formatThroughput(Double value) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return null;
		}
		return (double) Math.round(value);
	}

	/**
	 * Converts a Double to string with appropriate precision
	 */
	public static String toFormattedString(Double value, String unit) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return "N/A";
		}

		Double formatted = formatByUnit(value, unit);
		if (formatted == null) {
			return "N/A";
		}

		// Use DecimalFormat for consistent formatting
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
		DecimalFormat df;

		String lowerUnit = unit != null ? unit.toLowerCase() : "";

		if (lowerUnit.contains("%")) {
			df = new DecimalFormat("0.0", symbols);
		}
		else if (lowerUnit.contains("ms") || lowerUnit.contains("sec") || lowerUnit.contains("us")) {
			df = new DecimalFormat("0.00", symbols);
		}
		else {
			df = new DecimalFormat("0.##", symbols);
		}

		return df.format(formatted);
	}

	/**
	 * Converts a value to CSV-safe string (escaping quotes if needed)
	 */
	public static String toCsvString(String value) {
		if (value == null) {
			return "";
		}

		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}

		return value;
	}

	/**
	 * Converts a Double to CSV string with proper formatting
	 */
	public static String toCsvDouble(Double value, String unit) {
		if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return "";
		}
		return toFormattedString(value, unit);
	}

}
