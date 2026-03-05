#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/tests/$TIMESTAMP"

mkdir -p "$RESULTS_DIR"

echo "=== Spring PetClinic - Test Suite ==="
echo "Timestamp: $(date)"
echo "Results Directory: $RESULTS_DIR"

# Function to run tests
run_variant_tests() {
    local JAVA_HOME=$1
    local VARIANT_NAME=$2
    local PROFILE=$3
    
    export JAVA_HOME=$JAVA_HOME
    echo ""
    echo "--- Running tests on $VARIANT_NAME ---"
    echo "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
    
    cd "$PROJECT_ROOT"
    
    # Run tests
    if [ -n "$PROFILE" ]; then
        ./mvnw clean verify -P $PROFILE \
            -DskipITs=false \
            -Djacoco.destFile="$RESULTS_DIR/jacoco-$VARIANT_NAME.exec" 2>&1 | tee "$RESULTS_DIR/maven-$VARIANT_NAME.log"
    else
        ./mvnw clean verify \
            -DskipITs=false \
            -Djacoco.destFile="$RESULTS_DIR/jacoco-$VARIANT_NAME.exec" 2>&1 | tee "$RESULTS_DIR/maven-$VARIANT_NAME.log"
    fi
    
    # Copy test results
    cp -r target/surefire-reports \
        "$RESULTS_DIR/surefire-reports-$VARIANT_NAME" 2>/dev/null || true
    cp -r target/site/jacoco \
        "$RESULTS_DIR/jacoco-report-$VARIANT_NAME" 2>/dev/null || true
    
    echo "✓ Tests for $VARIANT_NAME completed"
}

# Run for each variant
if [ -n "$JAVA_17_HOME" ]; then
    run_variant_tests "$JAVA_17_HOME" "java17-baseline"
fi

if [ -n "$JAVA_21_HOME" ]; then
    run_variant_tests "$JAVA_21_HOME" "java21-variant-a"
    run_variant_tests "$JAVA_21_HOME" "java21-variant-b" "vthreads"
fi

echo ""
echo "=== All tests completed ==="
echo "Results saved to: $RESULTS_DIR"
