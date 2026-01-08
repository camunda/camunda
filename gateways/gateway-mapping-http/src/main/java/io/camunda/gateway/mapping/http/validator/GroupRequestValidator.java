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

import io.camunda.gateway.protocol.model.GroupCreateRequest;
import io.camunda.gateway.protocol.model.GroupUpdateRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ProblemDetail;

public final class GroupRequestValidator {

  private GroupRequestValidator() {}

  public static Optional<ProblemDetail> validateCreateRequest(
      final GroupCreateRequest request, final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateGroupId(request.getGroupId(), violations, identifierPattern);
          validateGroupName(request.getName(), violations);
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(
      final String groupId, final GroupUpdateRequest request, final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateGroupId(groupId, violations, identifierPattern);
          validateGroupName(request.getName(), violations);
          if (request.getDescription() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("description"));
          }
        });
  }

  public static Optional<ProblemDetail> validateMemberRequest(
      final String roleId,
      final String memberId,
      final EntityType memberType,
      final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateGroupId(roleId, violations, identifierPattern);
          validateMemberId(memberId, memberType, violations, identifierPattern);
        });
  }

  private static void validateId(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern identifierPattern) {
    IdentifierValidator.validateId(id, propertyName, violations, identifierPattern);
  }

  public static void validateGroupId(
      final String id, final List<String> violations, final Pattern identifierPattern) {
    IdentifierValidator.validateId(id, "groupId", violations, identifierPattern);
  }

  private static void validateGroupName(final String name, final List<String> violations) {
    if (name == null || name.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
    }
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
}
