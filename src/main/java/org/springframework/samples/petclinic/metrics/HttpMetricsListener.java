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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom HTTP metrics listener for tracking request/response metrics. Captures request
 * count, response times, and error counts by status code.
 */
@Component
public class HttpMetricsListener implements HandlerInterceptor {

	// Metrics storage
	private final AtomicLong totalRequests = new AtomicLong(0);

	private final Map<Integer, AtomicLong> errorCountByStatus = new HashMap<>();

	private final AtomicLong totalErrors = new AtomicLong(0);

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		totalRequests.incrementAndGet();
		int status = response.getStatus();
		if (status >= 400) {
			totalErrors.incrementAndGet();
			errorCountByStatus.computeIfAbsent(status, k -> new AtomicLong(0)).incrementAndGet();
		}
	}

	/**
	 * Get total request count
	 */
	public long getTotalRequests() {
		return totalRequests.get();
	}

	/**
	 * Get total error count
	 */
	public long getTotalErrors() {
		return totalErrors.get();
	}

	/**
	 * Get error count for specific status code
	 */
	public long getErrorCount(int statusCode) {
		return errorCountByStatus.getOrDefault(statusCode, new AtomicLong(0)).get();
	}

	/**
	 * Get all error status codes and their counts
	 */
	public Map<Integer, Long> getErrorCountByStatus() {
		Map<Integer, Long> result = new HashMap<>();
		errorCountByStatus.forEach((status, count) -> result.put(status, count.get()));
		return result;
	}

	/**
	 * Reset all metrics
	 */
	public void reset() {
		totalRequests.set(0);
		totalErrors.set(0);
		errorCountByStatus.clear();
	}

}
