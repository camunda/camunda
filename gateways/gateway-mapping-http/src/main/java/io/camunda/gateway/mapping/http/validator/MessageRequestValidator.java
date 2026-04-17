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

import io.camunda.gateway.protocol.model.MessageCorrelationRequest;
import io.camunda.gateway.protocol.model.MessagePublicationRequest;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

public final class MessageRequestValidator {

  public static Optional<ProblemDetail> validateMessageCorrelationRequest(
      final MessageCorrelationRequest correlationRequest, final int maxNameFieldLength) {
    return validate(
        violations -> {
          if (correlationRequest.getName() == null || correlationRequest.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("messageName"));
          }
          validateCorrelationKeyLength(
              correlationRequest.getCorrelationKey(), maxNameFieldLength, violations);
        });
  }

  public static Optional<ProblemDetail> validateMessagePublicationRequest(
      final MessagePublicationRequest publicationRequest, final int maxNameFieldLength) {
    return validate(
        violations -> {
          if (publicationRequest.getName() == null || publicationRequest.getName().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
          }
          validateCorrelationKeyLength(
              publicationRequest.getCorrelationKey(), maxNameFieldLength, violations);
        });
  }

  private static void validateCorrelationKeyLength(
      final @Nullable String correlationKey,
      final int maxNameFieldLength,
      final List<String> violations) {
    if (correlationKey != null && correlationKey.length() > maxNameFieldLength) {
      violations.add(
          ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted("correlationKey", maxNameFieldLength));
    }
  }
}
