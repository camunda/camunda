/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.CreateClusterVariableRequest;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ProblemDetail;

public class ClusterVariableRequestValidator {

  public static Optional<ProblemDetail> validateTenantClusterVariableCreateRequest(
      final CreateClusterVariableRequest request,
      final String tenantId,
      final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateClusterVariableName(request.getName(), violations, identifierPattern);
          validateTenantId(tenantId, violations);
          validateValue(request.getValue(), violations);
        });
  }

  public static Optional<ProblemDetail> validateGlobalClusterVariableCreateRequest(
      final CreateClusterVariableRequest request, final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateClusterVariableName(request.getName(), violations, identifierPattern);
          validateValue(request.getValue(), violations);
        });
  }

  public static Optional<ProblemDetail> validateTenantClusterVariableRequest(
      final String name, final String tenantId, final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateClusterVariableName(name, violations, identifierPattern);
          validateTenantId(tenantId, violations);
        });
  }

  public static Optional<ProblemDetail> validateGlobalClusterVariableRequest(
      final String name, final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateClusterVariableName(name, violations, identifierPattern);
        });
  }

  private static void validateClusterVariableName(
      final String variableName, final List<String> violations, final Pattern identifierPattern) {
    IdentifierValidator.validateId(variableName, "name", violations, identifierPattern);
  }

  private static void validateTenantId(final String id, final List<String> violations) {
    IdentifierValidator.validateTenantId(
        id, violations, TenantOwned.DEFAULT_TENANT_IDENTIFIER::equals);
  }

  private static void validateValue(final Object value, final List<String> violations) {
    if (value == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("value"));
    }
  }
}
