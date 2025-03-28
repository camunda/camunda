/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_PATTERN;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_REGEX;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.MAX_LENGTH;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class MappingValidator {

  public static Optional<ProblemDetail> validateMappingRequest(
      final MappingRuleUpdateRequest request) {
    return validate(
        violations -> {
          violations.addAll(validateClaims(request.getClaimName(), request.getClaimValue()));
          violations.addAll(validateName(request.getName()));
        });
  }

  public static Optional<ProblemDetail> validateMappingRequest(
      final MappingRuleCreateRequest request) {
    return validate(
        violations -> {
          violations.addAll(validateClaims(request.getClaimName(), request.getClaimValue()));
          violations.addAll(validateName(request.getName()));
          violations.addAll(validateId(request.getId()));
        });
  }

  private static List<String> validateId(final String id) {
    final List<String> violations = new ArrayList<>();
    if (id == null || id.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("id"));
    } else if (id.length() > MAX_LENGTH) {
      violations.add(ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted("id", MAX_LENGTH));
    } else if (!ID_PATTERN.matcher(id).matches()) {
      violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("id", ID_REGEX));
    }
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
