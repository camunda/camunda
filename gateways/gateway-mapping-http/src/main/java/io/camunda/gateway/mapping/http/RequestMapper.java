/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static io.camunda.gateway.mapping.http.validator.AdHocSubProcessActivityRequestValidator.validateAdHocSubProcessActivationRequest;
import static io.camunda.gateway.mapping.http.validator.ClockValidator.validateClockPinRequest;
import static io.camunda.gateway.mapping.http.validator.ConditionalEvaluationRequestValidator.validateConditionalEvaluationRequest;
import static io.camunda.gateway.mapping.http.validator.DocumentValidator.validateDocumentLinkParams;
import static io.camunda.gateway.mapping.http.validator.DocumentValidator.validateDocumentMetadata;
import static io.camunda.gateway.mapping.http.validator.ElementRequestValidator.validateVariableRequest;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.JobRequestValidator.validateJobActivationRequest;
import static io.camunda.gateway.mapping.http.validator.JobRequestValidator.validateJobErrorRequest;
import static io.camunda.gateway.mapping.http.validator.JobRequestValidator.validateJobUpdateRequest;
import static io.camunda.gateway.mapping.http.validator.MessageRequestValidator.validateMessageCorrelationRequest;
import static io.camunda.gateway.mapping.http.validator.MessageRequestValidator.validateMessagePublicationRequest;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantIds;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateCreateProcessInstanceTags;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateMigrationInstructions;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.createProblemDetail;
import static io.camunda.gateway.mapping.http.validator.ResourceRequestValidator.validateResourceDeletion;
import static io.camunda.gateway.mapping.http.validator.SignalRequestValidator.validateSignalBroadcastRequest;
import static io.camunda.gateway.mapping.http.validator.UserTaskRequestValidator.validateAssignmentRequest;
import static io.camunda.gateway.mapping.http.validator.UserTaskRequestValidator.validateUpdateRequest;
import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;
import static io.camunda.zeebe.protocol.record.value.JobResultType.AD_HOC_SUB_PROCESS;
import static io.camunda.zeebe.protocol.record.value.JobResultType.USER_TASK;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.mapping.http.validator.DocumentValidator;
import io.camunda.gateway.protocol.model.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.Changeset;
import io.camunda.gateway.protocol.model.ClockPinRequest;
import io.camunda.gateway.protocol.model.ConditionalEvaluationInstruction;
import io.camunda.gateway.protocol.model.DecisionEvaluationById;
import io.camunda.gateway.protocol.model.DecisionEvaluationByKey;
import io.camunda.gateway.protocol.model.DecisionEvaluationInstruction;
import io.camunda.gateway.protocol.model.DeleteResourceRequest;
import io.camunda.gateway.protocol.model.DirectAncestorKeyInstruction;
import io.camunda.gateway.protocol.model.DocumentLinkRequest;
import io.camunda.gateway.protocol.model.DocumentMetadata;
import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.JobCompletionRequest;
import io.camunda.gateway.protocol.model.JobErrorRequest;
import io.camunda.gateway.protocol.model.JobFailRequest;
import io.camunda.gateway.protocol.model.JobResultAdHocSubProcess;
import io.camunda.gateway.protocol.model.JobResultUserTask;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import io.camunda.gateway.protocol.model.MessageCorrelationRequest;
import io.camunda.gateway.protocol.model.MessagePublicationRequest;
import io.camunda.gateway.protocol.model.ModifyProcessInstanceVariableInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationTerminateInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveBatchOperationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationTerminateByIdInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationTerminateByKeyInstruction;
import io.camunda.gateway.protocol.model.SetVariableRequest;
import io.camunda.gateway.protocol.model.SignalBroadcastRequest;
import io.camunda.gateway.protocol.model.SourceElementIdInstruction;
import io.camunda.gateway.protocol.model.SourceElementInstanceKeyInstruction;
import io.camunda.gateway.protocol.model.TenantFilterEnum;
import io.camunda.gateway.protocol.model.UseSourceParentKeyInstruction;
import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.UserTaskCompletionRequest;
import io.camunda.gateway.protocol.model.UserTaskUpdateRequest;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest.AdHocSubProcessActivateActivityReference;
import io.camunda.service.ConditionalServices.EvaluateConditionalRequest;
import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.DocumentServices.DocumentLinkParams;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.service.ExpressionServices.ExpressionEvaluationRequest;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageServices.PublicationMessageRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.value.JobResultType;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.Either;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MultipartFile;

/**
 * This class is way too big and also way too big to be all static, causing leaky abstraction (e.g.
 * identifierPattern being passed through multiple layers to the IdentifierValidator).
 *
 * <p>As refactoring it at once would be a huge task, it should be split up and changed to be more
 * OOP piece by piece.
 *
 * <p>the split-up classes should be put in the @io.camunda.gateway.mapping.http.mapper package
 */
public class RequestMapper {

  public static final String VND_CAMUNDA_API_KEYS_STRING_JSON = "vnd.camunda.api.keys.string+json";
  public static final String MEDIA_TYPE_KEYS_STRING_VALUE =
      "application/" + VND_CAMUNDA_API_KEYS_STRING_JSON;

  private static final ObjectMapper PROTOCOL_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
        assignmentRequest.getAllowOverride().isEmpty()
            || assignmentRequest.getAllowOverride().get();

    return getResult(
        validateAssignmentRequest(assignmentRequest),
        () ->
            new AssignUserTaskRequest(
                userTaskKey,
                assignmentRequest.getAssignee().orElse(null),
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

  public static Either<ProblemDetail, Long> getPinnedEpoch(final ClockPinRequest pinRequest) {
    return getResult(validateClockPinRequest(pinRequest), pinRequest::getTimestamp);
  }

  public static Either<ProblemDetail, ActivateJobsRequest> toJobsActivationRequest(
      final JobActivationRequest activationRequest, final boolean multiTenancyEnabled) {

    final var tenantFilter = convertTenantFilter(activationRequest.getTenantFilter().orElse(null));

    // Validate job activation request
    final var jobValidationError = validateJobActivationRequest(activationRequest);
    if (jobValidationError.isPresent()) {
      return Either.left(jobValidationError.get());
    }

    // Resolve and validate tenant IDs based on filter
    final Either<ProblemDetail, List<String>> tenantIdsResult =
        resolveTenantIds(activationRequest, tenantFilter, multiTenancyEnabled);
    if (tenantIdsResult.isLeft()) {
      return Either.left(tenantIdsResult.getLeft());
    }

    return Either.right(
        buildActivateJobsRequest(activationRequest, tenantIdsResult.get(), tenantFilter));
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
      final MessageCorrelationRequest correlationRequest,
      final boolean multiTenancyEnabled,
      final int maxNameFieldLength) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                correlationRequest.getTenantId().orElse(null),
                multiTenancyEnabled,
                "Correlate Message")
            .flatMap(
                tenantId ->
                    validateMessageCorrelationRequest(correlationRequest, maxNameFieldLength)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));

    return validationResponse.map(
        tenantId ->
            new CorrelateMessageRequest(
                correlationRequest.getName(),
                correlationRequest.getCorrelationKey().orElse(null),
                correlationRequest.getVariables().orElse(null),
                tenantId));
  }

  public static CompleteJobRequest toJobCompletionRequest(
      final JobCompletionRequest completionRequest, final long jobKey) {
    return new CompleteJobRequest(
        jobKey,
        getMapOrEmpty(completionRequest, JobCompletionRequest::getVariables),
        getJobResultOrDefault(completionRequest));
  }

  public static Either<ProblemDetail, UpdateJobRequest> toJobUpdateRequest(
      final JobUpdateRequest updateRequest, final long jobKey) {
    final var validationJobUpdateResponse = validateJobUpdateRequest(updateRequest);
    return getResult(
        validationJobUpdateResponse,
        () ->
            new UpdateJobRequest(
                jobKey,
                updateRequest.getOperationReference().orElse(null),
                new UpdateJobChangeset(
                    updateRequest.getChangeset().getRetries().orElse(null),
                    updateRequest.getChangeset().getTimeout().orElse(null))));
  }

  public static Either<ProblemDetail, DocumentCreateRequest> toDocumentCreateRequest(
      final String documentId,
      final String storeId,
      final Part file,
      final DocumentMetadata metadata) {
    final InputStream inputStream;
    try {
      inputStream = file.getInputStream();
    } catch (final IOException e) {
      return Either.left(createInternalErrorProblemDetail(e, "Failed to read document content"));
    }
    final var validationResponse = validateDocumentMetadata(metadata);
    // Only build internal metadata AFTER validation succeeds
    return getResult(
        validationResponse,
        () ->
            new DocumentCreateRequest(
                documentId, storeId, inputStream, toInternalDocumentMetadata(metadata, file)));
  }

  public static Either<ProblemDetail, List<DocumentCreateRequest>> toDocumentCreateRequestBatch(
      final List<Part> parts, final String storeId, final ObjectMapper objectMapper) {
    // Delegate to new overload without metadataList (null indicates fallback to headers)
    return toDocumentCreateRequestBatch(parts, storeId, objectMapper, null);
  }

  /**
   * Backward compatible batch creation accepting an optional ordered metadata list. If {@code
   * metadataList} is provided (non-null & non-empty) it takes precedence and all per-part headers
   * (X-Document-Metadata) are ignored. Otherwise the legacy header-based extraction is used.
   */
  public static Either<ProblemDetail, List<DocumentCreateRequest>> toDocumentCreateRequestBatch(
      final List<Part> parts,
      final String storeId,
      final ObjectMapper objectMapper,
      final List<DocumentMetadata> metadataList) {

    final boolean hasList = metadataList != null && !metadataList.isEmpty();
    final boolean hasHeaderMetadata =
        parts.stream().anyMatch(part -> part.getHeader("X-Document-Metadata") != null);

    // Disallow providing both metadata sources simultaneously
    if (hasList && hasHeaderMetadata) {
      final ProblemDetail pd = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
      pd.setDetail("Specify either metadataList part or X-Document-Metadata headers, but not both");
      return Either.left(pd);
    }

    if (hasList) {
      // Size must match number of files
      if (metadataList.size() != parts.size()) {
        final ProblemDetail pd = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail(
            "metadataList length ("
                + metadataList.size()
                + ") does not match files length ("
                + parts.size()
                + ")");
        return Either.left(pd);
      }

      // Validate each metadata object
      final ProblemDetail validationErrors =
          metadataList.stream()
              .map(DocumentValidator::validateDocumentMetadata)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .reduce(
                  CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST),
                  (acc, detail) -> {
                    final String existing = acc.getDetail();
                    acc.setDetail((existing == null ? "" : existing + ". ") + detail.getDetail());
                    return acc;
                  });

      if (validationErrors.getDetail() != null) {
        return Either.left(validationErrors);
      }

      final List<DocumentCreateRequest> requests = new ArrayList<>(parts.size());
      for (int i = 0; i < parts.size(); i++) {
        final Part part = parts.get(i);
        final DocumentMetadata metadata =
            metadataList.get(i) == null ? new DocumentMetadata() : metadataList.get(i);
        final InputStream inputStream;
        try {
          inputStream = part.getInputStream();
        } catch (final IOException e) {
          return Either.left(
              createInternalErrorProblemDetail(e, "Failed to read document content"));
        }
        requests.add(
            new DocumentCreateRequest(
                null, storeId, inputStream, toInternalDocumentMetadata(metadata, part)));
      }
      return Either.right(List.copyOf(requests));
    }

    // Legacy header-based path (original implementation)
    final Map<Part, DocumentMetadata> metadataMap =
        parts.stream()
            .collect(
                Collectors.toMap(
                    part -> part,
                    part ->
                        Optional.ofNullable(part.getHeader("X-Document-Metadata"))
                            .map(
                                header -> {
                                  try {
                                    return objectMapper.readValue(header, DocumentMetadata.class);
                                  } catch (final IOException e) {
                                    throw new RuntimeException(e);
                                  }
                                })
                            .orElse(new DocumentMetadata())));

    final ProblemDetail validationErrors =
        metadataMap.values().stream()
            .map(DocumentValidator::validateDocumentMetadata)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .reduce(
                CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST),
                (acc, detail) -> {
                  final String existing = acc.getDetail();
                  acc.setDetail((existing == null ? "" : existing + ". ") + detail.getDetail());
                  return acc;
                });

    if (validationErrors.getDetail() != null) {
      return Either.left(validationErrors);
    }

    final var requests = new HashMap<Part, DocumentCreateRequest>();
    for (final var part : parts) {
      final var metadata = metadataMap.get(part);
      final InputStream inputStream;
      try {
        inputStream = part.getInputStream();
      } catch (final IOException e) {
        return Either.left(createInternalErrorProblemDetail(e, "Failed to read document content"));
      }
      requests.put(
          part,
          new DocumentCreateRequest(
              null, storeId, inputStream, toInternalDocumentMetadata(metadata, part)));
    }
    return Either.right(List.copyOf(requests.values()));
  }

  public static Either<ProblemDetail, DocumentLinkParams> toDocumentLinkParams(
      final DocumentLinkRequest documentLinkRequest) {
    return getResult(
        validateDocumentLinkParams(documentLinkRequest),
        () ->
            new DocumentLinkParams(
                Duration.ofMillis(documentLinkRequest.getTimeToLive().orElse(0L))));
  }

  public static Either<ProblemDetail, ExpressionEvaluationRequest> toExpressionEvaluationRequest(
      final String expression,
      final String tenantId,
      final Map<String, Object> variables,
      final boolean isMultiTenancyEnabled) {
    final var validator =
        validateTenantId(tenantId, isMultiTenancyEnabled, "Expression Evaluation");
    if (expression == null || expression.isBlank()) {
      return Either.left(
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("expression"),
              INVALID_ARGUMENT.name()));
    }
    return validator.map(
        validTenantId -> new ExpressionEvaluationRequest(expression, validTenantId, variables));
  }

  public static Either<ProblemDetail, DeployResourcesRequest> toDeployResourceRequest(
      final List<MultipartFile> resources,
      final String tenantId,
      final boolean multiTenancyEnabled) {
    try {
      final Either<ProblemDetail, String> validationResponse =
          validateTenantId(tenantId, multiTenancyEnabled, "Deploy Resources");
      if (validationResponse.isLeft()) {
        return Either.left(validationResponse.getLeft());
      }
      return Either.right(createDeployResourceRequest(resources, validationResponse.get()));
    } catch (final IOException e) {
      return Either.left(createInternalErrorProblemDetail(e, "Failed to read resources content"));
    }
  }

  public static Either<ProblemDetail, DeployResourcesRequest> toDeployResourceRequestFromParts(
      final List<Part> resources, final String tenantId, final boolean multiTenancyEnabled) {
    try {
      final Either<ProblemDetail, String> validationResponse =
          validateTenantId(tenantId, multiTenancyEnabled, "Deploy Resources");
      if (validationResponse.isLeft()) {
        return Either.left(validationResponse.getLeft());
      }
      final Map<String, byte[]> resourceMap = new HashMap<>();
      for (final Part resource : resources) {
        resourceMap.put(resource.getSubmittedFileName(), resource.getInputStream().readAllBytes());
      }
      return Either.right(new DeployResourcesRequest(resourceMap, validationResponse.get()));
    } catch (final IOException e) {
      return Either.left(createInternalErrorProblemDetail(e, "Failed to read resources content"));
    }
  }

  public static Either<ProblemDetail, SetVariablesRequest> toVariableRequest(
      final SetVariableRequest variableRequest, final long elementInstanceKey) {
    return getResult(
        validateVariableRequest(variableRequest),
        () ->
            new SetVariablesRequest(
                elementInstanceKey,
                variableRequest.getVariables(),
                variableRequest.getLocal().orElse(null),
                variableRequest.getOperationReference().orElse(null)));
  }

  public static Either<ProblemDetail, PublicationMessageRequest> toMessagePublicationRequest(
      final MessagePublicationRequest messagePublicationRequest,
      final boolean multiTenancyEnabled,
      final int maxNameFieldLength) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                messagePublicationRequest.getTenantId().orElse(null),
                multiTenancyEnabled,
                "Publish Message")
            .flatMap(
                tenantId ->
                    validateMessagePublicationRequest(messagePublicationRequest, maxNameFieldLength)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));

    return validationResponse.map(
        tenantId ->
            new PublicationMessageRequest(
                messagePublicationRequest.getName(),
                messagePublicationRequest.getCorrelationKey().orElse(null),
                messagePublicationRequest.getTimeToLive().orElse(null),
                getStringOrEmpty(
                    messagePublicationRequest, MessagePublicationRequest::getMessageId),
                getMapOrEmpty(messagePublicationRequest, MessagePublicationRequest::getVariables),
                tenantId));
  }

  public static Either<ProblemDetail, ResourceDeletionRequest> toResourceDeletion(
      final long resourceKey, final DeleteResourceRequest deleteRequest) {
    final Long operationReference =
        deleteRequest != null ? deleteRequest.getOperationReference().orElse(null) : null;
    final boolean deleteHistory =
        deleteRequest != null && Boolean.TRUE.equals(deleteRequest.getDeleteHistory().orElse(null));
    return getResult(
        validateResourceDeletion(deleteRequest),
        () -> new ResourceDeletionRequest(resourceKey, operationReference, deleteHistory));
  }

  public static Either<ProblemDetail, BroadcastSignalRequest> toBroadcastSignalRequest(
      final SignalBroadcastRequest request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                request.getTenantId().orElse(null), multiTenancyEnabled, "Broadcast Signal")
            .flatMap(
                tenantId ->
                    validateSignalBroadcastRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new BroadcastSignalRequest(
                request.getSignalName(), request.getVariables().orElse(null), tenantId));
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
    if (updateRequest == null || updateRequest.getChangeset().isEmpty()) {
      return record;
    }
    final Changeset changeset = updateRequest.getChangeset().get();
    if (changeset.getCandidateGroups().isPresent()) {
      record
          .setCandidateGroupsList(changeset.getCandidateGroups().get())
          .setCandidateGroupsChanged();
    }
    if (changeset.getCandidateUsers().isPresent()) {
      record.setCandidateUsersList(changeset.getCandidateUsers().get()).setCandidateUsersChanged();
    }
    if (changeset.getDueDate().isPresent()) {
      record.setDueDate(changeset.getDueDate().get()).setDueDateChanged();
    }
    if (changeset.getFollowUpDate().isPresent()) {
      record.setFollowUpDate(changeset.getFollowUpDate().get()).setFollowUpDateChanged();
    }
    if (changeset.getPriority().isPresent()) {
      record.setPriority(changeset.getPriority().get()).setPriorityChanged();
    }
    return record;
  }

  private static DocumentMetadataModel toInternalDocumentMetadata(
      final DocumentMetadata metadata, final Part file) {

    if (metadata == null) {
      return new DocumentMetadataModel(
          file.getContentType(),
          file.getSubmittedFileName(),
          null,
          file.getSize(),
          null,
          null,
          Map.of());
    }
    final OffsetDateTime expiresAt;
    final var expiresAtOpt = metadata.getExpiresAt();
    if (expiresAtOpt.isEmpty() || expiresAtOpt.get().isBlank()) {
      expiresAt = null;
    } else {
      expiresAt = OffsetDateTime.parse(expiresAtOpt.get());
    }
    final var fileName = metadata.getFileName().orElse(file.getSubmittedFileName());
    final var contentType = metadata.getContentType().orElse(file.getContentType());

    return new DocumentMetadataModel(
        contentType,
        fileName,
        expiresAt,
        file.getSize(),
        metadata.getProcessDefinitionId().orElse(null),
        KeyUtil.keyToLong(metadata.getProcessInstanceKey().orElse(null)),
        metadata.getCustomProperties().orElse(null));
  }

  private static DeployResourcesRequest createDeployResourceRequest(
      final List<MultipartFile> resources, final String tenantId) throws IOException {
    final Map<String, byte[]> resourceMap = new HashMap<>();
    for (final MultipartFile resource : resources) {
      resourceMap.put(resource.getOriginalFilename(), resource.getBytes());
    }
    return new DeployResourcesRequest(resourceMap, tenantId);
  }

  private static ProblemDetail createInternalErrorProblemDetail(
      final IOException e, final String message) {
    return GatewayErrorMapper.createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), message);
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstruction request, final boolean multiTenancyEnabled) {
    return switch (request) {
      case final ProcessInstanceCreationInstructionById req ->
          toCreateProcessInstance(req, multiTenancyEnabled);
      case final ProcessInstanceCreationInstructionByKey req ->
          toCreateProcessInstance(req, multiTenancyEnabled);
    };
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstructionById request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                request.getTenantId().orElse(null), multiTenancyEnabled, "Create Process Instance")
            .flatMap(
                tenant ->
                    validateCreateProcessInstanceTags(request.getTags().orElse(null))
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenant)));

    return validationResponse.map(
        tenantId ->
            new ProcessInstanceCreateRequest(
                -1L,
                request.getProcessDefinitionId(),
                getIntOrDefault(
                    request,
                    ProcessInstanceCreationInstructionById::getProcessDefinitionVersion,
                    -1),
                getMapOrEmpty(request, ProcessInstanceCreationInstructionById::getVariables),
                tenantId,
                request.getAwaitCompletion().orElse(null),
                request.getRequestTimeout().orElse(null),
                request.getOperationReference().orElse(null),
                request.getStartInstructions().orElse(List.of()).stream()
                    .map(
                        instruction ->
                            new ProcessInstanceCreationStartInstruction()
                                .setElementId(instruction.getElementId()))
                    .toList(),
                mapRuntimeInstructions(request.getRuntimeInstructions().orElse(null)),
                request.getFetchVariables().orElse(List.of()),
                request.getTags().orElse(null),
                request.getBusinessId().orElse(null)));
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstructionByKey request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                request.getTenantId().orElse(null), multiTenancyEnabled, "Create Process Instance")
            .flatMap(
                tenant ->
                    validateCreateProcessInstanceTags(request.getTags().orElse(null))
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenant)));

    return validationResponse.map(
        tenantId ->
            new ProcessInstanceCreateRequest(
                KeyUtil.keyToLong(request.getProcessDefinitionKey()),
                "",
                -1,
                getMapOrEmpty(request, ProcessInstanceCreationInstructionByKey::getVariables),
                tenantId,
                request.getAwaitCompletion().orElse(null),
                request.getRequestTimeout().orElse(null),
                request.getOperationReference().orElse(null),
                request.getStartInstructions().orElse(List.of()).stream()
                    .map(
                        instruction ->
                            new ProcessInstanceCreationStartInstruction()
                                .setElementId(instruction.getElementId()))
                    .toList(),
                mapRuntimeInstructions(request.getRuntimeInstructions().orElse(null)),
                request.getFetchVariables().orElse(List.of()),
                request.getTags().orElse(null),
                request.getBusinessId().orElse(null)));
  }

  private static List<ProcessInstanceCreationRuntimeInstruction> mapRuntimeInstructions(
      final List<Object> instructions) {
    if (instructions == null) {
      return List.of();
    }
    return instructions.stream()
        .map(
            instruction -> {
              final var instructionCasted =
                  PROTOCOL_MAPPER.convertValue(
                      instruction, ProcessInstanceCreationTerminateInstruction.class);
              return new ProcessInstanceCreationRuntimeInstruction()
                  .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                  .setAfterElementId(instructionCasted.getAfterElementId());
            })
        .toList();
  }

  public static Either<ProblemDetail, ProcessInstanceCancelRequest> toCancelProcessInstance(
      final long processInstanceKey, final CancelProcessInstanceRequest request) {
    final Long operationReference =
        request != null ? request.getOperationReference().orElse(null) : null;
    return Either.right(new ProcessInstanceCancelRequest(processInstanceKey, operationReference));
  }

  public static Either<ProblemDetail, ProcessInstanceMigrateRequest> toMigrateProcessInstance(
      final long processInstanceKey, final ProcessInstanceMigrationInstruction request) {
    return getResult(
        validateMigrationInstructions(request),
        () ->
            new ProcessInstanceMigrateRequest(
                processInstanceKey,
                KeyUtil.keyToLong(request.getTargetProcessDefinitionKey()),
                request.getMappingInstructions().stream()
                    .map(
                        instruction ->
                            new ProcessInstanceMigrationMappingInstruction()
                                .setSourceElementId(instruction.getSourceElementId())
                                .setTargetElementId(instruction.getTargetElementId()))
                    .toList(),
                request.getOperationReference().orElse(null)));
  }

  public static Either<ProblemDetail, ProcessInstanceFilter> toRequiredProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter request) {

    final var filter = SearchQueryFilterMapper.toRequiredProcessInstanceFilter(request);
    if (filter.isLeft()) {
      return Either.left(createProblemDetail(filter.getLeft()).get());
    }

    return Either.right(filter.get());
  }

  public static Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest>
      toProcessInstanceMigrationBatchOperationRequest(
          final ProcessInstanceMigrationBatchOperationRequest request) {
    final var filterResult = toRequiredProcessInstanceFilter(request.getFilter());
    if (filterResult.isLeft()) {
      return Either.left(filterResult.getLeft());
    }

    final var migrationPlan = request.getMigrationPlan();
    final List<String> violations = new ArrayList<>();
    if (migrationPlan == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("migrationPlan"));
      return Either.left(createProblemDetail(violations).get());
    }
    if (migrationPlan.getTargetProcessDefinitionKey() == null
        || migrationPlan.getTargetProcessDefinitionKey().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("targetProcessDefinitionKey"));
    }
    if (migrationPlan.getMappingInstructions() == null
        || migrationPlan.getMappingInstructions().isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingInstructions"));
    }
    if (!violations.isEmpty()) {
      return Either.left(createProblemDetail(violations).get());
    }

    return Either.right(
        new ProcessInstanceMigrateBatchOperationRequest(
            filterResult.get(),
            KeyUtil.keyToLong(migrationPlan.getTargetProcessDefinitionKey()),
            migrationPlan.getMappingInstructions().stream()
                .map(
                    instruction ->
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId(instruction.getSourceElementId())
                            .setTargetElementId(instruction.getTargetElementId()))
                .toList()));
  }

  public static Either<ProblemDetail, ProcessInstanceModifyRequest> toModifyProcessInstance(
      final long processInstanceKey, final ProcessInstanceModificationInstruction request) {
    return Either.right(
        new ProcessInstanceModifyRequest(
            processInstanceKey,
            mapProcessInstanceModificationActivateInstruction(
                request.getActivateInstructions().orElse(List.of())),
            mapProcessInstanceModificationMoveInstruction(
                request.getMoveInstructions().orElse(List.of())),
            request.getTerminateInstructions().orElse(List.of()).stream()
                .map(
                    instruction -> {
                      final var mappedInstruction =
                          new ProcessInstanceModificationTerminateInstruction();
                      final var typed =
                          PROTOCOL_MAPPER.convertValue(
                              instruction,
                              io.camunda.gateway.protocol.model
                                  .ProcessInstanceModificationTerminateInstruction.class);
                      if (typed
                          instanceof
                          final ProcessInstanceModificationTerminateByKeyInstruction byKey) {
                        mappedInstruction.setElementInstanceKey(
                            KeyUtil.keyToLong(byKey.getElementInstanceKey()));
                      } else {
                        mappedInstruction.setElementId(
                            ((ProcessInstanceModificationTerminateByIdInstruction) typed)
                                .getElementId());
                      }

                      return mappedInstruction;
                    })
                .toList(),
            request.getOperationReference().orElse(null)));
  }

  public static Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest>
      toProcessInstanceModifyBatchOperationRequest(
          final ProcessInstanceModificationBatchOperationRequest request) {
    final var filterResult = toRequiredProcessInstanceFilter(request.getFilter());
    if (filterResult.isLeft()) {
      return Either.left(filterResult.getLeft());
    }

    final List<String> violations = new ArrayList<>();
    if (request.getMoveInstructions() == null || request.getMoveInstructions().isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("moveInstructions"));
    }
    if (!violations.isEmpty()) {
      return Either.left(createProblemDetail(violations).get());
    }

    return Either.right(
        new ProcessInstanceModifyBatchOperationRequest(
            filterResult.get(),
            mapProcessInstanceModificationMoveBatchInstruction(request.getMoveInstructions())));
  }

  public static Either<ProblemDetail, DecisionEvaluationRequest> toEvaluateDecisionRequest(
      final DecisionEvaluationInstruction request, final boolean multiTenancyEnabled) {
    return switch (request) {
      case final DecisionEvaluationById req -> toEvaluateDecisionRequest(req, multiTenancyEnabled);
      case final DecisionEvaluationByKey req -> toEvaluateDecisionRequest(req, multiTenancyEnabled);
    };
  }

  public static Either<ProblemDetail, DecisionEvaluationRequest> toEvaluateDecisionRequest(
      final DecisionEvaluationById request, final boolean multiTenancyEnabled) {

    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
            request.getTenantId().orElse(null), multiTenancyEnabled, "Evaluate Decision");
    return validationResponse.map(
        tenantId ->
            new DecisionEvaluationRequest(
                request.getDecisionDefinitionId(),
                -1L,
                getMapOrEmpty(request, DecisionEvaluationById::getVariables),
                tenantId));
  }

  public static Either<ProblemDetail, DecisionEvaluationRequest> toEvaluateDecisionRequest(
      final DecisionEvaluationByKey request, final boolean multiTenancyEnabled) {

    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
            request.getTenantId().orElse(null), multiTenancyEnabled, "Evaluate Decision");
    return validationResponse.map(
        tenantId ->
            new DecisionEvaluationRequest(
                "",
                KeyUtil.keyToLong(request.getDecisionDefinitionKey()),
                getMapOrEmpty(request, DecisionEvaluationByKey::getVariables),
                tenantId));
  }

  public static Either<ProblemDetail, DecisionInstanceFilter> toRequiredDecisionInstanceFilter(
      final io.camunda.gateway.protocol.model.DecisionInstanceFilter request) {

    final var filter = SearchQueryFilterMapper.toRequiredDecisionInstanceFilter(request);
    if (filter.isLeft()) {
      return Either.left(createProblemDetail(filter.getLeft()).get());
    }

    return Either.right(filter.get());
  }

  public static Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest>
      toAdHocSubProcessActivateActivitiesRequest(
          final String adHocSubProcessInstanceKey,
          final AdHocSubProcessActivateActivitiesInstruction request) {
    return getResult(
        validateAdHocSubProcessActivationRequest(request),
        () ->
            new AdHocSubProcessActivateActivitiesRequest(
                Long.parseLong(adHocSubProcessInstanceKey),
                request.getElements().stream()
                    .map(
                        element ->
                            new AdHocSubProcessActivateActivityReference(
                                element.getElementId(),
                                getMapOrEmpty(element, e -> e.getVariables())))
                    .toList(),
                request.getCancelRemainingInstances().orElse(false)));
  }

  public static Either<ProblemDetail, EvaluateConditionalRequest> toEvaluateConditionalRequest(
      final ConditionalEvaluationInstruction request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                request.getTenantId().orElse(null), multiTenancyEnabled, "Evaluate Conditional")
            .flatMap(
                tenantId ->
                    validateConditionalEvaluationRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new EvaluateConditionalRequest(
                tenantId,
                request.getProcessDefinitionKey().map(KeyUtil::keyToLong).orElse(-1L),
                request.getVariables()));
  }

  private static TenantFilter convertTenantFilter(final TenantFilterEnum gatewayFilter) {
    if (gatewayFilter == null) {
      return TenantFilter.PROVIDED;
    }

    return switch (gatewayFilter) {
      case TenantFilterEnum.ASSIGNED -> TenantFilter.ASSIGNED;
      case TenantFilterEnum.PROVIDED -> TenantFilter.PROVIDED;
    };
  }

  private static Either<ProblemDetail, List<String>> resolveTenantIds(
      final JobActivationRequest activationRequest,
      final TenantFilter tenantFilter,
      final boolean multiTenancyEnabled) {

    if (tenantFilter == TenantFilter.ASSIGNED) {
      if (!multiTenancyEnabled) {
        return Either.left(
            GatewayErrorMapper.createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Expected to handle request Activate Jobs with ASSIGNED tenant filter, but multi-tenancy is disabled",
                INVALID_ARGUMENT.name()));
      }
      return Either.right(Collections.emptyList());
    }

    final List<String> providedTenantIds =
        getStringListOrEmpty(activationRequest, JobActivationRequest::getTenantIds);
    return validateTenantIds(providedTenantIds, multiTenancyEnabled, "Activate Jobs");
  }

  private static ActivateJobsRequest buildActivateJobsRequest(
      final JobActivationRequest activationRequest,
      final List<String> tenantIds,
      final TenantFilter tenantFilter) {

    return new ActivateJobsRequest(
        activationRequest.getType(),
        activationRequest.getMaxJobsToActivate(),
        tenantIds,
        tenantFilter,
        activationRequest.getTimeout(),
        getStringOrEmpty(activationRequest, JobActivationRequest::getWorker),
        getStringListOrEmpty(activationRequest, JobActivationRequest::getFetchVariable),
        getLongOrZero(activationRequest, JobActivationRequest::getRequestTimeout));
  }

  private static List<ProcessInstanceModificationActivateInstruction>
      mapProcessInstanceModificationActivateInstruction(
          final List<
                  io.camunda.gateway.protocol.model.ProcessInstanceModificationActivateInstruction>
              instructions) {
    return instructions.stream()
        .map(
            instruction -> {
              final var mappedInstruction = new ProcessInstanceModificationActivateInstruction();
              mappedInstruction
                  .setElementId(instruction.getElementId())
                  .setAncestorScopeKey(
                      getAncestorKey(instruction.getAncestorElementInstanceKey().orElse(null)));
              instruction.getVariableInstructions().orElse(List.of()).stream()
                  .map(RequestMapper::mapVariableInstruction)
                  .forEach(mappedInstruction::addVariableInstruction);
              return mappedInstruction;
            })
        .toList();
  }

  private static ProcessInstanceModificationVariableInstruction mapVariableInstruction(
      final ModifyProcessInstanceVariableInstruction variable) {
    return new ProcessInstanceModificationVariableInstruction()
        .setElementId(variable.getScopeId().orElse(null))
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variable.getVariables())));
  }

  private static Long getAncestorKey(final String ancestorElementInstanceKey) {
    if (ancestorElementInstanceKey == null) {
      return -1L;
    }
    return KeyUtil.keyToLong(ancestorElementInstanceKey);
  }

  private static List<ProcessInstanceModificationMoveInstruction>
      mapProcessInstanceModificationMoveInstruction(
          final List<io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveInstruction>
              instructions) {
    return instructions.stream()
        .map(
            instruction -> {
              final var mappedInstruction =
                  new ProcessInstanceModificationMoveInstruction()
                      .setTargetElementId(instruction.getTargetElementId());

              final var source =
                  PROTOCOL_MAPPER.convertValue(
                      instruction.getSourceElementInstruction(),
                      io.camunda.gateway.protocol.model.SourceElementInstruction.class);
              switch (source) {
                case final SourceElementIdInstruction byId ->
                    mappedInstruction.setSourceElementId(byId.getSourceElementId());
                case final SourceElementInstanceKeyInstruction byKey ->
                    mappedInstruction.setSourceElementInstanceKey(
                        KeyUtil.keyToLong(byKey.getSourceElementInstanceKey()));
                default ->
                    throw new IllegalStateException(
                        "Unexpected value: " + instruction.getSourceElementInstruction());
              }

              final var ancestorRaw = instruction.getAncestorScopeInstruction().orElse(null);
              if (ancestorRaw == null) {
                mappedInstruction.setAncestorScopeKey(-1);
              } else {
                final var ancestor =
                    PROTOCOL_MAPPER.convertValue(
                        ancestorRaw,
                        io.camunda.gateway.protocol.model.AncestorScopeInstruction.class);
                switch (ancestor) {
                  case final DirectAncestorKeyInstruction direct ->
                      mappedInstruction.setAncestorScopeKey(
                          getAncestorKey(
                              direct.getAncestorElementInstanceKey() == null
                                  ? null
                                  : String.valueOf(direct.getAncestorElementInstanceKey())));
                  case final UseSourceParentKeyInstruction sourceParent ->
                      mappedInstruction.setUseSourceParentKeyAsAncestorScopeKey(true);
                  default -> mappedInstruction.setInferAncestorScopeFromSourceHierarchy(true);
                }
              }
              instruction.getVariableInstructions().orElse(List.of()).stream()
                  .map(RequestMapper::mapVariableInstruction)
                  .forEach(mappedInstruction::addVariableInstruction);
              return mappedInstruction;
            })
        .toList();
  }

  private static List<ProcessInstanceModificationMoveInstruction>
      mapProcessInstanceModificationMoveBatchInstruction(
          final List<ProcessInstanceModificationMoveBatchOperationInstruction> instructions) {
    return instructions.stream()
        .map(
            instruction ->
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId(instruction.getSourceElementId())
                    .setTargetElementId(instruction.getTargetElementId())
                    .setInferAncestorScopeFromSourceHierarchy(true))
        .toList();
  }

  private static <R> Map<String, Object> getMapOrEmpty(
      final R request, final Function<R, Optional<Map<String, Object>>> mapExtractor) {
    return request == null ? Map.of() : mapExtractor.apply(request).orElse(Map.of());
  }

  private static JobResult getJobResultOrDefault(final JobCompletionRequest request) {
    if (request == null) {
      return new JobResult();
    }
    final var rawResult = request.getResult().orElse(null);
    if (rawResult == null) {
      return new JobResult();
    }
    final var typedResult =
        PROTOCOL_MAPPER.convertValue(rawResult, io.camunda.gateway.protocol.model.JobResult.class);
    final var type =
        switch (typedResult) {
          case final JobResultUserTask ut -> ut.getType().orElse(null);
          case final JobResultAdHocSubProcess ahsp -> ahsp.getType().orElse(null);
        };
    if (USER_TASK.getType().equals(type)) {
      return getJobResult((JobResultUserTask) typedResult);
    }
    if (AD_HOC_SUB_PROCESS.getType().equals(type)) {
      return getJobResult((JobResultAdHocSubProcess) typedResult);
    }
    throw new IllegalStateException("Unexpected value: " + type);
  }

  private static JobResult getJobResult(final JobResultUserTask result) {
    final JobResult jobResult = new JobResult();
    jobResult.setType(JobResultType.from(result.getType().orElse(null)));
    jobResult.setDenied(result.getDenied().orElse(false));
    jobResult.setDenied(getBooleanOrDefault(result, JobResultUserTask::getDenied, false));
    jobResult.setDeniedReason(getStringOrEmpty(result, JobResultUserTask::getDeniedReason));

    final var jobResultCorrections = result.getCorrections().orElse(null);
    if (jobResultCorrections == null) {
      return jobResult;
    }

    final JobResultCorrections corrections = new JobResultCorrections();
    final List<String> correctedAttributes = new ArrayList<>();

    if (jobResultCorrections.getAssignee().isPresent()) {
      corrections.setAssignee(jobResultCorrections.getAssignee().get());
      correctedAttributes.add(UserTaskRecord.ASSIGNEE);
    }
    if (jobResultCorrections.getDueDate().isPresent()) {
      corrections.setDueDate(jobResultCorrections.getDueDate().get());
      correctedAttributes.add(UserTaskRecord.DUE_DATE);
    }
    if (jobResultCorrections.getFollowUpDate().isPresent()) {
      corrections.setFollowUpDate(jobResultCorrections.getFollowUpDate().get());
      correctedAttributes.add(UserTaskRecord.FOLLOW_UP_DATE);
    }
    if (jobResultCorrections.getCandidateUsers().isPresent()) {
      corrections.setCandidateUsersList(jobResultCorrections.getCandidateUsers().get());
      correctedAttributes.add(UserTaskRecord.CANDIDATE_USERS);
    }
    if (jobResultCorrections.getCandidateGroups().isPresent()) {
      corrections.setCandidateGroupsList(jobResultCorrections.getCandidateGroups().get());
      correctedAttributes.add(UserTaskRecord.CANDIDATE_GROUPS);
    }
    if (jobResultCorrections.getPriority().isPresent()) {
      corrections.setPriority(jobResultCorrections.getPriority().get());
      correctedAttributes.add(UserTaskRecord.PRIORITY);
    }

    jobResult.setCorrections(corrections);
    jobResult.setCorrectedAttributes(correctedAttributes);
    return jobResult;
  }

  private static JobResult getJobResult(final JobResultAdHocSubProcess result) {
    final JobResult jobResult = new JobResult();
    jobResult
        .setType(JobResultType.from(result.getType().orElse(null)))
        .setCompletionConditionFulfilled(result.getIsCompletionConditionFulfilled().orElse(false))
        .setCancelRemainingInstances(result.getIsCancelRemainingInstances().orElse(false));
    result.getActivateElements().orElse(List.of()).stream()
        .map(
            element -> {
              final var activateElement =
                  new JobResultActivateElement().setElementId(element.getElementId().orElse(null));
              element
                  .getVariables()
                  .ifPresent(
                      vars ->
                          activateElement.setVariables(
                              new UnsafeBuffer(MsgPackConverter.convertToMsgPack(vars))));
              return activateElement;
            })
        .forEach(jobResult::addActivateElement);
    return jobResult;
  }

  private static <R> boolean getBooleanOrDefault(
      final R request,
      final Function<R, Optional<Boolean>> valueExtractor,
      final boolean defaultValue) {
    return request == null ? defaultValue : valueExtractor.apply(request).orElse(defaultValue);
  }

  private static <R> String getStringOrEmpty(
      final R request, final Function<R, Optional<String>> valueExtractor) {
    return request == null ? "" : valueExtractor.apply(request).orElse("");
  }

  private static <R> long getLongOrZero(
      final R request, final Function<R, Optional<Long>> valueExtractor) {
    return getLongOrDefault(request, valueExtractor, 0L);
  }

  private static <R> long getLongOrDefault(
      final R request, final Function<R, Optional<Long>> valueExtractor, final Long defaultValue) {
    return request == null ? defaultValue : valueExtractor.apply(request).orElse(defaultValue);
  }

  private static <R> List<String> getStringListOrEmpty(
      final R request, final Function<R, Optional<List<String>>> valueExtractor) {
    return request == null ? List.of() : valueExtractor.apply(request).orElse(List.of());
  }

  private static <R> int getIntOrZero(
      final R request, final Function<R, Optional<Integer>> valueExtractor) {
    return getIntOrDefault(request, valueExtractor, 0);
  }

  private static <R> int getIntOrDefault(
      final R request,
      final Function<R, Optional<Integer>> valueExtractor,
      final Integer defaultValue) {
    return request == null ? defaultValue : valueExtractor.apply(request).orElse(defaultValue);
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

  public record CompleteJobRequest(long jobKey, Map<String, Object> variables, JobResult result) {}

  public record UpdateJobRequest(
      long jobKey, Long operationReference, UpdateJobChangeset changeset) {}

  public record BroadcastSignalRequest(
      String signalName, Map<String, Object> variables, String tenantId) {}

  public record DecisionEvaluationRequest(
      String decisionId, Long decisionKey, Map<String, Object> variables, String tenantId) {}
}
