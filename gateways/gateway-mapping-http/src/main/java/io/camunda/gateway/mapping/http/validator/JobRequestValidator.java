/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.JobErrorRequest;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class JobRequestValidator {

  public static Optional<ProblemDetail> validateActivationRequest(
      final JobActivationRequest request) {
    return validate(
        violations -> {
          if (request.getType() == null || request.getType().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("type"));
          }
          if (request.getTimeout() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("timeout"));
          } else if (request.getTimeout() < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "timeout", request.getTimeout(), "greater than 0"));
          }
          if (request.getMaxJobsToActivate() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("maxJobsToActivate"));
          } else if (request.getMaxJobsToActivate() < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "maxJobsToActivate", request.getMaxJobsToActivate(), "greater than 0"));
          }
        });
  }

  public static Optional<ProblemDetail> validateErrorRequest(final JobErrorRequest request) {
    return validate(
        violations -> {
          if (request.getErrorCode() == null || request.getErrorCode().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("errorCode"));
          }
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(final JobUpdateRequest request) {
    final var cs = request.getChangeset();
    return validate(
        violations -> {
          if (cs == null || (cs.getRetries() == null && cs.getTimeout() == null)) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(List.of("retries", "timeout")));
          }
        });
  }
}
