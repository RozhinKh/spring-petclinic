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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.samples.petclinic.benchmark.BenchmarkRunner.getObjectMapper;

/**
 * Correlates Java Flight Recorder (JFR) events with JMH benchmark execution windows.
 * Identifies and analyzes relationships between low-level runtime events (GC, threads,
 * blocking) and high-level performance measurements (latency, throughput).
 */
public class JFRCorrelator {

	private final JsonNode jfrMetrics;

	private final JsonNode jmhMetrics;

	private final long benchmarkStartTimeMs;

	private final long benchmarkDurationMs;

	private final long jfrRecordingStartTimeMs;

	private final long jfrRecordingEndTimeMs;

	/**
	 * Create a correlator for analyzing relationship between JFR and JMH results.
	 * @param jfrMetrics Parsed JFR metrics from JFREventParser
	 * @param jmhMetrics JMH benchmark results
	 * @param benchmarkStartTimeMs Start time of benchmark execution
	 * @param benchmarkDurationMs Duration of benchmark execution
	 * @param jfrStartTimeMs Start time of JFR recording
	 * @param jfrEndTimeMs End time of JFR recording
	 */
	public JFRCorrelator(JsonNode jfrMetrics, JsonNode jmhMetrics, long benchmarkStartTimeMs, long benchmarkDurationMs,
			long jfrStartTimeMs, long jfrEndTimeMs) {
		this.jfrMetrics = jfrMetrics;
		this.jmhMetrics = jmhMetrics;
		this.benchmarkStartTimeMs = benchmarkStartTimeMs;
		this.benchmarkDurationMs = benchmarkDurationMs;
		this.jfrRecordingStartTimeMs = jfrStartTimeMs;
		this.jfrRecordingEndTimeMs = jfrEndTimeMs;
	}

	/**
	 * Perform correlation analysis between JFR events and JMH benchmarks.
	 * @return ObjectNode containing correlation analysis results
	 */
	public ObjectNode correlate() {
		ObjectNode correlationResult = getObjectMapper().createObjectNode();

		// Add timing information for context
		correlationResult.set("timing", analyzeTimingAlignment());

		// Analyze GC impact on latency
		correlationResult.set("gc_latency_correlation", analyzeGcLatencyCorrelation());

		// Analyze thread activity during benchmark
		correlationResult.set("thread_correlation", analyzeThreadMetrics());

		// Analyze memory pressure
		correlationResult.set("memory_pressure_correlation", analyzeMemoryPressure());

		// Analyze blocking contention
		correlationResult.set("blocking_correlation", analyzeBlockingImpact());

		// Summary of findings
		correlationResult.set("summary", generateCorrelationSummary());

		return correlationResult;
	}

	/**
	 * Analyze timing alignment between JFR recording and benchmark execution.
	 */
	private ObjectNode analyzeTimingAlignment() {
		ObjectNode timing = getObjectMapper().createObjectNode();

		// Calculate overlap between benchmark and JFR recording
		long benchmarkEndTimeMs = benchmarkStartTimeMs + benchmarkDurationMs;

		// Determine overlap window
		long overlapStartMs = Math.max(benchmarkStartTimeMs, jfrRecordingStartTimeMs);
		long overlapEndMs = Math.min(benchmarkEndTimeMs, jfrRecordingEndTimeMs);
		long overlapDurationMs = Math.max(0, overlapEndMs - overlapStartMs);

		timing.put("benchmark_start_ms", benchmarkStartTimeMs);
		timing.put("benchmark_end_ms", benchmarkEndTimeMs);
		timing.put("benchmark_duration_ms", benchmarkDurationMs);
		timing.put("jfr_recording_start_ms", jfrRecordingStartTimeMs);
		timing.put("jfr_recording_end_ms", jfrRecordingEndTimeMs);
		timing.put("jfr_recording_duration_ms", jfrRecordingEndTimeMs - jfrRecordingStartTimeMs);
		timing.put("overlap_start_ms", overlapStartMs);
		timing.put("overlap_end_ms", overlapEndMs);
		timing.put("overlap_duration_ms", overlapDurationMs);
		timing.put("overlap_percentage", (overlapDurationMs * 100.0) / benchmarkDurationMs);

		return timing;
	}

	/**
	 * Analyze potential correlation between GC pauses and latency spikes.
	 */
	private ObjectNode analyzeGcLatencyCorrelation() {
		ObjectNode gcCorrelation = getObjectMapper().createObjectNode();

		try {
			JsonNode gcMetrics = jfrMetrics.get("gc_metrics");
			if (gcMetrics == null) {
				return gcCorrelation;
			}

			// Get GC pause details
			JsonNode pauseDetails = gcMetrics.get("pause_details");
			if (pauseDetails == null || !pauseDetails.isArray()) {
				return gcCorrelation;
			}

			// Count GC pauses during benchmark
			long gcCountDuringBenchmark = 0;
			long totalGcDuringBenchmark = 0;
			List<Map<String, Object>> pausesDuringBenchmark = new ArrayList<>();

			ArrayNode pauseArray = (ArrayNode) pauseDetails;
			for (JsonNode pauseNode : pauseArray) {
				long pauseTimeNanos = pauseNode.get("event_time_nanos").asLong();
				long pauseTimeMs = pauseTimeNanos / 1_000_000;

				// Check if pause occurred during benchmark window
				if (pauseTimeMs >= benchmarkStartTimeMs
						&& pauseTimeMs <= (benchmarkStartTimeMs + benchmarkDurationMs)) {
					gcCountDuringBenchmark++;
					totalGcDuringBenchmark += pauseNode.get("duration_ms").asLong();

					Map<String, Object> pauseInfo = new HashMap<>();
					pauseInfo.put("gc_type", pauseNode.get("gc_type").asText());
					pauseInfo.put("duration_ms", pauseNode.get("duration_ms").asLong());
					pauseInfo.put("offset_from_benchmark_start_ms", pauseTimeMs - benchmarkStartTimeMs);

					pausesDuringBenchmark.add(pauseInfo);
				}
			}

			gcCorrelation.put("gc_pauses_during_benchmark", gcCountDuringBenchmark);
			gcCorrelation.put("total_gc_time_during_benchmark_ms", totalGcDuringBenchmark);
			gcCorrelation.put("avg_gc_pause_during_benchmark_ms",
					gcCountDuringBenchmark > 0 ? totalGcDuringBenchmark / (double) gcCountDuringBenchmark : 0);

			// Calculate GC frequency (pauses per second during benchmark)
			double gcFrequencyPerSec = (gcCountDuringBenchmark * 1000.0) / benchmarkDurationMs;
			gcCorrelation.put("gc_frequency_per_second", gcFrequencyPerSec);

			// Add details of pauses
			ArrayNode pausesArray = getObjectMapper().createArrayNode();
			for (Map<String, Object> pause : pausesDuringBenchmark) {
				pausesArray.add(getObjectMapper().convertValue(pause, ObjectNode.class));
			}
			gcCorrelation.set("pauses_details", pausesArray);

			// Estimate impact
			String gcImpactLevel = "none";
			if (gcCountDuringBenchmark > 0) {
				if (gcFrequencyPerSec > 5) {
					gcImpactLevel = "high";
				}
				else if (gcFrequencyPerSec > 2) {
					gcImpactLevel = "moderate";
				}
				else {
					gcImpactLevel = "low";
				}
			}
			gcCorrelation.put("estimated_gc_impact_level", gcImpactLevel);

		}
		catch (Exception e) {
			System.err.println("Error analyzing GC-latency correlation: " + e.getMessage());
		}

		return gcCorrelation;
	}

	/**
	 * Analyze thread creation and destruction during benchmark.
	 */
	private ObjectNode analyzeThreadMetrics() {
		ObjectNode threadCorrelation = getObjectMapper().createObjectNode();

		try {
			JsonNode threadMetrics = jfrMetrics.get("thread_metrics");
			if (threadMetrics == null) {
				return threadCorrelation;
			}

			long threadStartCount = threadMetrics.get("thread_start_count").asLong(0);
			long threadEndCount = threadMetrics.get("thread_end_count").asLong(0);
			long threadParkCount = threadMetrics.get("thread_park_count").asLong(0);
			long netThreadCreation = threadMetrics.get("net_thread_creation").asLong(0);

			threadCorrelation.put("thread_start_count", threadStartCount);
			threadCorrelation.put("thread_end_count", threadEndCount);
			threadCorrelation.put("net_thread_creation", netThreadCreation);
			threadCorrelation.put("thread_park_count", threadParkCount);

			// Estimate thread pool saturation
			String threadSaturation = "low";
			if (netThreadCreation > 100) {
				threadSaturation = "high";
			}
			else if (netThreadCreation > 50) {
				threadSaturation = "moderate";
			}
			threadCorrelation.put("estimated_thread_pool_saturation", threadSaturation);

		}
		catch (Exception e) {
			System.err.println("Error analyzing thread correlation: " + e.getMessage());
		}

		return threadCorrelation;
	}

	/**
	 * Analyze memory allocation pressure during benchmark.
	 */
	private ObjectNode analyzeMemoryPressure() {
		ObjectNode memoryCorrelation = getObjectMapper().createObjectNode();

		try {
			JsonNode memoryMetrics = jfrMetrics.get("memory_metrics");
			if (memoryMetrics == null) {
				return memoryCorrelation;
			}

			long tlabAllocations = memoryMetrics.get("tlab_allocations").asLong(0);
			long outsideTlabAllocations = memoryMetrics.get("outside_tlab_allocations").asLong(0);
			double totalAllocationMb = memoryMetrics.get("total_allocation_mb").asDouble(0);

			memoryCorrelation.put("tlab_allocations", tlabAllocations);
			memoryCorrelation.put("outside_tlab_allocations", outsideTlabAllocations);
			memoryCorrelation.put("total_allocation_mb", totalAllocationMb);

			// Calculate allocation rate (MB per second)
			double allocationRateMbPerSec = (totalAllocationMb * 1000.0) / benchmarkDurationMs;
			memoryCorrelation.put("allocation_rate_mb_per_sec", allocationRateMbPerSec);

			// Ratio of allocations outside TLAB (indicates contention)
			double outsideTlabRatio = 0;
			long totalAllocations = tlabAllocations + outsideTlabAllocations;
			if (totalAllocations > 0) {
				outsideTlabRatio = outsideTlabAllocations / (double) totalAllocations;
			}
			memoryCorrelation.put("outside_tlab_ratio", outsideTlabRatio);

			// Estimate memory pressure
			String memoryPressure = "low";
			if (outsideTlabRatio > 0.3) {
				memoryPressure = "high";
			}
			else if (outsideTlabRatio > 0.1) {
				memoryPressure = "moderate";
			}
			memoryCorrelation.put("estimated_memory_pressure", memoryPressure);

		}
		catch (Exception e) {
			System.err.println("Error analyzing memory pressure: " + e.getMessage());
		}

		return memoryCorrelation;
	}

	/**
	 * Analyze blocking/contention impact on performance.
	 */
	private ObjectNode analyzeBlockingImpact() {
		ObjectNode blockingCorrelation = getObjectMapper().createObjectNode();

		try {
			JsonNode blockingMetrics = jfrMetrics.get("blocking_metrics");
			if (blockingMetrics == null) {
				return blockingCorrelation;
			}

			long monitorEnterCount = blockingMetrics.get("monitor_enter_count").asLong(0);
			long monitorWaitCount = blockingMetrics.get("monitor_wait_count").asLong(0);
			long totalWaitDurationMs = blockingMetrics.get("total_wait_duration_ms").asLong(0);

			blockingCorrelation.put("monitor_enter_count", monitorEnterCount);
			blockingCorrelation.put("monitor_wait_count", monitorWaitCount);
			blockingCorrelation.put("total_wait_duration_ms", totalWaitDurationMs);

			// Calculate blocking frequency
			double blockingFrequencyPerSec = (monitorWaitCount * 1000.0) / benchmarkDurationMs;
			blockingCorrelation.put("blocking_frequency_per_second", blockingFrequencyPerSec);

			// Estimate contention level
			String contentionLevel = "none";
			if (blockingFrequencyPerSec > 100) {
				contentionLevel = "high";
			}
			else if (blockingFrequencyPerSec > 10) {
				contentionLevel = "moderate";
			}
			else if (monitorWaitCount > 0) {
				contentionLevel = "low";
			}
			blockingCorrelation.put("estimated_contention_level", contentionLevel);

		}
		catch (Exception e) {
			System.err.println("Error analyzing blocking impact: " + e.getMessage());
		}

		return blockingCorrelation;
	}

	/**
	 * Generate summary of key findings from correlation analysis.
	 */
	private ObjectNode generateCorrelationSummary() {
		ObjectNode summary = getObjectMapper().createObjectNode();

		try {
			// Count potential performance bottlenecks
			List<String> bottlenecks = new ArrayList<>();

			JsonNode gcCorr = analyzeGcLatencyCorrelation();
			String gcImpact = gcCorr.get("estimated_gc_impact_level").asText();
			if ("high".equals(gcImpact)) {
				bottlenecks.add("High GC pressure detected (" + gcImpact + ")");
			}

			JsonNode threadCorr = analyzeThreadMetrics();
			String threadSat = threadCorr.get("estimated_thread_pool_saturation").asText();
			if ("high".equals(threadSat)) {
				bottlenecks.add("High thread pool saturation");
			}

			JsonNode memCorr = analyzeMemoryPressure();
			String memPressure = memCorr.get("estimated_memory_pressure").asText();
			if ("high".equals(memPressure)) {
				bottlenecks.add("High memory allocation pressure");
			}

			JsonNode blockingCorr = analyzeBlockingImpact();
			String contention = blockingCorr.get("estimated_contention_level").asText();
			if ("high".equals(contention)) {
				bottlenecks.add("High lock contention detected");
			}

			ArrayNode bottleneckArray = getObjectMapper().createArrayNode();
			for (String bottleneck : bottlenecks) {
				bottleneckArray.add(bottleneck);
			}
			summary.set("potential_bottlenecks", bottleneckArray);

			// Overall assessment
			String overallAssessment;
			if (bottlenecks.isEmpty()) {
				overallAssessment = "No significant bottlenecks detected";
			}
			else if (bottlenecks.size() == 1) {
				overallAssessment = "One potential bottleneck: " + bottlenecks.get(0);
			}
			else {
				overallAssessment = "Multiple bottlenecks detected (" + bottlenecks.size() + ")";
			}
			summary.put("overall_assessment", overallAssessment);

		}
		catch (Exception e) {
			System.err.println("Error generating correlation summary: " + e.getMessage());
		}

		return summary;
	}

}
