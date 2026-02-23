/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static java.util.Collections.emptyList;

import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class IdentifierValidator {

  /** Stricter validation for tenant IDs */
  public static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");

  private static final String DEFAULT_TENANT_ID = "<default>";

  private static final int MAX_LENGTH = 256;
  private static final int TENANT_ID_MAX_LENGTH = 31;

  private final Pattern idPattern;

  /** To allow for the "bring your own groups" feature, we need a separate ID pattern. */
  private final Pattern groupIdPattern;

  public IdentifierValidator(final Pattern idPattern, final Pattern groupIdPattern) {
    this.idPattern = idPattern;
    this.groupIdPattern = groupIdPattern;
  }

  public List<String> validateMembers(final List<String> memberIds, final EntityType memberType) {
    if (memberIds == null) {
      return emptyList();
    }
    final List<String> violations = new ArrayList<>();
    memberIds.forEach(memberId -> validateMemberId(memberId, memberType, violations));
    return violations;
  }

  public void validateMemberId(
      final String entityId, final EntityType entityType, final List<String> violations) {
    if (entityType == EntityType.GROUP) {
      validateGroupId(entityId, violations);
      return;
    }
    final var propertyName =
        switch (entityType) {
          case USER -> "username";
          case MAPPING_RULE -> "mappingRuleId";
          case ROLE -> "roleId";
          case CLIENT -> "clientId";
          default -> "entityId";
        };
    validateId(entityId, propertyName, violations);
  }

  public void validateId(
      final String id, final String propertyName, final List<String> violations) {
    validateId(id, propertyName, violations, s -> false);
  }

  public void validateId(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Function<String, Boolean> alternativeCheck) {
    validateIdInternal(id, propertyName, violations, idPattern, alternativeCheck, MAX_LENGTH);
  }

  public void validateTenantId(final String id, final List<String> violations) {
    validateIdInternal(
        id,
        "tenantId",
        violations,
        TENANT_ID_MASK,
        IdentifierValidator.DEFAULT_TENANT_ID::equals,
        TENANT_ID_MAX_LENGTH);
  }

  public void validateGroupId(final String id, final List<String> violations) {
    validateIdInternal(id, "groupId", violations, groupIdPattern, s -> false, MAX_LENGTH);
  }

  private static void validateIdInternal(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern idPattern,
      final Function<String, Boolean> alternativeCheck,
      final int maxLength) {
    if (id == null || id.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(propertyName));
    } else if (id.length() > maxLength) {
      violations.add(ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(propertyName, maxLength));
    } else if (!(idPattern.matcher(id).matches() || alternativeCheck.apply(id))) {
      violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted(propertyName, idPattern));
    }
  }
}
