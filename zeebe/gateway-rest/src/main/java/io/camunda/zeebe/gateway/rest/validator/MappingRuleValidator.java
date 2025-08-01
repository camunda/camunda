/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_PATTERN;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class MappingRuleValidator {

  public static Optional<ProblemDetail> validateMappingRuleRequest(
      final MappingRuleUpdateRequest request) {
    return validate(
        violations -> {
          violations.addAll(validateClaims(request.getClaimName(), request.getClaimValue()));
          violations.addAll(validateName(request.getName()));
        });
  }

  public static Optional<ProblemDetail> validateMappingRuleRequest(
      final MappingRuleCreateRequest request) {
    return validate(
        violations -> {
          violations.addAll(validateClaims(request.getClaimName(), request.getClaimValue()));
          violations.addAll(validateName(request.getName()));
          violations.addAll(validateId(request.getMappingRuleId()));
        });
  }

  private static List<String> validateId(final String mappingRuleId) {
    final List<String> violations = new ArrayList<>();
    IdentifierValidator.validateId(mappingRuleId, "mappingRuleId", violations, ID_PATTERN);
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
