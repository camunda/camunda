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

import io.camunda.gateway.protocol.model.RoleCreateRequest;
import io.camunda.gateway.protocol.model.RoleUpdateRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ProblemDetail;

public final class RoleRequestValidator {
  private RoleRequestValidator() {}

  public static Optional<ProblemDetail> validateCreateRequest(
      final RoleCreateRequest request, final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateRoleId(request.getRoleId(), violations, identifierPattern);
          validateRoleName(request.getName(), violations);
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(final RoleUpdateRequest request) {
    return validate(
        violations -> {
          validateRoleName(request.getName(), violations);
          validateRoleDescription(request.getDescription(), violations);
        });
  }

  public static Optional<ProblemDetail> validateMemberRequest(
      final String roleId,
      final String memberId,
      final EntityType memberType,
      final Pattern roleIdentifierPattern,
      final Pattern memberIdentifierPattern) {
    return validate(
        violations -> {
          validateRoleId(roleId, violations, roleIdentifierPattern);
          validateMemberId(memberId, memberType, violations, memberIdentifierPattern);
        });
  }

  private static void validateRoleName(final String name, final List<String> violations) {
    if (name == null || name.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
    }
  }

  private static void validateId(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern identifierPattern) {
    IdentifierValidator.validateId(id, propertyName, violations, identifierPattern);
  }

  private static void validateRoleId(
      final String id, final List<String> violations, final Pattern identifierPattern) {
    validateId(id, "roleId", violations, identifierPattern);
  }

  private static void validateMemberId(
      final String entityId,
      final EntityType entityType,
      final List<String> violations,
      final Pattern identifierPattern) {
    switch (entityType) {
      case USER:
        validateId(entityId, "username", violations, identifierPattern);
        break;
      case GROUP:
        validateId(entityId, "groupId", violations, identifierPattern);
        break;
      case MAPPING_RULE:
        validateId(entityId, "mappingRuleId", violations, identifierPattern);
        break;
      default:
        validateId(entityId, "entityId", violations, identifierPattern);
    }
  }

  private static void validateRoleDescription(
      final String description, final List<String> violations) {
    if (description == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("description"));
    }
  }
}
