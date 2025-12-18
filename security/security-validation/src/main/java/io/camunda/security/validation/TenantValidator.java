/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.List;

public class TenantValidator {

  public static final String DEFAULT_TENANT_ID = "<default>";

  private final IdentifierValidator identifierValidator;

  public TenantValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public List<String> validate(final String tenantId, final String name) {
    final List<String> violations = new ArrayList<>();
    validateTenantId(tenantId, violations);
    validateTenantName(name, violations);
    return violations;
  }

  private void validateTenantId(final String id, final List<String> violations) {
    identifierValidator.validateTenantId(id, violations, DEFAULT_TENANT_ID::equals);
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

  private void validateMemberId(
      final String entityId, final EntityType entityType, final List<String> violations) {
    switch (entityType) {
      case USER:
        validateId(entityId, "username", violations);
        break;
      case GROUP:
        validateId(entityId, "groupId", violations);
        break;
      case MAPPING_RULE:
        validateId(entityId, "mappingRuleId", violations);
        break;
      case ROLE:
        validateId(entityId, "roleId", violations);
        break;
      case CLIENT:
        validateId(entityId, "clientId", violations);
        break;
      default:
        validateId(entityId, "entityId", violations);
    }
  }

  private void validateId(
      final String id, final String propertyName, final List<String> violations) {
    identifierValidator.validateId(id, propertyName, violations);
  }
}
