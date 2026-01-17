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
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class UserRequestValidator {

  private final UserValidator userValidator;

  public UserRequestValidator(final UserValidator userValidator) {
    this.userValidator = userValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(final UserRequest request) {
    return validate(
        () ->
            userValidator.validateCreateRequest(
                request.getUsername(), request.getPassword(), request.getEmail()));
  }

  public Optional<ProblemDetail> validateUpdateRequest(final UserUpdateRequest request) {
    return validate(() -> userValidator.validateUpdateRequest(request.getEmail()));
  }
}
