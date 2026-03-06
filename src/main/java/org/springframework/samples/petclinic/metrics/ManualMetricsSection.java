package org.springframework.samples.petclinic.metrics;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for manual metrics sections that are not automatically captured. Includes
 * test pass rate, cloud cost, instances required, and effort estimates.
 */
public class ManualMetricsSection implements Serializable {

	/**
	 * Test Suite Pass Rate metrics
	 */
	public static class TestPassRate implements Serializable {

		private Integer totalTests;

		private Integer passedTests;

		private Integer failedTests;

		private Double passPercentage;

		public TestPassRate() {
		}

		public TestPassRate(Integer totalTests, Integer passedTests, Integer failedTests) {
			this.totalTests = totalTests;
			this.passedTests = passedTests;
			this.failedTests = failedTests;
			this.passPercentage = totalTests > 0 ? (passedTests.doubleValue() / totalTests) * 100 : 0.0;
		}

		// Getters and Setters
		public Integer getTotalTests() {
			return totalTests;
		}

		public void setTotalTests(Integer totalTests) {
			this.totalTests = totalTests;
		}

		public Integer getPassedTests() {
			return passedTests;
		}

		public void setPassedTests(Integer passedTests) {
			this.passedTests = passedTests;
		}

		public Integer getFailedTests() {
			return failedTests;
		}

		public void setFailedTests(Integer failedTests) {
			this.failedTests = failedTests;
		}

		public Double getPassPercentage() {
			return passPercentage;
		}

		public void setPassPercentage(Double passPercentage) {
			this.passPercentage = passPercentage;
		}

	}

	/**
	 * Cloud Cost Analysis metrics
	 */
	public static class CloudCostAnalysis implements Serializable {

		private Double computeHourlyCost; // $/hour for compute resources

		private Double throughputOpsPerSec; // baseline throughput from metrics

		private Double requestsPerHour;

		private Double costPerRequest; // Calculated as (computeHourlyCost * 1) /
										// requestsPerHour

		private String formula; // Human-readable formula

		public CloudCostAnalysis() {
		}

		public CloudCostAnalysis(Double computeHourlyCost, Double throughputOpsPerSec) {
			this.computeHourlyCost = computeHourlyCost;
			this.throughputOpsPerSec = throughputOpsPerSec;
			calculateCostPerRequest();
		}

		private void calculateCostPerRequest() {
			if (throughputOpsPerSec != null && throughputOpsPerSec > 0 && computeHourlyCost != null
					&& computeHourlyCost > 0) {
				this.requestsPerHour = throughputOpsPerSec * 3600;
				this.costPerRequest = computeHourlyCost / requestsPerHour;
				this.formula = String.format("$%.4f/request = ($%.2f/hour) / (%.0f ops/sec × 3600)", costPerRequest,
						computeHourlyCost, throughputOpsPerSec);
			}
		}

		// Getters and Setters
		public Double getComputeHourlyCost() {
			return computeHourlyCost;
		}

		public void setComputeHourlyCost(Double computeHourlyCost) {
			this.computeHourlyCost = computeHourlyCost;
			if (this.throughputOpsPerSec != null) {
				calculateCostPerRequest();
			}
		}

		public Double getThroughputOpsPerSec() {
			return throughputOpsPerSec;
		}

		public void setThroughputOpsPerSec(Double throughputOpsPerSec) {
			this.throughputOpsPerSec = throughputOpsPerSec;
			if (this.computeHourlyCost != null) {
				calculateCostPerRequest();
			}
		}

		public Double getRequestsPerHour() {
			return requestsPerHour;
		}

		public Double getCostPerRequest() {
			return costPerRequest;
		}

		public String getFormula() {
			return formula;
		}

	}

	/**
	 * Instances Required for Peak Load
	 */
	public static class InstancesRequired implements Serializable {

		private Double peakLoadOpsPerSec; // From load test results

		private Double perInstanceCapacity; // Configurable: ops/sec per instance

		private Double instancesRequired; // Calculated: peak / capacity

		private String formula; // Human-readable formula

		public InstancesRequired() {
		}

		public InstancesRequired(Double peakLoadOpsPerSec, Double perInstanceCapacity) {
			this.peakLoadOpsPerSec = peakLoadOpsPerSec;
			this.perInstanceCapacity = perInstanceCapacity;
			calculateInstancesRequired();
		}

		private void calculateInstancesRequired() {
			if (peakLoadOpsPerSec != null && perInstanceCapacity != null && perInstanceCapacity > 0) {
				this.instancesRequired = Math.ceil(peakLoadOpsPerSec / perInstanceCapacity);
				this.formula = String.format("%.0f instances = %.0f ops/sec ÷ %.0f ops/sec/instance", instancesRequired,
						peakLoadOpsPerSec, perInstanceCapacity);
			}
		}

		// Getters and Setters
		public Double getPeakLoadOpsPerSec() {
			return peakLoadOpsPerSec;
		}

		public void setPeakLoadOpsPerSec(Double peakLoadOpsPerSec) {
			this.peakLoadOpsPerSec = peakLoadOpsPerSec;
			if (this.perInstanceCapacity != null) {
				calculateInstancesRequired();
			}
		}

		public Double getPerInstanceCapacity() {
			return perInstanceCapacity;
		}

		public void setPerInstanceCapacity(Double perInstanceCapacity) {
			this.perInstanceCapacity = perInstanceCapacity;
			if (this.peakLoadOpsPerSec != null) {
				calculateInstancesRequired();
			}
		}

		public Double getInstancesRequired() {
			return instancesRequired;
		}

		public String getFormula() {
			return formula;
		}

	}

	/**
	 * Effort Estimate for modernization
	 */
	public static class EffortEstimate implements Serializable {

		private Integer locRefactored; // Lines of code refactored

		private Double developerHoursSaved; // Estimated hours saved per month

		private String assumptions; // What assumptions were made

		private Map<String, Object> assumptions_map; // Structured assumptions

		public EffortEstimate() {
			this.assumptions_map = new HashMap<>();
		}

		public EffortEstimate(Integer locRefactored, Double developerHoursSaved) {
			this.locRefactored = locRefactored;
			this.developerHoursSaved = developerHoursSaved;
			this.assumptions_map = new HashMap<>();
		}

		// Getters and Setters
		public Integer getLocRefactored() {
			return locRefactored;
		}

		public void setLocRefactored(Integer locRefactored) {
			this.locRefactored = locRefactored;
		}

		public Double getDeveloperHoursSaved() {
			return developerHoursSaved;
		}

		public void setDeveloperHoursSaved(Double developerHoursSaved) {
			this.developerHoursSaved = developerHoursSaved;
		}

		public String getAssumptions() {
			return assumptions;
		}

		public void setAssumptions(String assumptions) {
			this.assumptions = assumptions;
		}

		public Map<String, Object> getAssumptions_map() {
			return assumptions_map;
		}

		public void setAssumptions_map(Map<String, Object> assumptions_map) {
			this.assumptions_map = assumptions_map;
		}

		public void addAssumption(String key, Object value) {
			if (this.assumptions_map == null) {
				this.assumptions_map = new HashMap<>();
			}
			this.assumptions_map.put(key, value);
		}

	}

	// Main container
	private TestPassRate testPassRate;

	private CloudCostAnalysis cloudCostAnalysis;

	private InstancesRequired instancesRequired;

	private EffortEstimate effortEstimate;

	public ManualMetricsSection() {
	}

	// Getters and Setters
	public TestPassRate getTestPassRate() {
		return testPassRate;
	}

	public void setTestPassRate(TestPassRate testPassRate) {
		this.testPassRate = testPassRate;
	}

	public CloudCostAnalysis getCloudCostAnalysis() {
		return cloudCostAnalysis;
	}

	public void setCloudCostAnalysis(CloudCostAnalysis cloudCostAnalysis) {
		this.cloudCostAnalysis = cloudCostAnalysis;
	}

	public InstancesRequired getInstancesRequired() {
		return instancesRequired;
	}

	public void setInstancesRequired(InstancesRequired instancesRequired) {
		this.instancesRequired = instancesRequired;
	}

	public EffortEstimate getEffortEstimate() {
		return effortEstimate;
	}

	public void setEffortEstimate(EffortEstimate effortEstimate) {
		this.effortEstimate = effortEstimate;
	}

}
