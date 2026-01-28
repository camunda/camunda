/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.AuthorizationIdBasedRequest;
import io.camunda.gateway.protocol.model.AuthorizationPropertyBasedRequest;
import io.camunda.security.validation.AuthorizationValidator;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.ProblemDetail;

public final class AuthorizationRequestValidator {

  private final AuthorizationValidator authorizationValidator;

  public AuthorizationRequestValidator(final AuthorizationValidator authorizationValidator) {
    this.authorizationValidator = authorizationValidator;
  }

  public Optional<ProblemDetail> validateIdBasedRequest(final AuthorizationIdBasedRequest request) {
    return validate(
        () ->
            authorizationValidator.validateIdBased(
                request.getOwnerId(),
                request.getOwnerType(),
                request.getResourceType(),
                request.getResourceId(),
                Set.copyOf(request.getPermissionTypes())));
  }

  public Optional<ProblemDetail> validatePropertyBasedRequest(
      final AuthorizationPropertyBasedRequest request) {
    return validate(
        () ->
            authorizationValidator.validatePropertyBased(
                request.getOwnerId(),
                request.getOwnerType(),
                request.getResourceType(),
                request.getResourcePropertyName(),
                Set.copyOf(request.getPermissionTypes())));
  }
}
