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

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AOP Interceptor for virtualizing Spring Data JPA repository operations.
 *
 * This interceptor intercepts all method calls on Spring Data repository interfaces and
 * executes them on a virtual thread executor when the java21-virtual profile is active.
 *
 * By wrapping repository calls in virtual threads, we achieve: - Better resource
 * utilization (virtual threads are cheap to create) - Higher concurrency without
 * exhausting platform threads - Simplified error handling (compared to reactive
 * approaches) - Natural blocking semantics (no need for reactive streams)
 *
 * Repository operations virtualized: - OwnerRepository: findById,
 * findByLastNameStartingWith, save, saveAll, delete, deleteAll - VetRepository: findAll,
 * findAll(Pageable) - PetTypeRepository: findAll, findPetTypes, save, saveAll
 *
 * Execution Model: 1. Controller/Service calls repository method (e.g.,
 * owners.findById(1)) 2. AOP interceptor captures the call 3. Method is executed on
 * virtual thread executor 4. Calling thread blocks waiting for result (natural blocking
 * semantics) 5. Virtual thread handles I/O blocking without platform thread exhaustion
 *
 * Thread-Safety Guarantees: - Synchronous semantics maintained (caller blocks until
 * result available) - No race conditions or visibility issues - Transactional context is
 * preserved via ThreadLocal (HibernateSession)
 *
 * @author Wick Dynex
 */
@Component
@ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true", matchIfMissing = false)
public class VirtualThreadRepositoryInterceptor implements MethodInterceptor {

	@Autowired(required = false)
	@Qualifier("databaseVirtualThreadExecutor")
	private ExecutorService databaseVirtualThreadExecutor;

	/**
	 * Intercepts repository method invocations and executes them on a virtual thread.
	 * @param invocation the method invocation to intercept
	 * @return the result of the method invocation
	 * @throws Throwable if the intercepted method throws an exception
	 */
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// If virtual thread executor is not available, fall back to direct invocation
		if (databaseVirtualThreadExecutor == null) {
			return invocation.proceed();
		}

		Method method = invocation.getMethod();
		String methodName = method.getName();
		String className = invocation.getThis().getClass().getSimpleName();

		// Skip methods that shouldn't be virtualized (e.g., toString, equals, hashCode)
		if (isNonVirtualizableMethod(methodName)) {
			return invocation.proceed();
		}

		// Avoid double-virtualizing when already on a virtual thread
		if (Thread.currentThread().isVirtual()) {
			return invocation.proceed();
		}

		// Wrap the invocation in a Callable for virtual thread execution
		Callable<?> task = () -> {
			try {
				return invocation.proceed();
			}
			catch (Throwable throwable) {
				// Re-throw checked exceptions as RuntimeException for Callable
				// compatibility
				if (throwable instanceof RuntimeException rte) {
					throw rte;
				}
				else {
					throw new RuntimeException(throwable);
				}
			}
		};

		// Submit the task to the virtual thread executor and wait for completion
		Future<?> future = databaseVirtualThreadExecutor.submit(task);

		try {
			// Block until the virtual thread completes (maintains synchronous semantics)
			return future.get();
		}
		catch (ExecutionException ee) {
			// Unwrap the actual exception
			Throwable cause = ee.getCause();
			if (cause instanceof RuntimeException rte) {
				throw rte;
			}
			else {
				throw cause;
			}
		}
		catch (InterruptedException ie) {
			// Restore interrupt status and throw
			Thread.currentThread().interrupt();
			throw new RuntimeException("Repository operation interrupted", ie);
		}
	}

	/**
	 * Determines if a method should be virtualized.
	 *
	 * Some methods should not be virtualized: - Object methods: toString(), equals(),
	 * hashCode() - Proxy methods: getProxyTargetClass(), etc.
	 * @param methodName the method name to check
	 * @return true if the method should NOT be virtualized
	 */
	private boolean isNonVirtualizableMethod(String methodName) {
		return methodName.equals("toString") || methodName.equals("equals") || methodName.equals("hashCode")
				|| methodName.startsWith("get");
	}

}
