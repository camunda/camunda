/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.security.validation.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AuthorizationValidator {

  private static final String ERROR_MUTUALLY_EXCLUSIVE_IDENTIFIERS =
      "resourceId and resourcePropertyName are mutually exclusive. Provide only one of them";
  private static final String ERROR_MISSING_IDENTIFIER =
      "Either resourceId or resourcePropertyName must be provided";

  private final IdentifierValidator identifierValidator;

  public AuthorizationValidator(final IdentifierValidator identifierValidator) {
    this.identifierValidator = identifierValidator;
  }

  /**
   * Validates authorization properties. Determines the authorization type (ID-based vs
   * property-based) and validates accordingly.
   *
   * <p>As enum values are only checked for null-values and there are different enum types that
   * might be checked, they are typed as Enum<?>.
   *
   * @param ownerId the owner identifier
   * @param ownerType the owner type enum
   * @param resourceType the resource type enum
   * @param resourceId the resource identifier (mutually exclusive with resourcePropertyName)
   * @param resourcePropertyName the resource property name (mutually exclusive with resourceId)
   * @param permissions the set of permissions
   * @return a list of validation violations, empty if validation passes
   */
  public List<String> validate(
      final String ownerId,
      final Enum<?> ownerType,
      final Enum<?> resourceType,
      final String resourceId,
      final String resourcePropertyName,
      final Set<? extends Enum<?>> permissions) {

    // Validate common properties
    final var violations =
        new ArrayList<>(validateCommonProperties(ownerId, ownerType, resourceType, permissions));

    // Check for mutually exclusive identifiers
    final boolean hasResourceId = resourceId != null && !resourceId.isBlank();
    final boolean hasPropertyName = resourcePropertyName != null && !resourcePropertyName.isBlank();

    if (hasResourceId && hasPropertyName) {
      violations.add(ERROR_MUTUALLY_EXCLUSIVE_IDENTIFIERS);
      return violations;
    }

    if (!hasResourceId && !hasPropertyName) {
      violations.add(ERROR_MISSING_IDENTIFIER);
      return violations;
    }

    // Validate type-specific properties
    if (hasResourceId) {
      identifierValidator.validateId(
          resourceId, "resourceId", violations, AuthorizationScope.WILDCARD_CHAR::equals);
    } else {
      identifierValidator.validateId(resourcePropertyName, "resourcePropertyName", violations);
    }

    return violations;
  }

  /* The validate method takes individual arguments instead of a whole object,
   * because there is currently no common model for all intended use cases of these validators.
   */
  private List<String> validateCommonProperties(
      final String ownerId,
      final Enum<?> ownerType,
      final Enum<?> resourceType,
      final Set<? extends Enum<?>> permissions) {
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
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("permissionTypes"));
    }
    return violations;
  }
}
