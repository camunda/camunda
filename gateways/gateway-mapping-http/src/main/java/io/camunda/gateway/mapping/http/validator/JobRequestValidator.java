/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateBusinessId;

import io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper;
import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.JobBatchUpdateRequest;
import io.camunda.gateway.protocol.model.JobChangeset;
import io.camunda.gateway.protocol.model.JobCompletionRequest;
import io.camunda.gateway.protocol.model.JobErrorRequest;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import io.camunda.gateway.protocol.model.StandaloneJobCreationRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class JobRequestValidator {

  /** The longest a client may wait for the answer to a standalone job. */
  public static final long MAX_STANDALONE_JOB_REQUEST_TIMEOUT = 60_000L;

  public static Optional<ProblemDetail> validateJobActivationRequest(
      final JobActivationRequest activationRequest) {
    return validate(
        violations -> {
          if (activationRequest.getType() == null || activationRequest.getType().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("type"));
          }
          if (activationRequest.getTimeout() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("timeout"));
          } else if (activationRequest.getTimeout() < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "timeout", activationRequest.getTimeout(), "greater than 0"));
          }
          if (activationRequest.getMaxJobsToActivate() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("maxJobsToActivate"));
          } else if (activationRequest.getMaxJobsToActivate() < 1) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "maxJobsToActivate", activationRequest.getTimeout(), "greater than 0"));
          }
        });
  }

  public static Optional<ProblemDetail> validateJobErrorRequest(
      final JobErrorRequest errorRequest) {
    return validate(
        violations -> {
          // errorCode can't be null or empty
          if (errorRequest.getErrorCode() == null || errorRequest.getErrorCode().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("errorCode"));
          }
        });
  }

  public static Optional<ProblemDetail> validateStandaloneJobCreationRequest(
      final StandaloneJobCreationRequest creationRequest) {
    return validate(
        violations -> {
          if (creationRequest.getType() == null || creationRequest.getType().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("type"));
          }
          final Long requestTimeout = creationRequest.getRequestTimeout();
          if (requestTimeout != null
              && (requestTimeout < 0 || requestTimeout > MAX_STANDALONE_JOB_REQUEST_TIMEOUT)) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "requestTimeout",
                    requestTimeout,
                    "between 0 and %d milliseconds".formatted(MAX_STANDALONE_JOB_REQUEST_TIMEOUT)));
          }
        });
  }

  public static Optional<ProblemDetail> validateJobCompletionRequest(
      final JobCompletionRequest completionRequest) {
    return validate(
        violations -> {
          if (completionRequest == null) {
            return;
          }
          final String businessId = completionRequest.getBusinessId();
          if (businessId == null) {
            return;
          }
          if (businessId.isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("businessId"));
          } else {
            validateBusinessId(businessId, violations);
          }
        });
  }

  public static Optional<ProblemDetail> validateJobUpdateRequest(
      final JobUpdateRequest updateRequest) {
    return validate(
        violations -> {
          final JobChangeset changeset = updateRequest.getChangeset();
          if (changeset == null
              || (changeset.getRetries() == null
                  && changeset.getTimeout() == null
                  && changeset.getPriority() == null)) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("retries", "timeout", "priority")));
          }
        });
  }

  public static Optional<ProblemDetail> validateJobBatchUpdateRequest(
      final JobBatchUpdateRequest request) {
    return validate(
        violations -> {
          final var filter = SearchQueryFilterMapper.toRequiredJobFilter(request.getFilter());
          filter.ifLeft(violations::addAll);

          final JobChangeset changeset = request.getChangeset();
          if (changeset == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("changeset"));
          } else if (changeset.getRetries() == null
              && changeset.getTimeout() == null
              && changeset.getPriority() == null) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("retries", "timeout", "priority")));
          }
        });
  }
}
