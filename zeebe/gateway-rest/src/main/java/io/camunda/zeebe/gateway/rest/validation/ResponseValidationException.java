/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validation;

import jakarta.validation.ConstraintViolation;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exception thrown when gateway strict mode detects that a response body violates the API contract.
 *
 * <p>This indicates a bug in the gateway implementation (not a user error) and should result in a
 * 500 Internal Server Error.
 */
public class ResponseValidationException extends RuntimeException {

  private final Set<? extends ConstraintViolation<?>> violations;

  public ResponseValidationException(final Set<? extends ConstraintViolation<?>> violations) {
    super(buildMessage(violations));
    this.violations = violations;
  }

  public Set<? extends ConstraintViolation<?>> getViolations() {
    return violations;
  }

  private static String buildMessage(final Set<? extends ConstraintViolation<?>> violations) {
    final String details =
        violations.stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .sorted()
            .collect(Collectors.joining("; "));
    return "Response validation failed: " + details;
  }
}
