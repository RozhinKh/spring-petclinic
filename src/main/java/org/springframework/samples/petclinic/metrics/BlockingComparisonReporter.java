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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Correlates static analysis findings with runtime JFR events. Produces comparison table
 * showing: - Potential blockers identified statically - Actual blocking observed at
 * runtime - False positives (static findings not triggered) - False negatives (runtime
 * blocking not caught statically)
 */
public class BlockingComparisonReporter {

	private static final Logger logger = LoggerFactory.getLogger(BlockingComparisonReporter.class);

	private final StaticBlockingAnalyzer staticAnalyzer;

	private final RuntimeBlockingTracker runtimeTracker;

	private List<BlockingComparison> comparisons = new ArrayList<>();

	/**
	 * Create comparison reporter
	 */
	public BlockingComparisonReporter(StaticBlockingAnalyzer staticAnalyzer, RuntimeBlockingTracker runtimeTracker) {
		this.staticAnalyzer = staticAnalyzer;
		this.runtimeTracker = runtimeTracker;
	}

	/**
	 * Generate comparison report between static and runtime findings
	 */
	public void generateReport() {
		comparisons.clear();

		logger.info("Generating blocking comparison report...");

		// Get static findings
		List<StaticBlockingAnalyzer.BlockingFinding> staticFindings = staticAnalyzer.getFindings();
		Set<String> staticClasses = new HashSet<>();
		for (StaticBlockingAnalyzer.BlockingFinding finding : staticFindings) {
			staticClasses.add(finding.getClassName());
		}

		// Get runtime findings
		Map<String, RuntimeBlockingTracker.MethodBlockingStats> runtimeMethods = runtimeTracker
			.getMethodBlockingStats();
		Map<String, RuntimeBlockingTracker.ClassBlockingStats> runtimeClasses = runtimeTracker.getClassBlockingStats();

		// Generate comparisons for each static finding
		for (StaticBlockingAnalyzer.BlockingFinding staticFinding : staticFindings) {
			BlockingComparison comparison = new BlockingComparison();
			comparison.setBlockingPattern(staticFinding.getPattern());
			comparison.setClassName(staticFinding.getClassName());
			comparison.setStaticCount(1); // Each finding is one occurrence
			comparison.setStaticLocation(staticFinding.getLocation());
			comparison.setStaticSeverity(staticFinding.getSeverity());

			// Check if this class had runtime blocking
			String runtimeKey = staticFinding.getClassName() + ":" + mapPatternToEventType(staticFinding.getPattern());
			RuntimeBlockingTracker.ClassBlockingStats runtimeStats = runtimeClasses.get(runtimeKey);

			if (runtimeStats != null) {
				comparison.setRuntimeCount(runtimeStats.getCount());
				comparison.setRuntimeDuration(runtimeStats.getTotalDuration());
				comparison.setTriggered(true);
				logger.debug("Static finding '{}' in {} was triggered at runtime", staticFinding.getPattern(),
						staticFinding.getClassName());
			}
			else {
				comparison.setRuntimeCount(0);
				comparison.setRuntimeDuration(0);
				comparison.setTriggered(false);
				logger.debug("Static finding '{}' in {} was NOT triggered at runtime (potential false positive)",
						staticFinding.getPattern(), staticFinding.getClassName());
			}

			comparisons.add(comparison);
		}

		// Identify false negatives (runtime blocking not caught statically)
		for (Map.Entry<String, RuntimeBlockingTracker.ClassBlockingStats> entry : runtimeClasses.entrySet()) {
			RuntimeBlockingTracker.ClassBlockingStats stats = entry.getValue();
			if (!staticClasses.contains(stats.getClassName())) {
				BlockingComparison falseNegative = new BlockingComparison();
				falseNegative.setBlockingPattern(stats.getBlockingType() + "_NOT_DETECTED");
				falseNegative.setClassName(stats.getClassName());
				falseNegative.setStaticCount(0);
				falseNegative.setRuntimeCount(stats.getCount());
				falseNegative.setRuntimeDuration(stats.getTotalDuration());
				falseNegative.setTriggered(true);
				falseNegative.setFalseNegative(true);

				logger.debug("False negative: runtime blocking in {} not caught statically", stats.getClassName());
				comparisons.add(falseNegative);
			}
		}

		logger.info("Generated {} comparisons", comparisons.size());
	}

	/**
	 * Map static pattern to JFR event type for matching
	 */
	private String mapPatternToEventType(String pattern) {
		return switch (pattern) {
			case "synchronized_block", "synchronized_method", "object_lock" -> "MONITOR_ENTER";
			case "wait_notify" -> "MONITOR_WAIT";
			case "thread_sleep", "thread_park" -> "THREAD_PARK";
			default -> "UNKNOWN";
		};
	}

	/**
	 * Get all comparisons
	 */
	public List<BlockingComparison> getComparisons() {
		return new ArrayList<>(comparisons);
	}

	/**
	 * Get comparison summary
	 */
	public Map<String, Object> getSummary() {
		Map<String, Object> summary = new HashMap<>();

		int totalStatic = (int) comparisons.stream().filter(c -> c.getStaticCount() > 0).count();
		int totalRuntime = (int) comparisons.stream().filter(c -> c.getRuntimeCount() > 0).count();
		int triggered = (int) comparisons.stream().filter(BlockingComparison::isTriggered).count();
		int falsePositives = (int) comparisons.stream().filter(c -> c.getStaticCount() > 0 && !c.isTriggered()).count();
		int falseNegatives = (int) comparisons.stream().filter(BlockingComparison::isFalseNegative).count();

		summary.put("total_static_findings", totalStatic);
		summary.put("total_runtime_findings", totalRuntime);
		summary.put("triggered_findings", triggered);
		summary.put("false_positives", falsePositives);
		summary.put("false_negatives", falseNegatives);

		if (totalStatic > 0) {
			summary.put("correlation_rate_percent", (100.0 * triggered) / totalStatic);
		}

		// Top problematic patterns
		Map<String, Integer> runtimeCounts = new HashMap<>();
		for (BlockingComparison comp : comparisons) {
			if (comp.getRuntimeCount() > 0) {
				runtimeCounts.put(comp.getBlockingPattern(),
						runtimeCounts.getOrDefault(comp.getBlockingPattern(), 0) + comp.getRuntimeCount());
			}
		}

		List<Map<String, Object>> topPatterns = new ArrayList<>();
		runtimeCounts.entrySet()
			.stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.limit(10)
			.forEach(entry -> {
				Map<String, Object> patternMap = new HashMap<>();
				patternMap.put("pattern", entry.getKey());
				patternMap.put("runtime_count", entry.getValue());
				topPatterns.add(patternMap);
			});
		summary.put("top_blocking_patterns", topPatterns);

		return summary;
	}

	/**
	 * Comparison data class
	 */
	public static class BlockingComparison {

		private String blockingPattern;

		private String className;

		private int staticCount = 0;

		private String staticLocation;

		private String staticSeverity = "MEDIUM";

		private int runtimeCount = 0;

		private long runtimeDuration = 0;

		private boolean triggered = false;

		private boolean falseNegative = false;

		// Getters and setters
		public String getBlockingPattern() {
			return blockingPattern;
		}

		public void setBlockingPattern(String blockingPattern) {
			this.blockingPattern = blockingPattern;
		}

		public String getClassName() {
			return className;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		public int getStaticCount() {
			return staticCount;
		}

		public void setStaticCount(int staticCount) {
			this.staticCount = staticCount;
		}

		public String getStaticLocation() {
			return staticLocation;
		}

		public void setStaticLocation(String staticLocation) {
			this.staticLocation = staticLocation;
		}

		public String getStaticSeverity() {
			return staticSeverity;
		}

		public void setStaticSeverity(String staticSeverity) {
			this.staticSeverity = staticSeverity;
		}

		public int getRuntimeCount() {
			return runtimeCount;
		}

		public void setRuntimeCount(int runtimeCount) {
			this.runtimeCount = runtimeCount;
		}

		public long getRuntimeDuration() {
			return runtimeDuration;
		}

		public void setRuntimeDuration(long runtimeDuration) {
			this.runtimeDuration = runtimeDuration;
		}

		public boolean isTriggered() {
			return triggered;
		}

		public void setTriggered(boolean triggered) {
			this.triggered = triggered;
		}

		public boolean isFalseNegative() {
			return falseNegative;
		}

		public void setFalseNegative(boolean falseNegative) {
			this.falseNegative = falseNegative;
		}

		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("pattern", blockingPattern);
			map.put("class", className);
			map.put("static_count", staticCount);
			if (staticLocation != null) {
				map.put("static_location", staticLocation);
			}
			map.put("static_severity", staticSeverity);
			map.put("runtime_count", runtimeCount);
			map.put("runtime_duration_ms", runtimeDuration);
			map.put("triggered", triggered);
			map.put("false_negative", falseNegative);
			return map;
		}

	}

}
