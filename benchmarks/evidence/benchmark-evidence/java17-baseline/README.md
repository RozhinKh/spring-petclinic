# Java 17 Baseline Evidence Bundle

This folder packages the Java 17 baseline benchmark evidence for comparison work.

Contents:

- `runs/stable-load/`
  - Primary baseline run for startup, load, actuator, and JMH metrics.
  - Use this run for the cleaner performance numbers.
- `runs/artifact-complete/`
  - Baseline rerun with a valid `recording.jfr` and parsed `recording.json`.
  - Use this run when you need raw JFR profiling evidence.
- `profiles/`
  - Spring profile and application property files relevant to the benchmark setup.
- `tests/`
  - Surefire XML and text reports from the latest test-suite run.
- `coverage/`
  - JaCoCo execution data and XML coverage report.

Important provenance note:

- The cleanest load-test baseline is `runs/stable-load`.
- The complete raw JFR evidence is `runs/artifact-complete`.
- Do not mix the 500-user load figures from `artifact-complete` into the main baseline comparison without noting that this rerun had a very high error rate at that load level.

Recommended usage:

- Performance comparison baseline: `runs/stable-load/benchmark-summary.json`
- Raw profiling evidence: `runs/artifact-complete/recording.jfr`
- Static blocking evidence: `runs/stable-load/spotbugs-blocking-summary.json`
- Test evidence: `tests/`
- Coverage evidence: `coverage/jacoco.xml`
