/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.model.validator;

import static io.camunda.gateway.model.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.model.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.MappingRuleCreateRequest;
import io.camunda.gateway.protocol.model.MappingRuleUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ProblemDetail;

public final class MappingRuleRequestValidator {

  private MappingRuleRequestValidator() {}

  public static Optional<ProblemDetail> validateCreateRequest(
      final MappingRuleCreateRequest request, final Pattern identifierPattern) {
    return validate(
        violations -> {
          violations.addAll(validateClaims(request.getClaimName(), request.getClaimValue()));
          violations.addAll(validateName(request.getName()));
          violations.addAll(validateId(request.getMappingRuleId(), identifierPattern));
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(
      final MappingRuleUpdateRequest request) {
    return validate(
        violations -> {
          violations.addAll(validateClaims(request.getClaimName(), request.getClaimValue()));
          violations.addAll(validateName(request.getName()));
        });
  }

  private static List<String> validateId(
      final String mappingRuleId, final Pattern identifierPattern) {
    final List<String> violations = new ArrayList<>();
    IdentifierValidator.validateId(mappingRuleId, "mappingRuleId", violations, identifierPattern);
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
