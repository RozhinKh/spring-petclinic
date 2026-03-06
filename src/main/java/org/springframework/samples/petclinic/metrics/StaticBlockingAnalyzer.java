/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.metrics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static code analyzer for detecting blocking patterns in compiled Java classes. Scans
 * bytecode patterns using regular expressions and SpotBugs reports for: - Synchronized
 * blocks/methods - Thread.sleep() calls - BlockingQueue/BlockingDeque operations -
 * Blocking I/O (URLConnection, FileInputStream, etc.)
 */
public class StaticBlockingAnalyzer {

	private static final Logger logger = LoggerFactory.getLogger(StaticBlockingAnalyzer.class);

	private static final String SPOTBUGS_REPORT_DIR = "target/spotbugs";

	private static final String DEFAULT_CLASS_DIR = "target/classes";

	/**
	 * Blocking pattern definitions with regex matching
	 */
	private static final Map<String, Pattern> BLOCKING_PATTERNS = Map.ofEntries(
			Map.entry("synchronized_method",
					Pattern.compile("public\\s+synchronized|private\\s+synchronized|protected\\s+synchronized")),
			Map.entry("synchronized_block", Pattern.compile("synchronized\\s*\\(")),
			Map.entry("thread_sleep", Pattern.compile("Thread\\.sleep\\s*\\(")),
			Map.entry("blocking_queue", Pattern.compile("BlockingQueue|BlockingDeque|put\\s*\\(|take\\s*\\(")),
			Map.entry("file_input", Pattern.compile("FileInputStream|new\\s+FileInputStream")),
			Map.entry("url_connection", Pattern.compile("URLConnection|openConnection\\s*\\(")),
			Map.entry("wait_notify", Pattern.compile("\\.wait\\s*\\(|\\.notify\\s*\\(")),
			Map.entry("object_lock", Pattern.compile("synchronized\\s*\\(this\\)")));

	private final String classDirectory;

	private final String spotbugsReportDirectory;

	private final List<BlockingFinding> staticFindings = new ArrayList<>();

	/**
	 * Create analyzer with default directories
	 */
	public StaticBlockingAnalyzer() {
		this(DEFAULT_CLASS_DIR, SPOTBUGS_REPORT_DIR);
	}

	/**
	 * Create analyzer with custom directories
	 */
	public StaticBlockingAnalyzer(String classDirectory, String spotbugsReportDirectory) {
		this.classDirectory = classDirectory;
		this.spotbugsReportDirectory = spotbugsReportDirectory;
	}

	/**
	 * Run static analysis on the codebase
	 */
	public void analyze() throws IOException {
		logger.info("Starting static blocking analysis...");
		staticFindings.clear();

		// Scan source files in target/classes
		scanSourceFiles();

		// Parse SpotBugs report if available
		parseSpotBugsReport();

		logger.info("Static analysis complete. Found {} blocking patterns", staticFindings.size());
	}

	/**
	 * Scan compiled class files for blocking patterns
	 */
	private void scanSourceFiles() throws IOException {
		File classDir = new File(classDirectory);
		if (!classDir.exists()) {
			logger.warn("Class directory does not exist: {}", classDirectory);
			return;
		}

		try {
			Files.walk(Paths.get(classDirectory)).filter(path -> path.toString().endsWith(".class")).forEach(path -> {
				try {
					String content = new String(Files.readAllBytes(path));
					scanClassContent(path.toString(), content);
				}
				catch (IOException e) {
					logger.warn("Failed to read class file: {}", path);
				}
			});
		}
		catch (IOException e) {
			logger.warn("Failed to scan class directory: {}", e.getMessage());
		}
	}

	/**
	 * Scan individual class content for blocking patterns
	 */
	private void scanClassContent(String filePath, String content) {
		String className = extractClassNameFromPath(filePath);

		for (Map.Entry<String, Pattern> entry : BLOCKING_PATTERNS.entrySet()) {
			Pattern pattern = entry.getValue();
			Matcher matcher = pattern.matcher(content);

			while (matcher.find()) {
				BlockingFinding finding = new BlockingFinding();
				finding.setPattern(entry.getKey());
				finding.setClassName(className);
				finding.setLocation(filePath);
				finding.setSeverity("MEDIUM");
				finding.setSource("STATIC_SCAN");

				staticFindings.add(finding);
				logger.debug("Found blocking pattern '{}' in class {}", entry.getKey(), className);
			}
		}
	}

	/**
	 * Parse SpotBugs XML report for additional findings
	 */
	private void parseSpotBugsReport() {
		File reportDir = new File(spotbugsReportDirectory);
		if (!reportDir.exists()) {
			logger.debug("SpotBugs report directory does not exist: {}", spotbugsReportDirectory);
			return;
		}

		try {
			Files.walk(Paths.get(spotbugsReportDirectory))
				.filter(path -> path.toString().endsWith("spotbugsXml.xml"))
				.findFirst()
				.ifPresent(path -> {
					try {
						parseSpotBugsXml(path.toString());
					}
					catch (IOException e) {
						logger.warn("Failed to parse SpotBugs report: {}", e.getMessage());
					}
				});
		}
		catch (IOException e) {
			logger.warn("Failed to scan spotbugs directory: {}", e.getMessage());
		}
	}

	/**
	 * Parse SpotBugs XML report
	 */
	private void parseSpotBugsXml(String xmlPath) throws IOException {
		String content = new String(Files.readAllBytes(Paths.get(xmlPath)));

		// Extract bug patterns related to threading
		Pattern bugPattern = Pattern
			.compile("<Bug.*?type=\"([^\"]+)\".*?abbrev=\"([^\"]+)\".*?category=\"([^\"]+)\".*?priority=\"([^\"]+)\"");
		Matcher matcher = bugPattern.matcher(content);

		while (matcher.find()) {
			String type = matcher.group(1);
			String abbrev = matcher.group(2);
			String category = matcher.group(3);
			String priority = matcher.group(4);

			// Filter for concurrency-related issues
			if (category.toLowerCase().contains("concurrency") || abbrev.contains("SYN") || abbrev.contains("WAIT")
					|| type.contains("Lock")) {

				BlockingFinding finding = new BlockingFinding();
				finding.setPattern(abbrev);
				finding.setClassName(extractBugSourceFromXml(content, matcher.start()));
				finding.setSeverity(priority);
				finding.setSource("SPOTBUGS");

				staticFindings.add(finding);
				logger.debug("Found SpotBugs issue: {} ({})", abbrev, priority);
			}
		}
	}

	/**
	 * Extract source class from SpotBugs XML around bug location
	 */
	private String extractBugSourceFromXml(String content, int position) {
		String substring = content.substring(Math.max(0, position - 500), position);
		Pattern sourcePattern = Pattern.compile("classname=\"([^\"]+)\"");
		Matcher matcher = sourcePattern.matcher(substring);
		return matcher.find() ? matcher.group(1) : "UNKNOWN";
	}

	/**
	 * Get all static findings
	 */
	public List<BlockingFinding> getFindings() {
		return new ArrayList<>(staticFindings);
	}

	/**
	 * Get findings summary
	 */
	public Map<String, Object> getSummary() {
		Map<String, Object> summary = new HashMap<>();
		summary.put("total_findings", staticFindings.size());
		summary.put("findings_by_pattern", groupByPattern());
		summary.put("findings_by_severity", groupBySeverity());
		summary.put("affected_classes", getAffectedClasses());
		return summary;
	}

	/**
	 * Group findings by pattern type
	 */
	private Map<String, Integer> groupByPattern() {
		Map<String, Integer> grouped = new HashMap<>();
		for (BlockingFinding finding : staticFindings) {
			grouped.put(finding.getPattern(), grouped.getOrDefault(finding.getPattern(), 0) + 1);
		}
		return grouped;
	}

	/**
	 * Group findings by severity level
	 */
	private Map<String, Integer> groupBySeverity() {
		Map<String, Integer> grouped = new HashMap<>();
		for (BlockingFinding finding : staticFindings) {
			grouped.put(finding.getSeverity(), grouped.getOrDefault(finding.getSeverity(), 0) + 1);
		}
		return grouped;
	}

	/**
	 * Get count of affected classes
	 */
	private int getAffectedClasses() {
		return (int) staticFindings.stream().map(BlockingFinding::getClassName).distinct().count();
	}

	/**
	 * Extract class name from file path
	 */
	private String extractClassNameFromPath(String filePath) {
		// Convert /path/to/ClassName.class to ClassName
		String className = filePath.replaceAll(".*[\\\\/]", "").replace(".class", "");
		// Convert path/to/ClassName to path.to.ClassName
		return filePath.replace(File.separator, ".").replaceAll("^.*target\\.classes\\.", "").replace(".class", "");
	}

	/**
	 * Data class representing a blocking finding
	 */
	public static class BlockingFinding {

		private String pattern;

		private String className;

		private String location;

		private String severity;

		private String source;

		private int lineNumber = -1;

		// Getters and setters
		public String getPattern() {
			return pattern;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}

		public String getClassName() {
			return className;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		public String getLocation() {
			return location;
		}

		public void setLocation(String location) {
			this.location = location;
		}

		public String getSeverity() {
			return severity;
		}

		public void setSeverity(String severity) {
			this.severity = severity;
		}

		public String getSource() {
			return source;
		}

		public void setSource(String source) {
			this.source = source;
		}

		public int getLineNumber() {
			return lineNumber;
		}

		public void setLineNumber(int lineNumber) {
			this.lineNumber = lineNumber;
		}

		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("pattern", pattern);
			map.put("class", className);
			map.put("location", location);
			map.put("severity", severity);
			map.put("source", source);
			if (lineNumber > 0) {
				map.put("line", lineNumber);
			}
			return map;
		}

	}

}
