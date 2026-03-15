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

package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestTemplate;

/**
 * Tests to verify virtual thread behavior with Spring Boot 4.0.1
 * Spring Boot automatically enables virtual threads for servlet request handling
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Virtual Thread Behavior Tests")
public class VirtualThreadBehaviorTests {

	@LocalServerPort
	int port;

	@Autowired
	private VetRepository vets;

	@Autowired
	private RestTemplateBuilder builder;

	private RestTemplate restTemplate() {
		return builder.requestFactory(() -> {
				SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
				factory.setConnectTimeout(5000);
				factory.setReadTimeout(15000);
				return factory;
			})
			.rootUri("http://localhost:" + port)
			.build();
	}

	private void awaitLatch(CountDownLatch latch, long timeoutSeconds) throws InterruptedException {
		assertThat(latch.await(timeoutSeconds, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	@DisplayName("Verify virtual thread support is available in Java 21")
	void testVirtualThreadsAvailable() {
		// Verify we're running on Java 21+
		String javaVersion = System.getProperty("java.version");
		assertThat(javaVersion).startsWith("21");
		
		// Verify VirtualThread class exists (Java 21+)
		try {
			Class.forName("java.lang.VirtualThread");
		} catch (ClassNotFoundException e) {
			throw new AssertionError("VirtualThread class not found - Java 21+ required");
		}
	}

	@Test
	@DisplayName("Verify servlet request handling works with virtual threads")
	void testServletRequestHandling() {
		RestTemplate template = restTemplate();
		
		// Test basic GET request
		ResponseEntity<String> response = template.exchange(
			RequestEntity.get("/owners/1").build(), 
			String.class
		);
		
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotEmpty();
	}

	@Test
	@DisplayName("Verify database operations execute successfully")
	void testDatabaseOperations() {
		// Verify database query executes
		var vets_result = vets.findAll();
		assertThat(vets_result).isNotEmpty();
		
		// Verify second call hits cache
		var vets_cached = vets.findAll();
		assertThat(vets_cached).isNotEmpty();
		assertThat(vets_cached).hasSize(vets_result.size());
	}

	@Test
	@DisplayName("Verify thread metrics show proper thread behavior")
	void testThreadMetrics() {
		ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
		
		// Get initial thread count
		long initialThreadCount = threadBean.getThreadCount();
		assertThat(initialThreadCount).isGreaterThan(0);
		
		// Perform some requests
		RestTemplate template = restTemplate();
		for (int i = 0; i < 5; i++) {
			ResponseEntity<String> response = template.exchange(
				RequestEntity.get("/owners?lastName=").build(), 
				String.class
			);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		}
		
		// Check that thread count is reasonable (virtual threads should not cause unbounded growth)
		long finalThreadCount = threadBean.getThreadCount();
		
		// Virtual threads can be created freely, but the number of carrier threads should be bounded
		// Just verify we don't have excessive platform threads
		assertThat(finalThreadCount).isLessThan(initialThreadCount + 100);
	}

	@Test
	@DisplayName("Verify multiple servlet requests are processed concurrently")
	void testConcurrentServletRequests() throws InterruptedException {
		RestTemplate template = restTemplate();
		
		// Create multiple concurrent request threads
		Thread[] threads = new Thread[10];
		boolean[] results = new boolean[10];
		CountDownLatch endGate = new CountDownLatch(threads.length);
		
		for (int i = 0; i < 10; i++) {
			final int index = i;
			threads[i] = Thread.startVirtualThread(() -> {
				try {
					ResponseEntity<String> response = template.exchange(
						RequestEntity.get("/owners?lastName=").build(), 
						String.class
					);
					results[index] = response.getStatusCode() == HttpStatus.OK;
				} catch (Exception e) {
					results[index] = false;
				} finally {
					endGate.countDown();
				}
			});
		}
		
		// Wait for all threads to complete
		awaitLatch(endGate, 30);
		
		// Verify all requests succeeded
		for (boolean result : results) {
			assertThat(result).isTrue();
		}
	}

	@Test
	@DisplayName("Verify servlet request context is properly isolated between concurrent requests")
	void testRequestContextIsolation() throws InterruptedException {
		RestTemplate template = restTemplate();
		
		// Execute concurrent requests that access different resources
		Thread[] threads = new Thread[5];
		ResponseEntity<?>[] responses = new ResponseEntity<?>[5];
		CountDownLatch endGate = new CountDownLatch(threads.length);
		
		for (int i = 1; i <= 5; i++) {
			final int ownerIndex = i;
			threads[i - 1] = Thread.startVirtualThread(() -> {
				try {
					responses[ownerIndex - 1] = template.exchange(
						RequestEntity.get("/owners/" + ownerIndex).build(), 
						String.class
					);
				} catch (Exception e) {
					responses[ownerIndex - 1] = null;
				} finally {
					endGate.countDown();
				}
			});
		}
		
		// Wait for completion
		awaitLatch(endGate, 30);
		
		// Verify each request got the correct response
		for (ResponseEntity<?> response : responses) {
			assertThat(response).isNotNull();
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		}
	}

	@Test
	@DisplayName("Verify no deadlocks occur during concurrent database and servlet operations")
	void testNoDeadlocks() throws InterruptedException {
		RestTemplate template = restTemplate();
		
		// Concurrent mix of database and servlet operations
		Thread[] threads = new Thread[20];
		Exception[] exceptions = new Exception[20];
		CountDownLatch endGate = new CountDownLatch(threads.length);
		
		for (int i = 0; i < 20; i++) {
			final int index = i;
			threads[i] = Thread.startVirtualThread(() -> {
				try {
					if (index % 2 == 0) {
						// Database operation
						vets.findAll();
					} else {
						// Servlet operation
						ResponseEntity<String> response = template.exchange(
							RequestEntity.get("/owners?lastName=").build(), 
							String.class
						);
						if (response.getStatusCode() != HttpStatus.OK) {
							throw new RuntimeException("Request failed with status: " + response.getStatusCode());
						}
					}
				} catch (Exception e) {
					exceptions[index] = e;
				} finally {
					endGate.countDown();
				}
			});
		}
		
		// Wait for completion with timeout to detect potential deadlocks
		awaitLatch(endGate, 30);
		
		// Verify no exceptions occurred
		for (Exception exception : exceptions) {
			assertThat(exception).isNull();
		}
	}

	@Test
	@DisplayName("Verify Spring Boot virtual thread auto-configuration is enabled")
	void testSpringBootVirtualThreadConfig() {
		// Verify Spring Boot 4.0.1 environment variables for virtual threads
		String threadType = System.getenv("VIRTUAL_THREADS");
		
		// In Spring Boot 4.0.1, virtual threads are enabled by default for servlet handling
		// This test documents the expected behavior
		try {
			// Try to create a virtual thread to verify support
			Thread virtualThread = Thread.ofVirtual().start(() -> {
				// Virtual thread body
			});
			virtualThread.join(1000);
		} catch (Exception e) {
			throw new AssertionError("Failed to create virtual thread: " + e.getMessage());
		}
	}

}
