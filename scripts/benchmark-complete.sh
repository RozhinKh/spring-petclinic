#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

# Configuration
export JAVA_HOME=${JAVA_HOME:-$(java -XshowSettings:properties -version 2>&1 | grep 'java.home' | awk '{print $3}' 2>/dev/null || echo "")}
VARIANT="${1:-java17-baseline}"

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  Spring PetClinic - Complete Benchmark Suite                       ║"
echo "║  Variant: $VARIANT"
echo "║  Java: $(java -version 2>&1 | head -1)"
echo "║  Timestamp: $(date)"
echo "║  Results: $RESULTS_DIR"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""

# Timing tracking
START_TIME=$(date +%s)
PHASE_START=$START_TIME

# ============================================================================
# Phase 1: Preparation
# ============================================================================
echo "┌─ PHASE 1: Preparation ─────────────────────────────────────────────┐"
PHASE_START=$(date +%s)

echo "▶ Building application..."
cd "$PROJECT_ROOT"
./mvnw clean package -DskipTests -q
BUILD_TIME=$(($(date +%s) - PHASE_START))
echo "✓ Build completed ($BUILD_TIME seconds)"

echo "▶ Verifying tools..."
java -version 2>&1 | head -1
command -v jmeter >/dev/null 2>&1 && echo "✓ JMeter available" || echo "⚠ JMeter not available"
command -v jcmd >/dev/null 2>&1 && echo "✓ jcmd available" || echo "⚠ jcmd not available"
command -v jq >/dev/null 2>&1 && echo "✓ jq available" || echo "⚠ jq not available"
echo ""

PHASE_TIME=$(($(date +%s) - PHASE_START))
echo "└─ Phase 1 completed in $PHASE_TIME seconds"
echo ""

# ============================================================================
# Phase 2: Unit & Integration Tests
# ============================================================================
echo "┌─ PHASE 2: Test Suite Execution ───────────────────────────────────┐"
PHASE_START=$(date +%s)

echo "▶ Running unit and integration tests..."
./mvnw verify -DskipITs=false -q 2>/dev/null || true
echo "✓ Tests completed"

# Copy test results
cp -r target/surefire-reports "$RESULTS_DIR/surefire-reports" 2>/dev/null || true
cp -r target/site/jacoco "$RESULTS_DIR/jacoco-report" 2>/dev/null || true

TEST_COUNT=$(find "$RESULTS_DIR/surefire-reports" -name "TEST-*.xml" 2>/dev/null | wc -l)
echo "  Test reports: $TEST_COUNT"

PHASE_TIME=$(($(date +%s) - PHASE_START))
echo "└─ Phase 2 completed in $PHASE_TIME seconds"
echo ""

# ============================================================================
# Phase 3: Application with JFR & Load Testing
# ============================================================================
echo "┌─ PHASE 3: JFR Recording with Load Testing ────────────────────────┐"
PHASE_START=$(date +%s)

echo "▶ Starting application with JFR recording..."
java \
    -XX:StartFlightRecording=settings=profile,duration=600s,filename=$RESULTS_DIR/recording.jfr \
    -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
    > "$RESULTS_DIR/app.log" 2>&1 &
APP_PID=$!

sleep 10

# Check if app started
if ! curl -s --max-time 2 http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "✗ Application failed to start"
    cat "$RESULTS_DIR/app.log" | head -20
    kill $APP_PID 2>/dev/null || true
    exit 1
fi
echo "✓ Application started (PID: $APP_PID)"

echo "▶ Starting metrics collection..."
"$SCRIPT_DIR/collect-actuator-metrics.sh" localhost 8080 \
    "$RESULTS_DIR/metrics.jsonl" 5 120 &
METRICS_PID=$!
sleep 2
echo "✓ Metrics collection started"

echo "▶ Running load test..."
if command -v jmeter >/dev/null 2>&1; then
    "$SCRIPT_DIR/run-load-test.sh" localhost 8080 100 10 10 > "$RESULTS_DIR/load-test.log" 2>&1 || true
    echo "✓ Load test completed"
else
    echo "⚠ JMeter not available, skipping load test"
fi

echo "▶ Collecting application logs..."
cp "$RESULTS_DIR/app.log" "$RESULTS_DIR/app-full.log"

echo "▶ Waiting for metrics and JFR..."
wait $METRICS_PID 2>/dev/null || true
sleep 5
kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true
echo "✓ Application stopped"

echo "▶ Converting JFR to JSON..."
if [ -f "$RESULTS_DIR/recording.jfr" ] && command -v jfr >/dev/null 2>&1; then
    jfr print --json "$RESULTS_DIR/recording.jfr" \
        > "$RESULTS_DIR/recording.json" 2>/dev/null || true
    echo "✓ JFR conversion completed"
fi

PHASE_TIME=$(($(date +%s) - PHASE_START))
echo "└─ Phase 3 completed in $PHASE_TIME seconds"
echo ""

# ============================================================================
# Phase 4: Analysis & Reporting
# ============================================================================
echo "┌─ PHASE 4: Analysis & Reporting ───────────────────────────────────┐"
PHASE_START=$(date +%s)

echo "▶ Parsing results..."

TOTAL_REQUESTS=0
ERRORS=0
ERROR_RATE=0
AVG_TIME=0
SAMPLE_COUNT=0
AVG_MEMORY="N/A"

if [ -f "$RESULTS_DIR/jmeter-results.jtl" ] && command -v awk >/dev/null 2>&1; then
    TOTAL_REQUESTS=$(tail -n +2 "$RESULTS_DIR/jmeter-results.jtl" 2>/dev/null | wc -l)
    if [ $TOTAL_REQUESTS -gt 0 ]; then
        ERRORS=$(awk -F',' '$4 >= 400 {count++} END {print count+0}' "$RESULTS_DIR/jmeter-results.jtl")
        ERROR_RATE=$((ERRORS * 100 / TOTAL_REQUESTS))
        AVG_TIME=$(awk -F',' 'NR>1 {sum+=$2; count++} END {if(count>0) printf "%.0f", sum/count; else print "0"}' "$RESULTS_DIR/jmeter-results.jtl")
    fi
fi

if [ -f "$RESULTS_DIR/metrics.jsonl" ] && command -v jq >/dev/null 2>&1; then
    SAMPLE_COUNT=$(wc -l < "$RESULTS_DIR/metrics.jsonl")
    AVG_MEMORY=$(jq -s 'map(.jvm.memory.heap.used_bytes | select(. != null)) | if length > 0 then (add / length / 1048576 | round) else "N/A" end' \
        "$RESULTS_DIR/metrics.jsonl" 2>/dev/null || echo "N/A")
fi

# Create summary report
cat > "$RESULTS_DIR/RESULTS.txt" <<EOF
╔════════════════════════════════════════════════════════════════════╗
║              BENCHMARK RESULTS - $VARIANT
║              Timestamp: $(date)
║              Duration: $(($(date +%s) - START_TIME)) seconds
╚════════════════════════════════════════════════════════════════════╝

BENCHMARK CONFIGURATION
=======================
Java Version: $(java -version 2>&1 | head -1)
Variant: $VARIANT

LOAD TEST METRICS
=================
Total Requests: ${TOTAL_REQUESTS:-N/A}
Errors: ${ERRORS:-N/A} (${ERROR_RATE:-N/A}%)
Avg Response Time: ${AVG_TIME:-N/A}ms

JVM METRICS (from Actuator)
===========================
Avg Memory Usage: ${AVG_MEMORY}MB
Samples Collected: ${SAMPLE_COUNT}

OUTPUT FILES
============
- metrics.jsonl: Actuator metrics timeseries
- recording.jfr: Java Flight Recorder output
- recording.json: JFR converted to JSON
- jmeter-results.jtl: Load test results (CSV)
- surefire-reports/: Unit test results
- jacoco-report/: Code coverage report
- app.log: Application startup log

END-TO-END EXECUTION TIME
==========================
Total: $(($(date +%s) - START_TIME)) seconds (~$(( ($(date +%s) - START_TIME) / 60 )) minutes)
EOF

cat "$RESULTS_DIR/RESULTS.txt"

PHASE_TIME=$(($(date +%s) - PHASE_START))
echo "└─ Phase 4 completed in $PHASE_TIME seconds"
echo ""

# ============================================================================
# Summary
# ============================================================================
TOTAL_TIME=$(($(date +%s) - START_TIME))

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  BENCHMARK COMPLETE                                                ║"
echo "║  Total Time: $TOTAL_TIME seconds (~$(( TOTAL_TIME / 60 )) minutes)"
echo "║  Results: $RESULTS_DIR"
echo "╚════════════════════════════════════════════════════════════════════╝"
