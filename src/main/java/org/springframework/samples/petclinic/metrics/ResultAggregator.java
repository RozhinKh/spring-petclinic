package org.springframework.samples.petclinic.metrics;

import org.springframework.samples.petclinic.metrics.parser.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main orchestrator for result aggregation.
 * Coordinates parsing of all data sources, normalization, aggregation, and comparison
 * table generation.
 */
public class ResultAggregator {

	private final MetricsNormalizer normalizer;
	private final MetricsAggregator aggregator;
	private final ComparisonTableGenerator tableGenerator;
	private final BenchmarkExporter exporter;

	// Parsers
	private final JmhJsonParser jmhParser;
	private final JfrMetricsParser jfrParser;
	private final TestSuiteParser testSuiteParser;
	private final LoadTestParser loadTestParser;
	private final ActuatorMetricsParser actuatorParser;
	private final BlockingDetectionParser blockingParser;
	private final ModernizationMetricsParser modernizationParser;

	public ResultAggregator() {
		this.normalizer = new MetricsNormalizer();
		this.aggregator = new MetricsAggregator();
		this.tableGenerator = new ComparisonTableGenerator();
		this.exporter = new BenchmarkExporter();

		this.jmhParser = new JmhJsonParser();
		this.jfrParser = new JfrMetricsParser();
		this.testSuiteParser = new TestSuiteParser();
		this.loadTestParser = new LoadTestParser();
		this.actuatorParser = new ActuatorMetricsParser();
		this.blockingParser = new BlockingDetectionParser();
		this.modernizationParser = new ModernizationMetricsParser();
	}

	/**
	 * Main aggregation workflow: parse -> normalize -> aggregate -> compare
	 */
	public AggregationResult aggregate(String baseDir, String variant, Integer runNumber)
			throws Exception {
		List<NormalizedMetric> allMetrics = new ArrayList<>();

		// Parse all data sources
		allMetrics.addAll(parseJmhResults(new File(baseDir), variant, runNumber));
		allMetrics.addAll(parseJfrResults(new File(baseDir), variant, runNumber));
		allMetrics.addAll(parseTestResults(new File(baseDir), variant, runNumber));
		allMetrics.addAll(parseLoadTestResults(new File(baseDir), variant, runNumber));
		allMetrics.addAll(parseActuatorResults(new File(baseDir), variant, runNumber));
		allMetrics.addAll(parseBlockingResults(new File(baseDir), variant, runNumber));
		allMetrics.addAll(parseModernizationResults(new File(baseDir), variant, runNumber));

		// Normalize and handle missing data
		List<NormalizedMetric> normalized = normalizer.handleMissingData(allMetrics, false);

		// Aggregate across runs
		List<AggregatedMetric> aggregated = aggregator.aggregate(normalized);

		// Create result
		AggregationResult result = new AggregationResult();
		result.setNormalizedMetrics(normalized);
		result.setAggregatedMetrics(aggregated);
		result.setAggregatedByCategory(aggregator.aggregateByCategory(aggregated));

		return result;
	}

	/**
	 * Aggregates multiple runs of the same variant
	 */
	public Map<String, List<AggregatedMetric>> aggregateMultipleRuns(String baseDir,
			String variant, List<Integer> runNumbers) throws Exception {
		List<NormalizedMetric> allMetrics = new ArrayList<>();

		for (Integer runNumber : runNumbers) {
			try {
				AggregationResult result = aggregate(baseDir, variant, runNumber);
				allMetrics.addAll(result.getNormalizedMetrics());
			} catch (Exception e) {
				System.err.println("Warning: Failed to parse run " + runNumber + " for variant "
						+ variant + ": " + e.getMessage());
			}
		}

		List<AggregatedMetric> aggregated = aggregator.aggregate(allMetrics);
		return aggregator.aggregateByCategory(aggregated);
	}

	/**
	 * Generates comparison tables across all variants
	 */
	public Map<String, List<MetricComparison>> generateComparisons(
			List<AggregationResult> variantResults) {
		// Merge all aggregated metrics from all variants
		List<AggregatedMetric> allAggregated = variantResults.stream()
				.flatMap(r -> r.getAggregatedMetrics().stream())
				.collect(Collectors.toList());

		return tableGenerator.generateComparisonsByCategory(allAggregated);
	}

	/**
	 * Generates formatted comparison tables for display
	 */
	public String generateFormattedComparisons(Map<String, List<MetricComparison>> comparisons) {
		return tableGenerator.generateFormattedTable(comparisons);
	}

	/**
	 * Exports aggregated results to JSON, CSV, and summary formats
	 */
	public void exportResults(List<AggregatedMetric> aggregatedMetrics, String outputDir,
			ManualMetricsSection manualMetrics, ExportMetadata metadata) throws IOException {
		exporter.export(aggregatedMetrics, outputDir, manualMetrics, metadata);
	}

	/**
	 * Exports aggregated results with default metadata
	 */
	public void exportResults(List<AggregatedMetric> aggregatedMetrics, String outputDir)
			throws IOException {
		ExportMetadata metadata = new ExportMetadata();
		exporter.export(aggregatedMetrics, outputDir, null, metadata);
	}

	// Parsing helper methods
	private List<NormalizedMetric> parseJmhResults(File baseDir, String variant,
			Integer runNumber) {
		try {
			File jmhFile = findFile(baseDir, "jmh-results", ".json");
			if (jmhFile != null) {
				return jmhParser.parse(jmhFile, variant, runNumber);
			}
		} catch (IOException e) {
			System.err.println("Error parsing JMH results: " + e.getMessage());
		}
		return new ArrayList<>();
	}

	private List<NormalizedMetric> parseJfrResults(File baseDir, String variant,
			Integer runNumber) {
		try {
			File jfrFile = findFile(baseDir, "jfr-metrics", ".json");
			if (jfrFile != null) {
				return jfrParser.parse(jfrFile, variant, runNumber);
			}
		} catch (IOException e) {
			System.err.println("Error parsing JFR results: " + e.getMessage());
		}
		return new ArrayList<>();
	}

	private List<NormalizedMetric> parseTestResults(File baseDir, String variant,
			Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();
		try {
			// Parse JUnit XML
			File junitFile = findFile(baseDir, "test-results", ".xml");
			if (junitFile != null) {
				metrics.addAll(testSuiteParser.parseJunitXml(junitFile, variant, runNumber));
			}

			// Parse JaCoCo XML
			File jacocoFile = findFile(baseDir, "jacoco", ".xml");
			if (jacocoFile != null) {
				metrics.addAll(testSuiteParser.parseJacocoXml(jacocoFile, variant, runNumber));
			}
		} catch (Exception e) {
			System.err.println("Error parsing test suite results: " + e.getMessage());
		}
		return metrics;
	}

	private List<NormalizedMetric> parseLoadTestResults(File baseDir, String variant,
			Integer runNumber) {
		try {
			File loadTestFile = findFile(baseDir, "load-test", ".json");
			if (loadTestFile != null) {
				return loadTestParser.parse(loadTestFile, variant, runNumber);
			}
		} catch (IOException e) {
			System.err.println("Error parsing load test results: " + e.getMessage());
		}
		return new ArrayList<>();
	}

	private List<NormalizedMetric> parseActuatorResults(File baseDir, String variant,
			Integer runNumber) {
		try {
			File actuatorFile = findFile(baseDir, "actuator-metrics", ".json");
			if (actuatorFile != null) {
				return actuatorParser.parse(actuatorFile, variant, runNumber);
			}
		} catch (IOException e) {
			System.err.println("Error parsing actuator metrics: " + e.getMessage());
		}
		return new ArrayList<>();
	}

	private List<NormalizedMetric> parseBlockingResults(File baseDir, String variant,
			Integer runNumber) {
		List<NormalizedMetric> metrics = new ArrayList<>();
		try {
			// Parse static findings
			File staticFile = findFile(baseDir, "blocking-static", ".json");
			if (staticFile != null) {
				metrics.addAll(blockingParser.parseStaticFindings(staticFile, variant, runNumber));
			}

			// Parse runtime findings
			File runtimeFile = findFile(baseDir, "blocking-runtime", ".json");
			if (runtimeFile != null) {
				metrics.addAll(blockingParser.parseRuntimeFindings(runtimeFile, variant, runNumber));
			}
		} catch (IOException e) {
			System.err.println("Error parsing blocking detection results: " + e.getMessage());
		}
		return metrics;
	}

	private List<NormalizedMetric> parseModernizationResults(File baseDir, String variant,
			Integer runNumber) {
		try {
			File modernizationFile = findFile(baseDir, "modernization-metrics", ".json");
			if (modernizationFile != null) {
				return modernizationParser.parse(modernizationFile, variant, runNumber);
			}
		} catch (IOException e) {
			System.err.println("Error parsing modernization metrics: " + e.getMessage());
		}
		return new ArrayList<>();
	}

	/**
	 * Finds a file in the directory by pattern matching
	 */
	private File findFile(File directory, String namePattern, String extension) {
		if (!directory.exists() || !directory.isDirectory()) {
			return null;
		}

		File[] files = directory.listFiles((dir, name) -> name.contains(namePattern)
				&& name.endsWith(extension));

		return files != null && files.length > 0 ? files[0] : null;
	}

}
