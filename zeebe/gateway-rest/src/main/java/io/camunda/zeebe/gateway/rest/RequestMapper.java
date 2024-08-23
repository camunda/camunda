/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.gateway.rest.validator.AuthorizationRequestValidator.validateAuthorizationAssignRequest;
import static io.camunda.zeebe.gateway.rest.validator.DocumentValidator.validateDocumentMetadata;
import static io.camunda.zeebe.gateway.rest.validator.JobRequestValidator.validateJobActivationRequest;
import static io.camunda.zeebe.gateway.rest.validator.JobRequestValidator.validateJobErrorRequest;
import static io.camunda.zeebe.gateway.rest.validator.JobRequestValidator.validateJobUpdateRequest;
import static io.camunda.zeebe.gateway.rest.validator.MessageCorrelateValidator.validateMessageCorrelationRequest;
import static io.camunda.zeebe.gateway.rest.validator.MultiTenancyValidator.validateAuthorization;
import static io.camunda.zeebe.gateway.rest.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.zeebe.gateway.rest.validator.UserTaskRequestValidator.validateAssignmentRequest;
import static io.camunda.zeebe.gateway.rest.validator.UserTaskRequestValidator.validateUpdateRequest;

import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.DocumentServices.DocumentMetadataModel;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.security.auth.Authentication.Builder;
import io.camunda.zeebe.auth.api.JwtAuthorizationBuilder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationAssignRequest;
import io.camunda.zeebe.gateway.protocol.rest.Changeset;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobErrorRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobFailRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.MessageCorrelationRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public class RequestMapper {

  public static CompleteUserTaskRequest toUserTaskCompletionRequest(
      final UserTaskCompletionRequest completionRequest, final long userTaskKey) {

    return new CompleteUserTaskRequest(
        userTaskKey,
        getMapOrEmpty(completionRequest, UserTaskCompletionRequest::getVariables),
        getStringOrEmpty(completionRequest, UserTaskCompletionRequest::getAction));
  }

  public static Either<ProblemDetail, AssignUserTaskRequest> toUserTaskAssignmentRequest(
      final UserTaskAssignmentRequest assignmentRequest, final long userTaskKey) {

    final String actionValue =
        getStringOrEmpty(assignmentRequest, UserTaskAssignmentRequest::getAction);

    final boolean allowOverride =
        assignmentRequest.getAllowOverride() == null || assignmentRequest.getAllowOverride();

    return getResult(
        validateAssignmentRequest(assignmentRequest),
        () ->
            new AssignUserTaskRequest(
                userTaskKey,
                assignmentRequest.getAssignee(),
                actionValue.isBlank() ? "assign" : actionValue,
                allowOverride));
  }

  public static AssignUserTaskRequest toUserTaskUnassignmentRequest(final long userTaskKey) {
    return new AssignUserTaskRequest(userTaskKey, "", "unassign", true);
  }

  public static Either<ProblemDetail, UpdateUserTaskRequest> toUserTaskUpdateRequest(
      final UserTaskUpdateRequest updateRequest, final long userTaskKey) {

    return getResult(
        validateUpdateRequest(updateRequest),
        () ->
            new UpdateUserTaskRequest(
                userTaskKey,
                getRecordWithChangedAttributes(updateRequest),
                getStringOrEmpty(updateRequest, UserTaskUpdateRequest::getAction)));
  }

  public static Either<ProblemDetail, ActivateJobsRequest> toJobsActivationRequest(
      final JobActivationRequest activationRequest) {

    return getResult(
        validateJobActivationRequest(activationRequest),
        () ->
            new ActivateJobsRequest(
                activationRequest.getType(),
                activationRequest.getMaxJobsToActivate(),
                getStringListOrEmpty(activationRequest, JobActivationRequest::getTenantIds),
                activationRequest.getTimeout(),
                getStringOrEmpty(activationRequest, JobActivationRequest::getWorker),
                getStringListOrEmpty(activationRequest, JobActivationRequest::getFetchVariable),
                getLongOrZero(activationRequest, JobActivationRequest::getRequestTimeout)));
  }

  public static FailJobRequest toJobFailRequest(
      final JobFailRequest failRequest, final long jobKey) {

    return new FailJobRequest(
        jobKey,
        getIntOrZero(failRequest, JobFailRequest::getRetries),
        getStringOrEmpty(failRequest, JobFailRequest::getErrorMessage),
        getLongOrZero(failRequest, JobFailRequest::getRetryBackOff),
        getMapOrEmpty(failRequest, JobFailRequest::getVariables));
  }

  public static Either<ProblemDetail, ErrorJobRequest> toJobErrorRequest(
      final JobErrorRequest errorRequest, final long jobKey) {

    final var validationErrorResponse = validateJobErrorRequest(errorRequest);
    return getResult(
        validationErrorResponse,
        () ->
            new ErrorJobRequest(
                jobKey,
                errorRequest.getErrorCode(),
                getStringOrEmpty(errorRequest, JobErrorRequest::getErrorMessage),
                getMapOrEmpty(errorRequest, JobErrorRequest::getVariables)));
  }

  public static Either<ProblemDetail, CorrelateMessageRequest> toMessageCorrelationRequest(
      final MessageCorrelationRequest correlationRequest, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(correlationRequest.getTenantId(), multiTenancyEnabled, "Correlate Message")
            .flatMap(
                tenantId ->
                    validateAuthorization(tenantId, multiTenancyEnabled, "Correlate Message")
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)))
            .flatMap(
                tenantId ->
                    validateMessageCorrelationRequest(correlationRequest)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));

    return validationResponse.map(
        tenantId ->
            new CorrelateMessageRequest(
                correlationRequest.getName(),
                correlationRequest.getCorrelationKey(),
                correlationRequest.getVariables(),
                tenantId));
  }

  public static CompleteJobRequest toJobCompletionRequest(
      final JobCompletionRequest completionRequest, final long jobKey) {

    return new CompleteJobRequest(
        jobKey, getMapOrEmpty(completionRequest, JobCompletionRequest::getVariables));
  }

  public static Either<ProblemDetail, UpdateJobRequest> toJobUpdateRequest(
      final JobUpdateRequest updateRequest, final long jobKey) {
    final var validationJobUpdateResponse = validateJobUpdateRequest(updateRequest);
    return getResult(
        validationJobUpdateResponse,
        () ->
            new UpdateJobRequest(
                jobKey,
                new UpdateJobChangeset(
                    updateRequest.getChangeset().getRetries(),
                    updateRequest.getChangeset().getTimeout())));
  }

  public static Either<ProblemDetail, AuthorizationAssignRequest> toAuthorizationAssignRequest(
      final AuthorizationAssignRequest authorizationAssignRequest) {
    return getResult(
        validateAuthorizationAssignRequest(authorizationAssignRequest),
        () -> authorizationAssignRequest);
  }

  public static Either<ProblemDetail, DocumentCreateRequest> toDocumentCreateRequest(
      final String documentId,
      final String storeId,
      final MultipartFile file,
      final DocumentMetadata metadata) {
    final InputStream inputStream;
    try {
      inputStream = file.getInputStream();
    } catch (IOException e) {
      return Either.left(
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), "Failed to read document content"));
    }
    final var validationResponse = validateDocumentMetadata(metadata);
    final var internalMetadata = toInternalDocumentMetadata(metadata, file);
    return getResult(
        validationResponse,
        () -> new DocumentCreateRequest(documentId, storeId, inputStream, internalMetadata));
  }

  public static <BrokerResponseT> CompletableFuture<ResponseEntity<Object>> executeServiceMethod(
      final Supplier<CompletableFuture<BrokerResponseT>> method,
      final Function<BrokerResponseT, ResponseEntity<Object>> result) {
    return method
        .get()
        .handleAsync(
            (response, error) ->
                RestErrorMapper.getResponse(error, RestErrorMapper.DEFAULT_REJECTION_MAPPER)
                    .orElseGet(() -> result.apply(response)));
  }

  public static <BrokerResponseT>
      CompletableFuture<ResponseEntity<Object>> executeServiceMethodWithNoContentResult(
          final Supplier<CompletableFuture<BrokerResponseT>> method) {
    return RequestMapper.executeServiceMethod(
        method, ignored -> ResponseEntity.noContent().build());
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

  public static <T> Either<ProblemDetail, T> getResult(
      final Optional<ProblemDetail> error, final Supplier<T> resultSupplier) {
    return error
        .<Either<ProblemDetail, T>>map(Either::left)
        .orElseGet(() -> Either.right(resultSupplier.get()));
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
    if (changeset.getPriority() != null) {
      record.setPriority(changeset.getPriority()).setPriorityChanged();
    }
    return record;
  }

  private static DocumentMetadataModel toInternalDocumentMetadata(
      DocumentMetadata metadata, MultipartFile file) {

    if (metadata == null) {
      return new DocumentMetadataModel(
          file.getContentType(), file.getOriginalFilename(), null, Map.of());
    }
    final ZonedDateTime expiresAt;
    if (metadata.getExpiresAt() == null || metadata.getExpiresAt().isBlank()) {
      expiresAt = null;
    } else {
      expiresAt = ZonedDateTime.parse(metadata.getExpiresAt());
    }
    final var fileName =
        Optional.ofNullable(metadata.getFileName()).orElse(file.getOriginalFilename());
    final var contentType =
        Optional.ofNullable(metadata.getContentType()).orElse(file.getContentType());

    return new DocumentMetadataModel(
        contentType, fileName, expiresAt, metadata.getAdditionalProperties());
  }

  private static <R> Map<String, Object> getMapOrEmpty(
      final R request, final Function<R, Map<String, Object>> mapExtractor) {
    return request == null ? Map.of() : mapExtractor.apply(request);
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

  public record CompleteJobRequest(long jobKey, Map<String, Object> variables) {}

  public record UpdateJobRequest(long jobKey, UpdateJobChangeset changeset) {}
}
