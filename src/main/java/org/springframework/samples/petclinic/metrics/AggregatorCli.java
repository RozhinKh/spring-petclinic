package org.springframework.samples.petclinic.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Command-line interface for metrics aggregation. Example usage: java AggregatorCli
 * /path/to/results java17,java21-trad,java21-virtual run1,run2,run3
 */
public class AggregatorCli {

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			printUsage();
			System.exit(1);
		}

		String resultsDir = args[0];
		String[] variants = args[1].split(",");
		String[] runStr = args[2].split(",");
		String outputDir = args.length > 3 ? args[3] : resultsDir + "/exports";

		List<Integer> runNumbers = new ArrayList<>();
		for (String r : runStr) {
			try {
				runNumbers.add(Integer.parseInt(r.trim()));
			}
			catch (NumberFormatException e) {
				System.err.println("Invalid run number: " + r);
			}
		}

		if (variants.length == 0 || runNumbers.isEmpty()) {
			printUsage();
			System.exit(1);
		}

		long startTime = System.currentTimeMillis();

		System.out.println("=== Metrics Aggregator ===");
		System.out.println("Results directory: " + resultsDir);
		System.out.println("Variants: " + String.join(", ", variants));
		System.out.println("Runs: " + runNumbers);
		System.out.println("Output directory: " + outputDir);
		System.out.println();

		try {
			ResultAggregator aggregator = new ResultAggregator();

			// Aggregate each variant
			List<AggregationResult> results = new ArrayList<>();
			List<AggregatedMetric> allAggregatedMetrics = new ArrayList<>();

			for (String variant : variants) {
				System.out.println("Processing variant: " + variant);

				try {
					Map<String, List<AggregatedMetric>> variantResults = aggregator.aggregateMultipleRuns(resultsDir,
							variant, runNumbers);

					// For now, just accumulate the aggregated metrics
					AggregationResult agg = new AggregationResult();
					List<AggregatedMetric> allMetrics = new ArrayList<>();
					for (List<AggregatedMetric> metricList : variantResults.values()) {
						allMetrics.addAll(metricList);
					}
					agg.setAggregatedMetrics(allMetrics);
					results.add(agg);
					allAggregatedMetrics.addAll(allMetrics);

					System.out.println("  ✓ Aggregated " + allMetrics.size() + " metrics");
				}
				catch (Exception e) {
					System.err.println("  ✗ Error processing variant: " + e.getMessage());
				}
			}

			if (!results.isEmpty()) {
				System.out.println();
				System.out.println("Generating comparison tables...");

				// Generate comparisons
				Map<String, List<MetricComparison>> comparisons = aggregator.generateComparisons(results);

				// Print formatted output
				String output = aggregator.generateFormattedComparisons(comparisons);
				System.out.println(output);

				// Export results
				System.out.println();
				System.out.println("Exporting results to JSON, CSV, and summary formats...");
				ExportMetadata metadata = new ExportMetadata();
				metadata.addToolVersion("JMH", "1.35");
				metadata.addToolVersion("JFR", "latest");
				metadata.addEnvironmentInfo("OS", System.getProperty("os.name"));
				metadata.addEnvironmentInfo("Architecture", System.getProperty("os.arch"));

				aggregator.exportResults(allAggregatedMetrics, outputDir, null, metadata);
				System.out.println("  ✓ Results exported successfully");
			}

			long endTime = System.currentTimeMillis();
			System.out.println();
			System.out.println("Aggregation completed in " + (endTime - startTime) + "ms");

		}
		catch (Exception e) {
			System.err.println("Error during aggregation: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void printUsage() {
		System.err.println("Usage: AggregatorCli <results_dir> <variants> <runs> [output_dir]");
		System.err.println();
		System.err.println("Parameters:");
		System.err.println("  <results_dir>: Base directory containing variant subdirectories");
		System.err.println("  <variants>: Comma-separated variant identifiers");
		System.err.println("              e.g., 'Java 17,Java 21 Trad,Java 21 Virtual'");
		System.err.println("  <runs>: Comma-separated run numbers");
		System.err.println("          e.g., '1,2,3' for runs 1, 2, and 3");
		System.err.println("  [output_dir]: Optional output directory for JSON/CSV exports");
		System.err.println("               Default: <results_dir>/exports");
		System.err.println();
		System.err.println("Example:");
		System.err.println("  AggregatorCli /benchmark/results 'Java 17,Java 21 Trad' 1,2,3 /output/path");
	}

}
