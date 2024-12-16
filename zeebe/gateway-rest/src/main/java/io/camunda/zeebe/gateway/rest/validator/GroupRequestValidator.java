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

import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class GroupRequestValidator {

  private GroupRequestValidator() {}

  public static Optional<ProblemDetail> validateGroupName(final String name) {
    return validate(
        violations -> {
          if (name == null || name.isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(final GroupUpdateRequest request) {
    return validateGroupName(request.getChangeset().getName());
  }

  public static Optional<ProblemDetail> validateCreateRequest(final GroupCreateRequest request) {
    return validateGroupName(request.getName());
  }
}
