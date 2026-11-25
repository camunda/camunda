/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.UserRequest;
import io.camunda.gateway.protocol.model.UserUpdateRequest;
import io.camunda.security.validation.UserValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.ProblemDetail;

public class UserRequestValidator {

  static final String ERROR_MESSAGE_UNADDRESSABLE_USERNAME =
      "The provided username '%s' cannot be addressed in /v2/users/{username}. "
          + "It must not be '.' or '..' and must not contain '%%' or ';'";

  private static final Set<String> DOT_SEGMENTS = Set.of(".", "..");

  private final UserValidator userValidator;

  public UserRequestValidator(final UserValidator userValidator) {
    this.userValidator = userValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(final UserRequest request) {
    return validate(
        () -> {
          final var violations =
              new ArrayList<>(
                  userValidator.validateCreateRequest(
                      request.getUsername(),
                      request.getPassword(),
                      request.getName(),
                      request.getEmail()));
          if (violations.isEmpty()) {
            validateUsernameIsAddressable(request.getUsername(), violations);
          }
          return violations;
        });
  }

  private static void validateUsernameIsAddressable(
      final String username, final List<String> violations) {
    if (username != null
        && (DOT_SEGMENTS.contains(username) || username.contains("%") || username.contains(";"))) {
      violations.add(ERROR_MESSAGE_UNADDRESSABLE_USERNAME.formatted(username));
    }
  }

  public Optional<ProblemDetail> validateUpdateRequest(final UserUpdateRequest request) {
    return validate(
        () -> userValidator.validateUpdateRequest(request.getName(), request.getEmail()));
  }
}
