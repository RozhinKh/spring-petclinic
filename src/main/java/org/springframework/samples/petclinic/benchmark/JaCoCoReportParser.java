/*
 * Copyright 2015-2024 the original author or authors.
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
package org.springframework.samples.petclinic.benchmark;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses JaCoCo XML coverage reports. Extracts line, branch, and method coverage
 * percentages.
 */
public class JaCoCoReportParser {

	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Parse JaCoCo XML report and extract coverage metrics.
	 * @param reportXmlPath Path to JaCoCo XML report (target/site/jacoco/index.xml or
	 * similar)
	 * @return JSON object with coverage metrics
	 */
	public ObjectNode parseJaCoCoReport(String reportXmlPath) throws Exception {
		System.out.println(">>> Parsing JaCoCo report from: " + reportXmlPath);

		File xmlFile = new File(reportXmlPath);
		if (!xmlFile.exists()) {
			System.err.println("JaCoCo report not found: " + reportXmlPath);
			return createEmptyCoverageNode();
		}

		return parseJaCoCoXml(xmlFile);
	}

	/**
	 * Parse JaCoCo XML file and extract coverage data. JaCoCo XML structure: <report>
	 * <package ...> <counter type="LINE" ... /> <counter type="BRANCH" ... /> <counter
	 * type="METHOD" ... /> </package> </report>
	 */
	private ObjectNode parseJaCoCoXml(File xmlFile) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(xmlFile);

		Element root = doc.getDocumentElement();

		ObjectNode result = mapper.createObjectNode();

		// Aggregate coverage across all packages
		CoverageMetrics aggregated = new CoverageMetrics();

		NodeList packageElements = root.getElementsByTagName("package");
		ArrayNode packageArray = mapper.createArrayNode();

		for (int i = 0; i < packageElements.getLength(); i++) {
			Element pkgElement = (Element) packageElements.item(i);
			String packageName = pkgElement.getAttribute("name");

			CoverageMetrics pkgMetrics = extractCoverageFromElement(pkgElement);
			ObjectNode pkgNode = mapper.createObjectNode();
			pkgNode.put("package", packageName);
			pkgNode.put("line_coverage_percent", pkgMetrics.lineCoverage);
			pkgNode.put("branch_coverage_percent", pkgMetrics.branchCoverage);
			pkgNode.put("method_coverage_percent", pkgMetrics.methodCoverage);
			pkgNode.put("line_missed", pkgMetrics.lineMissed);
			pkgNode.put("line_covered", pkgMetrics.lineCovered);
			pkgNode.put("branch_missed", pkgMetrics.branchMissed);
			pkgNode.put("branch_covered", pkgMetrics.branchCovered);

			packageArray.add(pkgNode);

			// Accumulate into aggregated metrics
			aggregated.lineMissed += pkgMetrics.lineMissed;
			aggregated.lineCovered += pkgMetrics.lineCovered;
			aggregated.branchMissed += pkgMetrics.branchMissed;
			aggregated.branchCovered += pkgMetrics.branchCovered;
		}

		// Calculate aggregated percentages
		if (aggregated.lineCovered + aggregated.lineMissed > 0) {
			aggregated.lineCoverage = (double) aggregated.lineCovered / (aggregated.lineCovered + aggregated.lineMissed)
					* 100.0;
		}
		if (aggregated.branchCovered + aggregated.branchMissed > 0) {
			aggregated.branchCoverage = (double) aggregated.branchCovered
					/ (aggregated.branchCovered + aggregated.branchMissed) * 100.0;
		}

		// Also extract method coverage from the root element if available
		CoverageMetrics rootMetrics = extractCoverageFromElement(root);
		aggregated.methodCoverage = rootMetrics.methodCoverage;

		// Build result node
		result.put("line_coverage_percent", Math.round(aggregated.lineCoverage * 100.0) / 100.0);
		result.put("branch_coverage_percent", Math.round(aggregated.branchCoverage * 100.0) / 100.0);
		result.put("method_coverage_percent", Math.round(aggregated.methodCoverage * 100.0) / 100.0);

		result.put("total_lines_missed", aggregated.lineMissed);
		result.put("total_lines_covered", aggregated.lineCovered);
		result.put("total_branches_missed", aggregated.branchMissed);
		result.put("total_branches_covered", aggregated.branchCovered);

		// Add per-package details
		result.set("packages", packageArray);

		return result;
	}

	/**
	 * Extract coverage metrics from an XML element containing counter elements.
	 */
	private CoverageMetrics extractCoverageFromElement(Element element) {
		CoverageMetrics metrics = new CoverageMetrics();

		NodeList counters = element.getElementsByTagName("counter");

		for (int i = 0; i < counters.getLength(); i++) {
			Element counter = (Element) counters.item(i);
			String type = counter.getAttribute("type");
			int missed = parseInt(counter.getAttribute("missed"));
			int covered = parseInt(counter.getAttribute("covered"));

			if ("LINE".equals(type)) {
				metrics.lineMissed = missed;
				metrics.lineCovered = covered;
				if (covered + missed > 0) {
					metrics.lineCoverage = (double) covered / (covered + missed) * 100.0;
				}
			}
			else if ("BRANCH".equals(type)) {
				metrics.branchMissed = missed;
				metrics.branchCovered = covered;
				if (covered + missed > 0) {
					metrics.branchCoverage = (double) covered / (covered + missed) * 100.0;
				}
			}
			else if ("METHOD".equals(type)) {
				metrics.methodMissed = missed;
				metrics.methodCovered = covered;
				if (covered + missed > 0) {
					metrics.methodCoverage = (double) covered / (covered + missed) * 100.0;
				}
			}
		}

		return metrics;
	}

	/**
	 * Create empty coverage node when report not found.
	 */
	private ObjectNode createEmptyCoverageNode() {
		ObjectNode node = mapper.createObjectNode();
		node.put("line_coverage_percent", 0.0);
		node.put("branch_coverage_percent", 0.0);
		node.put("method_coverage_percent", 0.0);
		node.put("total_lines_missed", 0);
		node.put("total_lines_covered", 0);
		node.put("total_branches_missed", 0);
		node.put("total_branches_covered", 0);
		node.set("packages", mapper.createArrayNode());
		return node;
	}

	/**
	 * Parse integer, default to 0 on error.
	 */
	private int parseInt(String value) {
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Helper class for coverage metrics.
	 */
	private static class CoverageMetrics {

		double lineCoverage = 0.0;

		double branchCoverage = 0.0;

		double methodCoverage = 0.0;

		int lineMissed = 0;

		int lineCovered = 0;

		int branchMissed = 0;

		int branchCovered = 0;

		int methodMissed = 0;

		int methodCovered = 0;

	}

}
