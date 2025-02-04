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
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class AuthorizationRequestValidator {

  public static Optional<ProblemDetail> validateAuthorizationRequest(
      final AuthorizationRequest request) {
    return validate(
        violations -> {
          // owner validation
          if (request.getOwnerId() == null || request.getOwnerId().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerId"));
          }
          if (request.getOwnerType() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerType"));
          }

          // resource validation
          if (request.getResourceId() == null || request.getResourceId().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("resourceId"));
          }
          if (request.getResourceType() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("resourceType"));
          }

          // permissions validation
          if (request.getPermissions() == null || request.getPermissions().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("permissions"));
          }
        });
  }
}
