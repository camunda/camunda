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

import io.camunda.gateway.mapping.http.search.contract.generated.DocumentLinkRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DocumentMetadataContract;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class DocumentValidator {

  public static Optional<ProblemDetail> validateDocumentMetadata(
      final DocumentMetadataContract metadata) {
    if (metadata == null) {
      return Optional.empty();
    }
    return validate(
        violations -> {
          if (metadata.fileName() != null && metadata.fileName().isBlank()) {
            violations.add("The file name must not be empty, if present");
          }

          if (metadata.contentType() != null && metadata.contentType().isBlank()) {
            violations.add("The content type must not be empty, if present");
          }

          if (metadata.expiresAt() != null) {
            validateDate(metadata.expiresAt(), "expiresAt", violations);
          }
        });
  }

  public static Optional<ProblemDetail> validateDocumentLinkParams(
      final DocumentLinkRequestContract request) {
    if (request == null) {
      return Optional.empty();
    }
    return validate(
        violations -> {
          final Long timeToLive = request.timeToLive();
          if (timeToLive <= 0) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "timeToLive", timeToLive, "greater than 0"));
          }
        });
  }
}
