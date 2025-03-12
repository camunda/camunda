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
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class MappingValidator {

  public static Optional<ProblemDetail> validateMappingRequest(
      final MappingRuleUpdateRequest request) {
    return validate(
        violations -> {
          if (request.getClaimName() == null || request.getClaimName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimName"));
          }
          if (request.getClaimValue() == null || request.getClaimValue().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimValue"));
          }
          if (request.getName() == null || request.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }
        });
  }

  public static Optional<ProblemDetail> validateMappingRequest(
      final MappingRuleCreateRequest request) {
    return validate(
        violations -> {
          if (request.getClaimName() == null || request.getClaimName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimName"));
          }
          if (request.getClaimValue() == null || request.getClaimValue().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("claimValue"));
          }
          if (request.getName() == null || request.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }
          if (request.getId() == null || request.getId().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("id"));
          } else if (request.getId().length() > MAX_LENGTH) {
            violations.add(ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted("id", MAX_LENGTH));
          } else if (!ID_PATTERN.matcher(request.getId()).matches()) {
            violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("id", ID_REGEX));
          }
        });
  }
}
