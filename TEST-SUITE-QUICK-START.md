# Test Suite Execution - Quick Start Guide

## One-Command Execution

### Run All Tests Across All Variants

```bash
# Build the project first
mvn clean package -DskipTests

# Execute test suite for all variants
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.TestSuiteRunner
```

**Time:** ~45-60 seconds total (15-20 seconds per variant)

### Run Tests + Benchmarks Together

```bash
java -cp target/classes:target/lib/* \
  org.springframework.samples.petclinic.benchmark.BenchmarkRunner . include-tests
```

**Time:** ~5-8 minutes (JMH benchmarks take longer)

## Output Files

After execution, you'll find:

1. **test-results.json** - Full metrics for all variants
   - Test counts, pass rates, execution times
   - Coverage percentages (line, branch, method)
   - Detailed failed tests list
   - Regression detection results

2. **test-results.csv** - Summary table for Excel/PowerBI
   ```
   Variant,Total Tests,Passed,Failed,Skipped,Pass Rate,Line Coverage,Branch Coverage,Method Coverage,Duration (ms),Has Regressions
   java17-baseline,42,42,0,0,100.00%,87.50%,82.30%,91.20%,15234,NO
   java21-traditional,42,41,1,0,97.60%,87.40%,82.10%,91.00%,14892,YES
   java21-virtual,42,42,0,0,100.00%,87.60%,82.40%,91.30%,14567,NO
   ```

3. **consolidated-metrics.json** - Combined benchmark + test results
   - Run `MetricsAggregator` to generate

## Understanding the Output

### Test Counts

| Metric | Meaning |
|--------|---------|
| Total Tests | All tests executed |
| Passed | Tests that passed |
| Failed | Tests that failed (includes errors) |
| Skipped | Tests that were skipped |
| Pass Rate | Passed / Total × 100 |

### Coverage Percentages

| Type | Meaning |
|------|---------|
| Line Coverage | % of code lines executed |
| Branch Coverage | % of conditional branches executed |
| Method Coverage | % of methods executed |

**Goal:** All three metrics should be >80% for good test coverage.

### Regression Detection

- **has_regressions: true** = Tests failing that weren't failing in baseline
- **Details** = List of newly failing tests
- Compare Java 21 variants against Java 17 baseline

## Example Results Interpretation

### Scenario 1: All Tests Pass, Good Coverage

```
java17-baseline: 42 tests, 100% pass rate, 87.5% line coverage ✓
java21-traditional: 42 tests, 100% pass rate, 87.4% line coverage ✓
java21-virtual: 42 tests, 100% pass rate, 87.6% line coverage ✓
```

**Conclusion:** Code is compatible across all Java versions and threading models.

### Scenario 2: Regression in Java 21 Virtual Threads

```
java17-baseline: 42 tests, 100% pass rate
java21-virtual: 42 tests, 97.6% pass rate, REGRESSION in testOwnerDetails
```

**Conclusion:** Virtual threads expose a concurrency issue. Failed test details:
```
org.springframework.samples.petclinic.PetClinicIntegrationTests.testOwnerDetails
  Error: Expected 200 but got 500
  Message: Request timeout in async context
```

**Action:** Investigate async/concurrency code for virtual thread compatibility.

### Scenario 3: Coverage Drops in Java 21

```
java17-baseline: 87.5% line coverage
java21-traditional: 87.4% line coverage (similar)
java21-virtual: 85.2% line coverage (DROP)
```

**Conclusion:** Virtual threads take different code paths. Some code not exercised.

**Action:** Add tests for virtual thread-specific scenarios.

## Comparing Across Variants

### Quick Comparison

```bash
# View test-results.csv in Excel
open test-results.csv

# Or view JSON in terminal
cat test-results.json | jq '.summary'
```

### Check for Regressions Only

```bash
cat test-results.json | jq '.variants[] | select(.regression_detection.has_regressions == true)'
```

### Get Coverage Summary

```bash
cat test-results.json | jq '.summary | {avg_line_coverage, avg_branch_coverage}'
```

## Detailed Analysis

### View Specific Failed Tests

```bash
# JSON - Pretty print failed tests
cat test-results.json | jq '.variants[] | .failed_tests[]'

# CSV - Easy sorting
sort -t',' -k3 -rn test-results.csv | head
```

### Export for Dashboard

```bash
# Use test-results.csv directly in Excel/PowerBI
# Or import JSON to database

# Example: Load into pandas (Python)
import pandas as pd
df = pd.read_csv('test-results.csv')
df.plot(x='Variant', y=['Line Coverage', 'Method Coverage'])
```

## Troubleshooting

### Tests Fail But You Can't Reproduce Locally

1. Check variant-specific configuration
2. Verify JVM settings in TestSuiteRunner
3. Check if database setup (Testcontainers) is working
4. Review Docker daemon status (if using Testcontainers)

### No JaCoCo Report Generated

- Ensure JaCoCo plugin is configured in pom.xml
- Run `mvn verify` instead of just `mvn test`
- Check target/site/jacoco/ directory exists
- Verify -DskipITs=false flag is set

### Coverage Numbers Seem Low

1. Check if test suite includes integration tests
2. Verify JaCoCo is measuring only application code (not tests)
3. Ensure application code is actually used by tests
4. Consider code in external dependencies isn't measured

### Tests Timeout

- Increase timeout in TestSuiteRunner (default: 30 minutes)
- Check if databases are starting properly
- Monitor CPU/memory during execution
- Reduce test parallelism if configured

## Next Steps

1. **Monitor Trends:** Run tests regularly and track coverage/pass rates
2. **Analyze Failures:** Investigate any regressions found
3. **Improve Coverage:** Add tests for uncovered code
4. **Optimize:** Parallelize tests if possible for faster execution
5. **Integrate:** Add to CI/CD pipeline for every commit

## Related Commands

```bash
# Run only Java 17 baseline tests
mvn clean verify -Pjava17-baseline -DskipITs=false

# Run only Java 21 virtual thread tests  
mvn clean verify -Pjava21-virtual -DskipITs=false

# Generate JaCoCo report manually
mvn jacoco:report

# Run with specific test class
mvn clean verify -Dit.test=PetClinicIntegrationTests -DskipITs=false

# Clean up test artifacts
rm -rf target/surefire-reports target/site/jacoco
```

## Key Metrics to Track

Create a dashboard tracking:

1. **Pass Rate Trend** - Any downward trend?
2. **Coverage Trend** - Are tests keeping up with code?
3. **Execution Time** - Is it getting slower?
4. **Variant Regressions** - Any new failures in Java 21?
5. **Branch Coverage** - Often lower than line coverage, focus here

## Example Dashboard Query (if using database)

```sql
SELECT variant, AVG(line_coverage) as avg_coverage, 
       COUNT(*) as test_count, 
       SUM(CASE WHEN pass_rate = 100 THEN 1 ELSE 0 END) as clean_runs
FROM test_results
GROUP BY variant
ORDER BY test_count DESC;
```
