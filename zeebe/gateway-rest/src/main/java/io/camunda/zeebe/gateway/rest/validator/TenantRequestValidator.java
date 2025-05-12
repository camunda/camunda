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

import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class TenantRequestValidator {

  private TenantRequestValidator() {}

  public static Optional<ProblemDetail> validateTenantCreateRequest(
      final TenantCreateRequest request) {
    return validate(
        violations -> {
          validateTenantId(request.getTenantId(), violations);
          validateTenantName(request.getName(), violations);
        });
  }

  public static Optional<ProblemDetail> validateTenantUpdateRequest(
      final TenantUpdateRequest request) {
    return validate(
        violations -> {
          validateTenantName(request.getName(), violations);
          validateTenantDescription(request.getDescription(), violations);
        });
  }

  public static Optional<ProblemDetail> validateMemberRequest(
      final String tenantId, final String memberId, final EntityType memberType) {
    return validate(
        violations -> {
          validateTenantId(tenantId, violations);
          validateMemberId(memberId, memberType, violations);
        });
  }

  private static void validateTenantId(final String id, final List<String> violations) {
    validateId(id, "tenantId", violations);
  }

  private static void validateId(
      final String id, final String propertyName, final List<String> violations) {
    if (id == null || id.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(propertyName));
    } else if (id.length() > MAX_LENGTH) {
      violations.add(ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(propertyName, MAX_LENGTH));
    } else if (!ID_PATTERN.matcher(id).matches()) {
      violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted(propertyName, ID_REGEX));
    }
  }

  private static void validateTenantName(final String name, final List<String> violations) {
    if (name == null || name.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
    }
  }

  private static void validateTenantDescription(
      final String description, final List<String> violations) {
    if (description == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("description"));
    }
  }

  public static void validateMemberId(
      final String entityId, final EntityType entityType, final List<String> violations) {
    switch (entityType) {
      case USER:
        validateId(entityId, "username", violations);
        break;
      case GROUP:
        validateId(entityId, "groupId", violations);
        break;
      case MAPPING:
        validateId(entityId, "mappingRuleId", violations);
        break;
      case ROLE:
        validateId(entityId, "roleId", violations);
        break;
      case APPLICATION:
        validateId(entityId, "applicationId", violations);
        break;
      default:
        validateId(entityId, "entityId", violations);
    }
  }
}
