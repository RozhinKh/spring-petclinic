package org.springframework.samples.petclinic.metrics;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for metadata about the benchmark export. Includes timestamp, JDK versions,
 * tool versions, and environment information.
 */
public class ExportMetadata implements Serializable {

	private String timestamp;

	private Map<String, String> jdkVersions;

	private Map<String, String> toolVersions;

	private Map<String, String> environmentInfo;

	private Long benchmarkDurationMinutes;

	public ExportMetadata() {
		this.timestamp = Instant.now().toString();
		this.jdkVersions = new HashMap<>();
		this.toolVersions = new HashMap<>();
		this.environmentInfo = new HashMap<>();
	}

	public ExportMetadata(String timestamp) {
		this.timestamp = timestamp;
		this.jdkVersions = new HashMap<>();
		this.toolVersions = new HashMap<>();
		this.environmentInfo = new HashMap<>();
	}

	// Getters and Setters
	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public Map<String, String> getJdkVersions() {
		return jdkVersions;
	}

	public void setJdkVersions(Map<String, String> jdkVersions) {
		this.jdkVersions = jdkVersions;
	}

	public void addJdkVersion(String variant, String version) {
		this.jdkVersions.put(variant, version);
	}

	public Map<String, String> getToolVersions() {
		return toolVersions;
	}

	public void setToolVersions(Map<String, String> toolVersions) {
		this.toolVersions = toolVersions;
	}

	public void addToolVersion(String toolName, String version) {
		this.toolVersions.put(toolName, version);
	}

	public Map<String, String> getEnvironmentInfo() {
		return environmentInfo;
	}

	public void setEnvironmentInfo(Map<String, String> environmentInfo) {
		this.environmentInfo = environmentInfo;
	}

	public void addEnvironmentInfo(String key, String value) {
		this.environmentInfo.put(key, value);
	}

	public Long getBenchmarkDurationMinutes() {
		return benchmarkDurationMinutes;
	}

	public void setBenchmarkDurationMinutes(Long benchmarkDurationMinutes) {
		this.benchmarkDurationMinutes = benchmarkDurationMinutes;
	}

}
