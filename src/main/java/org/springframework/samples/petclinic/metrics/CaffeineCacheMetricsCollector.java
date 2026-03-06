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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Collects cache metrics for Caffeine caches used in the application. Tracks hit/miss
 * rates, evictions, and other cache statistics for performance analysis.
 */
@Component
public class CaffeineCacheMetricsCollector {

	private static final Logger logger = LoggerFactory.getLogger(CaffeineCacheMetricsCollector.class);

	// Cache metrics storage by cache name
	private final Map<String, CacheMetrics> cacheMetricsMap = new HashMap<>();

	/**
	 * Record a cache hit
	 */
	public void recordHit(String cacheName) {
		CacheMetrics metrics = cacheMetricsMap.computeIfAbsent(cacheName, k -> new CacheMetrics(cacheName));
		metrics.hits.incrementAndGet();
		metrics.accesses.incrementAndGet();
	}

	/**
	 * Record a cache miss
	 */
	public void recordMiss(String cacheName) {
		CacheMetrics metrics = cacheMetricsMap.computeIfAbsent(cacheName, k -> new CacheMetrics(cacheName));
		metrics.misses.incrementAndGet();
		metrics.accesses.incrementAndGet();
	}

	/**
	 * Record a cache eviction
	 */
	public void recordEviction(String cacheName) {
		CacheMetrics metrics = cacheMetricsMap.computeIfAbsent(cacheName, k -> new CacheMetrics(cacheName));
		metrics.evictions.incrementAndGet();
	}

	/**
	 * Record a cache removal
	 */
	public void recordRemoval(String cacheName) {
		CacheMetrics metrics = cacheMetricsMap.computeIfAbsent(cacheName, k -> new CacheMetrics(cacheName));
		metrics.removals.incrementAndGet();
	}

	/**
	 * Record cache size
	 */
	public void recordSize(String cacheName, long size) {
		CacheMetrics metrics = cacheMetricsMap.computeIfAbsent(cacheName, k -> new CacheMetrics(cacheName));
		metrics.size.set(size);
	}

	/**
	 * Get cache metrics for specific cache
	 */
	public CacheMetrics getMetrics(String cacheName) {
		return cacheMetricsMap.get(cacheName);
	}

	/**
	 * Get all cache metrics
	 */
	public Map<String, CacheMetrics> getAllMetrics() {
		return new HashMap<>(cacheMetricsMap);
	}

	/**
	 * Reset metrics for cache
	 */
	public void resetMetrics(String cacheName) {
		cacheMetricsMap.remove(cacheName);
	}

	/**
	 * Reset all metrics
	 */
	public void resetAll() {
		cacheMetricsMap.clear();
	}

	/**
	 * Cache metrics container
	 */
	public static class CacheMetrics {

		private final String cacheName;

		private final AtomicLong hits = new AtomicLong(0);

		private final AtomicLong misses = new AtomicLong(0);

		private final AtomicLong accesses = new AtomicLong(0);

		private final AtomicLong evictions = new AtomicLong(0);

		private final AtomicLong removals = new AtomicLong(0);

		private final AtomicLong size = new AtomicLong(0);

		public CacheMetrics(String cacheName) {
			this.cacheName = cacheName;
		}

		public String getCacheName() {
			return cacheName;
		}

		public long getHits() {
			return hits.get();
		}

		public long getMisses() {
			return misses.get();
		}

		public long getAccesses() {
			return accesses.get();
		}

		public double getHitRate() {
			long total = accesses.get();
			if (total == 0) {
				return 0.0;
			}
			return (double) hits.get() / total * 100.0;
		}

		public double getMissRate() {
			return 100.0 - getHitRate();
		}

		public long getEvictions() {
			return evictions.get();
		}

		public long getRemovals() {
			return removals.get();
		}

		public long getSize() {
			return size.get();
		}

		/**
		 * Get metrics as a map
		 */
		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("cache_name", cacheName);
			map.put("hits", getHits());
			map.put("misses", getMisses());
			map.put("accesses", getAccesses());
			map.put("hit_rate_percent", getHitRate());
			map.put("miss_rate_percent", getMissRate());
			map.put("evictions", getEvictions());
			map.put("removals", getRemovals());
			map.put("size", getSize());
			return map;
		}

	}

}
