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

import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationByKey;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationInstruction;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class EvaluateDecisionRequestValidator {

  public static Optional<ProblemDetail> validateEvaluateDecisionRequest(
      final DecisionEvaluationInstruction request) {
    if (request instanceof final DecisionEvaluationByKey byKey) {
      return validate(
          violations ->
              validateKeyFormat(
                  byKey.getDecisionDefinitionKey(), "decisionDefinitionKey", violations));
    }
    return Optional.empty();
  }
}
