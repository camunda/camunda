/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedConditionalEvaluationInstructionStrictContract;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class ConditionalRequestValidator {

  public static Optional<ProblemDetail> validateEvaluateRequest(
      final GeneratedConditionalEvaluationInstructionStrictContract request) {
    return validate(
        violations -> {
          if (request.variables() == null || request.variables().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("variables"));
          }
        });
  }
}
