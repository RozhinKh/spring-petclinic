#!/bin/bash

# Test execution script for Java 21 unit test validation

echo "=========================================="
echo "Unit Test Suite Execution Report"
echo "=========================================="
echo "Execution Date: $(date)"
echo "Java Version: $(java -version 2>&1 | head -1)"
echo "Project: Spring PetClinic"
echo ""

# Create results directory
mkdir -p test_results

# ====================================
# Maven Test Execution
# ====================================
echo "Starting Maven test execution..."
echo "Command: mvn clean test -X"
echo ""

mvn clean test -X > test_results/maven_test_output.txt 2>&1
MAVEN_EXIT_CODE=$?

echo "Maven test execution completed with exit code: $MAVEN_EXIT_CODE"
echo ""

# Extract Maven test summary
echo "=========================================="
echo "Maven Test Results Summary"
echo "=========================================="
if grep -q "BUILD SUCCESS" test_results/maven_test_output.txt; then
    echo "Build Status: SUCCESS"
else
    echo "Build Status: FAILURE"
fi

# Count tests
if grep -E "Tests run:" test_results/maven_test_output.txt > /dev/null; then
    grep -E "Tests run:" test_results/maven_test_output.txt | tail -1
fi

echo ""

# ====================================
# Gradle Test Execution
# ====================================
echo "Starting Gradle test execution..."
echo "Command: ./gradlew clean test --info"
echo ""

./gradlew clean test --info > test_results/gradle_test_output.txt 2>&1
GRADLE_EXIT_CODE=$?

echo "Gradle test execution completed with exit code: $GRADLE_EXIT_CODE"
echo ""

echo "=========================================="
echo "Gradle Test Results Summary"
echo "=========================================="
if grep -q "BUILD SUCCESSFUL" test_results/gradle_test_output.txt; then
    echo "Build Status: SUCCESS"
else
    echo "Build Status: FAILURE"
fi

# Extract test count from gradle output
if grep -E "[0-9]+ tests? completed" test_results/gradle_test_output.txt > /dev/null; then
    grep -E "[0-9]+ tests? completed" test_results/gradle_test_output.txt | tail -1
fi

echo ""

# Check for deprecation warnings
echo "=========================================="
echo "Deprecation Warnings Check"
echo "=========================================="
echo "Checking Maven output for deprecation warnings..."
MAVEN_WARNINGS=$(grep -i "deprecated\|warning" test_results/maven_test_output.txt | grep -v "^#" | wc -l)
echo "Maven warnings found: $MAVEN_WARNINGS"

echo "Checking Gradle output for deprecation warnings..."
GRADLE_WARNINGS=$(grep -i "deprecated\|warning" test_results/gradle_test_output.txt | grep -v "^#" | wc -l)
echo "Gradle warnings found: $GRADLE_WARNINGS"

echo ""
echo "=========================================="
echo "Final Status Summary"
echo "=========================================="
echo "Maven Tests: $([ $MAVEN_EXIT_CODE -eq 0 ] && echo 'PASSED' || echo 'FAILED')"
echo "Gradle Tests: $([ $GRADLE_EXIT_CODE -eq 0 ] && echo 'PASSED' || echo 'FAILED')"
echo ""

if [ $MAVEN_EXIT_CODE -eq 0 ] && [ $GRADLE_EXIT_CODE -eq 0 ]; then
    echo "All tests PASSED!"
    exit 0
else
    echo "Some tests FAILED - See test_results/ for details"
    exit 1
fi
