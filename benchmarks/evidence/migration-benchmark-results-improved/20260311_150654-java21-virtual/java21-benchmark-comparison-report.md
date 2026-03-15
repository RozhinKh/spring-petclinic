# Java 21 Benchmark Comparison Report

- Baseline summary: migration-benchmark-results/20260306_174229-java21-virtual/migration-benchmark-summary.json
- Improved summary: migration-benchmark-results-improved/20260311_150654-java21-virtual/migration-benchmark-summary.json

## Startup
- cold_ms: 23384 -> 7841 (-66.5%)
- warm_ms: 22315 -> 8705 (-61.0%)

## Load Test
- users_100:
  throughput_rps: 273.75 -> 319.58 (+16.7%)
  total_requests: 8299 -> 9685 (+16.7%)
  successful_requests: 8299 -> 9685 (+16.7%)
  error_count: 0 -> 0 (n/a)
  error_rate_pct: 0.0 -> 0.0 (n/a)
  duration_seconds: 30.32 -> 30.31 (-0.0%)
  latency.avg_ms: 363.68 -> 311.55 (-14.3%)
  latency.min_ms: 13.81 -> 13.4 (-3.0%)
  latency.p50_ms: 365.07 -> 299.41 (-18.0%)
  latency.p95_ms: 589.79 -> 663.24 (+12.5%)
  latency.p99_ms: 1180.91 -> 976.93 (-17.3%)
  latency.p99_9_ms: 1447.98 -> 1257.89 (-13.1%)
  status_counts (improved): {'200': 9685}
  error_samples (improved): []
- users_250:
  throughput_rps: 303.34 -> 401.93 (+32.5%)
  total_requests: 9317 -> 12310 (+32.1%)
  successful_requests: 9317 -> 12310 (+32.1%)
  error_count: 0 -> 0 (n/a)
  error_rate_pct: 0.0 -> 0.0 (n/a)
  duration_seconds: 30.71 -> 30.63 (-0.3%)
  latency.avg_ms: 815.24 -> 616.82 (-24.3%)
  latency.min_ms: 33.21 -> 10.88 (-67.2%)
  latency.p50_ms: 971.14 -> 598.11 (-38.4%)
  latency.p95_ms: 1330.95 -> 1210.25 (-9.1%)
  latency.p99_ms: 1513.39 -> 1329.87 (-12.1%)
  latency.p99_9_ms: 1726.38 -> 1452.39 (-15.9%)
  status_counts (improved): {'200': 12310}
  error_samples (improved): []
- users_500:
  throughput_rps: 287.32 -> 389.09 (+35.4%)
  total_requests: 9063 -> 12125 (+33.8%)
  successful_requests: 9063 -> 12125 (+33.8%)
  error_count: 0 -> 0 (n/a)
  error_rate_pct: 0.0 -> 0.0 (n/a)
  duration_seconds: 31.54 -> 31.16 (-1.2%)
  latency.avg_ms: 1702.5 -> 1264.15 (-25.7%)
  latency.min_ms: 31.78 -> 9.69 (-69.5%)
  latency.p50_ms: 2050.21 -> 1143.36 (-44.2%)
  latency.p95_ms: 3170.89 -> 2408.33 (-24.0%)
  latency.p99_ms: 3811.68 -> 3231.11 (-15.2%)
  latency.p99_9_ms: 4412.93 -> 3366.91 (-23.7%)
  status_counts (improved): {'200': 12125}
  error_samples (improved): []

## JMH Latency (ms/op)
- org.springframework.samples.petclinic.benchmark.LatencyBenchmark.getOwnerById: 8.865450287086757 -> 4.804709425299957 (-45.8%)
- org.springframework.samples.petclinic.benchmark.LatencyBenchmark.getOwnerFind: 7.89484741309347 -> 6.237971981493433 (-21.0%)
- org.springframework.samples.petclinic.benchmark.LatencyBenchmark.getOwners: 14.134882884217232 -> 10.460850262045769 (-26.0%)
- org.springframework.samples.petclinic.benchmark.LatencyBenchmark.getVets: 2.5744579759587047 -> 1.5074151673454619 (-41.4%)
- org.springframework.samples.petclinic.benchmark.LatencyBenchmark.postNewOwner: 8.090176532505792 -> 7.283958734884504 (-10.0%)

## JMH Throughput (ops/s)
- org.springframework.samples.petclinic.benchmark.ThroughputBenchmark.getOwnerByIdThroughput: 109.73790710510553 -> 207.71774975206935 (+89.3%)
- org.springframework.samples.petclinic.benchmark.ThroughputBenchmark.getOwnersThroughput: 67.00995630587501 -> 90.47098488636946 (+35.0%)
- org.springframework.samples.petclinic.benchmark.ThroughputBenchmark.getVetsThroughput: 371.56127207900107 -> 553.411728489482 (+48.9%)
- org.springframework.samples.petclinic.benchmark.ThroughputBenchmark.mixedWorkloadThroughput: 125.34926467745224 -> 156.26700751176563 (+24.7%)

## Actuator
- baseline: {'heap_avg_mb': 120.22, 'heap_peak_mb': 175.52, 'allocation_rate_mb_per_sec': 577.58, 'promotion_rate_mb_per_sec': 0.95, 'allocation_pressure_mb': 35905, 'promotion_total_mb': 60.84}
- improved: {'heap_avg_mb': 96.52, 'heap_peak_mb': 155.88, 'allocation_rate_mb_per_sec': 748.83, 'promotion_rate_mb_per_sec': 0.8, 'allocation_pressure_mb': 49953, 'promotion_total_mb': 55.6}

## JFR
- baseline: {'runtime_blocking_event_count': 12944}
- improved: {'runtime_blocking_event_count': 2594}

## SpotBugs
- baseline: no SpotBugs data
- improved: {'report_file': 'C:\\Users\\BiaDigi\\Desktop\\Spring Pet Clinic\\target\\\\spotbugsXml.xml', 'total_bug_instances': 6526, 'blocking_bug_instances': 18}

## Migration Effort Metrics
- baseline: {'total_java_loc_in_project': 19627, 'baseline_java_loc_in_project': None, 'lines_of_code_migrated_refactored': 5343, 'percentage_of_codebase_modified': None, 'number_of_files_modified': 94, 'number_of_java_files_modified': 88, 'number_of_modules_or_packages_touched': 8, 'packages_touched': ['org.springframework.samples.petclinic', 'org.springframework.samples.petclinic.benchmark', 'org.springframework.samples.petclinic.metrics', 'org.springframework.samples.petclinic.metrics.parser', 'org.springframework.samples.petclinic.owner', 'org.springframework.samples.petclinic.service', 'org.springframework.samples.petclinic.system', 'org.springframework.samples.petclinic.vet'], 'records_introduced': 1, 'pattern_matching_usages_introduced': 2, 'switch_expression_or_pattern_switch_conversions': 0, 'virtual_thread_usage_points_introduced': 27, 'deprecated_api_replacements': 0, 'removed_legacy_constructs': 1, 'dependency_upgrades_required_for_java21': 2, 'number_of_build_configuration_changes': 3, 'build_files_changed': ['build.gradle', 'pom.xml', 'run_tests.sh'], 'static_analysis_issues_before_migration': 6525, 'static_analysis_issues_after_migration': 6526, 'blocking_related_findings_before_migration': {'static': 18, 'runtime': 'See baseline artifact_complete JFR evidence'}, 'blocking_related_findings_after_migration': {'static': 18, 'runtime': 12944}, 'test_suite_pass_rate_before_migration': 96.15, 'test_suite_pass_rate_after_migration': 97.2, 'test_failures_introduced_by_migration': ['Virtual Thread Resource Tests.testExceptionHandling'], 'test_failures_resolved_during_migration': ['org.springframework.samples.petclinic.metrics.BlockingDetectionTests.testBlockingDetectionHarnessMultipleVariants'], 'total_test_suite_execution_time_before_migration_s': 27.81, 'total_test_suite_execution_time_after_migration_s': 71.94, 'code_coverage_before_migration_pct': 9.91, 'code_coverage_after_migration_pct': 9.71, 'number_of_migration_errors_encountered': 0, 'number_of_rework_cycles_required': 'not_tracked', 'estimated_manual_migration_effort': 'not_tracked', 'actual_migration_effort_with_artemis': 'not_tracked', 'time_spent_debugging_migration_issues': 'not_tracked', 'percentage_of_migration_automated_by_artemis': 'not_tracked', 'manual_code_review_effort_reduction': 'not_tracked', 'cves_resolved_by_upgrading_runtime_or_dependencies': 'not_tracked'}
- improved: {'total_java_loc_in_project': 19708, 'baseline_java_loc_in_project': 109, 'lines_of_code_migrated_refactored': 5343, 'percentage_of_codebase_modified': 4901.83, 'number_of_files_modified': 94, 'number_of_java_files_modified': 88, 'number_of_modules_or_packages_touched': 8, 'packages_touched': ['org.springframework.samples.petclinic', 'org.springframework.samples.petclinic.benchmark', 'org.springframework.samples.petclinic.metrics', 'org.springframework.samples.petclinic.metrics.parser', 'org.springframework.samples.petclinic.owner', 'org.springframework.samples.petclinic.service', 'org.springframework.samples.petclinic.system', 'org.springframework.samples.petclinic.vet'], 'records_introduced': 1, 'pattern_matching_usages_introduced': 2, 'switch_expression_or_pattern_switch_conversions': 0, 'virtual_thread_usage_points_introduced': 27, 'deprecated_api_replacements': 0, 'removed_legacy_constructs': 1, 'dependency_upgrades_required_for_java21': 2, 'number_of_build_configuration_changes': 3, 'build_files_changed': ['build.gradle', 'pom.xml', 'run_tests.sh'], 'static_analysis_issues_before_migration': 6525, 'static_analysis_issues_after_migration': 6526, 'blocking_related_findings_before_migration': {'static': 18, 'runtime': 'See baseline artifact_complete JFR evidence'}, 'blocking_related_findings_after_migration': {'static': 18, 'runtime': 2594}, 'test_suite_pass_rate_before_migration': 96.15, 'test_suite_pass_rate_after_migration': 100, 'test_failures_introduced_by_migration': [], 'test_failures_resolved_during_migration': ['org.springframework.samples.petclinic.metrics.BlockingDetectionTests.testBlockingDetectionHarnessMultipleVariants', 'org.springframework.samples.petclinic.metrics.MetricsCollectorTests.contextLoads', 'org.springframework.samples.petclinic.metrics.MetricsCollectorTests.metricsSnapshotCanBeCreated'], 'total_test_suite_execution_time_before_migration_s': 27.81, 'total_test_suite_execution_time_after_migration_s': 118.9, 'code_coverage_before_migration_pct': 9.91, 'code_coverage_after_migration_pct': 9.84, 'number_of_migration_errors_encountered': 0, 'number_of_rework_cycles_required': 'not_tracked', 'estimated_manual_migration_effort': 'not_tracked', 'actual_migration_effort_with_artemis': 'not_tracked', 'time_spent_debugging_migration_issues': 'not_tracked', 'percentage_of_migration_automated_by_artemis': 'not_tracked', 'manual_code_review_effort_reduction': 'not_tracked', 'cves_resolved_by_upgrading_runtime_or_dependencies': 'not_tracked'}

## Code Shape Metrics
- baseline: {'record_declarations_after': 1, 'pattern_matching_usages_after': 12, 'switch_expression_markers_after': 0, 'virtual_thread_references_after': 40}
- improved: {'record_declarations_after': 1, 'pattern_matching_usages_after': 12, 'switch_expression_markers_after': 0, 'virtual_thread_references_after': 64}

## Artifact Locations
- baseline: {'summary': 'C:\\Users\\BiaDigi\\Desktop\\Spring Pet Clinic\\migration-benchmark-results\\20260306_174229-java21-virtual\\migration-benchmark-summary.json', 'raw': 'C:\\Users\\BiaDigi\\Desktop\\Spring Pet Clinic\\migration-benchmark-results\\20260306_174229-java21-virtual\\raw', 'tests': 'C:\\Users\\BiaDigi\\Desktop\\Spring Pet Clinic\\migration-benchmark-results\\20260306_174229-java21-virtual\\tests', 'coverage': 'C:\\Users\\BiaDigi\\Desktop\\Spring Pet Clinic\\migration-benchmark-results\\20260306_174229-java21-virtual\\coverage'}
- improved: {'summary': 'migration-benchmark-results-improved\\20260311_150654-java21-virtual\\migration-benchmark-summary.json', 'raw': 'migration-benchmark-results-improved\\20260311_150654-java21-virtual\\raw', 'tests': 'migration-benchmark-results-improved\\20260311_150654-java21-virtual\\tests', 'coverage': 'migration-benchmark-results-improved\\20260311_150654-java21-virtual\\coverage'}