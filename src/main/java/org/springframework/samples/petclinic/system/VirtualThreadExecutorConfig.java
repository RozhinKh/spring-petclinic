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
package org.springframework.samples.petclinic.system;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for virtual thread executor services (Java 21+).
 * 
 * This configuration creates executor services that use virtual threads
 * for I/O-bound operations, enabling better resource utilization
 * and higher concurrency without the overhead of platform threads.
 * 
 * Virtual threads are lightweight threads managed by the Java virtual machine.
 * They excel at I/O-bound workloads such as:
 * - Database queries and connection pooling
 * - HTTP client requests
 * - File I/O operations
 * - Message queue operations
 * 
 * Key Benefits:
 * - 1000s of virtual threads can be created cheaply
 * - No need for complex async/await patterns
 * - Simplified error handling compared to CompletableFuture
 * - Better throughput for I/O-bound applications
 * 
 * Virtualization Points in PetClinic (21 total):
 * 1. OwnerRepository.findById() - Line 67 (OwnerController)
 * 2. OwnerRepository.findById() - Line 82 (PetController)
 * 3. OwnerRepository.findById() - Line 68 (PetController)
 * 4. OwnerRepository.findByLastNameStartingWith() - Line 134
 * 5. OwnerRepository.save() - Line 84 (OwnerController)
 * 6. OwnerRepository.save() - Line 158 (OwnerController)
 * 7. OwnerRepository.save() - Line 130 (PetController)
 * 8. OwnerRepository.save() - Line 197 (PetController)
 * 9. OwnerRepository.save() - Line 105 (VisitController)
 * 10. PetTypeRepository.findPetTypes() - Line 63 (PetController)
 * 11. VetRepository.findAll() - Line 78 (VetController)
 * 12. VetRepository.findAll(Pageable) - Line 70 (VetController)
 * 
 * @author Wick Dynex
 */
@Configuration
@EnableAsync
@ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true", matchIfMissing = false)
public class VirtualThreadExecutorConfig {

	/**
	 * Creates a virtual thread per-task executor for I/O-bound operations.
	 * 
	 * This executor uses Executors.newVirtualThreadPerTaskExecutor() which:
	 * - Creates a new virtual thread for each submitted task
	 * - Automatically manages thread lifecycle
	 * - Ideal for I/O-bound operations (database queries, HTTP requests)
	 * - Not ideal for CPU-bound operations
	 * 
	 * Used for:
	 * - All Spring Data JPA repository calls (via AOP interceptor if enabled)
	 * - Optional @Async method invocations
	 * 
	 * @return ExecutorService backed by virtual threads
	 */
	@Bean(name = "virtualThreadExecutor")
	public ExecutorService virtualThreadExecutor() {
		// Create a thread factory that names virtual threads for monitoring
		ThreadFactory factory = Thread.ofVirtual()
			.name("virtual-io-", 0)
			.factory();
		
		// Create an executor that uses the virtual thread factory
		// Note: newVirtualThreadPerTaskExecutor() is the recommended approach
		// It automatically creates a new virtual thread for each task
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		
		return executor;
	}

	/**
	 * Creates a virtual thread executor specifically for database operations.
	 * 
	 * This executor is tuned for JPA/Hibernate operations which typically:
	 * - Have bounded concurrency (limited by connection pool)
	 * - Are I/O-bound (network latency to database)
	 * - Benefit from lightweight thread management
	 * 
	 * Used for:
	 * - JPA repository method interception (if AOP is enabled)
	 * - Database query execution
	 * - Entity persistence operations
	 * 
	 * @return ExecutorService optimized for database I/O
	 */
	@Bean(name = "databaseVirtualThreadExecutor")
	public ExecutorService databaseVirtualThreadExecutor() {
		// Virtual threads per task for database operations
		// The number of concurrent tasks is naturally bounded by HikariCP pool size
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	/**
	 * Creates a virtual thread executor for general HTTP/network operations.
	 * 
	 * This executor is designed for:
	 * - HTTP client requests
	 * - REST API calls
	 * - Network I/O operations
	 * - Any blocking network-related work
	 * 
	 * Virtual threads allow high concurrency for network-bound operations
	 * without the complexity of reactive frameworks.
	 * 
	 * @return ExecutorService optimized for HTTP/network I/O
	 */
	@Bean(name = "httpVirtualThreadExecutor")
	public ExecutorService httpVirtualThreadExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

}
