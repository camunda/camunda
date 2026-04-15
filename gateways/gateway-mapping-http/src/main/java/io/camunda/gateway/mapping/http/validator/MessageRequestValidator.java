/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.mapping.http.search.contract.generated.MessageCorrelationRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MessagePublicationRequestContract;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class MessageRequestValidator {

  public static Optional<ProblemDetail> validatePublicationRequest(
      final MessagePublicationRequestContract request, final int maxNameFieldLength) {
    return validate(
        violations -> {
          if (request.name() == null || request.name().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }
          if (request.correlationKey() != null
              && request.correlationKey().length() > maxNameFieldLength) {
            violations.add(
                ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted("correlationKey", maxNameFieldLength));
          }
        });
  }

  public static Optional<ProblemDetail> validateCorrelationRequest(
      final MessageCorrelationRequestContract request, final int maxNameFieldLength) {
    return validate(
        violations -> {
          if (request.name() == null || request.name().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("messageName"));
          }
          if (request.correlationKey() != null
              && request.correlationKey().length() > maxNameFieldLength) {
            violations.add(
                ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted("correlationKey", maxNameFieldLength));
          }
        });
  }
}
