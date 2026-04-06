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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCreateGlobalTaskListenerRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUpdateGlobalTaskListenerRequestStrictContract;
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
      final GeneratedCreateGlobalTaskListenerRequestStrictContract request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(request.id(), "id", violations);
          identifierValidator.validateId(request.type(), "type", violations);
          if (request.eventTypes() == null || request.eventTypes().isEmpty()) {
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
      final String id, final GeneratedUpdateGlobalTaskListenerRequestStrictContract request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();
          identifierValidator.validateId(id, "id", violations);
          identifierValidator.validateId(request.type(), "type", violations);
          if (request.eventTypes() == null || request.eventTypes().isEmpty()) {
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
