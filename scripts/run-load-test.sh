#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/load-tests/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - Load Testing Suite ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"

# Configuration
HOST=${1:-localhost}
PORT=${2:-8080}
THREADS=${3:-100}
RAMPUP=${4:-10}
ITERATIONS=${5:-10}

echo "Configuration:"
echo "  Host: $HOST"
echo "  Port: $PORT"
echo "  Threads: $THREADS"
echo "  Ramp-up: ${RAMPUP}s"
echo "  Iterations: $ITERATIONS"
echo ""

# Check if application is running
echo "Checking if application is running on $HOST:$PORT..."
if ! command -v nc &> /dev/null; then
    # Fallback: try curl
    if curl -s --max-time 2 http://$HOST:$PORT/actuator/health > /dev/null 2>&1; then
        echo "✓ Application is running"
    else
        echo "Error: Application not running on $HOST:$PORT"
        exit 1
    fi
else
    if nc -z $HOST $PORT 2>/dev/null; then
        echo "✓ Application is running"
    else
        echo "Error: Application not running on $HOST:$PORT"
        exit 1
    fi
fi
echo ""

# Check if JMeter is available
if ! command -v jmeter &> /dev/null; then
    echo "Warning: JMeter not found in PATH"
    echo "Please install JMeter or add it to PATH"
    exit 1
fi

# Run JMeter test
echo "--- Starting JMeter Load Test ---"
jmeter -n \
    -t "$PROJECT_ROOT/src/test/jmeter/petclinic_test_plan.jmx" \
    -l "$RESULTS_DIR/jmeter-results.jtl" \
    -j "$RESULTS_DIR/jmeter.log" \
    -Jpetclinic.host=$HOST \
    -Jpetclinic.port=$PORT \
    -Jpetclinic.threads=$THREADS \
    -Jpetclinic.rampup=$RAMPUP \
    -Jpetclinic.iterations=$ITERATIONS

echo "✓ JMeter load test completed"
echo ""

# Parse results if awk is available
if command -v awk &> /dev/null; then
    echo "--- JMeter Results Summary ---"
    TOTAL=$(tail -n +2 "$RESULTS_DIR/jmeter-results.jtl" 2>/dev/null | wc -l)
    ERRORS=$(awk -F',' '$4 >= 400 {count++} END {print count+0}' "$RESULTS_DIR/jmeter-results.jtl" 2>/dev/null || echo "0")
    
    if [ $TOTAL -gt 0 ]; then
        RATE=$((ERRORS * 100 / TOTAL))
        AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {if(count>0) printf "%.0f", sum/count; else print "N/A"}' "$RESULTS_DIR/jmeter-results.jtl")
        
        echo "Total Requests: $TOTAL"
        echo "Errors: $ERRORS ($RATE%)"
        echo "Avg Response Time: ${AVG}ms"
    fi
fi

echo ""
echo "=== Load testing completed ==="
echo "Results saved to: $RESULTS_DIR"
