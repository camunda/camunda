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
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.UserChangeset;
import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.http.ProblemDetail;

public final class UserValidator {

  public static Optional<ProblemDetail> validateUserUpdateRequest(final UserUpdateRequest request) {
    final UserChangeset changeset = request.getChangeset();
    return validate(
        violoations ->
            violoations.addAll(
                validateUserNameAndEmail(changeset.getName(), changeset.getEmail())));
  }

  public static Optional<ProblemDetail> validateUserCreateRequest(final UserRequest request) {
    return validate(
        violations -> {
          if (request.getUsername() == null || request.getUsername().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("username"));
          }
          if (request.getPassword() == null || request.getPassword().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("password"));
          }
          violations.addAll(validateUserNameAndEmail(request.getName(), request.getEmail()));
        });
  }

  private static List<String> validateUserNameAndEmail(final String name, final String email) {
    final List<String> violations = new ArrayList<>();
    if (name == null || name.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
    }
    if (email == null || email.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("email"));
    } else if (!EmailValidator.getInstance().isValid(email)) {
      violations.add(ERROR_MESSAGE_INVALID_EMAIL.formatted(email));
    }
    return violations;
  }
}
