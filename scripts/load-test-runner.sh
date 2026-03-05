#!/bin/bash
set -e

# Load Test Runner Script
# Orchestrates complete multi-variant load testing using the LoadTestExecutionHarness
# Handles Java version detection, configuration, and result aggregation

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CONFIG_FILE="${PROJECT_ROOT}/src/main/resources/load-test-harness-config.properties"
HARNESS_CLASS="org.springframework.samples.petclinic.benchmark.LoadTestExecutionHarness"
MASTER_RESULTS="${PROJECT_ROOT}/target/load-test-results"

# ============================================
# Helper Functions
# ============================================

print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  $1${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════╝${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}→ $1${NC}"
}

# ============================================
# Prerequisite Checks
# ============================================

check_prerequisites() {
    print_header "Checking Prerequisites"

    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -1)
    print_success "Java found: $JAVA_VERSION"

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        if ! command -v ./mvnw &> /dev/null; then
            print_error "Maven is not installed"
            exit 1
        fi
        MVN_CMD="./mvnw"
    else
        MVN_CMD="mvn"
    fi
    print_success "Maven found"

    # Check JMeter
    if ! command -v jmeter &> /dev/null; then
        print_error "JMeter is not installed or not in PATH"
        print_info "Install JMeter from https://jmeter.apache.org/"
        exit 1
    fi

    JMETER_VERSION=$(jmeter --version 2>&1 | head -1)
    print_success "JMeter found: $JMETER_VERSION"

    # Check configuration file
    if [ ! -f "$CONFIG_FILE" ]; then
        print_error "Configuration file not found: $CONFIG_FILE"
        exit 1
    fi
    print_success "Configuration file found"

    # Check test plan
    TEST_PLAN=$(grep "jmeter.test_plan=" "$CONFIG_FILE" | cut -d'=' -f2)
    if [ ! -f "$TEST_PLAN" ]; then
        print_error "Test plan not found: $TEST_PLAN"
        exit 1
    fi
    print_success "Test plan found"
}

# ============================================
# Build Check
# ============================================

verify_build() {
    print_header "Verifying Build Artifacts"

    JAR_PATH="${PROJECT_ROOT}/target/spring-petclinic-4.0.0-SNAPSHOT.jar"

    if [ -f "$JAR_PATH" ]; then
        print_success "Build artifact already exists: $JAR_PATH"
        return 0
    fi

    print_info "Building project with Maven..."
    cd "$PROJECT_ROOT"
    $MVN_CMD clean package -q -DskipTests

    if [ -f "$JAR_PATH" ]; then
        print_success "Build completed successfully"
    else
        print_error "Build failed - artifact not found"
        exit 1
    fi
}

# ============================================
# Test Data Setup
# ============================================

setup_test_data() {
    print_header "Setting Up Test Data"

    DB_TYPE=$(grep "^database.type=" "$CONFIG_FILE" | cut -d'=' -f2)
    print_info "Database type: $DB_TYPE"

    case "$DB_TYPE" in
        h2)
            print_success "H2 database - test data will be initialized automatically"
            ;;
        mysql)
            print_info "MySQL database - ensure it's running on localhost:3306"
            print_info "You may need to execute: mysql -u root -p petclinic < src/main/resources/db/mysql/data.sql"
            ;;
        postgres)
            print_info "PostgreSQL database - ensure it's running on localhost:5432"
            print_info "You may need to execute: psql -U postgres -d petclinic < src/main/resources/db/postgres/data.sql"
            ;;
        *)
            print_error "Unknown database type: $DB_TYPE"
            exit 1
            ;;
    esac
}

# ============================================
# Main Execution
# ============================================

run_load_tests() {
    print_header "Running Multi-Variant Load Tests"

    mkdir -p "$MASTER_RESULTS"

    cd "$PROJECT_ROOT"

    # Compile test harness
    print_info "Compiling test harness..."
    $MVN_CMD test-compile -q

    # Run harness
    print_info "Starting LoadTestExecutionHarness..."
    $MVN_CMD exec:java \
        -Dexec.mainClass="$HARNESS_CLASS" \
        -Dexec.args="$CONFIG_FILE" \
        2>&1 | tee "$MASTER_RESULTS/harness.log"

    HARNESS_EXIT_CODE=$?
    if [ $HARNESS_EXIT_CODE -ne 0 ]; then
        print_error "Harness execution failed with exit code: $HARNESS_EXIT_CODE"
        echo "See log for details: $MASTER_RESULTS/harness.log"
        return 1
    fi

    print_success "Load testing completed"
    return 0
}

# ============================================
# Results Analysis
# ============================================

analyze_results() {
    print_header "Analyzing Results"

    if [ ! -f "$MASTER_RESULTS/master-report.json" ]; then
        print_info "Master report not found"
        return
    fi

    print_success "Results available at: $MASTER_RESULTS"

    # Count result files
    RESULT_FILES=$(find "$MASTER_RESULTS" -name "load-test-results-*.json" 2>/dev/null | wc -l)
    print_info "Generated $RESULT_FILES result files"

    # Display summary
    echo ""
    echo "Results location: $MASTER_RESULTS"
    echo "Master report: $MASTER_RESULTS/master-report.json"
    echo ""
}

# ============================================
# Main Script
# ============================================

main() {
    OVERALL_START=$(date +%s)

    echo ""
    print_header "PetClinic Multi-Variant Load Test Harness"
    echo ""

    # Run prerequisite checks
    check_prerequisites

    echo ""
    # Verify build
    verify_build

    echo ""
    # Setup test data
    setup_test_data

    echo ""
    # Run load tests
    if run_load_tests; then
        echo ""
        analyze_results
        
        OVERALL_END=$(date +%s)
        OVERALL_TIME=$((OVERALL_END - OVERALL_START))
        
        echo ""
        print_header "Load Testing Complete"
        print_success "Total execution time: $(($OVERALL_TIME / 60)) minutes $(($OVERALL_TIME % 60)) seconds"
        echo ""
        exit 0
    else
        echo ""
        print_error "Load testing failed"
        echo ""
        exit 1
    fi
}

# Run main function
main
