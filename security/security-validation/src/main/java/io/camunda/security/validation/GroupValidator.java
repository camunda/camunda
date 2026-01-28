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

public final class GroupValidator {

  private final IdentifierValidator identifierValidator;

  public GroupValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public List<String> validate(final String groupId, final String name) {
    final List<String> violations = new ArrayList<>();
    validateGroupId(groupId, violations);
    validateGroupName(name, violations);
    return violations;
  }

  public List<String> validateMember(
      final String roleId, final String memberId, final EntityType memberType) {
    final List<String> violations = new ArrayList<>();
    validateGroupId(roleId, violations);
    validateMemberId(memberId, memberType, violations);
    return violations;
  }

  private void validateId(
      final String id, final String propertyName, final List<String> violations) {
    identifierValidator.validateId(id, propertyName, violations);
  }

  private void validateGroupId(final String id, final List<String> violations) {
    identifierValidator.validateId(id, "groupId", violations);
  }

  private static void validateGroupName(final String name, final List<String> violations) {
    if (name == null || name.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
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
      default:
        validateId(entityId, "entityId", violations);
    }
  }
}
