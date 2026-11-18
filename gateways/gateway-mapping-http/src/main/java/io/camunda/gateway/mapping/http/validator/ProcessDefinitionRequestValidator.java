/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateKeyFormat;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateProcessDefinitionId;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

public class ProcessDefinitionRequestValidator {

  public static Optional<ProblemDetail> validateSuspensionByKey(final String processDefinitionKey) {
    return validate(
        violations -> validateKeyFormat(processDefinitionKey, "processDefinitionKey", violations));
  }

  public static Optional<ProblemDetail> validateSuspensionById(
      final String processDefinitionId, final @Nullable Integer processDefinitionVersion) {
    return validate(
        violations -> {
          validateProcessDefinitionId(processDefinitionId, violations);
          if (processDefinitionVersion != null && processDefinitionVersion < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "processDefinitionVersion", processDefinitionVersion, "a positive integer"));
          }
        });
  }
}
