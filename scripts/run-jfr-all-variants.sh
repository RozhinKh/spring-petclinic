#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/jfr/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - JFR Recording Suite ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"

# Function to run with JFR
run_variant_jfr() {
    local JAVA_HOME=$1
    local VARIANT_NAME=$2
    
    export JAVA_HOME=$JAVA_HOME
    echo ""
    echo "--- Running application with JFR on $VARIANT_NAME ---"
    echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
    
    cd "$PROJECT_ROOT"
    
    # Build
    ./mvnw clean package -DskipTests -q
    
    # Start with JFR recording
    timeout 600 \
    java \
        -XX:StartFlightRecording=settings=profile,\
duration=600s,\
filename=$RESULTS_DIR/jfr-$VARIANT_NAME.jfr \
        -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar &
    
    APP_PID=$!
    
    # Wait for app startup
    sleep 5
    
    # Optional: Run load test during recording if script exists
    if [ -f "$SCRIPT_DIR/run-load-test.sh" ]; then
        $SCRIPT_DIR/run-load-test.sh || true
    fi
    
    # Wait for recording to complete
    wait $APP_PID 2>/dev/null || true
    
    # Convert to JSON for easier analysis
    if command -v jfr &> /dev/null; then
        jfr print --json "$RESULTS_DIR/jfr-$VARIANT_NAME.jfr" \
            > "$RESULTS_DIR/jfr-$VARIANT_NAME.json" 2>/dev/null || true
    fi
    
    echo "✓ JFR recording for $VARIANT_NAME completed"
}

# Run for each variant
if [ -n "$JAVA_17_HOME" ]; then
    run_variant_jfr "$JAVA_17_HOME" "java17-baseline"
fi

if [ -n "$JAVA_21_HOME" ]; then
    run_variant_jfr "$JAVA_21_HOME" "java21-variant-a"
fi

echo ""
echo "=== All JFR recordings completed ==="
echo "Results saved to: $RESULTS_DIR"
