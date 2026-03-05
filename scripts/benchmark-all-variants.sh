#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
MASTER_RESULTS="$PROJECT_ROOT/benchmark-results/MASTER_$(date +%Y%m%d_%H%M%S)"

mkdir -p "$MASTER_RESULTS"

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  Spring PetClinic - Multi-Variant Benchmark Suite                  ║"
echo "║  Master Results: $MASTER_RESULTS"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""

OVERALL_START=$(date +%s)

# Run Java 17 Baseline
if [ -n "$JAVA_17_HOME" ]; then
    echo "Starting Java 17 baseline benchmark..."
    export JAVA_HOME=$JAVA_17_HOME
    "$SCRIPT_DIR/benchmark-complete.sh" "java17-baseline" \
        | tee "$MASTER_RESULTS/java17-baseline.log"
    
    # Archive results (find most recent)
    LATEST_RESULTS=$(find $PROJECT_ROOT/benchmark-results -maxdepth 1 -type d -name "[0-9]*" | sort -r | head -1)
    if [ -n "$LATEST_RESULTS" ]; then
        mv "$LATEST_RESULTS" "$MASTER_RESULTS/java17-baseline"
    fi
    echo ""
fi

# Run Java 21 Variant A
if [ -n "$JAVA_21_HOME" ]; then
    echo "Starting Java 21 Variant A benchmark..."
    export JAVA_HOME=$JAVA_21_HOME
    "$SCRIPT_DIR/benchmark-complete.sh" "java21-variant-a" \
        | tee "$MASTER_RESULTS/java21-variant-a.log"
    
    LATEST_RESULTS=$(find $PROJECT_ROOT/benchmark-results -maxdepth 1 -type d -name "[0-9]*" | sort -r | head -1)
    if [ -n "$LATEST_RESULTS" ]; then
        mv "$LATEST_RESULTS" "$MASTER_RESULTS/java21-variant-a"
    fi
    echo ""
    
    # Run Java 21 Variant B (Virtual Threads)
    echo "Starting Java 21 Variant B (Virtual Threads) benchmark..."
    export SPRING_PROFILES_ACTIVE=vthreads
    "$SCRIPT_DIR/benchmark-complete.sh" "java21-variant-b" \
        | tee "$MASTER_RESULTS/java21-variant-b.log"
    
    LATEST_RESULTS=$(find $PROJECT_ROOT/benchmark-results -maxdepth 1 -type d -name "[0-9]*" | sort -r | head -1)
    if [ -n "$LATEST_RESULTS" ]; then
        mv "$LATEST_RESULTS" "$MASTER_RESULTS/java21-variant-b"
    fi
    unset SPRING_PROFILES_ACTIVE
    echo ""
fi

OVERALL_TIME=$(($(date +%s) - OVERALL_START))

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  ALL VARIANTS COMPLETE                                              ║"
echo "║  Total Time: $OVERALL_TIME seconds (~$(( OVERALL_TIME / 60 )) minutes)"
echo "║  Master Results: $MASTER_RESULTS"
echo "╚════════════════════════════════════════════════════════════════════╝"
