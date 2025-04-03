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
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_PATTERN;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_REGEX;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.MAX_LENGTH;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.RoleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class RoleRequestValidator {
  private RoleRequestValidator() {}

  public static void validateRoleName(final String name, final List<String> violations) {
    if (name == null || name.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
    }
  }

  public static void validateRoleId(final String id, final List<String> violations) {
    if (id == null || id.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("roleId"));
    } else if (id.length() > MAX_LENGTH) {
      violations.add(ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted("roleId", MAX_LENGTH));
    } else if (!ID_PATTERN.matcher(id).matches()) {
      violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("roleId", ID_REGEX));
    }
  }

  public static void validateRoleDescription(
      final String description, final List<String> violations) {
    if (description == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("description"));
    }
  }

  public static Optional<ProblemDetail> validateUpdateRequest(final RoleUpdateRequest request) {
    return validate(
        violations -> {
          validateRoleName(request.getName(), violations);
          validateRoleDescription(request.getDescription(), violations);
        });
  }

  public static Optional<ProblemDetail> validateCreateRequest(final RoleCreateRequest request) {
    return validate(
        violations -> {
          validateRoleId(request.getRoleId(), violations);
          validateRoleName(request.getName(), violations);
        });
  }
}
