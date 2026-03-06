package org.springframework.samples.petclinic.metrics.parser;

import org.springframework.samples.petclinic.metrics.NormalizedMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for test suite results: JUnit XML and JaCoCo coverage reports.
 */
public class TestSuiteParser {

	private static final ObjectMapper mapper = new ObjectMapper();

	public List<NormalizedMetric> parseJunitXml(File junitXmlFile, String variant, Integer runNumber) throws Exception {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!junitXmlFile.exists()) {
			return metrics;
		}

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(junitXmlFile);
		doc.getDocumentElement().normalize();

		// Parse testsuite element
		NodeList testsuites = doc.getElementsByTagName("testsuite");
		if (testsuites.getLength() > 0) {
			org.w3c.dom.Element testsuite = (org.w3c.dom.Element) testsuites.item(0);

			// Extract counts
			int tests = Integer.parseInt(testsuite.getAttribute("tests"));
			int failures = Integer.parseInt(testsuite.getAttribute("failures"));
			int skipped = Integer.parseInt(testsuite.getAttribute("skipped"));
			double time = Double.parseDouble(testsuite.getAttribute("time"));

			metrics.add(new NormalizedMetric("test_count", (double) tests, "count", variant, runNumber, "test_suite",
					"test_suite"));
			metrics.add(new NormalizedMetric("test_failures", (double) failures, "count", variant, runNumber,
					"test_suite", "test_suite"));
			metrics.add(new NormalizedMetric("test_skipped", (double) skipped, "count", variant, runNumber,
					"test_suite", "test_suite"));
			metrics.add(new NormalizedMetric("test_execution_time", time, "seconds", variant, runNumber, "test_suite",
					"test_suite"));

			// Calculate pass rate
			int passed = tests - failures - skipped;
			double passRate = tests > 0 ? (passed * 100.0 / tests) : 100.0;
			metrics.add(new NormalizedMetric("test_pass_rate", passRate, "%", variant, runNumber, "test_suite",
					"test_suite"));
		}

		return metrics;
	}

	public List<NormalizedMetric> parseJacocoXml(File jacocoXmlFile, String variant, Integer runNumber)
			throws Exception {
		List<NormalizedMetric> metrics = new ArrayList<>();

		if (!jacocoXmlFile.exists()) {
			return metrics;
		}

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(jacocoXmlFile);
		doc.getDocumentElement().normalize();

		// Parse report element
		NodeList reports = doc.getElementsByTagName("report");
		if (reports.getLength() > 0) {
			// Parse counter elements for line coverage
			NodeList counters = doc.getElementsByTagName("counter");
			for (int i = 0; i < counters.getLength(); i++) {
				org.w3c.dom.Element counter = (org.w3c.dom.Element) counters.item(i);
				String type = counter.getAttribute("type");
				int missed = Integer.parseInt(counter.getAttribute("missed"));
				int covered = Integer.parseInt(counter.getAttribute("covered"));

				if ("LINE".equals(type)) {
					double total = missed + covered;
					double coveragePercent = total > 0 ? (covered * 100.0 / total) : 0.0;
					metrics.add(new NormalizedMetric("code_coverage_lines", coveragePercent, "%", variant, runNumber,
							"test_suite", "test_suite"));
				}
				else if ("BRANCH".equals(type)) {
					double total = missed + covered;
					double coveragePercent = total > 0 ? (covered * 100.0 / total) : 0.0;
					metrics.add(new NormalizedMetric("code_coverage_branches", coveragePercent, "%", variant, runNumber,
							"test_suite", "test_suite"));
				}
			}
		}

		return metrics;
	}

}
