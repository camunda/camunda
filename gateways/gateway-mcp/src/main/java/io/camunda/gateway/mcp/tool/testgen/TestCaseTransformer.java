/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.testgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for transforming GitHub Epics into TestRail test case specifications.
 *
 * <p>This transformer analyzes Epic content and extracts:
 *
 * <ul>
 *   <li>Test scenarios from acceptance criteria
 *   <li>Test steps from detailed descriptions
 *   <li>Expected results from requirements
 * </ul>
 */
public final class TestCaseTransformer {

  private static final Pattern ACCEPTANCE_CRITERIA_PATTERN =
      Pattern.compile(
          "(?i)##?\\s*acceptance\\s+criteria\\s*:?(.+?)(?=##|$)", Pattern.DOTALL);
  private static final Pattern SCENARIO_PATTERN =
      Pattern.compile("(?i)(?:^|\\n)[-*]\\s*scenario:?(.+?)(?=\\n[-*]|$)", Pattern.DOTALL);
  private static final Pattern GIVEN_WHEN_THEN_PATTERN =
      Pattern.compile(
          "(?i)(given|when|then|and)\\s+(.+?)(?=\\n\\s*(?:given|when|then|and)|$)",
          Pattern.DOTALL);

  private TestCaseTransformer() {
    // Utility class
  }

  /**
   * Transforms an Epic into a list of test case specifications.
   *
   * @param epic The Epic data from GitHub
   * @return List of test case specifications
   */
  public static List<Map<String, Object>> transformEpicToTestCases(
      final Map<String, Object> epic) {
    final var testCases = new ArrayList<Map<String, Object>>();
    final var body = (String) epic.getOrDefault("body", "");
    final var title = (String) epic.get("title");

    // Try to extract acceptance criteria
    final var acceptanceCriteriaMatcher = ACCEPTANCE_CRITERIA_PATTERN.matcher(body);
    if (acceptanceCriteriaMatcher.find()) {
      final var criteriaSection = acceptanceCriteriaMatcher.group(1).trim();
      testCases.addAll(extractTestCasesFromCriteria(title, criteriaSection));
    }

    // If no acceptance criteria found, create a basic test case from the Epic
    if (testCases.isEmpty()) {
      testCases.add(createBasicTestCase(epic));
    }

    return testCases;
  }

  /**
   * Extracts test cases from acceptance criteria section.
   */
  private static List<Map<String, Object>> extractTestCasesFromCriteria(
      final String epicTitle, final String criteria) {
    final var testCases = new ArrayList<Map<String, Object>>();

    // Try to find scenarios
    final var scenarioMatcher = SCENARIO_PATTERN.matcher(criteria);
    var scenarioCount = 1;

    while (scenarioMatcher.find()) {
      final var scenarioText = scenarioMatcher.group(1).trim();
      final var testCase = createTestCaseFromScenario(epicTitle, scenarioText, scenarioCount++);
      testCases.add(testCase);
    }

    // If no scenarios found, create a single test case from the criteria
    if (testCases.isEmpty()) {
      final var testCase = new HashMap<String, Object>();
      testCase.put("title", "Verify " + epicTitle);
      testCase.put("description", epicTitle);
      testCase.put("steps", formatCriteriaAsSteps(criteria));
      testCase.put("expected", "All acceptance criteria are met");
      testCases.add(testCase);
    }

    return testCases;
  }

  /**
   * Creates a test case from a scenario description.
   */
  private static Map<String, Object> createTestCaseFromScenario(
      final String epicTitle, final String scenarioText, final int scenarioNumber) {
    final var testCase = new HashMap<String, Object>();

    // Extract scenario title
    final var lines = scenarioText.split("\\n");
    final var scenarioTitle = lines[0].trim();

    testCase.put("title", epicTitle + " - Scenario " + scenarioNumber + ": " + scenarioTitle);
    testCase.put("description", epicTitle);

    // Extract Given/When/Then steps
    final var steps = new StringBuilder();
    final var expected = new StringBuilder();

    final var matcher = GIVEN_WHEN_THEN_PATTERN.matcher(scenarioText);
    while (matcher.find()) {
      final var keyword = matcher.group(1).trim();
      final var text = matcher.group(2).trim();

      if ("then".equalsIgnoreCase(keyword)) {
        expected.append(text).append("\n");
      } else {
        steps.append(keyword).append(" ").append(text).append("\n");
      }
    }

    testCase.put("steps", steps.toString().trim());
    testCase.put(
        "expected",
        expected.length() > 0 ? expected.toString().trim() : "Scenario executes successfully");

    return testCase;
  }

  /**
   * Creates a basic test case from an Epic when no structured criteria are found.
   */
  private static Map<String, Object> createBasicTestCase(final Map<String, Object> epic) {
    final var testCase = new HashMap<String, Object>();
    final var title = (String) epic.get("title");
    final var body = (String) epic.getOrDefault("body", "");

    testCase.put("title", "Verify " + title);
    testCase.put("description", title);

    // Create basic steps from Epic description
    final var steps = new StringBuilder();
    steps.append("1. Review Epic requirements: ").append(title).append("\n");
    steps.append("2. Implement feature according to Epic specifications\n");
    steps.append("3. Verify all requirements are met\n");

    testCase.put("steps", steps.toString());
    testCase.put("expected", "Feature works as described in Epic: " + title);

    // Add Epic URL as reference
    if (epic.containsKey("url")) {
      testCase.put("refs", epic.get("url"));
    }

    return testCase;
  }

  /**
   * Formats acceptance criteria as test steps.
   */
  private static String formatCriteriaAsSteps(final String criteria) {
    final var steps = new StringBuilder();
    final var lines = criteria.split("\\n");
    var stepNumber = 1;

    for (var line : lines) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }

      // Remove leading bullet points or numbers
      line = line.replaceFirst("^[-*•]\\s*", "").replaceFirst("^\\d+\\.\\s*", "");

      if (!line.isEmpty()) {
        steps.append(stepNumber++).append(". ").append(line).append("\n");
      }
    }

    return steps.toString();
  }
}
