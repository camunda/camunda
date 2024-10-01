/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateOperationReference;

import io.camunda.zeebe.gateway.protocol.rest.DeleteResourceRequest;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ResourceRequestValidator {

  public static Optional<ProblemDetail> validateResourceDeletion(
      final DeleteResourceRequest request) {
    return validate(
        violations -> {
          if (request != null) {
            validateOperationReference(request.getOperationReference(), violations);
          }
        });
  }
}
