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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AuthorizationValidator {

  private final IdentifierValidator identifierValidator;

  public AuthorizationValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  public List<String> validatePropertyBased(
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final String resourcePropertyName,
      final Set<?> permissions) {
    final List<String> violations =
        validateCommonProperties(ownerId, ownerType, resourceType, permissions);
    identifierValidator.validateId(resourcePropertyName, "resourcePropertyName", violations);
    return violations;
  }

  public List<String> validateIdBased(
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final Set<?> permissions) {
    final List<String> violations =
        validateCommonProperties(ownerId, ownerType, resourceType, permissions);
    identifierValidator.validateId(
        resourceId, "resourceId", violations, AuthorizationScope.WILDCARD_CHAR::equals);
    return violations;
  }

  /* The validate method takes individual arguments instead of a whole object,
   * because there is currently no common model for all intended use cases of these validators.
   */
  private List<String> validateCommonProperties(
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final Set<?> permissions) {
    final List<String> violations = new ArrayList<>();
    // owner validation
    identifierValidator.validateId(ownerId, "ownerId", violations);
    if (ownerType == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerType"));
    }

    // resource validation
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
