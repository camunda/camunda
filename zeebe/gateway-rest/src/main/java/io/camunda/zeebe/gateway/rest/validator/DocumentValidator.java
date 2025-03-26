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
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateDate;

import io.camunda.zeebe.gateway.protocol.rest.DocumentDetails;
import io.camunda.zeebe.gateway.protocol.rest.DocumentLinkRequest;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class DocumentValidator {

  public static Optional<ProblemDetail> validateDocumentMetadata(final DocumentDetails metadata) {
    if (metadata == null) {
      return Optional.empty();
    }
    return validate(
        violations -> {
          if (metadata.getFileName() != null && metadata.getFileName().isBlank()) {
            violations.add("The file name must not be empty, if present");
          }

          if (metadata.getContentType() != null && metadata.getContentType().isBlank()) {
            violations.add("The content type must not be empty, if present");
          }

          if (metadata.getExpiresAt() != null) {
            validateDate(metadata.getExpiresAt(), "expiresAt", violations);
          }
        });
  }

  public static Optional<ProblemDetail> validateDocumentLinkParams(
      final DocumentLinkRequest request) {
    if (request == null) {
      return Optional.empty();
    }
    return validate(
        violations -> {
          final Long timeToLive = request.getTimeToLive();
          if (timeToLive <= 0) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "timeToLive", timeToLive, "greater than 0"));
          }
        });
  }
}
