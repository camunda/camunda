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

import io.camunda.gateway.protocol.model.AuthorizationIdBasedRequest;
import io.camunda.gateway.protocol.model.AuthorizationPropertyBasedRequest;
import io.camunda.gateway.protocol.model.OwnerTypeEnum;
import io.camunda.gateway.protocol.model.PermissionTypeEnum;
import io.camunda.gateway.protocol.model.ResourceTypeEnum;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ProblemDetail;

public final class AuthorizationRequestValidator {

  private AuthorizationRequestValidator() {
    // utility class
  }

  public static Optional<ProblemDetail> validateIdBasedRequest(
      final AuthorizationIdBasedRequest request, final Pattern idPattern) {
    return validate(
        violations -> {
          validateCommonRequestProperties(
              request.getOwnerId(),
              request.getOwnerType(),
              request.getResourceType(),
              request.getPermissionTypes(),
              idPattern,
              violations);

          // resourceId validation
          IdentifierValidator.validateId(
              request.getResourceId(),
              "resourceId",
              violations,
              idPattern,
              AuthorizationScope.WILDCARD_CHAR::equals);
        });
  }

  public static Optional<ProblemDetail> validatePropertyBasedRequest(
      final AuthorizationPropertyBasedRequest request, final Pattern idPattern) {
    return validate(
        violations -> {
          validateCommonRequestProperties(
              request.getOwnerId(),
              request.getOwnerType(),
              request.getResourceType(),
              request.getPermissionTypes(),
              idPattern,
              violations);

          // resourcePropertyName validation
          IdentifierValidator.validateId(
              request.getResourcePropertyName(), "resourcePropertyName", violations, idPattern);
        });
  }

  private static void validateCommonRequestProperties(
      final String ownerId,
      final OwnerTypeEnum ownerType,
      final ResourceTypeEnum resourceType,
      final List<PermissionTypeEnum> permissionTypes,
      final Pattern idPattern,
      final List<String> violations) {

    // owner validation
    IdentifierValidator.validateId(ownerId, "ownerId", violations, idPattern);

    // ownerType validation
    if (ownerType == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerType"));
    }

    // resourceType validation
    if (resourceType == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("resourceType"));
    }

    // permissions validation
    if (permissionTypes == null || permissionTypes.isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("permissionTypes"));
    }
  }
}
