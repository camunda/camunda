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

public class ClusterVariableValidator {

  private final IdentifierValidator identifierValidator;

  public ClusterVariableValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public List<String> validateTenantClusterVariableRequestWithValue(
      final String name, final Object value, final String tenantId) {
    final List<String> violations = new ArrayList<>();
    validateClusterVariableName(name, violations);
    validateTenantId(tenantId, violations);
    validateValue(value, violations);
    return violations;
  }

  public List<String> validateGlobalClusterVariableRequestWithValue(
      final String name, final Object value) {
    final List<String> violations = new ArrayList<>();
    validateClusterVariableName(name, violations);
    validateValue(value, violations);
    return violations;
  }

  public List<String> validateTenantClusterVariableRequest(
      final String name, final String tenantId) {
    final List<String> violations = new ArrayList<>();
    validateClusterVariableName(name, violations);
    validateTenantId(tenantId, violations);
    return violations;
  }

  public List<String> validateGlobalClusterVariableRequest(final String name) {
    final List<String> violations = new ArrayList<>();
    validateClusterVariableName(name, violations);
    return violations;
  }

  private void validateClusterVariableName(
      final String variableName, final List<String> violations) {
    identifierValidator.validateId(variableName, "name", violations);
  }

  private void validateTenantId(final String id, final List<String> violations) {
    identifierValidator.validateTenantId(id, violations);
  }

  private static void validateValue(final Object value, final List<String> violations) {
    if (value == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("value"));
    }
  }
}
