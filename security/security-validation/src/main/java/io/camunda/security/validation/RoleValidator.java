/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static java.util.Collections.emptyList;

import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.List;

public final class RoleValidator {

  private final IdentifierValidator identifierValidator;

  public RoleValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public List<String> validate(final String roleId, final String name) {
    final List<String> violations = new ArrayList<>();
    validateRoleId(roleId, violations);
    validateRoleName(name, violations);
    return violations;
  }

  public List<String> validateMembers(final List<String> memberIds, final EntityType memberType) {
    if (memberIds == null) {
      return emptyList();
    }
    final List<String> violations = new ArrayList<>();
    memberIds.forEach(
        memberId -> identifierValidator.validateMemberId(memberId, memberType, violations));
    return violations;
  }

  public List<String> validateMember(
      final String roleId, final String memberId, final EntityType memberType) {
    final List<String> violations = new ArrayList<>();
    validateRoleId(roleId, violations);
    identifierValidator.validateMemberId(memberId, memberType, violations);
    return violations;
  }

  private static void validateRoleName(final String name, final List<String> violations) {
    if (name == null || name.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
    }
  }

  private void validateRoleId(final String id, final List<String> violations) {
    identifierValidator.validateId(id, "roleId", violations);
  }
}
