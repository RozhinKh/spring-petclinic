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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Exports blocking detection results to JSON and CSV formats.
 * Produces files in target/blocking/ directory for aggregation with other metrics.
 */
public class BlockingExporter {

	private static final String DEFAULT_EXPORT_DIR = "target/blocking";

	private static final ObjectMapper objectMapper;

	static {
		objectMapper = new ObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
	}

	private final String exportDirectory;

	/**
	 * Create exporter with default directory
	 */
	public BlockingExporter() {
		this(DEFAULT_EXPORT_DIR);
	}

	/**
	 * Create exporter with custom directory
	 */
	public BlockingExporter(String exportDirectory) {
		this.exportDirectory = exportDirectory;
		ensureDirectoryExists();
	}

	/**
	 * Export blocking detection result for variant to JSON
	 */
	public String exportToJson(BlockingDetectionHarness.BlockingDetectionResult result,
			String variant) throws IOException {
		String filename = generateFilename("blocking", variant, "json");
		String filePath = exportDirectory + File.separator + filename;

		Map<String, Object> exportData = new HashMap<>();
		exportData.put("export_timestamp", Instant.now().toString());
		exportData.put("variant", variant);
		exportData.put("analysis", result.toMap());

		Files.createDirectories(Paths.get(exportDirectory));
		objectMapper.writeValue(new File(filePath), exportData);

		return filePath;
	}

	/**
	 * Export blocking detection result for variant to CSV
	 */
	public String exportToCsv(BlockingDetectionHarness.BlockingDetectionResult result,
			String variant) throws IOException {
		String filename = generateFilename("blocking", variant, "csv");
		String filePath = exportDirectory + File.separator + filename;

		Files.createDirectories(Paths.get(exportDirectory));

		StringBuilder csv = new StringBuilder();
		// Header
		csv.append(
				"blocking_pattern,class,static_count,static_location,static_severity,runtime_count,runtime_duration_ms,triggered,false_negative\n");

		// Data rows from comparisons
		for (BlockingComparisonReporter.BlockingComparison comp : result.getComparisons()) {
			csv.append(escapeForCsv(comp.getBlockingPattern())).append(",")
					.append(escapeForCsv(comp.getClassName())).append(",")
					.append(comp.getStaticCount()).append(",")
					.append(escapeForCsv(comp.getStaticLocation())).append(",")
					.append(escapeForCsv(comp.getStaticSeverity())).append(",")
					.append(comp.getRuntimeCount()).append(",")
					.append(comp.getRuntimeDuration()).append(",")
					.append(comp.isTriggered()).append(",")
					.append(comp.isFalseNegative()).append("\n");
		}

		Files.writeString(Paths.get(filePath), csv.toString());

		return filePath;
	}

	/**
	 * Export comparison across all variants to JSON
	 */
	public String exportComparisonJson(Map<String, BlockingDetectionHarness.BlockingDetectionResult> results)
			throws IOException {
		String filename = generateComparisonFilename("blocking-comparison", "json");
		String filePath = exportDirectory + File.separator + filename;

		Map<String, Object> exportData = new HashMap<>();
		exportData.put("export_timestamp", Instant.now().toString());
		exportData.put("variant_count", results.size());
		exportData.put("variants", results.keySet());

		// Add comparative analysis
		Map<String, Object> comparativeAnalysis = generateComparativeAnalysis(results);
		exportData.put("comparative_analysis", comparativeAnalysis);

		// Add variant details
		Map<String, Object> variantDetails = new HashMap<>();
		for (Map.Entry<String, BlockingDetectionHarness.BlockingDetectionResult> entry : results.entrySet()) {
			variantDetails.put(entry.getKey(), entry.getValue().toMap());
		}
		exportData.put("variants_details", variantDetails);

		Files.createDirectories(Paths.get(exportDirectory));
		objectMapper.writeValue(new File(filePath), exportData);

		return filePath;
	}

	/**
	 * Export comparison across all variants to CSV
	 */
	public String exportComparisonCsv(Map<String, BlockingDetectionHarness.BlockingDetectionResult> results)
			throws IOException {
		String filename = generateComparisonFilename("blocking-comparison", "csv");
		String filePath = exportDirectory + File.separator + filename;

		Files.createDirectories(Paths.get(exportDirectory));

		StringBuilder csv = new StringBuilder();
		// Header - includes all variants
		csv.append("blocking_pattern,class");
		List<String> variants = new ArrayList<>(results.keySet());
		for (String variant : variants) {
			csv.append(",").append(variant).append("_runtime_count")
					.append(",").append(variant).append("_triggered");
		}
		csv.append("\n");

		// Collect all patterns across variants
		Map<String, Map<String, Integer>> patternStats = new HashMap<>();
		Map<String, Map<String, Boolean>> patternTriggered = new HashMap<>();

		for (Map.Entry<String, BlockingDetectionHarness.BlockingDetectionResult> entry : results.entrySet()) {
			String variant = entry.getKey();
			for (BlockingComparisonReporter.BlockingComparison comp : entry.getValue().getComparisons()) {
				String key = comp.getBlockingPattern() + ":" + comp.getClassName();
				patternStats.computeIfAbsent(key, k -> new HashMap<>()).put(variant,
						comp.getRuntimeCount());
				patternTriggered.computeIfAbsent(key, k -> new HashMap<>()).put(variant,
						comp.isTriggered());
			}
		}

		// Write rows
		for (Map.Entry<String, Map<String, Integer>> entry : patternStats.entrySet()) {
			String[] parts = entry.getKey().split(":");
			csv.append(escapeForCsv(parts[0])).append(",").append(escapeForCsv(parts[1]));

			for (String variant : variants) {
				int count = entry.getValue().getOrDefault(variant, 0);
				boolean triggered = patternTriggered.get(entry.getKey()).getOrDefault(variant,
						false);
				csv.append(",").append(count).append(",").append(triggered);
			}
			csv.append("\n");
		}

		Files.writeString(Paths.get(filePath), csv.toString());

		return filePath;
	}

	/**
	 * Generate comparative analysis across variants
	 */
	private Map<String, Object> generateComparativeAnalysis(
			Map<String, BlockingDetectionHarness.BlockingDetectionResult> results) {
		Map<String, Object> analysis = new HashMap<>();

		// Summary by variant
		Map<String, Map<String, Object>> variantSummaries = new HashMap<>();
		for (Map.Entry<String, BlockingDetectionHarness.BlockingDetectionResult> entry : results.entrySet()) {
			variantSummaries.put(entry.getKey(), entry.getValue().getComparisonSummary());
		}
		analysis.put("variant_summaries", variantSummaries);

		// Differences between variants
		if (results.size() >= 2) {
			List<String> variantList = new ArrayList<>(results.keySet());

			// Compare Java 17 vs Java 21 variants
			if (variantList.contains("java17") && variantList.stream().anyMatch(v -> v.contains("java21"))) {
				Map<String, Object> j17_j21_comparison = compareVariants(
						results.get("java17"),
						variantList.stream().filter(v -> v.contains("java21")).findFirst().orElse(null),
						results);
				if (j17_j21_comparison != null) {
					analysis.put("java17_vs_java21_comparison", j17_j21_comparison);
				}
			}

			// Compare traditional vs virtual thread variants
			if (variantList.contains("java21-traditional") && variantList.contains("java21-virtual")) {
				Map<String, Object> traditionalVsVirtual = compareVariants(
						results.get("java21-traditional"),
						results.get("java21-virtual"),
						results);
				if (traditionalVsVirtual != null) {
					analysis.put("traditional_vs_virtual_comparison", traditionalVsVirtual);
				}
			}
		}

		return analysis;
	}

	/**
	 * Compare two variants
	 */
	private Map<String, Object> compareVariants(
			BlockingDetectionHarness.BlockingDetectionResult variant1,
			BlockingDetectionHarness.BlockingDetectionResult variant2,
			Map<String, BlockingDetectionHarness.BlockingDetectionResult> allResults) {

		if (variant1 == null || variant2 == null) {
			return null;
		}

		Map<String, Object> comparison = new HashMap<>();
		comparison.put("variant1", variant1.getVariant());
		comparison.put("variant2", variant2.getVariant());

		// Runtime event differences
		int v1Events = variant1.getRuntimeEvents().size();
		int v2Events = variant2.getRuntimeEvents().size();
		double eventDiff = v2Events > 0 ? ((double) (v2Events - v1Events) / v1Events * 100) : 0;
		comparison.put("runtime_events_delta_percent", eventDiff);

		// Total blocking time differences
		long v1Time = variant1.getRuntimeSummary().containsKey("total_blocking_time_ms")
				? (long) variant1.getRuntimeSummary().get("total_blocking_time_ms") : 0;
		long v2Time = variant2.getRuntimeSummary().containsKey("total_blocking_time_ms")
				? (long) variant2.getRuntimeSummary().get("total_blocking_time_ms") : 0;
		double timeDiff = v1Time > 0 ? ((double) (v2Time - v1Time) / v1Time * 100) : 0;
		comparison.put("total_blocking_time_delta_percent", timeDiff);

		// Triggered findings ratio
		Object v1Triggered = variant1.getComparisonSummary().get("triggered_findings");
		Object v2Triggered = variant2.getComparisonSummary().get("triggered_findings");
		if (v1Triggered != null && v2Triggered != null) {
			comparison.put("variant1_triggered", v1Triggered);
			comparison.put("variant2_triggered", v2Triggered);
		}

		return comparison;
	}

	/**
	 * Escape string for CSV output
	 */
	private String escapeForCsv(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	/**
	 * Generate timestamped filename
	 */
	private String generateFilename(String prefix, String variant, String extension) {
		long timestamp = System.currentTimeMillis();
		return String.format("%s-%s-%d.%s", prefix, sanitizeVariant(variant), timestamp,
				extension);
	}

	/**
	 * Generate comparison filename
	 */
	private String generateComparisonFilename(String prefix, String extension) {
		long timestamp = System.currentTimeMillis();
		return String.format("%s-%d.%s", prefix, timestamp, extension);
	}

	/**
	 * Sanitize variant name for filename
	 */
	private String sanitizeVariant(String variant) {
		return variant.toLowerCase().replaceAll("[^a-z0-9-]", "-");
	}

	/**
	 * Ensure export directory exists
	 */
	private void ensureDirectoryExists() {
		try {
			Files.createDirectories(Paths.get(exportDirectory));
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to create export directory: " + exportDirectory, e);
		}
	}

}
