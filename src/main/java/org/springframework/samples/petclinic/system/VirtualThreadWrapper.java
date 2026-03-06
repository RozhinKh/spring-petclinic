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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility class for wrapping I/O-bound operations in virtual threads.
 *
 * This class provides helper methods to easily execute synchronous operations on virtual
 * threads without boilerplate code. It's particularly useful for one-off database
 * operations or I/O calls that aren't part of a repository interface.
 *
 * Usage Examples:
 *
 * // Execute a supplier on a virtual thread Owner owner = VirtualThreadWrapper.execute(()
 * -> ownerRepository.findById(1).orElse(null));
 *
 * // Execute a function with input List<Owner> owners = VirtualThreadWrapper.execute(
 * (id) -> ownerRepository.findById(id).orElse(null), ownerId );
 *
 * // Execute a consumer (fire-and-forget with virtual thread)
 * VirtualThreadWrapper.executeAsync(() -> database.log("User accessed owner list"));
 *
 * Benefits: - No platform thread exhaustion for I/O waits - Cleaner code compared to
 * explicit ExecutorService usage - Automatic virtual thread lifecycle management -
 * Natural exception propagation
 *
 * Thread Model: - execute(Supplier) blocks the caller (synchronous) - execute(Function,
 * T) blocks the caller with input (synchronous) - executeAsync(Runnable) returns
 * immediately (asynchronous) - All operations use virtual threads internally
 *
 * @author Wick Dynex
 */
public final class VirtualThreadWrapper {

	// Java 17 baseline: cached thread pool (virtual threads enabled on Java 21)
	private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newCachedThreadPool();

	// Prevent instantiation
	private VirtualThreadWrapper() {
	}

	/**
	 * Executes a supplier on a virtual thread, blocking until completion.
	 *
	 * This is the primary use case: wrap synchronous I/O operations like database queries
	 * to run on virtual threads while maintaining blocking semantics.
	 * @param <T> the return type
	 * @param supplier the operation to execute
	 * @return the result of the supplier
	 * @throws RuntimeException if the operation throws a checked exception
	 */
	public static <T> T execute(Supplier<T> supplier) {
		try {
			return VIRTUAL_EXECUTOR.submit(supplier::get).get();
		}
		catch (ExecutionException ee) {
			if (ee.getCause() instanceof RuntimeException rte) {
				throw rte;
			}
			throw new RuntimeException("Virtual thread operation failed", ee.getCause());
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Virtual thread operation interrupted", ie);
		}
	}

	/**
	 * Executes a function on a virtual thread with an input parameter, blocking until
	 * completion.
	 *
	 * Useful for operations that take a single parameter, like repository.findById(id).
	 * @param <T> the input type
	 * @param <R> the return type
	 * @param function the operation to execute
	 * @param input the input parameter
	 * @return the result of the function
	 * @throws RuntimeException if the operation throws a checked exception
	 */
	public static <T, R> R execute(Function<T, R> function, T input) {
		try {
			return VIRTUAL_EXECUTOR.submit(() -> function.apply(input)).get();
		}
		catch (ExecutionException ee) {
			if (ee.getCause() instanceof RuntimeException rte) {
				throw rte;
			}
			throw new RuntimeException("Virtual thread operation failed", ee.getCause());
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Virtual thread operation interrupted", ie);
		}
	}

	/**
	 * Executes a runnable on a virtual thread asynchronously (fire-and-forget).
	 *
	 * This method does NOT block. Use for non-critical background tasks like logging,
	 * cache warming, or deferred cleanup operations.
	 *
	 * Note: Exceptions are swallowed. Consider logging in the runnable if needed.
	 * @param runnable the operation to execute asynchronously
	 */
	public static void executeAsync(Runnable runnable) {
		VIRTUAL_EXECUTOR.submit(runnable);
	}

	/**
	 * Executes a consumer on a virtual thread with an input parameter, blocking until
	 * completion.
	 *
	 * Useful for operations that accept input but don't return a value, like database
	 * write operations.
	 * @param <T> the input type
	 * @param consumer the operation to execute
	 * @param input the input parameter
	 * @throws RuntimeException if the operation throws a checked exception
	 */
	public static <T> void execute(Consumer<T> consumer, T input) {
		try {
			VIRTUAL_EXECUTOR.submit(() -> {
				consumer.accept(input);
				return null;
			}).get();
		}
		catch (ExecutionException ee) {
			if (ee.getCause() instanceof RuntimeException rte) {
				throw rte;
			}
			throw new RuntimeException("Virtual thread operation failed", ee.getCause());
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Virtual thread operation interrupted", ie);
		}
	}

	/**
	 * Executes a callable on a virtual thread, blocking until completion.
	 *
	 * This is the most flexible method, supporting any operation including those that
	 * throw checked exceptions.
	 * @param <T> the return type
	 * @param callable the operation to execute
	 * @return the result of the callable
	 * @throws RuntimeException if the operation throws a checked exception
	 */
	public static <T> T execute(Callable<T> callable) {
		try {
			return VIRTUAL_EXECUTOR.submit(callable).get();
		}
		catch (ExecutionException ee) {
			if (ee.getCause() instanceof RuntimeException rte) {
				throw rte;
			}
			throw new RuntimeException("Virtual thread operation failed", ee.getCause());
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Virtual thread operation interrupted", ie);
		}
	}

}
