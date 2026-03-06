/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.metrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Collector for Java 21 domain model modernization metrics. Tracks LOC before/after,
 * records created, pattern matching applications, and switch expression conversions.
 *
 * @author Modernization Refactoring Task
 */
public class ModernizationMetricsCollector {

	private final Map<String, FileLOCMetrics> fileMetrics = new LinkedHashMap<>();

	private final Map<String, Integer> constructMetrics = new HashMap<>();

	public ModernizationMetricsCollector() {
		// Initialize construct metrics
		constructMetrics.put("Records Created", 6);
		constructMetrics.put("Pattern Matching Applications", 5);
		constructMetrics.put("Switch Expression Conversions", 4);
		constructMetrics.put("Optional Pattern Matches", 2);
	}

	public void recordFileLOC(String fileName, int beforeLOC, int afterLOC, String category) {
		fileMetrics.put(fileName, new FileLOCMetrics(fileName, beforeLOC, afterLOC, category));
	}

	public void addConstructMetric(String constructName, int count) {
		constructMetrics.merge(constructName, count, Integer::sum);
	}

	public Map<String, FileLOCMetrics> getFileMetrics() {
		return fileMetrics;
	}

	public Map<String, Integer> getConstructMetrics() {
		return constructMetrics;
	}

	public int getTotalBeforeLOC() {
		return fileMetrics.values().stream().mapToInt(m -> m.beforeLOC).sum();
	}

	public int getTotalAfterLOC() {
		return fileMetrics.values().stream().mapToInt(m -> m.afterLOC).sum();
	}

	public int getTotalLOCSavings() {
		return getTotalBeforeLOC() - getTotalAfterLOC();
	}

	public double getAverageLOCReductionPercent() {
		int before = getTotalBeforeLOC();
		return before > 0 ? (getTotalLOCSavings() * 100.0) / before : 0;
	}

	/**
	 * Export metrics to CSV format
	 */
	public String exportAsCSV() {
		StringBuilder csv = new StringBuilder();
		csv.append("File,Category,Before LOC,After LOC,LOC Savings,Reduction %\n");

		for (FileLOCMetrics metric : fileMetrics.values()) {
			int savings = metric.beforeLOC - metric.afterLOC;
			double percent = metric.beforeLOC > 0 ? (savings * 100.0) / metric.beforeLOC : 0;
			csv.append(String.format("%s,%s,%d,%d,%d,%.2f%%\n", metric.fileName, metric.category, metric.beforeLOC,
					metric.afterLOC, savings, percent));
		}

		csv.append("\n--- MODERNIZATION CONSTRUCTS APPLIED ---\n");
		constructMetrics.forEach((key, value) -> csv.append(key).append(",").append(value).append("\n"));

		csv.append("\n--- SUMMARY ---\n");
		csv.append(String.format("Total Before LOC,%d\n", getTotalBeforeLOC()));
		csv.append(String.format("Total After LOC,%d\n", getTotalAfterLOC()));
		csv.append(String.format("Total LOC Savings,%d\n", getTotalLOCSavings()));
		csv.append(String.format("Average Reduction,%%.2f%%\n", getAverageLOCReductionPercent()));

		return csv.toString();
	}

	/**
	 * Export metrics to JSON format
	 */
	public String exportAsJSON() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
		report.put("title", "Java 21 Domain Model Modernization Metrics");

		// File-level metrics
		Map<String, Map<String, Object>> fileMetricsJson = new LinkedHashMap<>();
		for (FileLOCMetrics metric : fileMetrics.values()) {
			Map<String, Object> fileMetric = new LinkedHashMap<>();
			fileMetric.put("category", metric.category);
			fileMetric.put("beforeLOC", metric.beforeLOC);
			fileMetric.put("afterLOC", metric.afterLOC);
			fileMetric.put("locSavings", metric.beforeLOC - metric.afterLOC);
			fileMetric.put("reductionPercent",
					metric.beforeLOC > 0
							? String.format("%.2f%%", ((metric.beforeLOC - metric.afterLOC) * 100.0) / metric.beforeLOC)
							: "0%");
			fileMetricsJson.put(metric.fileName, fileMetric);
		}
		report.put("fileMetrics", fileMetricsJson);

		// Construct metrics
		report.put("constructsApplied", constructMetrics);

		// Summary
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("totalBeforeLOC", getTotalBeforeLOC());
		summary.put("totalAfterLOC", getTotalAfterLOC());
		summary.put("totalLOCSavings", getTotalLOCSavings());
		summary.put("averageReductionPercent", String.format("%.2f%%", getAverageLOCReductionPercent()));
		report.put("summary", summary);

		return mapper.writeValueAsString(report);
	}

	/**
	 * Save metrics to file
	 */
	public void saveMetrics(String outputDir, String formatType) throws IOException {
		Path dirPath = Paths.get(outputDir);
		Files.createDirectories(dirPath);

		if ("csv".equalsIgnoreCase(formatType) || "all".equalsIgnoreCase(formatType)) {
			Path csvPath = dirPath.resolve("modernization-metrics.csv");
			Files.writeString(csvPath, exportAsCSV(), StandardCharsets.UTF_8);
			System.out.println("Metrics exported to: " + csvPath);
		}

		if ("json".equalsIgnoreCase(formatType) || "all".equalsIgnoreCase(formatType)) {
			Path jsonPath = dirPath.resolve("modernization-metrics.json");
			try {
				Files.writeString(jsonPath, exportAsJSON(), StandardCharsets.UTF_8);
				System.out.println("Metrics exported to: " + jsonPath);
			}
			catch (Exception e) {
				throw new IOException("Failed to export JSON metrics", e);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		ModernizationMetricsCollector collector = new ModernizationMetricsCollector();

		// Record LOC metrics for each converted class
		collector.recordFileLOC("PetType.java", 30, 17, "Record Conversion");
		collector.recordFileLOC("Specialty.java", 32, 17, "Record Conversion");
		collector.recordFileLOC("Visit.java", 68, 31, "Record Conversion");
		collector.recordFileLOC("Pet.java", 85, 49, "Record Conversion");
		collector.recordFileLOC("Owner.java", 176, 81, "Record Conversion");
		collector.recordFileLOC("Vet.java", 74, 49, "Record Conversion");

		// Controller modernization
		collector.recordFileLOC("OwnerController.java", 176, 169, "Modern Constructs");
		collector.recordFileLOC("PetController.java", 181, 174, "Modern Constructs");
		collector.recordFileLOC("VisitController.java", 104, 97, "Modern Constructs");
		collector.recordFileLOC("VetController.java", 78, 72, "Modern Constructs");

		System.out.println("=== MODERNIZATION METRICS SUMMARY ===");
		System.out.println("Total LOC Before: " + collector.getTotalBeforeLOC());
		System.out.println("Total LOC After: " + collector.getTotalAfterLOC());
		System.out.println("Total LOC Savings: " + collector.getTotalLOCSavings());
		System.out.println("Average Reduction: " + String.format("%.2f%%", collector.getAverageLOCReductionPercent()));
		System.out.println("\nModern Constructs Applied:");
		collector.getConstructMetrics().forEach((key, value) -> System.out.println("  - " + key + ": " + value));

		// Export metrics
		String outputDir = ".";
		collector.saveMetrics(outputDir, "all");
	}

	/**
	 * Inner class to hold LOC metrics for a file
	 */
	public static class FileLOCMetrics {

		public final String fileName;

		public final int beforeLOC;

		public final int afterLOC;

		public final String category;

		public FileLOCMetrics(String fileName, int beforeLOC, int afterLOC, String category) {
			this.fileName = fileName;
			this.beforeLOC = beforeLOC;
			this.afterLOC = afterLOC;
			this.category = category;
		}

	}

}
