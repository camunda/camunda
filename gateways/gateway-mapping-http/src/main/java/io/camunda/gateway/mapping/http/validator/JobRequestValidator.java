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
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateJobReservationToken;

import io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper;
import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.JobBatchUpdateRequest;
import io.camunda.gateway.protocol.model.JobChangeset;
import io.camunda.gateway.protocol.model.JobCompletionRequest;
import io.camunda.gateway.protocol.model.JobErrorRequest;
import io.camunda.gateway.protocol.model.JobFailRequest;
import io.camunda.gateway.protocol.model.JobReleaseRequest;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class JobRequestValidator {

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
          validateJobReservationToken(errorRequest.getJobReservationToken(), violations);
        });
  }

  public static Optional<ProblemDetail> validateJobFailRequest(final JobFailRequest failRequest) {
    return validate(
        violations -> {
          if (failRequest == null) {
            return;
          }
          validateJobReservationToken(failRequest.getJobReservationToken(), violations);
        });
  }

  public static Optional<ProblemDetail> validateJobCompletionRequest(
      final JobCompletionRequest completionRequest) {
    return validate(
        violations -> {
          if (completionRequest == null) {
            return;
          }
          validateJobReservationToken(completionRequest.getJobReservationToken(), violations);
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

  public static Optional<ProblemDetail> validateJobReleaseRequest(
      final JobReleaseRequest releaseRequest) {
    return validate(
        violations -> {
          if (releaseRequest == null || releaseRequest.getJobReservationToken() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("jobReservationToken"));
            return;
          }
          validateJobReservationToken(releaseRequest.getJobReservationToken(), violations);
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
          validateJobReservationToken(updateRequest.getJobReservationToken(), violations);
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
