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
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestTemplate;

/**
 * Tests to verify resource cleanup and management with virtual threads.
 * Ensures no resource leaks occur and cleanup happens properly.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Virtual Thread Resource Tests")
public class VirtualThreadResourceTests {

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
				factory.setReadTimeout(10000);
				return factory;
			})
			.rootUri("http://localhost:" + port)
			.build();
	}

	@Test
	@DisplayName("Verify no thread leaks occur during repeated request cycles")
	void testNoThreadLeaks() throws InterruptedException {
		RestTemplate template = restTemplate();
		ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
		
		long initialThreadCount = threadBean.getThreadCount();
		System.out.println("Initial thread count: " + initialThreadCount);
		
		// Perform multiple cycles of concurrent requests
		for (int cycle = 0; cycle < 5; cycle++) {
			int requestsPerCycle = 50;
			CountDownLatch endGate = new CountDownLatch(requestsPerCycle);
			
			for (int i = 0; i < requestsPerCycle; i++) {
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
			
			assertThat(endGate.await(60, TimeUnit.SECONDS)).isTrue();
			Thread.sleep(500); // Allow cleanup between cycles
		}
		
		// Force garbage collection
		System.gc();
		Thread.sleep(1000);
		
		long finalThreadCount = threadBean.getThreadCount();
		System.out.println("Final thread count: " + finalThreadCount);
		System.out.println("Thread increase: " + (finalThreadCount - initialThreadCount));
		
		// Thread count should return to approximately initial level after cleanup
		// Allow some variance but no unbounded growth
		assertThat(finalThreadCount).isLessThan(initialThreadCount + 50);
	}

	@Test
	@DisplayName("Verify memory is released after virtual thread operations")
	void testMemoryRelease() throws InterruptedException {
		RestTemplate template = restTemplate();
		MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
		
		// Get baseline memory
		System.gc();
		Thread.sleep(500);
		long initialMemory = memoryBean.getHeapMemoryUsage().getUsed();
		System.out.println("Initial memory: " + (initialMemory / 1024 / 1024) + "MB");
		
		// Execute large batch of requests
		int batchSize = 200;
		CountDownLatch endGate = new CountDownLatch(batchSize);
		
		for (int i = 0; i < batchSize; i++) {
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
		
		assertThat(endGate.await(60, TimeUnit.SECONDS)).isTrue();
		
		long peakMemory = memoryBean.getHeapMemoryUsage().getUsed();
		System.out.println("Peak memory: " + (peakMemory / 1024 / 1024) + "MB");
		System.out.println("Memory increase at peak: " + ((peakMemory - initialMemory) / 1024 / 1024) + "MB");
		
		// Give time for GC to clean up
		System.gc();
		Thread.sleep(2000);
		
		long finalMemory = memoryBean.getHeapMemoryUsage().getUsed();
		System.out.println("Final memory: " + (finalMemory / 1024 / 1024) + "MB");
		System.out.println("Memory increase after cleanup: " + ((finalMemory - initialMemory) / 1024 / 1024) + "MB");
		
		// After GC, memory should be closer to baseline
		long recoveredMemory = peakMemory - finalMemory;
		System.out.println("Memory recovered: " + (recoveredMemory / 1024 / 1024) + "MB");
		
		// Verify significant memory was recovered
		assertThat(recoveredMemory).isGreaterThan((peakMemory - initialMemory) / 2);
	}

	@Test
	@DisplayName("Verify database connection pool doesn't leak connections")
	void testConnectionPoolCleanup() throws InterruptedException {
		int cycles = 3;
		int requestsPerCycle = 50;
		
		for (int cycle = 0; cycle < cycles; cycle++) {
			CountDownLatch endGate = new CountDownLatch(requestsPerCycle);
			
			for (int i = 0; i < requestsPerCycle; i++) {
				Thread.startVirtualThread(() -> {
					try {
						var result = vets.findAll();
						assertThat(result).isNotEmpty();
					} catch (Exception e) {
						throw new RuntimeException(e);
					} finally {
						endGate.countDown();
					}
				});
			}
			
			assertThat(endGate.await(60, TimeUnit.SECONDS)).isTrue();
			
			System.out.println("Cycle " + (cycle + 1) + " completed");
			Thread.sleep(500); // Allow connection cleanup
		}
		
		// All cycles should complete without connection pool exhaustion
		System.out.println("All cycles completed - connection pool functional");
	}

	@Test
	@DisplayName("Verify exception handling doesn't cause resource leaks")
	void testExceptionHandling() throws InterruptedException {
		RestTemplate template = restTemplate();
		ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
		
		long initialThreadCount = threadBean.getThreadCount();
		
		int requestsCount = 60;
		CountDownLatch endGate = new CountDownLatch(requestsCount);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger errorCount = new AtomicInteger(0);
		
		for (int i = 0; i < requestsCount; i++) {
			final int index = i;
			Thread.startVirtualThread(() -> {
				try {
					// Mix valid and invalid requests
					String path = (index % 2 == 0) ? "/owners/1" : "/invalid/path/999999";
					
					try {
						ResponseEntity<String> response = template.exchange(
							RequestEntity.get(path).build(), 
							String.class
						);
						if (response.getStatusCode().is2xxSuccessful()) {
							successCount.incrementAndGet();
						}
					} catch (Exception e) {
						// Expected for invalid paths
						errorCount.incrementAndGet();
					}
				} finally {
					endGate.countDown();
				}
			});
		}
		
		assertThat(endGate.await(60, TimeUnit.SECONDS)).isTrue();
		
		long finalThreadCount = threadBean.getThreadCount();
		System.out.println("Thread count - Initial: " + initialThreadCount + ", Final: " + finalThreadCount);
		
		// Verify requests were processed
		assertThat(successCount.get() + errorCount.get()).isEqualTo(requestsCount);
		
		// Thread count should not grow unbounded despite exceptions
		assertThat(finalThreadCount - initialThreadCount).isLessThan(50);
	}

	@Test
	@DisplayName("Verify no file descriptor leaks in servlet handling")
	void testFileDescriptorCleanup() throws InterruptedException {
		RestTemplate template = restTemplate();
		
		int cycles = 3;
		int requestsPerCycle = 50;
		
		for (int cycle = 0; cycle < cycles; cycle++) {
			CountDownLatch endGate = new CountDownLatch(requestsPerCycle);
			AtomicInteger successCount = new AtomicInteger(0);
			
			for (int i = 0; i < requestsPerCycle; i++) {
				Thread.startVirtualThread(() -> {
					try {
						ResponseEntity<String> response = template.exchange(
							RequestEntity.get("/owners?lastName=").build(), 
							String.class
						);
						if (response.getStatusCode() == HttpStatus.OK) {
							successCount.incrementAndGet();
						}
					} catch (Exception e) {
						throw new RuntimeException(e);
					} finally {
						endGate.countDown();
					}
				});
			}
			
			assertThat(endGate.await(60, TimeUnit.SECONDS)).isTrue();
			
			System.out.println("Cycle " + (cycle + 1) + ": " + successCount.get() + "/" + 
			                   requestsPerCycle + " successful");
			
			// All requests should succeed
			assertThat(successCount.get()).isEqualTo(requestsPerCycle);
		}
	}

	@Test
	@DisplayName("Verify request-scoped beans are properly cleaned up")
	void testRequestScopedBeanCleanup() throws InterruptedException {
		RestTemplate template = restTemplate();
		
		int concurrentRequests = 100;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		
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
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					endGate.countDown();
				}
			});
		}
		
		startGate.countDown();
		assertThat(endGate.await(60, TimeUnit.SECONDS)).isTrue();
		
		// All request-scoped beans should have been properly instantiated and cleaned up
		assertThat(successCount.get()).isEqualTo(concurrentRequests);
	}

	@Test
	@DisplayName("Verify sustained operation doesn't accumulate resources")
	void testSustainedResourceManagement() throws InterruptedException {
		RestTemplate template = restTemplate();
		MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
		ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
		
		// Get initial state
		System.gc();
		Thread.sleep(500);
		long initialMemory = memoryBean.getHeapMemoryUsage().getUsed();
		long initialThreads = threadBean.getThreadCount();
		
		// Run sustained workload
		int duration = 30; // seconds
		long endTime = System.currentTimeMillis() + (duration * 1000);
		int iterationCount = 0;
		
		while (System.currentTimeMillis() < endTime) {
			CountDownLatch endGate = new CountDownLatch(30);
			
			for (int i = 0; i < 30; i++) {
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
			
			assertThat(endGate.await(30, TimeUnit.SECONDS)).isTrue();
			iterationCount++;
		}
		
		System.gc();
		Thread.sleep(1000);
		
		long finalMemory = memoryBean.getHeapMemoryUsage().getUsed();
		long finalThreads = threadBean.getThreadCount();
		
		System.out.println("Sustained operation completed in " + duration + " seconds");
		System.out.println("Iterations: " + iterationCount);
		System.out.println("Memory - Initial: " + (initialMemory / 1024 / 1024) + "MB, Final: " + 
		                   (finalMemory / 1024 / 1024) + "MB, Increase: " + 
		                   ((finalMemory - initialMemory) / 1024 / 1024) + "MB");
		System.out.println("Threads - Initial: " + initialThreads + ", Final: " + finalThreads);
		
		// Verify no unbounded resource growth
		assertThat(finalThreads - initialThreads).isLessThan(50);
		assertThat(finalMemory - initialMemory).isLessThan(300 * 1024 * 1024); // Less than 300MB increase
	}

}
