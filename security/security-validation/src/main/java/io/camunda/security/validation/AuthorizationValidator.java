/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class AuthorizationValidator {

  private final Pattern idPattern;

  public AuthorizationValidator(final Pattern idPattern) {
    this.idPattern = idPattern;
  }

  /* The validate method takes individual arguments instead of a whole object,
   * because there is currently no common model for all intended use cases of these validators.
   */
  public List<String> validate(
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final Set<PermissionType> permissions) {
    final List<String> violations = new ArrayList<>();
    // owner validation
    IdentifierValidator.validateId(ownerId, "ownerId", violations, idPattern);
    if (ownerType == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerType"));
    }

    // resource validation
    IdentifierValidator.validateId(
        resourceId, "resourceId", violations, idPattern, AuthorizationScope.WILDCARD_CHAR::equals);
    if (resourceType == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("resourceType"));
    }

    // permissions validation
    if (permissions == null || permissions.isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("permissions"));
    }
    return violations;
  }
}
