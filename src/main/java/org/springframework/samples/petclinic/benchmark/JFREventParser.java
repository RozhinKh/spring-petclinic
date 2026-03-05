/*
 * Copyright 2015-2024 the original author or authors.
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
package org.springframework.samples.petclinic.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.samples.petclinic.benchmark.BenchmarkRunner.getObjectMapper;

/**
 * Parses Java Flight Recorder (JFR) binary files and extracts metrics
 * relevant to benchmarking: GC pauses, thread counts, memory allocation rates,
 * and blocking events.
 */
public class JFREventParser {

	private final Path jfrFilePath;

	public JFREventParser(Path jfrFilePath) {
		this.jfrFilePath = jfrFilePath;
	}

	/**
	 * Parse JFR file and extract all relevant metrics.
	 *
	 * @return ObjectNode containing parsed JFR metrics
	 * @throws Exception if JFR file cannot be read
	 */
	public ObjectNode parseJFRFile() throws Exception {
		ObjectNode root = getObjectMapper().createObjectNode();

		// Extract different metric categories
		root.set("gc_metrics", extractGCMetrics());
		root.set("thread_metrics", extractThreadMetrics());
		root.set("memory_metrics", extractMemoryMetrics());
		root.set("blocking_metrics", extractBlockingMetrics());

		root.put("total_events_processed", countTotalEvents());

		return root;
	}

	/**
	 * Extract garbage collection metrics from JFR events.
	 */
	private ObjectNode extractGCMetrics() throws Exception {
		ObjectNode gcMetrics = getObjectMapper().createObjectNode();
		List<Map<String, Object>> gcPauses = new ArrayList<>();
		Map<String, Long> gcCounts = new HashMap<>();

		long totalPauseDuration = 0;
		long minPauseDuration = Long.MAX_VALUE;
		long maxPauseDuration = 0;

		try (RecordingFile recordingFile = new RecordingFile(jfrFilePath)) {
			while (recordingFile.hasMoreEvents()) {
				RecordedEvent event = recordingFile.readEvent();

				if ("jdk.GCPauseLevel".equals(event.getEventType().getName()) ||
					"jdk.GarbageCollection".equals(event.getEventType().getName())) {

					long duration = event.getDuration().toMillis();
					Instant eventTime = event.getStartTime();

					String gcName = event.getString("name");
					if (gcName == null) {
						gcName = event.getEventType().getName();
					}

					// Track GC counts
					gcCounts.put(gcName, gcCounts.getOrDefault(gcName, 0L) + 1);

					// Accumulate duration statistics
					totalPauseDuration += duration;
					minPauseDuration = Math.min(minPauseDuration, duration);
					maxPauseDuration = Math.max(maxPauseDuration, duration);

					// Store individual pause details
					Map<String, Object> pauseDetail = new HashMap<>();
					pauseDetail.put("gc_type", gcName);
					pauseDetail.put("duration_ms", duration);
					pauseDetail.put("timestamp", eventTime.toString());
					pauseDetail.put("event_time_nanos", event.getStartTime().toEpochMilli() * 1_000_000);

					gcPauses.add(pauseDetail);
				}
			}
		} catch (Exception e) {
			System.err.println("Warning: Failed to parse GC metrics from JFR: " + e.getMessage());
		}

		// Add summary statistics
		gcMetrics.put("pause_count", gcPauses.size());
		if (!gcPauses.isEmpty()) {
			gcMetrics.put("total_pause_duration_ms", totalPauseDuration);
			gcMetrics.put("avg_pause_duration_ms", totalPauseDuration / (double) gcPauses.size());
			gcMetrics.put("min_pause_duration_ms", minPauseDuration);
			gcMetrics.put("max_pause_duration_ms", maxPauseDuration);
		}

		// Add GC type breakdown
		ObjectNode gcTypeBreakdown = getObjectMapper().createObjectNode();
		for (String gcType : gcCounts.keySet()) {
			gcTypeBreakdown.put(gcType, gcCounts.get(gcType));
		}
		gcMetrics.set("gc_type_counts", gcTypeBreakdown);

		// Add individual pause details
		ArrayNode pauseDetails = getObjectMapper().createArrayNode();
		for (Map<String, Object> pause : gcPauses) {
			pauseDetails.add(getObjectMapper().convertValue(pause, ObjectNode.class));
		}
		gcMetrics.set("pause_details", pauseDetails);

		return gcMetrics;
	}

	/**
	 * Extract thread metrics from JFR events.
	 */
	private ObjectNode extractThreadMetrics() throws Exception {
		ObjectNode threadMetrics = getObjectMapper().createObjectNode();
		long threadStartCount = 0;
		long threadEndCount = 0;
		long threadParkCount = 0;
		long maxConcurrentThreads = 0;

		try (RecordingFile recordingFile = new RecordingFile(jfrFilePath)) {
			while (recordingFile.hasMoreEvents()) {
				RecordedEvent event = recordingFile.readEvent();
				String eventName = event.getEventType().getName();

				if ("jdk.ThreadStart".equals(eventName)) {
					threadStartCount++;
				} else if ("jdk.ThreadEnd".equals(eventName)) {
					threadEndCount++;
				} else if ("jdk.ThreadPark".equals(eventName)) {
					threadParkCount++;
				}
			}
		} catch (Exception e) {
			System.err.println("Warning: Failed to parse thread metrics from JFR: " + e.getMessage());
		}

		threadMetrics.put("thread_start_count", threadStartCount);
		threadMetrics.put("thread_end_count", threadEndCount);
		threadMetrics.put("thread_park_count", threadParkCount);
		threadMetrics.put("net_thread_creation", threadStartCount - threadEndCount);

		return threadMetrics;
	}

	/**
	 * Extract memory allocation metrics from JFR events.
	 */
	private ObjectNode extractMemoryMetrics() throws Exception {
		ObjectNode memoryMetrics = getObjectMapper().createObjectNode();
		long tlabAllocations = 0;
		long outsideTlabAllocations = 0;
		long totalAllocationBytes = 0;

		try (RecordingFile recordingFile = new RecordingFile(jfrFilePath)) {
			while (recordingFile.hasMoreEvents()) {
				RecordedEvent event = recordingFile.readEvent();
				String eventName = event.getEventType().getName();

				if ("jdk.ObjectAllocationInNewTLAB".equals(eventName)) {
					tlabAllocations++;
					try {
						long size = event.getLong("tlabSize");
						totalAllocationBytes += size;
					} catch (Exception ignored) {
					}
				} else if ("jdk.ObjectAllocationOutsideTLAB".equals(eventName)) {
					outsideTlabAllocations++;
					try {
						long size = event.getLong("allocSize");
						totalAllocationBytes += size;
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Warning: Failed to parse memory metrics from JFR: " + e.getMessage());
		}

		memoryMetrics.put("tlab_allocations", tlabAllocations);
		memoryMetrics.put("outside_tlab_allocations", outsideTlabAllocations);
		memoryMetrics.put("total_allocation_bytes", totalAllocationBytes);
		memoryMetrics.put("total_allocation_mb", totalAllocationBytes / (1024.0 * 1024.0));

		return memoryMetrics;
	}

	/**
	 * Extract blocking/contention metrics from JFR events.
	 */
	private ObjectNode extractBlockingMetrics() throws Exception {
		ObjectNode blockingMetrics = getObjectMapper().createObjectNode();
		long monitorEnterCount = 0;
		long monitorWaitCount = 0;
		long totalWaitDuration = 0;

		List<Map<String, Object>> blockingEvents = new ArrayList<>();

		try (RecordingFile recordingFile = new RecordingFile(jfrFilePath)) {
			while (recordingFile.hasMoreEvents()) {
				RecordedEvent event = recordingFile.readEvent();
				String eventName = event.getEventType().getName();

				if ("jdk.JavaMonitorEnter".equals(eventName)) {
					monitorEnterCount++;
				} else if ("jdk.JavaMonitorWait".equals(eventName)) {
					monitorWaitCount++;
					long waitDuration = event.getDuration().toMillis();
					totalWaitDuration += waitDuration;

					Map<String, Object> waitDetail = new HashMap<>();
					waitDetail.put("wait_duration_ms", waitDuration);
					try {
						waitDetail.put("timeout_ms", event.getLong("timeout"));
					} catch (Exception ignored) {
					}
					waitDetail.put("timestamp", event.getStartTime().toString());

					blockingEvents.add(waitDetail);
				}
			}
		} catch (Exception e) {
			System.err.println("Warning: Failed to parse blocking metrics from JFR: " + e.getMessage());
		}

		blockingMetrics.put("monitor_enter_count", monitorEnterCount);
		blockingMetrics.put("monitor_wait_count", monitorWaitCount);
		blockingMetrics.put("total_wait_duration_ms", totalWaitDuration);

		if (monitorWaitCount > 0) {
			blockingMetrics.put("avg_wait_duration_ms", totalWaitDuration / (double) monitorWaitCount);
		}

		// Add blocking event details
		ArrayNode blockingDetails = getObjectMapper().createArrayNode();
		for (Map<String, Object> event : blockingEvents) {
			blockingDetails.add(getObjectMapper().convertValue(event, ObjectNode.class));
		}
		blockingMetrics.set("wait_details", blockingDetails);

		return blockingMetrics;
	}

	/**
	 * Count total events in the JFR file.
	 */
	private long countTotalEvents() throws Exception {
		long count = 0;
		try (RecordingFile recordingFile = new RecordingFile(jfrFilePath)) {
			while (recordingFile.hasMoreEvents()) {
				recordingFile.readEvent();
				count++;
			}
		} catch (Exception e) {
			System.err.println("Warning: Failed to count total JFR events: " + e.getMessage());
		}
		return count;
	}

}
