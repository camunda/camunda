/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateKeyFormat;

import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationById;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationByKey;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationInstruction;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class EvaluateDecisionRequestValidator {

  public static Optional<ProblemDetail> validateEvaluateDecisionRequest(
      final DecisionEvaluationInstruction request) {
    if (request instanceof final DecisionEvaluationByKey byKey) {
      return validate(
          violations -> {
            if (byKey.getDecisionDefinitionKey() == null) {
              violations.add(
                  ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                      "[decisionDefinitionId, decisionDefinitionKey]"));
            }
            validateKeyFormat(
                byKey.getDecisionDefinitionKey(), "decisionDefinitionKey", violations);
          });
    }
    if (request instanceof final DecisionEvaluationById byId) {
      return validate(
          violations -> {
            if (byId.getDecisionDefinitionId() == null) {
              violations.add(
                  ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                      "[decisionDefinitionId, decisionDefinitionKey]"));
            }
          });
    }
    return Optional.empty();
  }
}
