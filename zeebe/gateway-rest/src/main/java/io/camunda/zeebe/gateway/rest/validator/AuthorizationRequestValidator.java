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

import io.camunda.zeebe.gateway.protocol.rest.AuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ProblemDetail;

public final class AuthorizationRequestValidator {

  public static Optional<ProblemDetail> validateAuthorizationRequest(
      final AuthorizationRequest request, final Pattern idPattern) {
    return validate(
        violations -> {
          // owner validation
          IdentifierValidator.validateId(request.getOwnerId(), "ownerId", violations, idPattern);
          if (request.getOwnerType() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerType"));
          }

          // resource validation
          IdentifierValidator.validateId(
              request.getResourceId(),
              "resourceId",
              violations,
              idPattern,
              AuthorizationScope.WILDCARD_CHAR::equals);
          if (request.getResourceType() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("resourceType"));
          }

          // permissions validation
          if (request.getPermissionTypes() == null || request.getPermissionTypes().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("permissionTypes"));
          }
        });
  }
}
