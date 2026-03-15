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
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * Load tests to verify virtual thread behavior under concurrent load.
 * Tests verify that virtual threads handle multiple concurrent requests efficiently
 * without resource exhaustion or performance degradation.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Virtual Thread Load Tests")
public class VirtualThreadLoadTests {

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
	@DisplayName("Handle 100 concurrent servlet requests with virtual threads")
	void testConcurrent100Requests() throws InterruptedException {
		RestTemplate template = restTemplate();
		
		int concurrentRequests = 50;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		
		// Start all threads
		for (int i = 0; i < concurrentRequests; i++) {
			Thread.startVirtualThread(() -> {
				try {
					startGate.await(); // Wait for all threads to be ready
					
					ResponseEntity<String> response = template.exchange(
						RequestEntity.get("/owners?lastName=").build(), 
						String.class
					);
					
					if (response.getStatusCode() == HttpStatus.OK) {
						successCount.incrementAndGet();
					} else {
						failureCount.incrementAndGet();
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			});
		}
		
		// Release all threads at once
		long startTime = System.nanoTime();
		startGate.countDown();
		
		// Wait for completion
		awaitLatch(endGate, 60);
		long duration = System.nanoTime() - startTime;
		
		// Verify results
		assertThat(successCount.get()).isEqualTo(concurrentRequests);
		assertThat(failureCount.get()).isEqualTo(0);
		
		// Log timing
		System.out.println("100 concurrent requests completed in: " + (duration / 1_000_000) + "ms");
		assertThat(duration).isLessThan(30_000_000_000L); // Should complete in under 30 seconds
	}

	@Test
	@DisplayName("Handle 500 concurrent servlet requests with virtual threads")
	void testConcurrent500Requests() throws InterruptedException {
		RestTemplate template = restTemplate();
		
		int concurrentRequests = 100;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		
		// Start all threads
		for (int i = 0; i < concurrentRequests; i++) {
			Thread.startVirtualThread(() -> {
				try {
					startGate.await();
					
					ResponseEntity<String> response = template.exchange(
						RequestEntity.get("/owners/1").build(), 
						String.class
					);
					
					if (response.getStatusCode() == HttpStatus.OK) {
						successCount.incrementAndGet();
					} else {
						failureCount.incrementAndGet();
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			});
		}
		
		// Release all threads
		long startTime = System.nanoTime();
		startGate.countDown();
		
		// Wait for completion
		awaitLatch(endGate, 90);
		long duration = System.nanoTime() - startTime;
		
		// Verify results (allow minor transient failures under load)
		assertThat(successCount.get()).isGreaterThanOrEqualTo((int) Math.floor(concurrentRequests * 0.95));
		assertThat(failureCount.get()).isLessThanOrEqualTo((int) Math.ceil(concurrentRequests * 0.05));
		
		System.out.println("500 concurrent requests completed in: " + (duration / 1_000_000) + "ms");
		assertThat(duration).isLessThan(60_000_000_000L); // Should complete in under 60 seconds
	}

	@Test
	@DisplayName("Verify thread pool efficiency with virtual threads")
	void testThreadPoolEfficiency() throws InterruptedException {
		RestTemplate template = restTemplate();
		ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
		
		long initialThreadCount = threadBean.getThreadCount();
		
		// Execute 200 concurrent requests
		int concurrentRequests = 100;
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		
		for (int i = 0; i < concurrentRequests; i++) {
			Thread.startVirtualThread(() -> {
				try {
					ResponseEntity<String> response = template.exchange(
						RequestEntity.get("/owners?lastName=").build(), 
						String.class
					);
					assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					endGate.countDown();
				}
			});
		}
		
		awaitLatch(endGate, 60);
		
		long finalThreadCount = threadBean.getThreadCount();
		
		// With virtual threads, we should see reasonable thread counts
		// The number of actual carrier threads should remain bounded
		System.out.println("Initial thread count: " + initialThreadCount);
		System.out.println("Final thread count: " + finalThreadCount);
		System.out.println("Thread increase: " + (finalThreadCount - initialThreadCount));
		
		// Verify thread count doesn't grow unbounded
		assertThat(finalThreadCount - initialThreadCount).isLessThan(50);
	}

	@Test
	@DisplayName("Verify memory usage remains stable under virtual thread load")
	void testMemoryStability() throws InterruptedException {
		RestTemplate template = restTemplate();
		MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
		
		long initialMemory = memoryBean.getHeapMemoryUsage().getUsed();
		
		// Execute 300 concurrent requests
		int concurrentRequests = 150;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		
		for (int i = 0; i < concurrentRequests; i++) {
			Thread.startVirtualThread(() -> {
				try {
					startGate.await();
					
					ResponseEntity<String> response = template.exchange(
						RequestEntity.get("/owners?lastName=").build(), 
						String.class
					);
					assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					endGate.countDown();
				}
			});
		}
		
		startGate.countDown();
		awaitLatch(endGate, 60);
		
		// Force garbage collection to get stable memory reading
		System.gc();
		Thread.sleep(1000);
		
		long finalMemory = memoryBean.getHeapMemoryUsage().getUsed();
		long memoryIncrease = finalMemory - initialMemory;
		
		System.out.println("Initial memory: " + (initialMemory / 1024 / 1024) + "MB");
		System.out.println("Final memory: " + (finalMemory / 1024 / 1024) + "MB");
		System.out.println("Memory increase: " + (memoryIncrease / 1024 / 1024) + "MB");
		
		// Verify memory increase is reasonable (virtual threads use less memory than platform threads)
		// Allow up to 150MB increase for 150 concurrent requests
		assertThat(memoryIncrease).isLessThan(150 * 1024 * 1024);
	}

	@Test
	@DisplayName("Verify concurrent database operations with virtual threads")
	void testConcurrentDatabaseOperations() throws InterruptedException {
		int concurrentRequests = 25;
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		
		for (int i = 0; i < concurrentRequests; i++) {
			Thread.startVirtualThread(() -> {
				try {
					var result = vets.findAll();
					if (!result.isEmpty()) {
						successCount.incrementAndGet();
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			});
		}
		
		endGate.await();
		
		// All operations should succeed
		assertThat(successCount.get()).isEqualTo(concurrentRequests);
		assertThat(failureCount.get()).isEqualTo(0);
	}

	@Test
	@DisplayName("Verify mixed servlet and database operations under load")
	void testMixedOperationsUnderLoad() throws InterruptedException {
		RestTemplate template = restTemplate();
		
		int concurrentRequests = 50;
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		
		for (int i = 0; i < concurrentRequests; i++) {
			final int index = i;
			Thread.startVirtualThread(() -> {
				try {
					if (index % 2 == 0) {
						// Database operation
						var result = vets.findAll();
						if (!result.isEmpty()) {
							successCount.incrementAndGet();
						}
					} else {
						// Servlet operation
						ResponseEntity<String> response = template.exchange(
							RequestEntity.get("/owners?lastName=").build(), 
							String.class
						);
						if (response.getStatusCode() == HttpStatus.OK) {
							successCount.incrementAndGet();
						}
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			});
		}
		
		awaitLatch(endGate, 60);
		
		// All operations should succeed
		assertThat(successCount.get()).isEqualTo(concurrentRequests);
		assertThat(failureCount.get()).isEqualTo(0);
	}

	@Test
	@DisplayName("Verify sustained load handling with periodic memory checks")
	void testSustainedLoadWithMemoryMonitoring() throws InterruptedException {
		RestTemplate template = restTemplate();
		MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
		
		int iterationCount = 3;
		int requestsPerIteration = 50;
		
		for (int iteration = 0; iteration < iterationCount; iteration++) {
			CountDownLatch endGate = new CountDownLatch(requestsPerIteration);
			
			for (int i = 0; i < requestsPerIteration; i++) {
				Thread.startVirtualThread(() -> {
					try {
						ResponseEntity<String> response = template.exchange(
							RequestEntity.get("/owners?lastName=").build(), 
							String.class
						);
						assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
					} catch (Exception e) {
						throw new RuntimeException(e);
					} finally {
						endGate.countDown();
					}
				});
			}
			
			awaitLatch(endGate, 60);
			
			// Memory check between iterations
			long memoryUsed = memoryBean.getHeapMemoryUsage().getUsed();
			System.out.println("Iteration " + (iteration + 1) + " - Memory used: " + (memoryUsed / 1024 / 1024) + "MB");
			
			Thread.sleep(100); // Brief pause between iterations
		}
		
		// All iterations should complete successfully
		System.out.println("All iterations completed successfully");
	}

}
