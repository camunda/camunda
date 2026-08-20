/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import io.camunda.gateway.protocol.model.CreateGlobalTaskListenerRequest;
import io.camunda.gateway.protocol.model.UpdateGlobalTaskListenerRequest;
import io.camunda.security.validation.IdentifierValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class GlobalListenerRequestValidator {

  private final IdentifierValidator identifierValidator;

  public GlobalListenerRequestValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(
      final CreateGlobalTaskListenerRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(request.getId(), "id", violations);
          identifierValidator.validateId(request.getType(), "type", violations);
          if (request.getEventTypes() == null || request.getEventTypes().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("eventTypes"));
          }
          return violations;
        });
  }

  public Optional<ProblemDetail> validateGetRequest(final String id) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(id, "id", violations);
          return violations;
        });
  }

  public Optional<ProblemDetail> validateUpdateRequest(
      final String id, final UpdateGlobalTaskListenerRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(id, "id", violations);
          identifierValidator.validateId(request.getType(), "type", violations);
          if (request.getEventTypes() == null || request.getEventTypes().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("eventTypes"));
          }
          return violations;
        });
  }

  public Optional<ProblemDetail> validateDeleteRequest(final String id) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(id, "id", violations);
          return violations;
        });
  }
}
