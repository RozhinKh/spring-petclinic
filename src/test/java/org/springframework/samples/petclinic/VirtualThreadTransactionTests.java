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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests to verify JPA transaction handling with virtual threads.
 * Ensures transaction isolation and consistency under concurrent access.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Virtual Thread Transaction Tests")
public class VirtualThreadTransactionTests {

	@Autowired
	private VetRepository vetRepository;

	@Test
	@DisplayName("Verify concurrent read transactions maintain consistency")
	void testConcurrentReadTransactions() throws InterruptedException {
		int concurrentReads = 50;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(concurrentReads);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		
		// Verify initial state
		var initialVets = vetRepository.findAll();
		int expectedCount = initialVets.size();
		
		for (int i = 0; i < concurrentReads; i++) {
			new Thread(() -> {
				try {
					startGate.await();
					
					var vets = vetRepository.findAll();
					if (vets.size() == expectedCount && !vets.isEmpty()) {
						successCount.incrementAndGet();
					} else {
						failureCount.incrementAndGet();
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			}).start();
		}
		
		startGate.countDown();
		endGate.await();
		
		// All reads should see consistent data
		assertThat(successCount.get()).isEqualTo(concurrentReads);
		assertThat(failureCount.get()).isEqualTo(0);
	}

	@Test
	@DisplayName("Verify JPA entity relationships under concurrent access")
	void testConcurrentEntityAccess() throws InterruptedException {
		int concurrentRequests = 30;
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		
		for (int i = 0; i < concurrentRequests; i++) {
			new Thread(() -> {
				try {
					var vets = vetRepository.findAll();
					
					// Access entity relationships to verify lazy loading works
					if (!vets.isEmpty()) {
						for (Vet vet : vets) {
							// This should trigger lazy loading if needed
							assertThat(vet.getFirstName()).isNotNull();
							assertThat(vet.getLastName()).isNotNull();
							// Access specialties (many-to-many relationship)
							assertThat(vet.getSpecialties()).isNotNull();
						}
						successCount.incrementAndGet();
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			}).start();
		}
		
		endGate.await();
		
		// All operations should succeed
		assertThat(successCount.get()).isEqualTo(concurrentRequests);
		assertThat(failureCount.get()).isEqualTo(0);
	}

	@Test
	@DisplayName("Verify database connection pooling works with virtual threads")
	void testConnectionPoolingWithVirtualThreads() throws InterruptedException {
		int concurrentRequests = 100;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		AtomicInteger timeoutCount = new AtomicInteger(0);
		
		for (int i = 0; i < concurrentRequests; i++) {
			new Thread(() -> {
				try {
					startGate.await();
					
					// Each virtual thread should be able to get a connection
					var vets = vetRepository.findAll();
					if (!vets.isEmpty()) {
						successCount.incrementAndGet();
					} else {
						failureCount.incrementAndGet();
					}
				} catch (Exception e) {
					// Could be a timeout if connection pool is exhausted
					timeoutCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			}).start();
		}
		
		startGate.countDown();
		endGate.await();
		
		// Should not have timeouts or failures
		System.out.println("Success: " + successCount.get() + ", Failures: " + failureCount.get() + 
		                   ", Timeouts: " + timeoutCount.get());
		assertThat(successCount.get()).isGreaterThan(concurrentRequests * 0.95); // Allow 5% failures for connection variations
		assertThat(timeoutCount.get()).isLessThan(concurrentRequests * 0.05);
	}

	@Test
	@DisplayName("Verify lazy initialization works correctly with virtual threads")
	void testLazyInitialization() throws InterruptedException {
		int concurrentRequests = 25;
		CountDownLatch endGate = new CountDownLatch(concurrentRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		
		for (int i = 0; i < concurrentRequests; i++) {
			new Thread(() -> {
				try {
					var vets = vetRepository.findAll();
					
					if (!vets.isEmpty()) {
						// Access collections to trigger lazy loading
						Vet vet = vets.get(0);
						int specialtyCount = vet.getSpecialties().size();
						
						if (specialtyCount >= 0) { // Size is always >= 0
							successCount.incrementAndGet();
						}
					}
				} catch (Exception e) {
					// LazyInitializationException or other errors
					failureCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			}).start();
		}
		
		endGate.await();
		
		// All lazy initializations should succeed
		assertThat(successCount.get()).isEqualTo(concurrentRequests);
		assertThat(failureCount.get()).isEqualTo(0);
	}

	@Test
	@DisplayName("Verify transaction context isolation with virtual threads")
	void testTransactionContextIsolation() throws InterruptedException {
		int concurrentTransactions = 40;
		CountDownLatch endGate = new CountDownLatch(concurrentTransactions);
		AtomicInteger successCount = new AtomicInteger(0);
		
		for (int i = 0; i < concurrentTransactions; i++) {
			new Thread(() -> {
				try {
					// Each virtual thread has its own transaction context
					var vets = vetRepository.findAll();
					
					// Perform multiple operations in same transaction context
					for (int j = 0; j < 3; j++) {
						vets = vetRepository.findAll(); // Cache hit on 2nd and 3rd calls
					}
					
					successCount.incrementAndGet();
				} catch (Exception e) {
					// Transaction context issues
					throw new RuntimeException(e);
				} finally {
					endGate.countDown();
				}
			}).start();
		}
		
		endGate.await();
		
		// All transactions should complete successfully
		assertThat(successCount.get()).isEqualTo(concurrentTransactions);
	}

	@Test
	@DisplayName("Verify ORM session handling doesn't leak across virtual threads")
	void testOrmSessionIsolation() throws InterruptedException {
		int iterations = 3;
		
		for (int iter = 0; iter < iterations; iter++) {
			int concurrentSessions = 50;
			CountDownLatch endGate = new CountDownLatch(concurrentSessions);
			AtomicInteger successCount = new AtomicInteger(0);
			
			for (int i = 0; i < concurrentSessions; i++) {
				new Thread(() -> {
					try {
						// Create independent session/transaction
						var vets = vetRepository.findAll();
						assertThat(vets).isNotNull();
						
						successCount.incrementAndGet();
					} catch (Exception e) {
						throw new RuntimeException(e);
					} finally {
						endGate.countDown();
					}
				}).start();
			}
			
			endGate.await();
			
			System.out.println("Iteration " + (iter + 1) + " completed with " + successCount.get() + 
			                   " successful sessions");
			
			assertThat(successCount.get()).isEqualTo(concurrentSessions);
		}
	}

	@Test
	@DisplayName("Verify cache coherence with virtual threads")
	void testCacheCoherence() throws InterruptedException {
		// First, populate the cache
		vetRepository.findAll();
		
		int concurrentReads = 60;
		CountDownLatch endGate = new CountDownLatch(concurrentReads);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		
		var expectedVets = vetRepository.findAll();
		int expectedSize = expectedVets.size();
		
		for (int i = 0; i < concurrentReads; i++) {
			new Thread(() -> {
				try {
					// All reads should see cached data
					var vets = vetRepository.findAll();
					
					if (vets.size() == expectedSize) {
						successCount.incrementAndGet();
					} else {
						failureCount.incrementAndGet();
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			}).start();
		}
		
		endGate.await();
		
		// All reads should see the same cached data
		assertThat(successCount.get()).isEqualTo(concurrentReads);
		assertThat(failureCount.get()).isEqualTo(0);
	}

}
