/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

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
    final var request =
        DecisionEvaluationByKey.Builder.create().decisionDefinitionKey("123456789").build();

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "12.34", "12abc", "", " "})
  @DisplayName("Should reject invalid decisionDefinitionKey formats")
  void shouldRejectInvalidDecisionDefinitionKey(final String invalidKey) {
    // Use a dummy valid key and rely on the validator for the format check;
    // the staged builder requires non-null but the validator checks format after.
    // For truly invalid keys, build with the invalid key value directly.
    final var request =
        DecisionEvaluationByKey.Builder.create().decisionDefinitionKey(invalidKey).build();

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
  @DisplayName("Should reject when decisionDefinitionId is blank (null-like invalid value)")
  void shouldRejectNullDecisionDefinitionId() {
    // The staged builder requires a non-null decisionDefinitionId; null is now a compile-time
    // error. Use empty string as the closest equivalent — the validator rejects blank ids.
    final var request = DecisionEvaluationById.Builder.create().decisionDefinitionId("").build();

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getDetail()).contains("decisionDefinitionId");
  }

  @Test
  @DisplayName("Should reject when decisionDefinitionKey is blank (null-like invalid value)")
  void shouldRejectNullDecisionDefinitionKey() {
    // The staged builder requires a non-null decisionDefinitionKey; null is now a compile-time
    // error. Use empty string as the closest equivalent — the validator rejects blank keys.
    final var request = DecisionEvaluationByKey.Builder.create().decisionDefinitionKey("").build();

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getDetail()).contains("decisionDefinitionKey");
  }

  @Test
  @DisplayName("Should handle edge case Long values")
  void shouldHandleEdgeCaseLongValues() {
    // Test with maximum Long value
    final var request =
        DecisionEvaluationByKey.Builder.create()
            .decisionDefinitionKey(String.valueOf(Long.MAX_VALUE))
            .build();

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject Long values that are too large")
  void shouldRejectLongValuesTooLarge() {
    // Create a number larger than Long.MAX_VALUE
    final var request =
        DecisionEvaluationByKey.Builder.create()
            .decisionDefinitionKey("99999999999999999999999999999")
            .build();

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
    final var request = DecisionEvaluationByKey.Builder.create().decisionDefinitionKey("0").build();

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept negative Long values")
  void shouldAcceptNegativeLongValues() {
    final var request =
        DecisionEvaluationByKey.Builder.create().decisionDefinitionKey("-123456789").build();

    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid decisionDefinitionId format")
  void shouldAcceptValidDecisionDefinitionId() {
    // given
    final var request =
        DecisionEvaluationById.Builder.create().decisionDefinitionId("my-decision_v1.0").build();

    // when
    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept Unicode decisionDefinitionId")
  void shouldAcceptUnicodeDecisionDefinitionId() {
    // given
    final var request =
        DecisionEvaluationById.Builder.create().decisionDefinitionId("üöäßÜÖÄ").build();

    // when
    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    // then
    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"9invalid", "-dash-start", ".dot-start", "has space"})
  @DisplayName("Should reject invalid decisionDefinitionId format")
  void shouldRejectInvalidDecisionDefinitionIdFormat(final String invalidId) {
    // given
    final var request =
        DecisionEvaluationById.Builder.create().decisionDefinitionId(invalidId).build();

    // when
    final Optional<ProblemDetail> result =
        EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    // then
    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problem.getDetail()).contains("decisionDefinitionId");
    assertThat(problem.getDetail()).contains("illegal characters");
  }
}
