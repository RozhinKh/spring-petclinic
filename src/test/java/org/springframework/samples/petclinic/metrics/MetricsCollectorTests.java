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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests for metrics collection system
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.profiles.active=benchmark")
@ActiveProfiles("benchmark")
class MetricsCollectorTests {

	private final MetricsExporter metricsExporter = MetricsExporter.createDefault();

	@Autowired(required = false)
	private MetricsCollector metricsCollector;

	@Autowired(required = false)
	private BenchmarkMetricsHarness benchmarkMetricsHarness;

	@Test
	void contextLoads() {
		// Verify metrics components are available
		assertThat(metricsExporter).isNotNull();
	}

	@Test
	void metricsSnapshotCanBeCreated() {
		// Create a sample metrics snapshot
		Map<String, Object> httpMetrics = Map.of("mean_response_time_ms", 50.0, "max_response_time_ms", 100.0,
				"request_count", 1000.0);

		Map<String, Object> jvmMetrics = Map.of("heap_memory_used_bytes", 1024000.0, "heap_memory_max_bytes",
				2048000.0);

		Map<String, Object> snapshot = MetricsExporter.createMetricsSnapshot(System.currentTimeMillis(), httpMetrics,
				jvmMetrics, Map.of(), Map.of(), Map.of());

		// Verify snapshot structure
		assertThat(snapshot).containsKeys("timestamp_ms", "timestamp_iso", "metrics");
		assertThat(snapshot.get("metrics")).isInstanceOf(Map.class);

		Map<String, Object> metrics = (Map<String, Object>) snapshot.get("metrics");
		assertThat(metrics).containsKeys("http", "jvm");
	}

}
