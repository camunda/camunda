/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateProcessDefinitionId;

import io.camunda.gateway.protocol.model.DocumentLinkRequest;
import io.camunda.gateway.protocol.model.DocumentMetadata;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class DocumentValidator {

  public static Optional<ProblemDetail> validateDocumentMetadata(final DocumentMetadata metadata) {
    if (metadata == null) {
      return Optional.empty();
    }
    return validate(
        violations -> {
          if (metadata.getFileName().isPresent() && metadata.getFileName().get().isBlank()) {
            violations.add("The file name must not be empty, if present");
          }

          if (metadata.getContentType().isPresent() && metadata.getContentType().get().isBlank()) {
            violations.add("The content type must not be empty, if present");
          }

          if (metadata.getExpiresAt().isPresent()) {
            validateDate(metadata.getExpiresAt().get(), "expiresAt", violations);
          }

          validateProcessDefinitionId(metadata.getProcessDefinitionId().orElse(null), violations);
        });
  }

  public static Optional<ProblemDetail> validateDocumentLinkParams(
      final DocumentLinkRequest request) {
    if (request == null) {
      return Optional.empty();
    }
    return validate(
        violations -> {
          final Long timeToLive = request.getTimeToLive().orElse(0L);
          if (timeToLive <= 0) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "timeToLive", timeToLive, "greater than 0"));
          }
        });
  }
}
