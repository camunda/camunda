/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_PATTERN;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_REGEX;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class TenantRequestValidator {

  private TenantRequestValidator() {}

  public static Optional<ProblemDetail> validateTenantCreateRequest(
      final TenantCreateRequest request) {
    return validate(
        violations -> {
          if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("tenantId"));
          } else if (!ID_PATTERN.matcher(request.getTenantId()).matches()) {
            violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("tenantId", ID_REGEX));
          }
          if (request.getName() == null || request.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }
        });
  }

  public static Optional<ProblemDetail> validateTenantUpdateRequest(
      final TenantUpdateRequest request) {
    return validate(
        violations -> {
          if (request.getName() == null || request.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }
          if (request.getDescription() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("description"));
          }
        });
  }
}
