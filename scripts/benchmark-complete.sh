#!/bin/bash
# Spring PetClinic — Comprehensive Benchmark Script
# Metrics: startup time, load test percentiles (P50/P95/P99/P99.9),
#          throughput at 100/250/500 users, JVM heap/threads/CPU,
#          GC pause/allocation/promotion, DB pool, JFR blocking events,
#          code modernization metrics.
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/$TIMESTAMP"
mkdir -p "$PROJECT_ROOT/benchmark-results" 2>/dev/null || true
JQ="$SCRIPT_DIR/jq.exe"
PYTHON=$(which python 2>/dev/null || which python3 2>/dev/null || echo "python")
JAVA_BIN=$(dirname "$(which java 2>/dev/null || echo /usr/bin/java)")
JCMD_CMD="$JAVA_BIN/jcmd"
JFR_CMD="$JAVA_BIN/jfr"

free_port_8080() {
    powershell.exe -Command "Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id \$_.OwningProcess -Force -ErrorAction SilentlyContinue }" 2>/dev/null || true
    sleep 3
}

mkdir -p "$RESULTS_DIR"

VARIANT="${1:-java17-baseline}"
LOAD_TEST_DURATION="${2:-30}"
APP_WARMUP_WAIT=35
START_TIME=$(date +%s)

echo "Spring PetClinic Comprehensive Benchmark — $VARIANT"
echo "Java: $(java -version 2>&1 | head -1)"
echo "Time: $(date)"
echo "Results: $RESULTS_DIR"
echo ""

# ============================================================================
# Phase 1: Build
# ============================================================================
echo "=== PHASE 1: Build ==="
PHASE_START=$(date +%s)
cd "$PROJECT_ROOT"
./mvnw clean package -Dmaven.test.skip=true -Dcheckstyle.skip -q
BUILD_TIME=$(($(date +%s) - PHASE_START))
APP_JAR=$(ls target/spring-petclinic-*.jar 2>/dev/null | head -1)
echo "Build: ${BUILD_TIME}s — $APP_JAR"
echo ""

# ============================================================================
# Phase 2: Code Modernization Metrics
# ============================================================================
echo "=== PHASE 2: Code Modernization Metrics ==="
SRC="src/main/java"
RECORD_COUNT=$(grep -rl "^public record\|^record " "$SRC" --include="*.java" 2>/dev/null | wc -l)
PATTERN_MATCH_COUNT=$(grep -r "instanceof .* [a-z][a-zA-Z]" "$SRC" --include="*.java" 2>/dev/null | wc -l)
VIRTUAL_THREAD_COUNT=$(grep -r "VirtualThread\|newVirtualThread\|newCachedThreadPool" "$SRC" --include="*.java" 2>/dev/null | wc -l)
TOTAL_JAVA_FILES=$(find "$SRC" -name "*.java" 2>/dev/null | wc -l)
TOTAL_JAVA_LOC=$(find "$SRC" -name "*.java" -exec cat {} \; 2>/dev/null | wc -l)
BENCHMARK_LOC=$(find "$SRC/org/springframework/samples/petclinic/benchmark" "$SRC/org/springframework/samples/petclinic/metrics" -name "*.java" 2>/dev/null -exec cat {} \; 2>/dev/null | wc -l)
SWITCH_EXPR_COUNT=$(grep -r "yield\b" "$SRC" --include="*.java" 2>/dev/null | wc -l)
echo "Records: $RECORD_COUNT | Pattern matching: $PATTERN_MATCH_COUNT | VT refs: $VIRTUAL_THREAD_COUNT"
echo "Files: $TOTAL_JAVA_FILES | LOC: $TOTAL_JAVA_LOC | Benchmark LOC: $BENCHMARK_LOC | Yields: $SWITCH_EXPR_COUNT"
echo ""

# ============================================================================
# Phase 3: Startup Time
# ============================================================================
echo "=== PHASE 3: Startup Time ==="

measure_startup() {
    local label=$1
    local logfile=$2
    free_port_8080
    java -Xms128m -Xmx512m -jar "$APP_JAR" > "$logfile" 2>&1 &
    local pid=$!
    local started=false
    local elapsed=0
    for i in $(seq 1 60); do
        sleep 1
        elapsed=$i
        if curl -s --max-time 2 http://localhost:8080/actuator/health >/dev/null 2>&1; then
            started=true; break
        fi
        kill -0 $pid 2>/dev/null || break
    done
    kill $pid 2>/dev/null || true
    wait $pid 2>/dev/null || true
    sleep 4
    local log_val
    log_val=$(grep -o "Started.*in [0-9.]* seconds" "$logfile" 2>/dev/null | tail -1 || echo "")
    local ms
    ms=$(echo "$log_val" | grep -o "[0-9.]*" | tail -1 | awk '{printf "%.0f", $1*1000}' 2>/dev/null || echo "")
    if $started && [ -n "$ms" ]; then
        echo "  $label: ${ms}ms  (log: $log_val)"
        echo "$ms"
    elif $started; then
        echo "  $label: started in ${elapsed}s (log parse failed)"
        echo "${elapsed}000"
    else
        echo "  $label: TIMEOUT"
        echo "timeout"
    fi
}

echo "Cold start..."
COLD_STARTUP_MS=$(measure_startup "Cold" "$RESULTS_DIR/cold-start.log" | tail -1)
echo "Warm start..."
WARM_STARTUP_MS=$(measure_startup "Warm" "$RESULTS_DIR/warm-start.log" | tail -1)
echo ""

# ============================================================================
# Phase 4: JFR + Load Test + Actuator Metrics
# ============================================================================
echo "=== PHASE 4: JFR + Load Test + Actuator Metrics ==="

JFR_FILE="$RESULTS_DIR/recording.jfr"
APP_LOG="$RESULTS_DIR/app.log"

echo "Ensuring port 8080 is free..."
free_port_8080
echo "Starting app with JFR..."
java \
    -Xms256m -Xmx512m \
    "-XX:StartFlightRecording=settings=profile,maxsize=128m,dumponexit=true,filename=$JFR_FILE" \
    -jar "$APP_JAR" \
    > "$APP_LOG" 2>&1 &
APP_PID=$!

APP_STARTED=false
for i in $(seq 1 $APP_WARMUP_WAIT); do
    sleep 1
    if curl -s --max-time 2 http://localhost:8080/actuator/health >/dev/null 2>&1; then
        APP_STARTED=true; STARTUP_ELAPSED=$i; break
    fi
done

if ! $APP_STARTED; then
    echo "App failed to start. Log:"
    tail -20 "$APP_LOG"
    kill $APP_PID 2>/dev/null; exit 1
fi
echo "App running (PID=$APP_PID, ${STARTUP_ELAPSED}s to start)"

echo ""
echo "Load test: ${LOAD_TEST_DURATION}s per level..."
LOAD_RESULTS="$RESULTS_DIR/load-test-results.json"
"$PYTHON" "$SCRIPT_DIR/load-test.py" \
    "http://localhost:8080" \
    "$LOAD_RESULTS" \
    "$LOAD_TEST_DURATION" 2>&1 | tee "$RESULTS_DIR/load-test.log"

echo ""
echo "Collecting actuator snapshots (6 x 10s)..."
METRICS_FILE="$RESULTS_DIR/metrics.jsonl"
> "$METRICS_FILE"

for i in $(seq 1 6); do
    TS=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ" 2>/dev/null || date -u +"%Y-%m-%dT%H:%M:%SZ")
    TS_UNIX=$(date +%s%3N 2>/dev/null || echo "0")

    HTTP_REQ=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/http.server.requests" 2>/dev/null || echo "{}")
    JVM_MEM=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap" 2>/dev/null || echo "{}")
    JVM_THREADS=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/jvm.threads.live" 2>/dev/null || echo "{}")
    CPU=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/process.cpu.usage" 2>/dev/null || echo "{}")
    GC_PAUSE=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/jvm.gc.pause" 2>/dev/null || echo "{}")
    GC_ALLOC=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/jvm.gc.memory.allocated" 2>/dev/null || echo "{}")
    GC_PROMO=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/jvm.gc.memory.promoted" 2>/dev/null || echo "{}")
    JVM_MEM_MAX=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/jvm.memory.max?tag=area:heap" 2>/dev/null || echo "{}")
    DB_POOL=$(curl -s --max-time 5 "http://localhost:8080/actuator/metrics/hikaricp.connections.active" 2>/dev/null || echo "{}")

    REQ_COUNT=$(echo "$HTTP_REQ" | "$JQ" -r '.measurements[]? | select(.statistic=="COUNT") | .value' 2>/dev/null | head -1 || echo "null")
    MEM_USED=$(echo "$JVM_MEM" | "$JQ" -r '.measurements[0]?.value // null' 2>/dev/null || echo "null")
    MEM_MAX=$(echo "$JVM_MEM_MAX" | "$JQ" -r '.measurements[0]?.value // null' 2>/dev/null || echo "null")
    THREADS_V=$(echo "$JVM_THREADS" | "$JQ" -r '.measurements[0]?.value // null' 2>/dev/null || echo "null")
    CPU_V=$(echo "$CPU" | "$JQ" -r '.measurements[0]?.value // null' 2>/dev/null || echo "null")
    GC_PAUSE_V=$(echo "$GC_PAUSE" | "$JQ" -r '.measurements[]? | select(.statistic=="TOTAL_TIME") | .value' 2>/dev/null | head -1 || echo "null")
    GC_ALLOC_V=$(echo "$GC_ALLOC" | "$JQ" -r '.measurements[0]?.value // null' 2>/dev/null || echo "null")
    GC_PROMO_V=$(echo "$GC_PROMO" | "$JQ" -r '.measurements[0]?.value // null' 2>/dev/null || echo "null")
    DB_POOL_V=$(echo "$DB_POOL" | "$JQ" -r '.measurements[0]?.value // null' 2>/dev/null || echo "null")

    echo "{\"ts\":\"$TS\",\"ts_unix\":$TS_UNIX,\"http.requests.count\":$REQ_COUNT,\"jvm.memory.heap.used\":$MEM_USED,\"jvm.memory.heap.max\":$MEM_MAX,\"jvm.threads.live\":$THREADS_V,\"process.cpu.usage\":$CPU_V,\"jvm.gc.pause.total_s\":$GC_PAUSE_V,\"jvm.gc.memory.allocated\":$GC_ALLOC_V,\"jvm.gc.memory.promoted\":$GC_PROMO_V,\"hikaricp.connections.active\":$DB_POOL_V}" >> "$METRICS_FILE"

    MEM_DISPLAY=$(echo "$MEM_USED" | awk '{if($1!="null"&&$1!="") printf "%.0fMB", $1/1048576; else print "N/A"}')
    CPU_DISPLAY=$(echo "$CPU_V" | awk '{if($1!="null"&&$1!="") printf "%.1f%%", $1*100; else print "N/A"}')
    printf "  [%d/6] heap=%-8s threads=%-4s cpu=%s\n" "$i" "$MEM_DISPLAY" "$THREADS_V" "$CPU_DISPLAY"
    [ $i -lt 6 ] && sleep 10
done

echo ""
echo "Stopping application (dumping JFR first)..."
# Dump JFR recording before killing the app so it is finalized
if [ -x "$JCMD_CMD" ]; then
    "$JCMD_CMD" $APP_PID JFR.dump filename="$JFR_FILE" name=1 2>/dev/null || true
    sleep 2
fi
kill -SIGTERM $APP_PID 2>/dev/null || kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true
cp "$APP_LOG" "$RESULTS_DIR/app-full.log" 2>/dev/null || true
sleep 3

JFR_BLOCKING_COUNT="N/A"
JFR_SIZE=$(du -b "$JFR_FILE" 2>/dev/null | cut -f1 || echo "0")
echo "JFR file size: ${JFR_SIZE} bytes"
if [ -f "$JFR_FILE" ] && [ "${JFR_SIZE:-0}" -gt 1024 ]; then
    JFR_JSON="$RESULTS_DIR/recording.json"
    if [ -x "$JFR_CMD" ]; then
        "$JFR_CMD" print --json "$JFR_FILE" > "$JFR_JSON" 2>/dev/null || true
    fi
    if [ -f "$JFR_JSON" ] && [ -s "$JFR_JSON" ]; then
        JFR_BLOCKING_COUNT=$("$JQ" -r '[.recording.events[]? | select(.type.name == "jdk.JavaMonitorWait" or .type.name == "jdk.ThreadPark")] | length' "$JFR_JSON" 2>/dev/null || echo "N/A")
        echo "JFR parsed: $JFR_BLOCKING_COUNT blocking events"
    fi
fi
echo ""

# ============================================================================
# Phase 5: Report
# ============================================================================
echo "=== PHASE 5: Report ==="

parse_lt() {
    if [ -f "$LOAD_RESULTS" ] && [ -s "$LOAD_RESULTS" ]; then
        "$JQ" -r --argjson c "$1" ".[] | select(.concurrency==\$c) | $2" "$LOAD_RESULTS" 2>/dev/null | head -1 || echo "N/A"
    else
        echo "N/A"
    fi
}

LT_100_TPS=$(parse_lt 100 '.throughput_rps')
LT_100_P50=$(parse_lt 100 '.latency_ms.p50')
LT_100_P95=$(parse_lt 100 '.latency_ms.p95')
LT_100_P99=$(parse_lt 100 '.latency_ms.p99')
LT_100_P999=$(parse_lt 100 '.latency_ms.p99_9')
LT_100_ERR=$(parse_lt 100 '.error_rate_pct')
LT_250_TPS=$(parse_lt 250 '.throughput_rps')
LT_250_P50=$(parse_lt 250 '.latency_ms.p50')
LT_250_P95=$(parse_lt 250 '.latency_ms.p95')
LT_250_P99=$(parse_lt 250 '.latency_ms.p99')
LT_250_P999=$(parse_lt 250 '.latency_ms.p99_9')
LT_250_ERR=$(parse_lt 250 '.error_rate_pct')
LT_500_TPS=$(parse_lt 500 '.throughput_rps')
LT_500_P50=$(parse_lt 500 '.latency_ms.p50')
LT_500_P95=$(parse_lt 500 '.latency_ms.p95')
LT_500_P99=$(parse_lt 500 '.latency_ms.p99')
LT_500_P999=$(parse_lt 500 '.latency_ms.p99_9')
LT_500_ERR=$(parse_lt 500 '.error_rate_pct')

parse_m() {
    if [ -f "$METRICS_FILE" ] && [ -s "$METRICS_FILE" ]; then
        "$JQ" -rs "$1" "$METRICS_FILE" 2>/dev/null || echo "N/A"
    else
        echo "N/A"
    fi
}

HEAP_AVG=$(parse_m '[.[]["jvm.memory.heap.used"] | select(. != null)] | if length>0 then (add/length/1048576|round|tostring)+"MB" else "N/A" end')
HEAP_PEAK=$(parse_m '[.[]["jvm.memory.heap.used"] | select(. != null)] | if length>0 then (max/1048576|round|tostring)+"MB" else "N/A" end')
HEAP_MAX_CFG=$(parse_m '.[0]["jvm.memory.heap.max"] // "N/A" | if . != "N/A" then ((./1048576|round|tostring)+"MB") else "N/A" end')
THREAD_AVG=$(parse_m '[.[]["jvm.threads.live"] | select(. != null)] | if length>0 then (add/length|round) else "N/A" end')
CPU_AVG=$(parse_m '[.[]["process.cpu.usage"] | select(. != null)] | if length>0 then ((add/length*100)|round|tostring)+"%" else "N/A" end')
GC_PAUSE_TOTAL=$(parse_m '[.[]["jvm.gc.pause.total_s"] | select(. != null)] | if length>0 then (last*1000|round|tostring)+"ms" else "N/A" end')
GC_ALLOC_TOTAL=$(parse_m '[.[]["jvm.gc.memory.allocated"] | select(. != null)] | if length>0 then (last/1048576|round|tostring)+"MB" else "N/A" end')
GC_PROMO_TOTAL=$(parse_m '[.[]["jvm.gc.memory.promoted"] | select(. != null)] | if length>0 then (last/1048576|round|tostring)+"MB" else "N/A" end')
DB_POOL_PEAK=$(parse_m '[.[]["hikaricp.connections.active"] | select(. != null)] | if length>0 then max else "N/A" end')

TOTAL_DURATION=$(($(date +%s) - START_TIME))

REPORT_FILE="$RESULTS_DIR/RESULTS.txt"
{
echo "============================================================"
echo "  BENCHMARK RESULTS — $VARIANT"
echo "  Run: $(date)"
echo "  Total duration: ${TOTAL_DURATION}s (~$(( TOTAL_DURATION/60 ))m)"
echo "============================================================"
echo ""
echo "CONFIGURATION"
echo "-------------"
echo "  Java Version : $(java -version 2>&1 | head -1)"
echo "  Variant      : $VARIANT"
echo "  Load test    : ${LOAD_TEST_DURATION}s per concurrency level"
echo ""
echo "STARTUP TIME"
echo "------------"
echo "  Cold Startup : ${COLD_STARTUP_MS}ms"
echo "  Warm Startup : ${WARM_STARTUP_MS}ms"
echo ""
echo "LOAD TEST — LATENCY PERCENTILES & THROUGHPUT"
echo "--------------------------------------------"
printf "  %-6s %-10s %-9s %-9s %-9s %-9s %-6s\n" "Users" "TPS" "P50" "P95" "P99" "P99.9" "Err%"
printf "  %-6s %-10s %-9s %-9s %-9s %-9s %-6s\n" "------" "----------" "---------" "---------" "---------" "---------" "------"
printf "  %-6s %-10s %-9s %-9s %-9s %-9s %-6s\n" "100" "${LT_100_TPS}" "${LT_100_P50}ms" "${LT_100_P95}ms" "${LT_100_P99}ms" "${LT_100_P999}ms" "${LT_100_ERR}%"
printf "  %-6s %-10s %-9s %-9s %-9s %-9s %-6s\n" "250" "${LT_250_TPS}" "${LT_250_P50}ms" "${LT_250_P95}ms" "${LT_250_P99}ms" "${LT_250_P999}ms" "${LT_250_ERR}%"
printf "  %-6s %-10s %-9s %-9s %-9s %-9s %-6s\n" "500" "${LT_500_TPS}" "${LT_500_P50}ms" "${LT_500_P95}ms" "${LT_500_P99}ms" "${LT_500_P999}ms" "${LT_500_ERR}%"
echo ""
echo "JVM MEMORY (Spring Actuator snapshots)"
echo "--------------------------------------"
echo "  Heap Used Avg    : $HEAP_AVG"
echo "  Heap Used Peak   : $HEAP_PEAK"
echo "  Heap Configured  : $HEAP_MAX_CFG (-Xmx)"
echo "  Threads (avg)    : $THREAD_AVG"
echo "  CPU Usage (avg)  : $CPU_AVG"
echo ""
echo "GC METRICS (Spring Actuator — jvm.gc.*)"
echo "----------------------------------------"
echo "  GC Total Pause     : $GC_PAUSE_TOTAL (cumulative)"
echo "  Allocation Pressure: $GC_ALLOC_TOTAL allocated (cumulative)"
echo "  Promotion Rate     : $GC_PROMO_TOTAL promoted (cumulative)"
echo "  DB Pool Peak Active: $DB_POOL_PEAK connections"
echo ""
echo "JFR — RUNTIME BLOCKING EVENTS"
echo "------------------------------"
echo "  MonitorWait + ThreadPark events: $JFR_BLOCKING_COUNT"
echo "  (See recording.jfr for full event trace)"
echo ""
echo "CODE MODERNIZATION METRICS"
echo "--------------------------"
echo "  Java record files        : $RECORD_COUNT"
echo "  Pattern matching usages  : $PATTERN_MATCH_COUNT"
echo "  Virtual thread references: $VIRTUAL_THREAD_COUNT"
echo "  Switch expression yields : $SWITCH_EXPR_COUNT"
echo "  Total Java source files  : $TOTAL_JAVA_FILES"
echo "  Total Java LOC           : $TOTAL_JAVA_LOC"
echo "  Benchmark+Metrics LOC    : $BENCHMARK_LOC"
echo ""
echo "OUTPUT FILES"
echo "------------"
echo "  RESULTS.txt              — this report"
echo "  load-test-results.json   — full JSON (all 3 concurrency levels)"
echo "  metrics.jsonl            — actuator time-series (6 snapshots)"
echo "  recording.jfr            — JFR binary recording"
echo "  app.log                  — application stdout/stderr"
echo "  cold-start.log / warm-start.log — startup logs"
echo "  load-test.log            — load test output"
} | tee "$REPORT_FILE"

echo ""
echo "Results saved to: $RESULTS_DIR"
