/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.DeleteResourceRequest;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ResourceRequestValidator {

  public static Optional<ProblemDetail> validateResourceDeletion(
      final DeleteResourceRequest request) {
    return validate(
        violations -> {
          if (request != null
              && request.getOperationReference() != null
              && request.getOperationReference() < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "operationReference", request.getOperationReference(), "> 0"));
          }
        });
  }
}
