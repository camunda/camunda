/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.mapping.http.search.contract.generated.UserRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserUpdateRequestContract;
import io.camunda.security.validation.UserValidator;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class UserRequestValidator {

  private final UserValidator userValidator;

  public UserRequestValidator(final UserValidator userValidator) {
    this.userValidator = userValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(final UserRequestContract request) {
    return validate(
        () ->
            userValidator.validateCreateRequest(
                request.username(), request.password(), request.name(), request.email()));
  }

  public Optional<ProblemDetail> validateUpdateRequest(final UserUpdateRequestContract request) {
    return validate(() -> userValidator.validateUpdateRequest(request.name(), request.email()));
  }
}
