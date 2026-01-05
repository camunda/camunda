/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.TenantCreateRequest;
import io.camunda.gateway.protocol.model.TenantUpdateRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ProblemDetail;

public final class TenantRequestValidator {

  private TenantRequestValidator() {}

  public static Optional<ProblemDetail> validateCreateRequest(final TenantCreateRequest request) {
    return validate(
        violations -> {
          validateTenantId(request.getTenantId(), violations);
          validateTenantName(request.getName(), violations);
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(final TenantUpdateRequest request) {
    return validate(
        violations -> {
          validateTenantName(request.getName(), violations);
          validateTenantDescription(request.getDescription(), violations);
        });
  }

  public static Optional<ProblemDetail> validateMemberRequest(
      final String tenantId,
      final String memberId,
      final EntityType memberType,
      final Pattern identifierPattern) {
    return validate(
        violations -> {
          validateTenantId(tenantId, violations);
          validateMemberId(memberId, memberType, violations, identifierPattern);
        });
  }

  private static void validateTenantId(final String id, final List<String> violations) {
    IdentifierValidator.validateTenantId(
        id, violations, TenantOwned.DEFAULT_TENANT_IDENTIFIER::equals);
  }

  private static void validateId(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern identifierPattern) {
    IdentifierValidator.validateId(id, propertyName, violations, identifierPattern);
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
      case ROLE:
        validateId(entityId, "roleId", violations, identifierPattern);
        break;
      case CLIENT:
        validateId(entityId, "clientId", violations, identifierPattern);
        break;
      default:
        validateId(entityId, "entityId", violations, identifierPattern);
    }
  }
}
