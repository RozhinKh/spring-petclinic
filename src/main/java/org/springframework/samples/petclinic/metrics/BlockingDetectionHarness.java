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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Harness for orchestrating blocking detection across benchmark phases. Coordinates: -
 * Static analysis via SpotBugs - Runtime JFR event listening - Comparison reporting -
 * Export in JSON and CSV formats
 *
 * Designed for integration with JMH benchmarks and variant comparison (Java 17, Java 21
 * traditional, Java 21 virtual).
 */
public class BlockingDetectionHarness {

	private static final Logger logger = LoggerFactory.getLogger(BlockingDetectionHarness.class);

	private final StaticBlockingAnalyzer staticAnalyzer;

	private final JfrBlockingListener jfrListener;

	private final RuntimeBlockingTracker runtimeTracker;

	private final BlockingComparisonReporter comparisonReporter;

	private final BlockingExporter blockingExporter;

	private final Map<String, BlockingDetectionResult> variantResults = new HashMap<>();

	private volatile boolean isCollecting = false;

	private volatile String currentVariant = null;

	/**
	 * Create blocking detection harness
	 */
	public BlockingDetectionHarness() {
		this.staticAnalyzer = new StaticBlockingAnalyzer();
		this.jfrListener = new JfrBlockingListener();
		this.runtimeTracker = new RuntimeBlockingTracker(jfrListener);
		this.comparisonReporter = new BlockingComparisonReporter(staticAnalyzer, runtimeTracker);
		this.blockingExporter = new BlockingExporter();
	}

	/**
	 * Initialize blocking detection (run once at startup)
	 */
	public void initialize() throws IOException {
		logger.info("Initializing blocking detection harness...");

		// Run static analysis
		try {
			staticAnalyzer.analyze();
			logger.info("Static analysis complete: {}", staticAnalyzer.getFindings().size());
		}
		catch (IOException e) {
			logger.warn("Failed to run static analysis: {}", e.getMessage());
		}
	}

	/**
	 * Start blocking detection for a benchmark variant
	 * @param variant variant name (e.g., "java17", "java21-traditional",
	 * "java21-virtual")
	 */
	public void startBenchmark(String variant) {
		logger.info("Starting blocking detection for variant: {}", variant);

		currentVariant = variant;
		isCollecting = true;

		// Reset tracking
		jfrListener.clear();
		runtimeTracker.clear();

		// Start JFR listening
		jfrListener.start();
	}

	/**
	 * Stop blocking detection and generate report for variant
	 * @param variant variant name
	 * @return exported file paths
	 */
	public BlockingDetectionResult stopBenchmark(String variant) {
		logger.info("Stopping blocking detection for variant: {}", variant);

		isCollecting = false;

		// Stop JFR listening
		jfrListener.stop();

		// Aggregate runtime events
		runtimeTracker.aggregateBlockingEvents();

		// Generate comparison
		comparisonReporter.generateReport();

		// Create result
		BlockingDetectionResult result = new BlockingDetectionResult();
		result.setVariant(variant);
		result.setStaticFindings(staticAnalyzer.getFindings());
		result.setRuntimeEvents(jfrListener.getBlockingEvents());
		result.setComparisons(comparisonReporter.getComparisons());
		result.setStaticSummary(staticAnalyzer.getSummary());
		result.setRuntimeSummary(runtimeTracker.getSummary());
		result.setComparisonSummary(comparisonReporter.getSummary());

		variantResults.put(variant, result);

		logger.info("Blocking detection complete for variant: {}", variant);

		return result;
	}

	/**
	 * Get result for specific variant
	 */
	public BlockingDetectionResult getResult(String variant) {
		return variantResults.get(variant);
	}

	/**
	 * Get all variant results
	 */
	public Map<String, BlockingDetectionResult> getAllResults() {
		return new HashMap<>(variantResults);
	}

	/**
	 * Export blocking detection results to JSON and CSV
	 * @param variant variant name
	 * @return export file paths (JSON and CSV)
	 */
	public Map<String, String> exportResults(String variant) throws IOException {
		BlockingDetectionResult result = variantResults.get(variant);
		if (result == null) {
			throw new IllegalArgumentException("No results found for variant: " + variant);
		}

		Map<String, String> exports = new HashMap<>();

		// Export to JSON
		String jsonFile = blockingExporter.exportToJson(result, variant);
		exports.put("json", jsonFile);
		logger.info("Exported blocking analysis to JSON: {}", jsonFile);

		// Export to CSV
		String csvFile = blockingExporter.exportToCsv(result, variant);
		exports.put("csv", csvFile);
		logger.info("Exported blocking analysis to CSV: {}", csvFile);

		return exports;
	}

	/**
	 * Export comparison across all variants
	 * @return export file paths
	 */
	public Map<String, String> exportComparison() throws IOException {
		Map<String, String> exports = new HashMap<>();

		// JSON comparison
		String jsonFile = blockingExporter.exportComparisonJson(variantResults);
		exports.put("json", jsonFile);
		logger.info("Exported blocking comparison to JSON: {}", jsonFile);

		// CSV comparison
		String csvFile = blockingExporter.exportComparisonCsv(variantResults);
		exports.put("csv", csvFile);
		logger.info("Exported blocking comparison to CSV: {}", csvFile);

		return exports;
	}

	/**
	 * Check if currently collecting
	 */
	public boolean isCollecting() {
		return isCollecting;
	}

	/**
	 * Get current variant
	 */
	public String getCurrentVariant() {
		return currentVariant;
	}

	/**
	 * Get JFR listener for advanced integration
	 */
	public JfrBlockingListener getJfrListener() {
		return jfrListener;
	}

	/**
	 * Result container for a variant
	 */
	public static class BlockingDetectionResult {

		private String variant;

		private List<StaticBlockingAnalyzer.BlockingFinding> staticFindings;

		private List<JfrBlockingListener.BlockingEvent> runtimeEvents;

		private List<BlockingComparisonReporter.BlockingComparison> comparisons;

		private Map<String, Object> staticSummary;

		private Map<String, Object> runtimeSummary;

		private Map<String, Object> comparisonSummary;

		// Getters and setters
		public String getVariant() {
			return variant;
		}

		public void setVariant(String variant) {
			this.variant = variant;
		}

		public List<StaticBlockingAnalyzer.BlockingFinding> getStaticFindings() {
			return staticFindings != null ? new ArrayList<>(staticFindings) : new ArrayList<>();
		}

		public void setStaticFindings(List<StaticBlockingAnalyzer.BlockingFinding> staticFindings) {
			this.staticFindings = staticFindings;
		}

		public List<JfrBlockingListener.BlockingEvent> getRuntimeEvents() {
			return runtimeEvents != null ? new ArrayList<>(runtimeEvents) : new ArrayList<>();
		}

		public void setRuntimeEvents(List<JfrBlockingListener.BlockingEvent> runtimeEvents) {
			this.runtimeEvents = runtimeEvents;
		}

		public List<BlockingComparisonReporter.BlockingComparison> getComparisons() {
			return comparisons != null ? new ArrayList<>(comparisons) : new ArrayList<>();
		}

		public void setComparisons(List<BlockingComparisonReporter.BlockingComparison> comparisons) {
			this.comparisons = comparisons;
		}

		public Map<String, Object> getStaticSummary() {
			return staticSummary != null ? new HashMap<>(staticSummary) : new HashMap<>();
		}

		public void setStaticSummary(Map<String, Object> staticSummary) {
			this.staticSummary = staticSummary;
		}

		public Map<String, Object> getRuntimeSummary() {
			return runtimeSummary != null ? new HashMap<>(runtimeSummary) : new HashMap<>();
		}

		public void setRuntimeSummary(Map<String, Object> runtimeSummary) {
			this.runtimeSummary = runtimeSummary;
		}

		public Map<String, Object> getComparisonSummary() {
			return comparisonSummary != null ? new HashMap<>(comparisonSummary) : new HashMap<>();
		}

		public void setComparisonSummary(Map<String, Object> comparisonSummary) {
			this.comparisonSummary = comparisonSummary;
		}

		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("variant", variant);
			map.put("static_summary", getStaticSummary());
			map.put("runtime_summary", getRuntimeSummary());
			map.put("comparison_summary", getComparisonSummary());
			map.put("static_findings", getStaticFindings());
			map.put("runtime_events", getRuntimeEvents());
			map.put("comparisons", getComparisons());
			return map;
		}

	}

}
