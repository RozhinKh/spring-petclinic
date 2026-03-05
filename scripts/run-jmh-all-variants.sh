#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/jmh/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - JMH Benchmark Suite ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"

# Function to run benchmarks for a variant
run_variant_jmh() {
    local JAVA_HOME=$1
    local VARIANT_NAME=$2
    local PROFILE=$3
    
    export JAVA_HOME=$JAVA_HOME
    echo ""
    echo "--- Running JMH benchmarks on $VARIANT_NAME ---"
    echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
    
    cd "$PROJECT_ROOT"
    
    # Build
    if [ -n "$PROFILE" ]; then
        ./mvnw clean package -P $PROFILE -DskipTests
    else
        ./mvnw clean package -DskipTests
    fi
    
    # Run JMH (if benchmarks exist)
    if [ -f target/benchmarks.jar ]; then
        java -jar target/benchmarks.jar \
            -f 2 \
            -wi 5 \
            -i 10 \
            -r 2s \
            -rf json \
            -rff "$RESULTS_DIR/jmh-$VARIANT_NAME.json"
        echo "✓ JMH benchmarks for $VARIANT_NAME completed"
    else
        echo "⚠ JMH benchmarks not configured (target/benchmarks.jar not found)"
    fi
}

# Run for each variant
if [ -n "$JAVA_17_HOME" ]; then
    run_variant_jmh "$JAVA_17_HOME" "java17-baseline"
else
    echo "Warning: JAVA_17_HOME not set, skipping Java 17 benchmarks"
fi

if [ -n "$JAVA_21_HOME" ]; then
    run_variant_jmh "$JAVA_21_HOME" "java21-variant-a"
    run_variant_jmh "$JAVA_21_HOME" "java21-variant-b" "vthreads"
else
    echo "Warning: JAVA_21_HOME not set, skipping Java 21 benchmarks"
fi

echo ""
echo "=== All JMH benchmarks completed ==="
echo "Results saved to: $RESULTS_DIR"
