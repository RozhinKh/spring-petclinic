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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregates runtime blocking events captured by JFR listener. Produces blocking
 * statistics aggregated by method/class for comparison with static analysis.
 */
public class RuntimeBlockingTracker {

	private static final Logger logger = LoggerFactory.getLogger(RuntimeBlockingTracker.class);

	private final Map<String, MethodBlockingStats> blockingStatsByMethod = new ConcurrentHashMap<>();

	private final Map<String, ClassBlockingStats> blockingStatsByClass = new ConcurrentHashMap<>();

	private volatile long totalBlockingTime = 0;

	private volatile int totalBlockingEvents = 0;

	private final JfrBlockingListener jfrListener;

	/**
	 * Create tracker with JFR listener
	 */
	public RuntimeBlockingTracker(JfrBlockingListener jfrListener) {
		this.jfrListener = jfrListener;
	}

	/**
	 * Process blocking events from JFR listener and aggregate statistics
	 */
	public void aggregateBlockingEvents() {
		blockingStatsByMethod.clear();
		blockingStatsByClass.clear();
		totalBlockingTime = 0;
		totalBlockingEvents = 0;

		List<JfrBlockingListener.BlockingEvent> events = jfrListener.getBlockingEvents();

		for (JfrBlockingListener.BlockingEvent event : events) {
			totalBlockingTime += event.getDuration();
			totalBlockingEvents++;

			// Aggregate by method
			String methodKey = formatMethodKey(event.getClassName(), event.getMethodName(), event.getType());
			MethodBlockingStats methodStats = blockingStatsByMethod.computeIfAbsent(methodKey,
					k -> new MethodBlockingStats(event.getClassName(), event.getMethodName(), event.getType()));
			methodStats.recordEvent(event);

			// Aggregate by class
			String classKey = formatClassKey(event.getClassName(), event.getType());
			ClassBlockingStats classStats = blockingStatsByClass.computeIfAbsent(classKey,
					k -> new ClassBlockingStats(event.getClassName(), event.getType()));
			classStats.recordEvent(event);
		}

		logger.info("Aggregated {} blocking events, total time: {}ms", totalBlockingEvents, totalBlockingTime);
	}

	/**
	 * Get all method-level blocking statistics
	 */
	public Map<String, MethodBlockingStats> getMethodBlockingStats() {
		return new HashMap<>(blockingStatsByMethod);
	}

	/**
	 * Get all class-level blocking statistics
	 */
	public Map<String, ClassBlockingStats> getClassBlockingStats() {
		return new HashMap<>(blockingStatsByClass);
	}

	/**
	 * Get overall summary
	 */
	public Map<String, Object> getSummary() {
		Map<String, Object> summary = new HashMap<>();
		summary.put("total_blocking_events", totalBlockingEvents);
		summary.put("total_blocking_time_ms", totalBlockingTime);
		summary.put("affected_methods", blockingStatsByMethod.size());
		summary.put("affected_classes", blockingStatsByClass.size());

		if (totalBlockingEvents > 0) {
			summary.put("average_blocking_time_ms", totalBlockingTime / totalBlockingEvents);
		}

		// Top blocking methods
		List<Map<String, Object>> topMethods = new ArrayList<>();
		blockingStatsByMethod.values()
			.stream()
			.sorted((a, b) -> Long.compare(b.getTotalDuration(), a.getTotalDuration()))
			.limit(10)
			.forEach(method -> {
				Map<String, Object> methodMap = new HashMap<>();
				methodMap.put("method", method.getMethodName());
				methodMap.put("class", method.getClassName());
				methodMap.put("type", method.getBlockingType());
				methodMap.put("count", method.getCount());
				methodMap.put("total_duration_ms", method.getTotalDuration());
				methodMap.put("average_duration_ms", method.getAverageDuration());
				topMethods.add(methodMap);
			});
		summary.put("top_blocking_methods", topMethods);

		// Top blocking classes
		List<Map<String, Object>> topClasses = new ArrayList<>();
		blockingStatsByClass.values()
			.stream()
			.sorted((a, b) -> Long.compare(b.getTotalDuration(), a.getTotalDuration()))
			.limit(10)
			.forEach(cls -> {
				Map<String, Object> classMap = new HashMap<>();
				classMap.put("class", cls.getClassName());
				classMap.put("type", cls.getBlockingType());
				classMap.put("count", cls.getCount());
				classMap.put("total_duration_ms", cls.getTotalDuration());
				topClasses.add(classMap);
			});
		summary.put("top_blocking_classes", topClasses);

		return summary;
	}

	/**
	 * Clear all tracking data
	 */
	public void clear() {
		blockingStatsByMethod.clear();
		blockingStatsByClass.clear();
		totalBlockingTime = 0;
		totalBlockingEvents = 0;
	}

	/**
	 * Format method key for aggregation
	 */
	private String formatMethodKey(String className, String methodName, String type) {
		if (className == null || methodName == null) {
			return "UNKNOWN-" + type;
		}
		return className + "#" + methodName + ":" + type;
	}

	/**
	 * Format class key for aggregation
	 */
	private String formatClassKey(String className, String type) {
		if (className == null) {
			return "UNKNOWN-" + type;
		}
		return className + ":" + type;
	}

	/**
	 * Statistics for blocking at method level
	 */
	public static class MethodBlockingStats {

		private final String className;

		private final String methodName;

		private final String blockingType;

		private int count = 0;

		private long totalDuration = 0;

		private long minDuration = Long.MAX_VALUE;

		private long maxDuration = 0;

		public MethodBlockingStats(String className, String methodName, String blockingType) {
			this.className = className;
			this.methodName = methodName;
			this.blockingType = blockingType;
		}

		public void recordEvent(JfrBlockingListener.BlockingEvent event) {
			count++;
			totalDuration += event.getDuration();
			minDuration = Math.min(minDuration, event.getDuration());
			maxDuration = Math.max(maxDuration, event.getDuration());
		}

		// Getters
		public String getClassName() {
			return className;
		}

		public String getMethodName() {
			return methodName;
		}

		public String getBlockingType() {
			return blockingType;
		}

		public int getCount() {
			return count;
		}

		public long getTotalDuration() {
			return totalDuration;
		}

		public long getAverageDuration() {
			return count > 0 ? totalDuration / count : 0;
		}

		public long getMinDuration() {
			return minDuration == Long.MAX_VALUE ? 0 : minDuration;
		}

		public long getMaxDuration() {
			return maxDuration;
		}

		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("class", className);
			map.put("method", methodName);
			map.put("type", blockingType);
			map.put("count", count);
			map.put("total_duration_ms", totalDuration);
			map.put("average_duration_ms", getAverageDuration());
			map.put("min_duration_ms", getMinDuration());
			map.put("max_duration_ms", maxDuration);
			return map;
		}

	}

	/**
	 * Statistics for blocking at class level
	 */
	public static class ClassBlockingStats {

		private final String className;

		private final String blockingType;

		private int count = 0;

		private long totalDuration = 0;

		public ClassBlockingStats(String className, String blockingType) {
			this.className = className;
			this.blockingType = blockingType;
		}

		public void recordEvent(JfrBlockingListener.BlockingEvent event) {
			count++;
			totalDuration += event.getDuration();
		}

		// Getters
		public String getClassName() {
			return className;
		}

		public String getBlockingType() {
			return blockingType;
		}

		public int getCount() {
			return count;
		}

		public long getTotalDuration() {
			return totalDuration;
		}

		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("class", className);
			map.put("type", blockingType);
			map.put("count", count);
			map.put("total_duration_ms", totalDuration);
			return map;
		}

	}

}
