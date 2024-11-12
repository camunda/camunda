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

import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import java.util.Optional;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.http.ProblemDetail;

public final class UserValidator {

  public static Optional<ProblemDetail> validateUserCreateRequest(final UserRequest request) {
    return validate(
        violations -> {
          if (request.getUsername() == null || request.getUsername().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("username"));
          }
          if (request.getName() == null || request.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }

          if (request.getPassword() == null || request.getPassword().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("password"));
          }
          if (request.getEmail() == null || request.getEmail().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("email"));
          } else if (!EmailValidator.getInstance().isValid(request.getEmail())) {
            violations.add(ERROR_MESSAGE_INVALID_EMAIL.formatted(request.getEmail()));
          }
        });
  }
}
