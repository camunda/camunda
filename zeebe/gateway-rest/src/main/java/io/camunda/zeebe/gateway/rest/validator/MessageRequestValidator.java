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

import io.camunda.gateway.protocol.model.MessageCorrelationRequest;
import io.camunda.gateway.protocol.model.MessagePublicationRequest;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class MessageRequestValidator {

  public static Optional<ProblemDetail> validateMessageCorrelationRequest(
      final MessageCorrelationRequest correlationRequest) {
    return validate(
        violations -> {
          if (correlationRequest.getName() == null || correlationRequest.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("messageName"));
          }
        });
  }

  public static Optional<ProblemDetail> validateMessagePublicationRequest(
      final MessagePublicationRequest publicationRequest) {
    return validate(
        violations -> {
          if (publicationRequest.getName() == null || publicationRequest.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }
        });
  }
}
