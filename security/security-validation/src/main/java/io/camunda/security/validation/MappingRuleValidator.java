/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import java.util.ArrayList;
import java.util.List;

public final class MappingRuleValidator {

  private final IdentifierValidator identifierValidator;

  public MappingRuleValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public List<String> validateCreateRequest(
      final String mappingRuleId,
      final String claimName,
      final String claimValue,
      final String name) {
    final List<String> violations = new ArrayList<>();
    violations.addAll(validateClaims(claimName, claimValue));
    violations.addAll(validateName(name));
    violations.addAll(validateId(mappingRuleId));
    return violations;
  }

  public List<String> validateUpdateRequest(
      final String claimName, final String claimValue, final String name) {
    final List<String> violations = new ArrayList<>();
    violations.addAll(validateClaims(claimName, claimValue));
    violations.addAll(validateName(name));
    return violations;
  }

  private List<String> validateId(final String mappingRuleId) {
    final List<String> violations = new ArrayList<>();
    identifierValidator.validateId(mappingRuleId, "mappingRuleId", violations);
    return violations;
  }

  private static List<String> validateName(final String name) {
    if (name == null || name.isBlank()) {
      return List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
    }
    return new ArrayList<>();
  }

  private static List<String> validateClaims(final String claimName, final String claimValue) {
    final List<String> violations = new ArrayList<>();
    if (claimName == null || claimName.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimName"));
    }
    if (claimValue == null || claimValue.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimValue"));
    }
    return violations;
  }
}
