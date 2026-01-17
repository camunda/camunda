/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_INVALID_EMAIL;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.validator.routines.EmailValidator;

public class UserValidator {

  private final IdentifierValidator identifierValidator;

  public UserValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public List<String> validateCreateRequest(
      final String username, final String password, final String email) {
    final List<String> violations = new ArrayList<>();
    identifierValidator.validateId(username, "username", violations);
    if (password == null || password.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("password"));
    }
    validateUserEmail(email, violations);
    return violations;
  }

  public List<String> validateUpdateRequest(final String email) {
    final List<String> violations = new ArrayList<>();
    validateUserEmail(email, violations);
    return violations;
  }

  private static void validateUserEmail(final String email, final List<String> violations) {
    if (email != null && !email.isBlank() && !EmailValidator.getInstance().isValid(email)) {
      violations.add(ERROR_MESSAGE_INVALID_EMAIL.formatted(email));
    }
  }
}
