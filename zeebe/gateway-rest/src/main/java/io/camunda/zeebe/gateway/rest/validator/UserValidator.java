/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_EMAIL;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_PATTERN;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.http.ProblemDetail;

public final class UserValidator {

  public static Optional<ProblemDetail> validateUserUpdateRequest(final UserUpdateRequest request) {
    return validate(violations -> violations.addAll(validateUserEmail(request.getEmail())));
  }

  public static Optional<ProblemDetail> validateUserCreateRequest(final UserRequest request) {
    return validate(
        violations -> {
          validateUsername(request, violations);
          if (request.getPassword() == null || request.getPassword().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("password"));
          }
          violations.addAll(validateUserEmail(request.getEmail()));
        });
  }

  private static void validateUsername(final UserRequest request, final List<String> violations) {
    final var username = request.getUsername();
    IdentifierValidator.validateId(username, "username", violations, ID_PATTERN);
  }

  private static List<String> validateUserEmail(final String email) {
    final List<String> violations = new ArrayList<>();
    if (email != null && !email.isBlank() && !EmailValidator.getInstance().isValid(email)) {
      violations.add(ERROR_MESSAGE_INVALID_EMAIL.formatted(email));
    }

    return violations;
  }
}
