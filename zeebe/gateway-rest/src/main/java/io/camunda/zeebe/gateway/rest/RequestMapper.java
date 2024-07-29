/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.gateway.rest.validator.JobRequestValidator.validateJobActivationRequest;
import static io.camunda.zeebe.gateway.rest.validator.JobRequestValidator.validateJobErrorRequest;
import static io.camunda.zeebe.gateway.rest.validator.UserTaskRequestValidator.validateAssignmentRequest;
import static io.camunda.zeebe.gateway.rest.validator.UserTaskRequestValidator.validateUpdateRequest;

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
import io.camunda.zeebe.gateway.protocol.rest.JobErrorRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobFailRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public class RequestMapper {

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

  public static Either<ProblemDetail, ErrorJobRequest> toJobErrorRequest(
      final JobErrorRequest errorRequest, final long jobKey) {

    final var validationErrorResponse = validateJobErrorRequest(errorRequest);
    return validationErrorResponse
        .<Either<ProblemDetail, ErrorJobRequest>>map(Either::left)
        .orElseGet(
            () ->
                Either.right(
                    new ErrorJobRequest(
                        jobKey,
                        errorRequest.getErrorCode(),
                        getStringOrEmpty(errorRequest, JobErrorRequest::getErrorMessage),
                        getMapOrEmpty(errorRequest, JobErrorRequest::getVariables))));
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

  public record ErrorJobRequest(
      long jobKey, String errorCode, String errorMessage, Map<String, Object> variables) {}
}
