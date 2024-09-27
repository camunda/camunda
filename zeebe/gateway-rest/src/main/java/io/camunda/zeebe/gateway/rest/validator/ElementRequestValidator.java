/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateOperationReference;

import io.camunda.zeebe.gateway.protocol.rest.SetVariableRequest;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ElementRequestValidator {

  public static Optional<ProblemDetail> validateVariableRequest(
      final SetVariableRequest variableRequest) {
    return validate(
        violations -> {
          if (variableRequest.getVariables() == null || variableRequest.getVariables().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("variables"));
          }
          validateOperationReference(variableRequest.getOperationReference(), violations);
        });
  }
}
