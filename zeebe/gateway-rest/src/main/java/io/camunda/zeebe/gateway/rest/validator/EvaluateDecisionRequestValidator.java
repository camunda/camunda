/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateKeyFormat;

import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationById;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationByKey;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationInstruction;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class EvaluateDecisionRequestValidator {

  public static Optional<ProblemDetail> validateEvaluateDecisionRequest(
      final DecisionEvaluationInstruction request) {
    if (request instanceof final DecisionEvaluationByKey byKey) {
      return validateByKey(byKey);
    }
    return validateById((DecisionEvaluationById) request);
  }

  private static Optional<ProblemDetail> validateByKey(final DecisionEvaluationByKey request) {
    return validate(
        violations -> {
          if (request.getDecisionDefinitionKey() == null) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("decisionDefinitionId", "decisionDefinitionKey")));
          } else {
            // Validate decisionDefinitionKey format
            validateKeyFormat(
                request.getDecisionDefinitionKey(), "decisionDefinitionKey", violations);
          }
        });
  }

  private static Optional<ProblemDetail> validateById(final DecisionEvaluationById request) {
    return validate(
        violations -> {
          if (request.getDecisionDefinitionId() == null) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("decisionDefinitionId", "decisionDefinitionKey")));
          }
        });
  }
}
