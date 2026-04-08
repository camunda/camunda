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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobActivationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobErrorRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobUpdateRequestStrictContract;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class JobRequestValidator {

  public static Optional<ProblemDetail> validateActivationRequest(
      final GeneratedJobActivationRequestStrictContract request) {
    return validate(
        violations -> {
          if (request.type() == null || request.type().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("type"));
          }
          if (request.timeout() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("timeout"));
          } else if (request.timeout() < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "timeout", request.timeout(), "greater than 0"));
          }
          if (request.maxJobsToActivate() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("maxJobsToActivate"));
          } else if (request.maxJobsToActivate() < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "maxJobsToActivate", request.maxJobsToActivate(), "greater than 0"));
          }
        });
  }

  public static Optional<ProblemDetail> validateErrorRequest(
      final GeneratedJobErrorRequestStrictContract request) {
    return validate(
        violations -> {
          if (request.errorCode() == null || request.errorCode().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("errorCode"));
          }
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(
      final GeneratedJobUpdateRequestStrictContract request) {
    final var cs = request.changeset();
    return validate(
        violations -> {
          if (cs == null || (cs.retries() == null && cs.timeout() == null)) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(List.of("retries", "timeout")));
          }
        });
  }
}
