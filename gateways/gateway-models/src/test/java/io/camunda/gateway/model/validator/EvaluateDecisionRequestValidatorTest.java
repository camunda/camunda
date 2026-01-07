/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.model.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.DecisionEvaluationById;
import io.camunda.gateway.protocol.model.DecisionEvaluationByKey;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;

@DisplayName("EvaluateDecisionRequestValidator Tests")
class EvaluateDecisionRequestValidatorTest {

  @Test
  @DisplayName("Should accept valid decisionDefinitionKey format")
  void shouldAcceptValidDecisionDefinitionKey() {
    final var request = new DecisionEvaluationByKey();
    request.setDecisionDefinitionKey("123456789");

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "12.34", "12abc", "", " "})
  @DisplayName("Should reject invalid decisionDefinitionKey formats")
  void shouldRejectInvalidDecisionDefinitionKey(final String invalidKey) {
    final var request = new DecisionEvaluationByKey();
    request.setDecisionDefinitionKey(invalidKey);

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("decisionDefinitionKey");
    assertThat(problem.getDetail())
        .contains(
            "is not a valid key. Expected a numeric value. Did you pass an entity id instead of an entity key?");
  }

  @Test
  @DisplayName("Should reject when both decisionDefinitionId is null")
  void shouldRejectNullDecisionDefinitionId() {
    final var request = new DecisionEvaluationById();
    request.setDecisionDefinitionId(null);

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getDetail())
        .contains("At least one of [decisionDefinitionId, decisionDefinitionKey] is required");
  }

  @Test
  @DisplayName("Should reject when both decisionDefinitionKey is null")
  void shouldRejectNullDecisionDefinitionKey() {
    final var request = new DecisionEvaluationByKey();
    request.setDecisionDefinitionKey(null);

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getDetail())
        .contains("At least one of [decisionDefinitionId, decisionDefinitionKey] is required");
  }

  @Test
  @DisplayName("Should handle edge case Long values")
  void shouldHandleEdgeCaseLongValues() {
    final var request = new DecisionEvaluationByKey();

    // Test with maximum Long value
    request.setDecisionDefinitionKey(String.valueOf(Long.MAX_VALUE));

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject Long values that are too large")
  void shouldRejectLongValuesTooLarge() {
    final var request = new DecisionEvaluationByKey();
    // Create a number larger than Long.MAX_VALUE
    request.setDecisionDefinitionKey("99999999999999999999999999999");

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("decisionDefinitionKey");
    assertThat(problem.getDetail())
        .contains(
            "is not a valid key. Expected a numeric value. Did you pass an entity id instead of an entity key?");
  }

  @Test
  @DisplayName("Should accept zero as valid Long value")
  void shouldAcceptZeroAsValidLong() {
    final var request = new DecisionEvaluationByKey();
    request.setDecisionDefinitionKey("0");

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept negative Long values")
  void shouldAcceptNegativeLongValues() {
    final var request = new DecisionEvaluationByKey();
    request.setDecisionDefinitionKey("-123456789");

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }
}
