#!/usr/bin/env python3
"""
HTTP Load Tester for Spring PetClinic benchmark.
Produces latency percentiles (P50/P95/P99/P99.9) and throughput at
multiple concurrency levels (100 / 250 / 500 users).
"""

import concurrent.futures
import urllib.request
import urllib.error
import time
import json
import sys
import threading
from datetime import datetime


ENDPOINTS = [
    "/owners?page=1",
    "/owners/1",
    "/owners/2",
    "/vets.html?page=1",
    "/owners/find",
]


def make_request(base_url, endpoint, timeout=15):
    url = base_url + endpoint
    start = time.perf_counter()
    try:
        req = urllib.request.Request(url, headers={"Accept": "text/html"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            resp.read()
            latency_ms = (time.perf_counter() - start) * 1000
            return {"ok": True, "status": resp.status, "latency_ms": latency_ms}
    except urllib.error.HTTPError as e:
        latency_ms = (time.perf_counter() - start) * 1000
        return {"ok": e.code < 500, "status": e.code, "latency_ms": latency_ms}
    except Exception as e:
        latency_ms = (time.perf_counter() - start) * 1000
        return {"ok": False, "status": 0, "latency_ms": latency_ms, "error": str(e)[:80]}


def percentile(sorted_data, pct):
    if not sorted_data:
        return 0.0
    idx = int(len(sorted_data) * pct / 100)
    idx = min(idx, len(sorted_data) - 1)
    return sorted_data[idx]


def run_load_test(base_url, concurrency, duration_seconds):
    """Run sustained load for `duration_seconds` at `concurrency` threads."""
    print(f"  [{concurrency} users / {duration_seconds}s] warming up...", flush=True)

    results = []
    stop_event = threading.Event()
    lock = threading.Lock()

    def worker(worker_id):
        local = []
        i = 0
        while not stop_event.is_set():
            ep = ENDPOINTS[i % len(ENDPOINTS)]
            r = make_request(base_url, ep)
            local.append(r)
            i += 1
        with lock:
            results.extend(local)

    # Warm-up: 5 seconds before timing starts
    warmup_stop = threading.Event()

    def warmup_worker():
        while not warmup_stop.is_set():
            make_request(base_url, ENDPOINTS[0])

    warmup_threads = [threading.Thread(target=warmup_worker, daemon=True) for _ in range(min(10, concurrency))]
    for t in warmup_threads:
        t.start()
    time.sleep(5)
    warmup_stop.set()
    for t in warmup_threads:
        t.join(timeout=2)

    print(f"  [{concurrency} users / {duration_seconds}s] running...", flush=True)

    # Actual load test
    threads = [threading.Thread(target=worker, args=(i,), daemon=True) for i in range(concurrency)]
    t_start = time.time()
    for t in threads:
        t.start()

    time.sleep(duration_seconds)
    stop_event.set()

    for t in threads:
        t.join(timeout=10)

    elapsed = time.time() - t_start

    if not results:
        return None

    latencies = sorted(r["latency_ms"] for r in results)
    successes = sum(1 for r in results if r["ok"])
    errors = len(results) - successes
    error_rate = errors / len(results) * 100 if results else 0
    throughput_rps = len(results) / elapsed

    report = {
        "concurrency": concurrency,
        "duration_seconds": round(elapsed, 2),
        "total_requests": len(results),
        "successful_requests": successes,
        "error_count": errors,
        "error_rate_pct": round(error_rate, 3),
        "throughput_rps": round(throughput_rps, 2),
        "latency_ms": {
            "min": round(latencies[0], 2),
            "avg": round(sum(latencies) / len(latencies), 2),
            "p50": round(percentile(latencies, 50), 2),
            "p95": round(percentile(latencies, 95), 2),
            "p99": round(percentile(latencies, 99), 2),
            "p99_9": round(percentile(latencies, 99.9), 2),
            "max": round(latencies[-1], 2),
        },
    }

    print(
        f"  [{concurrency} users] done: {len(results)} reqs, "
        f"avg={report['latency_ms']['avg']:.1f}ms "
        f"p50={report['latency_ms']['p50']:.1f}ms "
        f"p95={report['latency_ms']['p95']:.1f}ms "
        f"p99={report['latency_ms']['p99']:.1f}ms "
        f"p99.9={report['latency_ms']['p99_9']:.1f}ms "
        f"tps={report['throughput_rps']:.1f} "
        f"err={report['error_rate_pct']:.2f}%",
        flush=True,
    )
    return report


def main():
    base_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    output_file = sys.argv[2] if len(sys.argv) > 2 else "load-test-results.json"
    duration = int(sys.argv[3]) if len(sys.argv) > 3 else 30

    print(f"=== Python Load Tester ===")
    print(f"Target:    {base_url}")
    print(f"Duration:  {duration}s per concurrency level")
    print(f"Output:    {output_file}")
    print(f"Levels:    100 / 250 / 500 concurrent users")
    print()

    all_results = []

    for concurrency in [100, 250, 500]:
        result = run_load_test(base_url, concurrency, duration)
        if result:
            all_results.append(result)
        time.sleep(5)  # cool-down between levels

    with open(output_file, "w") as f:
        json.dump(all_results, f, indent=2)

    print(f"\nResults saved to: {output_file}")

    # Print concise table
    print()
    print(f"{'Users':>6} {'TPS':>8} {'Avg':>8} {'P50':>8} {'P95':>8} {'P99':>8} {'P99.9':>8} {'Err%':>6}")
    print("-" * 65)
    for r in all_results:
        lm = r["latency_ms"]
        print(
            f"{r['concurrency']:>6} "
            f"{r['throughput_rps']:>8.1f} "
            f"{lm['avg']:>7.1f}ms "
            f"{lm['p50']:>7.1f}ms "
            f"{lm['p95']:>7.1f}ms "
            f"{lm['p99']:>7.1f}ms "
            f"{lm['p99_9']:>7.1f}ms "
            f"{r['error_rate_pct']:>5.2f}%"
        )

    return all_results


if __name__ == "__main__":
    main()
