/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.createProblemDetail;

import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class AuthorizationRequestValidator {
  public static Optional<ProblemDetail> validateAuthorizationAssignRequest(
      final AuthorizationPatchRequest authorizationPatchRequest) {
    final List<String> violations = new ArrayList<>();
    if (authorizationPatchRequest.getOwnerKey() == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerKey"));
    }

    if (authorizationPatchRequest.getOwnerType() == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerType"));
    }

    if (authorizationPatchRequest.getResourceKey() == null
        || authorizationPatchRequest.getResourceKey().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("resourceKey"));
    }

    if (authorizationPatchRequest.getResourceType() == null
        || authorizationPatchRequest.getResourceType().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("resourceType"));
    }

    return createProblemDetail(violations);
  }
}
