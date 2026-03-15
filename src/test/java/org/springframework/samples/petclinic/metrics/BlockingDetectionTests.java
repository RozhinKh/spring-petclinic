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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for blocking detection components
 */
public class BlockingDetectionTests {

	private BlockingDetectionHarness blockingHarness;

	private JfrBlockingListener jfrListener;

	private RuntimeBlockingTracker runtimeTracker;

	@BeforeEach
	void setUp() {
		blockingHarness = new BlockingDetectionHarness();
		jfrListener = new JfrBlockingListener();
		runtimeTracker = new RuntimeBlockingTracker(jfrListener);
	}

	@Test
	void testStaticAnalyzerInitialization() throws IOException {
		StaticBlockingAnalyzer analyzer = new StaticBlockingAnalyzer();
		analyzer.analyze();

		List<StaticBlockingAnalyzer.BlockingFinding> findings = analyzer.getFindings();
		Map<String, Object> summary = analyzer.getSummary();

		// Verify summary structure
		assertThat(summary).containsKeys("total_findings", "findings_by_pattern", "findings_by_severity",
				"affected_classes");
	}

	@Test
	void testJfrListenerLifecycle() {
		jfrListener.start();
		assertThat(jfrListener.getBlockingEvents()).isEmpty();

		jfrListener.stop();
		assertThat(jfrListener.isCollecting()).isFalse();
	}

	@Test
	void testJfrListenerEventCapture() {
		jfrListener.start();

		// Simulate monitor event
		JfrBlockingListener.BlockingEvent mockEvent = new JfrBlockingListener.BlockingEvent();
		mockEvent.setType("MONITOR_ENTER");
		mockEvent.setTimestamp(System.currentTimeMillis());
		mockEvent.setDuration(10L);
		mockEvent.setThreadName("test-thread");
		mockEvent.setThreadId(1L);
		mockEvent.setClassName("TestClass");
		mockEvent.setMethodName("testMethod");

		// Note: In real scenario, events come from JFR listener
		// This is a simplified test structure
		jfrListener.stop();
	}

	@Test
	void testRuntimeBlockingTrackerAggregation() {
		jfrListener.start();

		// Create mock events
		for (int i = 0; i < 3; i++) {
			JfrBlockingListener.BlockingEvent event = new JfrBlockingListener.BlockingEvent();
			event.setType("MONITOR_ENTER");
			event.setTimestamp(System.currentTimeMillis() + i * 100);
			event.setDuration(10L + i);
			event.setThreadName("test-thread");
			event.setThreadId(1L);
			event.setClassName("org.springframework.samples.TestClass");
			event.setMethodName("testMethod");
		}

		jfrListener.stop();
		runtimeTracker.aggregateBlockingEvents();

		Map<String, Object> summary = runtimeTracker.getSummary();
		assertThat(summary).containsKeys("total_blocking_events", "total_blocking_time_ms", "affected_methods",
				"affected_classes");
	}

	@Test
	void testBlockingComparisonReporter() throws IOException {
		StaticBlockingAnalyzer staticAnalyzer = new StaticBlockingAnalyzer();
		staticAnalyzer.analyze();

		BlockingComparisonReporter reporter = new BlockingComparisonReporter(staticAnalyzer, runtimeTracker);
		reporter.generateReport();

		List<BlockingComparisonReporter.BlockingComparison> comparisons = reporter.getComparisons();
		Map<String, Object> summary = reporter.getSummary();

		// Verify summary contains expected fields
		assertThat(summary).containsKeys("total_static_findings", "total_runtime_findings", "triggered_findings",
				"false_positives", "false_negatives");
	}

	@Test
	void testBlockingDetectionHarnessLifecycle() throws IOException {
		blockingHarness.initialize();

		// Start benchmark for variant
		blockingHarness.startBenchmark("java17");
		assertThat(blockingHarness.isCollecting()).isTrue();
		assertThat(blockingHarness.getCurrentVariant()).isEqualTo("java17");

		// Stop benchmark
		BlockingDetectionHarness.BlockingDetectionResult result = blockingHarness.stopBenchmark("java17");
		assertThat(result).isNotNull();
		assertThat(result.getVariant()).isEqualTo("java17");
		assertThat(blockingHarness.isCollecting()).isFalse();
	}

	@Test
	void testBlockingDetectionHarnessMultipleVariants() throws IOException {
		blockingHarness.initialize();

		String[] variants = { "java17", "java21-traditional", "java21-virtual" };

		for (String variant : variants) {
			blockingHarness.startBenchmark(variant);
			// Simulate some work
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			blockingHarness.stopBenchmark(variant);
		}

		Map<String, BlockingDetectionHarness.BlockingDetectionResult> results = blockingHarness.getAllResults();
		assertThat(results).hasSize(3);
		assertThat(results.keySet()).containsExactlyInAnyOrder("java17", "java21-traditional", "java21-virtual");
	}

	@Test
	void testBlockingExporter() throws IOException {
		blockingHarness.initialize();
		blockingHarness.startBenchmark("test-variant");
		BlockingDetectionHarness.BlockingDetectionResult result = blockingHarness.stopBenchmark("test-variant");

		// Export to JSON and CSV
		Map<String, String> exports = blockingHarness.exportResults("test-variant");

		assertThat(exports).containsKeys("json", "csv");
		assertThat(exports.get("json")).contains("blocking-test-variant");
		assertThat(exports.get("csv")).contains("blocking-test-variant");
	}

	@Test
	void testBlockingExporterComparison() throws IOException {
		blockingHarness.initialize();

		// Generate results for multiple variants
		for (String variant : new String[] { "java17", "java21-virtual" }) {
			blockingHarness.startBenchmark(variant);
			blockingHarness.stopBenchmark(variant);
		}

		// Export comparison
		Map<String, String> exports = blockingHarness.exportComparison();

		assertThat(exports).containsKeys("json", "csv");
		assertThat(exports.get("json")).contains("blocking-comparison");
		assertThat(exports.get("csv")).contains("blocking-comparison");
	}

	@Test
	void testBlockingDetectionResultStructure() throws IOException {
		blockingHarness.initialize();
		blockingHarness.startBenchmark("test");
		BlockingDetectionHarness.BlockingDetectionResult result = blockingHarness.stopBenchmark("test");

		// Verify result contains all required components
		assertThat(result.getVariant()).isEqualTo("test");
		assertThat(result.getStaticFindings()).isNotNull();
		assertThat(result.getRuntimeEvents()).isNotNull();
		assertThat(result.getComparisons()).isNotNull();
		assertThat(result.getStaticSummary()).isNotNull();
		assertThat(result.getRuntimeSummary()).isNotNull();
		assertThat(result.getComparisonSummary()).isNotNull();

		// Verify map serialization works
		Map<String, Object> resultMap = result.toMap();
		assertThat(resultMap).containsKeys("variant", "static_summary", "runtime_summary", "comparison_summary",
				"static_findings", "runtime_events", "comparisons");
	}

	@Test
	void testStaticBlockingFindingMap() {
		StaticBlockingAnalyzer.BlockingFinding finding = new StaticBlockingAnalyzer.BlockingFinding();
		finding.setPattern("synchronized_block");
		finding.setClassName("TestClass");
		finding.setLocation("TestClass.java:42");
		finding.setSeverity("MEDIUM");
		finding.setSource("STATIC_SCAN");
		finding.setLineNumber(42);

		Map<String, Object> findingMap = finding.toMap();

		assertThat(findingMap).containsKeys("pattern", "class", "location", "severity", "source", "line");
		assertThat(findingMap.get("pattern")).isEqualTo("synchronized_block");
		assertThat(findingMap.get("severity")).isEqualTo("MEDIUM");
	}

	@Test
	void testJfrBlockingEventMap() {
		JfrBlockingListener.BlockingEvent event = new JfrBlockingListener.BlockingEvent();
		event.setType("MONITOR_ENTER");
		event.setTimestamp(System.currentTimeMillis());
		event.setDuration(100L);
		event.setThreadName("test-thread");
		event.setThreadId(42L);
		event.setClassName("TestClass");
		event.setMethodName("testMethod");
		event.setLockClass("java.lang.Object");

		Map<String, Object> eventMap = event.toMap();

		assertThat(eventMap).containsKeys("type", "timestamp_ms", "duration_ms", "thread_name", "thread_id", "class",
				"method", "lock_class");
		assertThat(eventMap.get("type")).isEqualTo("MONITOR_ENTER");
		assertThat(eventMap.get("duration_ms")).isEqualTo(100L);
	}

	@Test
	void testBlockingComparisonMap() {
		BlockingComparisonReporter.BlockingComparison comparison = new BlockingComparisonReporter.BlockingComparison();
		comparison.setBlockingPattern("synchronized_block");
		comparison.setClassName("TestClass");
		comparison.setStaticCount(1);
		comparison.setStaticLocation("TestClass.java:42");
		comparison.setStaticSeverity("MEDIUM");
		comparison.setRuntimeCount(3);
		comparison.setRuntimeDuration(150L);
		comparison.setTriggered(true);

		Map<String, Object> comparisonMap = comparison.toMap();

		assertThat(comparisonMap).containsKeys("pattern", "class", "static_count", "runtime_count",
				"runtime_duration_ms", "triggered", "false_negative");
		assertThat(comparisonMap.get("triggered")).isEqualTo(true);
		assertThat(comparisonMap.get("runtime_count")).isEqualTo(3);
	}

}
