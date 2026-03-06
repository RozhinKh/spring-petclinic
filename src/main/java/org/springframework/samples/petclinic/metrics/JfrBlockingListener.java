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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JFR event listener for capturing blocking events during benchmark execution. Captures
 * MONITOR_ENTER, MONITOR_WAIT, and THREAD_PARK events. Correlates events with benchmark
 * timestamps for analysis.
 */
public class JfrBlockingListener {

	private static final Logger logger = LoggerFactory.getLogger(JfrBlockingListener.class);

	// JFR event names
	private static final String MONITOR_ENTER_EVENT = "jdk.MonitorEnter";

	private static final String MONITOR_WAIT_EVENT = "jdk.MonitorWait";

	private static final String THREAD_PARK_EVENT = "jdk.ThreadPark";

	private static final String MONITOR_CONTENTION_EVENT = "jdk.JavaMonitorEnter";

	private final List<BlockingEvent> blockingEvents = new CopyOnWriteArrayList<>();

	private volatile long benchmarkStartTime = System.currentTimeMillis();

	private volatile long benchmarkEndTime = -1;

	private volatile boolean isCollecting = false;

	/**
	 * Start collecting blocking events
	 */
	public void start() {
		isCollecting = true;
		benchmarkStartTime = System.currentTimeMillis();
		benchmarkEndTime = -1;
		blockingEvents.clear();
		logger.info("Started JFR blocking event listener");
	}

	/**
	 * Stop collecting blocking events
	 */
	public void stop() {
		isCollecting = false;
		benchmarkEndTime = System.currentTimeMillis();
		logger.info("Stopped JFR blocking event listener. Captured {} blocking events", blockingEvents.size());
	}

	/**
	 * Process a JFR event for blocking detection
	 */
	public void processEvent(RecordedEvent event) {
		if (!isCollecting) {
			return;
		}

		String eventName = event.getEventType().getName();

		// Check if this is a blocking event we care about
		if (eventName.equals(MONITOR_ENTER_EVENT) || eventName.equals(MONITOR_CONTENTION_EVENT)) {
			handleMonitorEvent(event, "MONITOR_ENTER");
		}
		else if (eventName.equals(MONITOR_WAIT_EVENT)) {
			handleMonitorWaitEvent(event);
		}
		else if (eventName.equals(THREAD_PARK_EVENT)) {
			handleThreadParkEvent(event);
		}
	}

	/**
	 * Handle MONITOR_ENTER events (lock contention)
	 */
	private void handleMonitorEvent(RecordedEvent event, String type) {
		try {
			BlockingEvent blockingEvent = new BlockingEvent();
			blockingEvent.setType(type);
			blockingEvent.setTimestamp(event.getStartTime().toEpochMilli());
			blockingEvent.setDuration(event.getDuration().toMillis());
			blockingEvent.setThreadName(event.getThread().getJavaName());
			blockingEvent.setThreadId(event.getThread().getJavaThreadId());

			// Extract method information from stack trace
			if (event.getStackTrace() != null && !event.getStackTrace().getFrames().isEmpty()) {
				var frame = event.getStackTrace().getFrames().get(0);
				blockingEvent.setClassName(frame.getMethod().getType().getName());
				blockingEvent.setMethodName(frame.getMethod().getName());
			}

			// Get class information if available
			try {
				Object monitorClass = event.getValue("monitorClass");
				if (monitorClass != null) {
					blockingEvent.setLockClass(monitorClass.toString());
				}
			}
			catch (Exception e) {
				// Field not available in this event type
			}

			blockingEvents.add(blockingEvent);
			logger.debug("Captured MONITOR_ENTER event: class={}, duration={}ms", blockingEvent.getLockClass(),
					blockingEvent.getDuration());
		}
		catch (Exception e) {
			logger.warn("Error processing monitor event: {}", e.getMessage());
		}
	}

	/**
	 * Handle MONITOR_WAIT events (object.wait() calls)
	 */
	private void handleMonitorWaitEvent(RecordedEvent event) {
		try {
			BlockingEvent blockingEvent = new BlockingEvent();
			blockingEvent.setType("MONITOR_WAIT");
			blockingEvent.setTimestamp(event.getStartTime().toEpochMilli());
			blockingEvent.setDuration(event.getDuration().toMillis());
			blockingEvent.setThreadName(event.getThread().getJavaName());
			blockingEvent.setThreadId(event.getThread().getJavaThreadId());

			// Extract method information
			if (event.getStackTrace() != null && !event.getStackTrace().getFrames().isEmpty()) {
				var frame = event.getStackTrace().getFrames().get(0);
				blockingEvent.setClassName(frame.getMethod().getType().getName());
				blockingEvent.setMethodName(frame.getMethod().getName());
			}

			try {
				Object monitorClass = event.getValue("monitorClass");
				if (monitorClass != null) {
					blockingEvent.setLockClass(monitorClass.toString());
				}
			}
			catch (Exception e) {
				// Field not available
			}

			blockingEvents.add(blockingEvent);
			logger.debug("Captured MONITOR_WAIT event: class={}, duration={}ms", blockingEvent.getLockClass(),
					blockingEvent.getDuration());
		}
		catch (Exception e) {
			logger.warn("Error processing monitor wait event: {}", e.getMessage());
		}
	}

	/**
	 * Handle THREAD_PARK events (virtual thread blocking)
	 */
	private void handleThreadParkEvent(RecordedEvent event) {
		try {
			BlockingEvent blockingEvent = new BlockingEvent();
			blockingEvent.setType("THREAD_PARK");
			blockingEvent.setTimestamp(event.getStartTime().toEpochMilli());
			blockingEvent.setDuration(event.getDuration().toMillis());
			blockingEvent.setThreadName(event.getThread().getJavaName());
			blockingEvent.setThreadId(event.getThread().getJavaThreadId());

			// Extract method information
			if (event.getStackTrace() != null && !event.getStackTrace().getFrames().isEmpty()) {
				var frame = event.getStackTrace().getFrames().get(0);
				blockingEvent.setClassName(frame.getMethod().getType().getName());
				blockingEvent.setMethodName(frame.getMethod().getName());
			}

			// THREAD_PARK specific fields
			try {
				Object parkerClass = event.getValue("parkerClass");
				if (parkerClass != null) {
					blockingEvent.setLockClass(parkerClass.toString());
				}
			}
			catch (Exception e) {
				// Field not available
			}

			blockingEvents.add(blockingEvent);
			logger.debug("Captured THREAD_PARK event: duration={}ms", blockingEvent.getDuration());
		}
		catch (Exception e) {
			logger.warn("Error processing thread park event: {}", e.getMessage());
		}
	}

	/**
	 * Get all blocking events
	 */
	public List<BlockingEvent> getBlockingEvents() {
		return new ArrayList<>(blockingEvents);
	}

	/**
	 * Get blocking events of specific type
	 */
	public List<BlockingEvent> getBlockingEvents(String type) {
		List<BlockingEvent> filtered = new ArrayList<>();
		for (BlockingEvent event : blockingEvents) {
			if (event.getType().equals(type)) {
				filtered.add(event);
			}
		}
		return filtered;
	}

	/**
	 * Get summary statistics
	 */
	public Map<String, Object> getSummary() {
		Map<String, Object> summary = new HashMap<>();
		summary.put("total_blocking_events", blockingEvents.size());
		summary.put("benchmark_duration_ms", benchmarkEndTime - benchmarkStartTime);
		summary.put("collection_active", isCollecting);

		// Aggregate by type
		Map<String, Integer> eventsByType = new HashMap<>();
		for (BlockingEvent event : blockingEvents) {
			eventsByType.put(event.getType(), eventsByType.getOrDefault(event.getType(), 0) + 1);
		}
		summary.put("events_by_type", eventsByType);

		// Calculate aggregate durations
		Map<String, Long> durationsByType = new HashMap<>();
		for (BlockingEvent event : blockingEvents) {
			String type = event.getType();
			long duration = durationsByType.getOrDefault(type, 0L);
			durationsByType.put(type, duration + event.getDuration());
		}
		summary.put("total_duration_by_type_ms", durationsByType);

		// Affected classes and methods
		Map<String, Integer> classCounts = new HashMap<>();
		for (BlockingEvent event : blockingEvents) {
			if (event.getClassName() != null) {
				classCounts.put(event.getClassName(), classCounts.getOrDefault(event.getClassName(), 0) + 1);
			}
		}
		summary.put("affected_classes", classCounts);

		return summary;
	}

	/**
	 * Clear all events (for reset between variants)
	 */
	public void clear() {
		blockingEvents.clear();
		isCollecting = false;
		benchmarkStartTime = System.currentTimeMillis();
		benchmarkEndTime = -1;
	}

	public boolean isCollecting() {
		return isCollecting;
	}

	/**
	 * Data class representing a blocking event
	 */
	public static class BlockingEvent {

		private String type;

		private long timestamp;

		private long duration;

		private String threadName;

		private long threadId;

		private String className;

		private String methodName;

		private String lockClass;

		// Getters and setters
		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public long getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}

		public long getDuration() {
			return duration;
		}

		public void setDuration(long duration) {
			this.duration = duration;
		}

		public String getThreadName() {
			return threadName;
		}

		public void setThreadName(String threadName) {
			this.threadName = threadName;
		}

		public long getThreadId() {
			return threadId;
		}

		public void setThreadId(long threadId) {
			this.threadId = threadId;
		}

		public String getClassName() {
			return className;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		public String getMethodName() {
			return methodName;
		}

		public void setMethodName(String methodName) {
			this.methodName = methodName;
		}

		public String getLockClass() {
			return lockClass;
		}

		public void setLockClass(String lockClass) {
			this.lockClass = lockClass;
		}

		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("type", type);
			map.put("timestamp_ms", timestamp);
			map.put("duration_ms", duration);
			map.put("thread_name", threadName);
			map.put("thread_id", threadId);
			if (className != null) {
				map.put("class", className);
			}
			if (methodName != null) {
				map.put("method", methodName);
			}
			if (lockClass != null) {
				map.put("lock_class", lockClass);
			}
			return map;
		}

	}

}
