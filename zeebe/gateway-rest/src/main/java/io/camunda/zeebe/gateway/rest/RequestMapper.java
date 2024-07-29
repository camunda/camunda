/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.identity.automation.usermanagement.CamundaGroup;
import io.camunda.identity.automation.usermanagement.CamundaUserWithPassword;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.security.auth.Authentication.Builder;
import io.camunda.zeebe.auth.api.JwtAuthorizationBuilder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.gateway.protocol.rest.CamundaGroupRequest;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.protocol.rest.Changeset;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobFailRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.util.Either;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class RequestMapper {

  private static final String ERROR_MESSAGE_EMPTY_ATTRIBUTE = "No %s provided";
  private static final String ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE =
      "The value for %s is '%s' but must be %s";
  private static final String ERROR_MESSAGE_DATE_PARSING =
      "The provided %s '%s' cannot be parsed as a date according to RFC 3339, section 5.6.";
  private static final String ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET =
      """
      No update data provided. Provide at least an "action" or a non-null value \
      for a supported attribute in the "changeset".""";

  public static Either<ProblemDetail, CompleteUserTaskRequest> toUserTaskCompletionRequest(
      final UserTaskCompletionRequest completionRequest, final long userTaskKey) {

    return Either.right(
        new CompleteUserTaskRequest(
            userTaskKey,
            getMapOrEmpty(completionRequest, UserTaskCompletionRequest::getVariables),
            getStringOrEmpty(completionRequest, UserTaskCompletionRequest::getAction)));
  }

  public static Either<ProblemDetail, AssignUserTaskRequest> toUserTaskAssignmentRequest(
      final UserTaskAssignmentRequest assignmentRequest, final long userTaskKey) {

    final String actionValue =
        getStringOrEmpty(assignmentRequest, UserTaskAssignmentRequest::getAction);

    final boolean allowOverride =
        assignmentRequest.getAllowOverride() == null || assignmentRequest.getAllowOverride();

    return validateAssignmentRequest(assignmentRequest)
        .<Either<ProblemDetail, AssignUserTaskRequest>>map(Either::left)
        .orElseGet(
            () ->
                Either.right(
                    new AssignUserTaskRequest(
                        userTaskKey,
                        assignmentRequest.getAssignee(),
                        actionValue.isBlank() ? "assign" : actionValue,
                        allowOverride)));
  }

  public static Either<ProblemDetail, AssignUserTaskRequest> toUserTaskUnassignmentRequest(
      final long userTaskKey) {
    return Either.right(new AssignUserTaskRequest(userTaskKey, "", "unassign", true));
  }

  public static Either<ProblemDetail, UpdateUserTaskRequest> toUserTaskUpdateRequest(
      final UserTaskUpdateRequest updateRequest, final long userTaskKey) {

    return validateUpdateRequest(updateRequest)
        .<Either<ProblemDetail, UpdateUserTaskRequest>>map(Either::left)
        .orElseGet(
            () ->
                Either.right(
                    new UpdateUserTaskRequest(
                        userTaskKey,
                        getRecordWithChangedAttributes(updateRequest),
                        getStringOrEmpty(updateRequest, UserTaskUpdateRequest::getAction))));
  }

  public static Either<ProblemDetail, ActivateJobsRequest> toJobsActivationRequest(
      final JobActivationRequest activationRequest) {

    final var validationErrorResponse = validateJobActivationRequest(activationRequest);
    return validationErrorResponse
        .<Either<ProblemDetail, ActivateJobsRequest>>map(Either::left)
        .orElseGet(
            () ->
                Either.right(
                    new ActivateJobsRequest(
                        activationRequest.getType(),
                        activationRequest.getMaxJobsToActivate(),
                        getStringListOrEmpty(activationRequest, JobActivationRequest::getTenantIds),
                        activationRequest.getTimeout(),
                        getStringOrEmpty(activationRequest, JobActivationRequest::getWorker),
                        getStringListOrEmpty(
                            activationRequest, JobActivationRequest::getFetchVariable),
                        getLongOrZero(
                            activationRequest, JobActivationRequest::getRequestTimeout))));
  }

  public static Either<ProblemDetail, FailJobRequest> toJobFailRequest(
      final JobFailRequest failRequest, final long jobKey) {

    return Either.right(
        new FailJobRequest(
            jobKey,
            getIntOrZero(failRequest, JobFailRequest::getRetries),
            getStringOrEmpty(failRequest, JobFailRequest::getErrorMessage),
            getLongOrZero(failRequest, JobFailRequest::getRetryBackOff),
            getMapOrEmpty(failRequest, JobFailRequest::getVariables)));
  }

  private static Optional<ProblemDetail> validateAssignmentRequest(
      final UserTaskAssignmentRequest assignmentRequest) {
    if (assignmentRequest.getAssignee() == null || assignmentRequest.getAssignee().isBlank()) {
      final ProblemDetail problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("assignee"),
              INVALID_ARGUMENT.name());
      return Optional.of(problemDetail);
    }
    return Optional.empty();
  }

  private static Optional<ProblemDetail> validateUpdateRequest(
      final UserTaskUpdateRequest updateRequest) {
    final List<String> violations = new ArrayList<>();
    if (updateRequest == null
        || (updateRequest.getAction() == null && isEmpty(updateRequest.getChangeset()))) {
      violations.add(ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET);
    }
    if (updateRequest != null && !isEmpty(updateRequest.getChangeset())) {
      final Changeset changeset = updateRequest.getChangeset();
      validateDate(changeset.getDueDate(), "due date", violations);
      validateDate(changeset.getFollowUpDate(), "follow-up date", violations);
    }
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        RestErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST, String.join(" ", violations), INVALID_ARGUMENT.name()));
  }

  private static Optional<ProblemDetail> validateJobActivationRequest(
      final JobActivationRequest activationRequest) {
    final List<String> violations = new ArrayList<>();
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
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        RestErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST, String.join(". ", violations), INVALID_ARGUMENT.name()));
  }

  private static void validateDate(
      final String dateString, final String attributeName, final List<String> violations) {
    if (dateString != null && !dateString.isEmpty()) {
      try {
        ZonedDateTime.parse(dateString);
      } catch (final DateTimeParseException ex) {
        violations.add(ERROR_MESSAGE_DATE_PARSING.formatted(attributeName, dateString));
      }
    }
  }

  private static boolean isEmpty(final Changeset changeset) {
    return changeset == null
        || (changeset.getFollowUpDate() == null
            && changeset.getDueDate() == null
            && changeset.getCandidateGroups() == null
            && changeset.getCandidateUsers() == null);
  }

  public static CompletableFuture<ResponseEntity<Object>> executeServiceMethod(
      final Supplier<CompletableFuture<?>> method, final Supplier<ResponseEntity<Object>> result) {
    return method
        .get()
        .handleAsync(
            (response, error) ->
                RestErrorMapper.getResponse(error, RestErrorMapper.DEFAULT_REJECTION_MAPPER)
                    .orElseGet(result));
  }

  public static CompletableFuture<ResponseEntity<Object>> executeServiceMethodWithNoContenResult(
      final Supplier<CompletableFuture<?>> method) {
    return RequestMapper.executeServiceMethod(method, () -> ResponseEntity.noContent().build());
  }

  public static Authentication getAuthentication() {
    final List<String> authorizedTenants = TenantAttributeHolder.tenantIds();

    final String token =
        Authorization.jwtEncoder()
            .withIssuer(JwtAuthorizationBuilder.DEFAULT_ISSUER)
            .withAudience(JwtAuthorizationBuilder.DEFAULT_AUDIENCE)
            .withSubject(JwtAuthorizationBuilder.DEFAULT_SUBJECT)
            .withClaim(Authorization.AUTHORIZED_TENANTS, authorizedTenants)
            .encode();
    return new Builder().token(token).tenants(authorizedTenants).build();
  }

  private static UserTaskRecord getRecordWithChangedAttributes(
      final UserTaskUpdateRequest updateRequest) {
    final var record = new UserTaskRecord();
    if (updateRequest == null || updateRequest.getChangeset() == null) {
      return record;
    }
    final Changeset changeset = updateRequest.getChangeset();
    if (changeset.getCandidateGroups() != null) {
      record.setCandidateGroupsList(changeset.getCandidateGroups()).setCandidateGroupsChanged();
    }
    if (changeset.getCandidateUsers() != null) {
      record.setCandidateUsersList(changeset.getCandidateUsers()).setCandidateUsersChanged();
    }
    if (changeset.getDueDate() != null) {
      record.setDueDate(changeset.getDueDate()).setDueDateChanged();
    }
    if (changeset.getFollowUpDate() != null) {
      record.setFollowUpDate(changeset.getFollowUpDate()).setFollowUpDateChanged();
    }
    return record;
  }

  public static CamundaUserWithPassword toUserWithPassword(
      final CamundaUserWithPasswordRequest dto) {
    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();

    camundaUserWithPassword.setId(dto.getId());
    camundaUserWithPassword.setUsername(dto.getUsername());
    camundaUserWithPassword.setPassword(dto.getPassword());
    camundaUserWithPassword.setName(dto.getName());
    camundaUserWithPassword.setEmail(dto.getEmail());
    camundaUserWithPassword.setEnabled(dto.getEnabled());

    return camundaUserWithPassword;
  }

  public static CamundaGroup toGroup(final CamundaGroupRequest groupRequest) {
    return new CamundaGroup(groupRequest.getId(), groupRequest.getName());
  }

  private static <R> Map<String, Object> getMapOrEmpty(
      final R request, final Function<R, Map<String, Object>> variablesExtractor) {
    return request == null ? Map.of() : variablesExtractor.apply(request);
  }

  private static <R> String getStringOrEmpty(
      final R request, final Function<R, String> valueExtractor) {
    final String value = request == null ? null : valueExtractor.apply(request);
    return value == null ? "" : value;
  }

  private static <R> long getLongOrZero(final R request, final Function<R, Long> valueExtractor) {
    final Long value = request == null ? null : valueExtractor.apply(request);
    return value == null ? 0L : value;
  }

  private static <R> List<String> getStringListOrEmpty(
      final R request, final Function<R, List<String>> valueExtractor) {
    final List<String> value = request == null ? null : valueExtractor.apply(request);
    return value == null ? List.of() : value;
  }

  private static <R> int getIntOrZero(final R request, final Function<R, Integer> valueExtractor) {
    final Integer value = request == null ? null : valueExtractor.apply(request);
    return value == null ? 0 : value;
  }

  public record CompleteUserTaskRequest(
      long userTaskKey, Map<String, Object> variables, String action) {}

  public record UpdateUserTaskRequest(long userTaskKey, UserTaskRecord changeset, String action) {}

  public record AssignUserTaskRequest(
      long userTaskKey, String assignee, String action, boolean allowOverride) {}

  public record FailJobRequest(
      long jobKey,
      int retries,
      String errorMessage,
      Long retryBackoff,
      Map<String, Object> variables) {}
}
