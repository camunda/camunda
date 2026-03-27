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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EvaluateDecisionRequestValidator Tests")
class EvaluateDecisionRequestValidatorTest {

  @Test
  @DisplayName(
      "Should accept any DecisionEvaluationByKey request (spec validation in strict contract)")
  void shouldAcceptAnyByKeyRequest() {
    final var request = new DecisionEvaluationByKey();
    request.setDecisionDefinitionKey("123456789");

    final var result = EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName(
      "Should accept any DecisionEvaluationById request (spec validation in strict contract)")
  void shouldAcceptAnyByIdRequest() {
    final var request = new DecisionEvaluationById();
    request.setDecisionDefinitionId("my-decision");

    final var result = EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid decisionDefinitionId format")
  void shouldAcceptValidDecisionDefinitionId() {
    // given
    final var request = new DecisionEvaluationById();
    request.setDecisionDefinitionId("my-decision_v1.0");

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
    final var request = new DecisionEvaluationById();
    request.setDecisionDefinitionId("üöäßÜÖÄ");

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
    final var request = new DecisionEvaluationById();
    request.setDecisionDefinitionId(invalidId);

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
