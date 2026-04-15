/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationIdBasedRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationPropertyBasedRequestContract;
import io.camunda.security.validation.AuthorizationValidator;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.ProblemDetail;

public final class AuthorizationRequestValidator {

  private final AuthorizationValidator authorizationValidator;

  public AuthorizationRequestValidator(final AuthorizationValidator authorizationValidator) {
    this.authorizationValidator = authorizationValidator;
  }

  public Optional<ProblemDetail> validateIdBasedRequest(
      final AuthorizationIdBasedRequestContract request) {
    return validate(
        () ->
            authorizationValidator.validate(
                request.ownerId(),
                request.ownerType(),
                request.resourceType(),
                request.resourceId(),
                null,
                Set.copyOf(request.permissionTypes())));
  }

  public Optional<ProblemDetail> validatePropertyBasedRequest(
      final AuthorizationPropertyBasedRequestContract request) {
    return validate(
        () ->
            authorizationValidator.validate(
                request.ownerId(),
                request.ownerType(),
                request.resourceType(),
                null,
                request.resourcePropertyName(),
                Set.copyOf(request.permissionTypes())));
  }
}
