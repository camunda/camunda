/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.testgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestCaseTransformerTest {

  @Test
  void shouldTransformEpicWithAcceptanceCriteria() {
    // Given
    final var epic = new HashMap<String, Object>();
    epic.put("title", "User Authentication Feature");
    epic.put(
        "body",
        """
        ## Description
        Implement user authentication

        ## Acceptance Criteria
        - Scenario: User logs in successfully
          Given a registered user
          When they enter valid credentials
          Then they should be logged in

        - Scenario: User logs in with invalid credentials
          Given a registered user
          When they enter invalid credentials
          Then an error message should be displayed
        """);

    // When
    final var testCases = TestCaseTransformer.transformEpicToTestCases(epic);

    // Then
    assertThat(testCases).hasSize(2);

    final var firstTestCase = testCases.get(0);
    assertThat(firstTestCase.get("title"))
        .asString()
        .contains("User Authentication Feature", "Scenario 1");
    assertThat(firstTestCase.get("steps")).asString().contains("Given", "When");
    assertThat(firstTestCase.get("expected")).asString().contains("logged in");

    final var secondTestCase = testCases.get(1);
    assertThat(secondTestCase.get("title"))
        .asString()
        .contains("User Authentication Feature", "Scenario 2");
  }

  @Test
  void shouldTransformEpicWithSimpleCriteria() {
    // Given
    final var epic = new HashMap<String, Object>();
    epic.put("title", "Simple Feature");
    epic.put(
        "body",
        """
        ## Acceptance Criteria
        - Feature must work correctly
        - Performance should be acceptable
        - UI must be responsive
        """);

    // When
    final var testCases = TestCaseTransformer.transformEpicToTestCases(epic);

    // Then
    assertThat(testCases).hasSize(1);
    final var testCase = testCases.get(0);
    assertThat(testCase.get("title")).asString().contains("Verify Simple Feature");
    assertThat(testCase.get("steps"))
        .asString()
        .contains("Feature must work correctly", "Performance should be acceptable");
  }

  @Test
  void shouldCreateBasicTestCaseWhenNoAcceptanceCriteria() {
    // Given
    final var epic = new HashMap<String, Object>();
    epic.put("title", "Basic Feature");
    epic.put("body", "This is a simple description without acceptance criteria");
    epic.put("url", "https://github.com/camunda/product-hub/issues/123");

    // When
    final var testCases = TestCaseTransformer.transformEpicToTestCases(epic);

    // Then
    assertThat(testCases).hasSize(1);
    final var testCase = testCases.get(0);
    assertThat(testCase.get("title")).asString().contains("Verify Basic Feature");
    assertThat(testCase.get("steps")).asString().contains("Review Epic requirements");
    assertThat(testCase.get("expected")).asString().contains("Feature works as described");
    assertThat(testCase.get("refs"))
        .asString()
        .isEqualTo("https://github.com/camunda/product-hub/issues/123");
  }

  @Test
  void shouldHandleEmptyEpicBody() {
    // Given
    final var epic = new HashMap<String, Object>();
    epic.put("title", "Empty Epic");
    epic.put("body", "");

    // When
    final var testCases = TestCaseTransformer.transformEpicToTestCases(epic);

    // Then
    assertThat(testCases).hasSize(1);
    final var testCase = testCases.get(0);
    assertThat(testCase.get("title")).asString().contains("Verify Empty Epic");
  }

  @Test
  void shouldParseGivenWhenThenFormat() {
    // Given
    final var epic = new HashMap<String, Object>();
    epic.put("title", "BDD Feature");
    epic.put(
        "body",
        """
        ## Acceptance Criteria
        - Scenario: Complete workflow
          Given the system is initialized
          And the user is authenticated
          When the user performs an action
          And waits for response
          Then the result is displayed
          And the user is notified
        """);

    // When
    final var testCases = TestCaseTransformer.transformEpicToTestCases(epic);

    // Then
    assertThat(testCases).hasSize(1);
    final var testCase = testCases.get(0);
    assertThat(testCase.get("steps"))
        .asString()
        .contains("Given", "When", "And")
        .contains("system is initialized", "user performs an action");
    assertThat(testCase.get("expected"))
        .asString()
        .contains("result is displayed", "user is notified");
  }

  @Test
  void shouldHandleMixedCaseKeywords() {
    // Given
    final var epic = new HashMap<String, Object>();
    epic.put("title", "Mixed Case Feature");
    epic.put(
        "body",
        """
        ## ACCEPTANCE CRITERIA
        - Scenario: Test scenario
          GIVEN a condition
          WHEN an action occurs
          THEN a result happens
        """);

    // When
    final var testCases = TestCaseTransformer.transformEpicToTestCases(epic);

    // Then
    assertThat(testCases).hasSize(1);
    final var testCase = testCases.get(0);
    assertThat(testCase.get("steps")).asString().contains("GIVEN", "WHEN");
    assertThat(testCase.get("expected")).asString().contains("result happens");
  }
}
