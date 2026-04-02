/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static io.camunda.gateway.mapping.http.validator.AdHocSubProcessActivityRequestValidator.validateAdHocSubProcessActivationRequest;
import static io.camunda.gateway.mapping.http.validator.ConditionalEvaluationRequestValidator.validateConditionalEvaluationRequest;
import static io.camunda.gateway.mapping.http.validator.DocumentValidator.validateDocumentLinkParams;
import static io.camunda.gateway.mapping.http.validator.DocumentValidator.validateDocumentMetadata;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.JobRequestValidator.validateJobActivationRequest;
import static io.camunda.gateway.mapping.http.validator.JobRequestValidator.validateJobErrorRequest;
import static io.camunda.gateway.mapping.http.validator.JobRequestValidator.validateJobUpdateRequest;
import static io.camunda.gateway.mapping.http.validator.MessageRequestValidator.validateMessageCorrelationRequest;
import static io.camunda.gateway.mapping.http.validator.MessageRequestValidator.validateMessagePublicationRequest;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantIds;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateCancelProcessInstanceRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateCreateProcessInstanceTags;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateMigrateProcessInstanceRequest;
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
import io.camunda.gateway.mapping.http.search.contract.DecisionInstanceFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCancelProcessInstanceRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedConditionalEvaluationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionEvaluationByIdStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionEvaluationByKeyStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionEvaluationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeleteResourceRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentLinkRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentMetadataStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobActivationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobCompletionRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobErrorRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobFailRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessageCorrelationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessagePublicationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedModifyProcessInstanceVariableInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCreationInstructionByIdStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCreationInstructionByKeyStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCreationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceMigrationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceModificationActivateInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceModificationBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceModificationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceModificationMoveInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSignalBroadcastRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAssignmentRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskCompletionRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.mapping.http.validator.DocumentValidator;
import io.camunda.gateway.protocol.model.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.Changeset;
import io.camunda.gateway.protocol.model.ConditionalEvaluationInstruction;
import io.camunda.gateway.protocol.model.DeleteResourceRequest;
import io.camunda.gateway.protocol.model.DocumentLinkRequest;
import io.camunda.gateway.protocol.model.DocumentMetadata;
import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.JobCompletionRequest;
import io.camunda.gateway.protocol.model.JobErrorRequest;
import io.camunda.gateway.protocol.model.JobResultAdHocSubProcess;
import io.camunda.gateway.protocol.model.JobResultUserTask;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import io.camunda.gateway.protocol.model.MessageCorrelationRequest;
import io.camunda.gateway.protocol.model.MessagePublicationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationTerminateInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.SignalBroadcastRequest;
import io.camunda.gateway.protocol.model.TenantFilterEnum;
import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.UserTaskUpdateRequest;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest.AdHocSubProcessActivateActivityReference;
import io.camunda.service.ConditionalServices.EvaluateConditionalRequest;
import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.DocumentServices.DocumentLinkParams;
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

  // Jackson converter for types where strict-contract fields are untyped (Object / LinkedHashMap)
  // and the protocol model relies on registered deserializers (polymorphic filter properties,
  // oneOf sub-types). Used only for filter schemas and deeply nested polymorphic instructions.
  private static final ObjectMapper PROTOCOL_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @SuppressWarnings("unchecked")
  private static <T> T convertToProtocol(final Object value, final Class<T> type) {
    return value == null ? null : PROTOCOL_MAPPER.convertValue(value, type);
  }

  public static final String VND_CAMUNDA_API_KEYS_STRING_JSON = "vnd.camunda.api.keys.string+json";
  public static final String MEDIA_TYPE_KEYS_STRING_VALUE =
      "application/" + VND_CAMUNDA_API_KEYS_STRING_JSON;

  public static Either<ProblemDetail, AssignUserTaskRequest> toUserTaskAssignmentRequest(
      final io.camunda.gateway.protocol.model.simple.UserTaskAssignmentRequest assignmentRequest,
      final long userTaskKey) {

    return toUserTaskAssignmentRequest(
        new UserTaskAssignmentRequest()
            .action(assignmentRequest.getAction())
            .allowOverride(assignmentRequest.getAllowOverride())
            .assignee(assignmentRequest.getAssignee()),
        userTaskKey);
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
      final JobActivationRequest activationRequest, final boolean multiTenancyEnabled) {

    final var tenantFilter = convertTenantFilter(activationRequest.getTenantFilter());

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
        validateTenantId(correlationRequest.getTenantId(), multiTenancyEnabled, "Correlate Message")
            .flatMap(
                tenantId ->
                    validateMessageCorrelationRequest(correlationRequest, maxNameFieldLength)
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
                updateRequest.getOperationReference(),
                new UpdateJobChangeset(
                    updateRequest.getChangeset().getRetries(),
                    updateRequest.getChangeset().getTimeout())));
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
        () -> new DocumentLinkParams(Duration.ofMillis(documentLinkRequest.getTimeToLive())));
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

  public static Either<ProblemDetail, PublicationMessageRequest> toMessagePublicationRequest(
      final MessagePublicationRequest messagePublicationRequest,
      final boolean multiTenancyEnabled,
      final int maxNameFieldLength) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                messagePublicationRequest.getTenantId(), multiTenancyEnabled, "Publish Message")
            .flatMap(
                tenantId ->
                    validateMessagePublicationRequest(messagePublicationRequest, maxNameFieldLength)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));

    return validationResponse.map(
        tenantId ->
            new PublicationMessageRequest(
                messagePublicationRequest.getName(),
                messagePublicationRequest.getCorrelationKey(),
                messagePublicationRequest.getTimeToLive(),
                getStringOrEmpty(
                    messagePublicationRequest, MessagePublicationRequest::getMessageId),
                getMapOrEmpty(messagePublicationRequest, MessagePublicationRequest::getVariables),
                tenantId));
  }

  public static Either<ProblemDetail, ResourceDeletionRequest> toResourceDeletion(
      final long resourceKey, final DeleteResourceRequest deleteRequest) {
    final Long operationReference =
        deleteRequest != null ? deleteRequest.getOperationReference() : null;
    final boolean deleteHistory =
        deleteRequest != null && Boolean.TRUE.equals(deleteRequest.getDeleteHistory());
    return getResult(
        validateResourceDeletion(deleteRequest),
        () -> new ResourceDeletionRequest(resourceKey, operationReference, deleteHistory));
  }

  public static Either<ProblemDetail, BroadcastSignalRequest> toBroadcastSignalRequest(
      final SignalBroadcastRequest request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.getTenantId(), multiTenancyEnabled, "Broadcast Signal")
            .flatMap(
                tenantId ->
                    validateSignalBroadcastRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new BroadcastSignalRequest(request.getSignalName(), request.getVariables(), tenantId));
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
    if (metadata.getExpiresAt() == null || metadata.getExpiresAt().isBlank()) {
      expiresAt = null;
    } else {
      expiresAt = OffsetDateTime.parse(metadata.getExpiresAt());
    }
    final var fileName =
        Optional.ofNullable(metadata.getFileName()).orElse(file.getSubmittedFileName());
    final var contentType =
        Optional.ofNullable(metadata.getContentType()).orElse(file.getContentType());

    return new DocumentMetadataModel(
        contentType,
        fileName,
        expiresAt,
        file.getSize(),
        metadata.getProcessDefinitionId(),
        KeyUtil.keyToLong(metadata.getProcessInstanceKey()),
        metadata.getCustomProperties());
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
      default ->
          Either.left(
              GatewayErrorMapper.createProblemDetail(
                  HttpStatus.BAD_REQUEST,
                  "Unsupported process instance creation instruction type: "
                      + request.getClass().getSimpleName(),
                  "Only process instance creation by id or key is supported."));
    };
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstructionById request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.getTenantId(), multiTenancyEnabled, "Create Process Instance")
            .flatMap(
                tenant ->
                    validateCreateProcessInstanceRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenant)));

    return validationResponse.map(
        tenantId ->
            new ProcessInstanceCreateRequest(
                -1L,
                getStringOrEmpty(
                    request, ProcessInstanceCreationInstructionById::getProcessDefinitionId),
                getIntOrDefault(
                    request,
                    ProcessInstanceCreationInstructionById::getProcessDefinitionVersion,
                    -1),
                getMapOrEmpty(request, ProcessInstanceCreationInstructionById::getVariables),
                tenantId,
                request.getAwaitCompletion(),
                request.getRequestTimeout(),
                request.getOperationReference(),
                request.getStartInstructions().stream()
                    .map(
                        instruction ->
                            new ProcessInstanceCreationStartInstruction()
                                .setElementId(instruction.getElementId()))
                    .toList(),
                request.getRuntimeInstructions().stream()
                    .map(
                        instruction -> {
                          final var instructionCasted =
                              (ProcessInstanceCreationTerminateInstruction) instruction;
                          return new ProcessInstanceCreationRuntimeInstruction()
                              .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                              .setAfterElementId(instructionCasted.getAfterElementId());
                        })
                    .toList(),
                request.getFetchVariables(),
                request.getTags(),
                request.getBusinessId()));
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstructionByKey request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.getTenantId(), multiTenancyEnabled, "Create Process Instance")
            .flatMap(
                tenant ->
                    validateCreateProcessInstanceRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenant)));

    return validationResponse.map(
        tenantId ->
            new ProcessInstanceCreateRequest(
                getKeyOrDefault(
                    request, ProcessInstanceCreationInstructionByKey::getProcessDefinitionKey, -1L),
                "",
                -1,
                getMapOrEmpty(request, ProcessInstanceCreationInstructionByKey::getVariables),
                tenantId,
                request.getAwaitCompletion(),
                request.getRequestTimeout(),
                request.getOperationReference(),
                request.getStartInstructions().stream()
                    .map(
                        instruction ->
                            new ProcessInstanceCreationStartInstruction()
                                .setElementId(instruction.getElementId()))
                    .toList(),
                request.getRuntimeInstructions().stream()
                    .map(
                        instruction -> {
                          final var instructionCasted =
                              (ProcessInstanceCreationTerminateInstruction) instruction;
                          return new ProcessInstanceCreationRuntimeInstruction()
                              .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                              .setAfterElementId(instructionCasted.getAfterElementId());
                        })
                    .toList(),
                request.getFetchVariables(),
                request.getTags(),
                request.getBusinessId()));
  }

  // --- Strict contract overloads for process instance creation ---

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final GeneratedProcessInstanceCreationInstructionStrictContract request,
      final boolean multiTenancyEnabled) {
    return switch (request) {
      case GeneratedProcessInstanceCreationInstructionByIdStrictContract req ->
          toCreateProcessInstance(req, multiTenancyEnabled);
      case GeneratedProcessInstanceCreationInstructionByKeyStrictContract req ->
          toCreateProcessInstance(req, multiTenancyEnabled);
    };
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final GeneratedProcessInstanceCreationInstructionByIdStrictContract request,
      final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.tenantId(), multiTenancyEnabled, "Create Process Instance")
            .flatMap(
                tenant ->
                    validateCreateProcessInstanceTags(request.tags())
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenant)));

    return validationResponse.map(
        tenantId ->
            new ProcessInstanceCreateRequest(
                -1L,
                request.processDefinitionId() != null ? request.processDefinitionId() : "",
                request.processDefinitionVersion() != null
                    ? request.processDefinitionVersion()
                    : -1,
                request.variables() != null ? request.variables() : Map.of(),
                tenantId,
                request.awaitCompletion(),
                request.requestTimeout(),
                request.operationReference(),
                request.startInstructions() != null
                    ? request.startInstructions().stream()
                        .map(
                            si ->
                                new ProcessInstanceCreationStartInstruction()
                                    .setElementId(si.elementId()))
                        .toList()
                    : List.of(),
                mapStrictRuntimeInstructions(request.runtimeInstructions()),
                request.fetchVariables() != null ? request.fetchVariables() : List.of(),
                request.tags(),
                request.businessId()));
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final GeneratedProcessInstanceCreationInstructionByKeyStrictContract request,
      final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.tenantId(), multiTenancyEnabled, "Create Process Instance")
            .flatMap(
                tenant ->
                    validateCreateProcessInstanceTags(request.tags())
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenant)));

    return validationResponse.map(
        tenantId ->
            new ProcessInstanceCreateRequest(
                request.processDefinitionKey() != null
                    ? Long.parseLong(request.processDefinitionKey())
                    : -1L,
                "",
                -1,
                request.variables() != null ? request.variables() : Map.of(),
                tenantId,
                request.awaitCompletion(),
                request.requestTimeout(),
                request.operationReference(),
                request.startInstructions() != null
                    ? request.startInstructions().stream()
                        .map(
                            si ->
                                new ProcessInstanceCreationStartInstruction()
                                    .setElementId(si.elementId()))
                        .toList()
                    : List.of(),
                mapStrictRuntimeInstructions(request.runtimeInstructions()),
                request.fetchVariables() != null ? request.fetchVariables() : List.of(),
                request.tags(),
                request.businessId()));
  }

  @SuppressWarnings("unchecked")
  private static List<ProcessInstanceCreationRuntimeInstruction> mapStrictRuntimeInstructions(
      final List<Object> runtimeInstructions) {
    if (runtimeInstructions == null) {
      return List.of();
    }
    return runtimeInstructions.stream()
        .map(
            obj -> {
              final var map = (Map<String, Object>) obj;
              return new ProcessInstanceCreationRuntimeInstruction()
                  .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                  .setAfterElementId((String) map.get("afterElementId"));
            })
        .toList();
  }

  public static Either<ProblemDetail, ProcessInstanceCancelRequest> toCancelProcessInstance(
      final long processInstanceKey, final CancelProcessInstanceRequest request) {
    final Long operationReference = request != null ? request.getOperationReference() : null;
    return getResult(
        validateCancelProcessInstanceRequest(request),
        () -> new ProcessInstanceCancelRequest(processInstanceKey, operationReference));
  }

  public static Either<ProblemDetail, ProcessInstanceMigrateRequest> toMigrateProcessInstance(
      final long processInstanceKey, final ProcessInstanceMigrationInstruction request) {
    return getResult(
        validateMigrateProcessInstanceRequest(request),
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
                request.getOperationReference()));
  }

  // --- Strict contract overloads for decision evaluation ---

  public static Either<ProblemDetail, DecisionEvaluationRequest> toEvaluateDecisionRequest(
      final GeneratedDecisionEvaluationInstructionStrictContract request,
      final boolean multiTenancyEnabled) {
    return switch (request) {
      case GeneratedDecisionEvaluationByIdStrictContract req ->
          toEvaluateDecisionRequest(req, multiTenancyEnabled);
      case GeneratedDecisionEvaluationByKeyStrictContract req ->
          toEvaluateDecisionRequest(req, multiTenancyEnabled);
    };
  }

  public static Either<ProblemDetail, DecisionEvaluationRequest> toEvaluateDecisionRequest(
      final GeneratedDecisionEvaluationByIdStrictContract request,
      final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.tenantId(), multiTenancyEnabled, "Evaluate Decision");
    return validationResponse.map(
        tenantId ->
            new DecisionEvaluationRequest(
                request.decisionDefinitionId() != null ? request.decisionDefinitionId() : "",
                -1L,
                request.variables() != null ? request.variables() : Map.of(),
                tenantId));
  }

  public static Either<ProblemDetail, DecisionEvaluationRequest> toEvaluateDecisionRequest(
      final GeneratedDecisionEvaluationByKeyStrictContract request,
      final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.tenantId(), multiTenancyEnabled, "Evaluate Decision");
    return validationResponse.map(
        tenantId ->
            new DecisionEvaluationRequest(
                "",
                request.decisionDefinitionKey() != null
                    ? Long.parseLong(request.decisionDefinitionKey())
                    : -1L,
                request.variables() != null ? request.variables() : Map.of(),
                tenantId));
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
                request.getCancelRemainingInstances() != null
                    && request.getCancelRemainingInstances()));
  }

  public static Either<ProblemDetail, EvaluateConditionalRequest> toEvaluateConditionalRequest(
      final ConditionalEvaluationInstruction request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.getTenantId(), multiTenancyEnabled, "Evaluate Conditional")
            .flatMap(
                tenantId ->
                    validateConditionalEvaluationRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new EvaluateConditionalRequest(
                tenantId,
                getKeyOrDefault(
                    request, ConditionalEvaluationInstruction::getProcessDefinitionKey, -1L),
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

  private static Long getAncestorKey(final String ancestorElementInstanceKey) {
    if (ancestorElementInstanceKey == null) {
      return -1L;
    }
    return KeyUtil.keyToLong(ancestorElementInstanceKey);
  }

  private static <R> Map<String, Object> getMapOrEmpty(
      final R request, final Function<R, Map<String, Object>> mapExtractor) {
    final Map<String, Object> value = request == null ? null : mapExtractor.apply(request);
    return value == null ? Map.of() : value;
  }

  private static JobResult getJobResultOrDefault(final JobCompletionRequest request) {
    if (request == null || request.getResult() == null) {
      return new JobResult();
    }
    final var type = request.getResult().getType();
    if (USER_TASK.getType().equals(type)) {
      return getJobResult((JobResultUserTask) request.getResult());
    }
    if (AD_HOC_SUB_PROCESS.getType().equals(type)) {
      return getJobResult((JobResultAdHocSubProcess) request.getResult());
    }
    throw new IllegalStateException("Unexpected value: " + type);
  }

  private static JobResult getJobResult(final JobResultUserTask result) {
    final JobResult jobResult = new JobResult();
    jobResult.setType(JobResultType.from(result.getType()));
    jobResult.setDenied(result.getDenied() != null ? result.getDenied() : false);
    jobResult.setDenied(getBooleanOrDefault(result, JobResultUserTask::getDenied, false));
    jobResult.setDeniedReason(getStringOrEmpty(result, JobResultUserTask::getDeniedReason));

    final var jobResultCorrections = result.getCorrections();
    if (jobResultCorrections == null) {
      return jobResult;
    }

    final JobResultCorrections corrections = new JobResultCorrections();
    final List<String> correctedAttributes = new ArrayList<>();

    if (jobResultCorrections.getAssignee() != null) {
      corrections.setAssignee(jobResultCorrections.getAssignee());
      correctedAttributes.add(UserTaskRecord.ASSIGNEE);
    }
    if (jobResultCorrections.getDueDate() != null) {
      corrections.setDueDate(jobResultCorrections.getDueDate());
      correctedAttributes.add(UserTaskRecord.DUE_DATE);
    }
    if (jobResultCorrections.getFollowUpDate() != null) {
      corrections.setFollowUpDate(jobResultCorrections.getFollowUpDate());
      correctedAttributes.add(UserTaskRecord.FOLLOW_UP_DATE);
    }
    if (jobResultCorrections.getCandidateUsers() != null) {
      corrections.setCandidateUsersList(jobResultCorrections.getCandidateUsers());
      correctedAttributes.add(UserTaskRecord.CANDIDATE_USERS);
    }
    if (jobResultCorrections.getCandidateGroups() != null) {
      corrections.setCandidateGroupsList(jobResultCorrections.getCandidateGroups());
      correctedAttributes.add(UserTaskRecord.CANDIDATE_GROUPS);
    }
    if (jobResultCorrections.getPriority() != null) {
      corrections.setPriority(jobResultCorrections.getPriority());
      correctedAttributes.add(UserTaskRecord.PRIORITY);
    }

    jobResult.setCorrections(corrections);
    jobResult.setCorrectedAttributes(correctedAttributes);
    return jobResult;
  }

  private static JobResult getJobResult(final JobResultAdHocSubProcess result) {
    final JobResult jobResult = new JobResult();
    jobResult
        .setType(JobResultType.from(result.getType()))
        .setCompletionConditionFulfilled(result.getIsCompletionConditionFulfilled())
        .setCancelRemainingInstances(result.getIsCancelRemainingInstances());
    result.getActivateElements().stream()
        .map(
            element -> {
              final var activateElement =
                  new JobResultActivateElement().setElementId(element.getElementId());
              if (element.getVariables() != null) {
                activateElement.setVariables(
                    new UnsafeBuffer(MsgPackConverter.convertToMsgPack(element.getVariables())));
              }
              return activateElement;
            })
        .forEach(jobResult::addActivateElement);
    return jobResult;
  }

  private static <R> boolean getBooleanOrDefault(
      final R request, final Function<R, Boolean> valueExtractor, final boolean defaultValue) {
    final Boolean value = request == null ? null : valueExtractor.apply(request);
    return value == null ? defaultValue : value;
  }

  private static <R> String getStringOrEmpty(
      final R request, final Function<R, String> valueExtractor) {
    final String value = request == null ? null : valueExtractor.apply(request);
    return value == null ? "" : value;
  }

  private static <R> long getLongOrZero(final R request, final Function<R, Long> valueExtractor) {
    return getLongOrDefault(request, valueExtractor, 0L);
  }

  private static <R> long getLongOrDefault(
      final R request, final Function<R, Long> valueExtractor, final Long defaultValue) {
    final Long value = request == null ? null : valueExtractor.apply(request);
    return value == null ? defaultValue : value;
  }

  private static <R> long getKeyOrDefault(
      final R request, final Function<R, String> valueExtractor, final Long defaultValue) {
    final String value = request == null ? null : valueExtractor.apply(request);
    return value == null ? defaultValue : Long.parseLong(value);
  }

  private static <R> List<String> getStringListOrEmpty(
      final R request, final Function<R, List<String>> valueExtractor) {
    final List<String> value = request == null ? null : valueExtractor.apply(request);
    return value == null ? List.of() : value;
  }

  private static <R> int getIntOrZero(final R request, final Function<R, Integer> valueExtractor) {
    return getIntOrDefault(request, valueExtractor, 0);
  }

  private static <R> int getIntOrDefault(
      final R request, final Function<R, Integer> valueExtractor, final Integer defaultValue) {
    final Integer value = request == null ? null : valueExtractor.apply(request);
    return value == null ? defaultValue : value;
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

  // ---- Strict contract methods (direct field access) ----

  public static CompleteUserTaskRequest toUserTaskCompletionRequest(
      final GeneratedUserTaskCompletionRequestStrictContract request, final long userTaskKey) {
    return new CompleteUserTaskRequest(
        userTaskKey,
        request == null || request.variables() == null ? Map.of() : request.variables(),
        request == null || request.action() == null ? "" : request.action());
  }

  public static Either<ProblemDetail, AssignUserTaskRequest> toUserTaskAssignmentRequest(
      final GeneratedUserTaskAssignmentRequestStrictContract request, final long userTaskKey) {
    final var assignmentRequest =
        new UserTaskAssignmentRequest()
            .assignee(request.assignee())
            .allowOverride(request.allowOverride())
            .action(request.action());
    return toUserTaskAssignmentRequest(assignmentRequest, userTaskKey);
  }

  public static Either<ProblemDetail, UpdateUserTaskRequest> toUserTaskUpdateRequest(
      final GeneratedUserTaskUpdateRequestStrictContract request, final long userTaskKey) {
    final var updateRequest =
        new UserTaskUpdateRequest().action(request != null ? request.action() : null);
    if (request != null && request.changeset() != null) {
      final var cs = request.changeset();
      updateRequest.changeset(
          new Changeset()
              .dueDate(cs.dueDate())
              .followUpDate(cs.followUpDate())
              .candidateUsers(cs.candidateUsers())
              .candidateGroups(cs.candidateGroups())
              .priority(cs.priority()));
    }
    return toUserTaskUpdateRequest(updateRequest, userTaskKey);
  }

  public static Either<ProblemDetail, ActivateJobsRequest> toJobsActivationRequest(
      final GeneratedJobActivationRequestStrictContract request,
      final boolean multiTenancyEnabled) {
    final var activationRequest =
        new JobActivationRequest()
            .type(request.type())
            .worker(request.worker())
            .timeout(request.timeout())
            .maxJobsToActivate(request.maxJobsToActivate())
            .fetchVariable(request.fetchVariable())
            .requestTimeout(request.requestTimeout())
            .tenantIds(request.tenantIds())
            .tenantFilter(
                request.tenantFilter() != null
                    ? TenantFilterEnum.fromValue(request.tenantFilter().getValue())
                    : null);
    return toJobsActivationRequest(activationRequest, multiTenancyEnabled);
  }

  public static FailJobRequest toJobFailRequest(
      final GeneratedJobFailRequestStrictContract request, final long jobKey) {
    return new FailJobRequest(
        jobKey,
        request == null || request.retries() == null ? 0 : request.retries(),
        request == null || request.errorMessage() == null ? "" : request.errorMessage(),
        request == null || request.retryBackOff() == null ? 0L : request.retryBackOff(),
        request == null || request.variables() == null ? Map.of() : request.variables());
  }

  public static Either<ProblemDetail, ErrorJobRequest> toJobErrorRequest(
      final GeneratedJobErrorRequestStrictContract request, final long jobKey) {
    final var errorRequest =
        new JobErrorRequest()
            .errorCode(request.errorCode())
            .errorMessage(request.errorMessage())
            .variables(request.variables());
    return toJobErrorRequest(errorRequest, jobKey);
  }

  public static CompleteJobRequest toJobCompletionRequest(
      final GeneratedJobCompletionRequestStrictContract request, final long jobKey) {
    // JobResult is polymorphic (oneOf: JobResultUserTask, JobResultAdHocSubProcess) with
    // custom deserialization. The strict contract holds it as Object (LinkedHashMap at runtime).
    // Construct the protocol model to reuse existing polymorphic deserialization logic.
    final var completionRequest = convertToProtocol(request, JobCompletionRequest.class);
    return toJobCompletionRequest(completionRequest, jobKey);
  }

  public static Either<ProblemDetail, UpdateJobRequest> toJobUpdateRequest(
      final GeneratedJobUpdateRequestStrictContract request, final long jobKey) {
    final var updateRequest =
        new JobUpdateRequest()
            .operationReference(request.operationReference())
            .changeset(
                new io.camunda.gateway.protocol.model.JobChangeset()
                    .retries(request.changeset().retries())
                    .timeout(request.changeset().timeout()));
    return toJobUpdateRequest(updateRequest, jobKey);
  }

  public static Either<ProblemDetail, BroadcastSignalRequest> toBroadcastSignalRequest(
      final GeneratedSignalBroadcastRequestStrictContract request,
      final boolean multiTenancyEnabled) {
    final var signalRequest =
        new SignalBroadcastRequest()
            .signalName(request.signalName())
            .variables(request.variables())
            .tenantId(request.tenantId());
    return toBroadcastSignalRequest(signalRequest, multiTenancyEnabled);
  }

  public static Either<ProblemDetail, ResourceDeletionRequest> toResourceDeletion(
      final long resourceKey, final GeneratedDeleteResourceRequestStrictContract request) {
    final Long operationReference = request != null ? request.operationReference() : null;
    final boolean deleteHistory = request != null && Boolean.TRUE.equals(request.deleteHistory());
    return getResult(
        validateResourceDeletion(
            request != null
                ? new DeleteResourceRequest()
                    .operationReference(operationReference)
                    .deleteHistory(deleteHistory)
                : null),
        () -> new ResourceDeletionRequest(resourceKey, operationReference, deleteHistory));
  }

  public static Either<ProblemDetail, DocumentCreateRequest> toDocumentCreateRequest(
      final String documentId,
      final String storeId,
      final Part file,
      final GeneratedDocumentMetadataStrictContract metadata) {
    final var docMetadata =
        metadata != null
            ? new DocumentMetadata()
                .contentType(metadata.contentType())
                .fileName(metadata.fileName())
                .expiresAt(metadata.expiresAt())
                .size(metadata.size())
                .processDefinitionId(metadata.processDefinitionId())
                .processInstanceKey(metadata.processInstanceKey())
                .customProperties(metadata.customProperties())
            : null;
    return toDocumentCreateRequest(documentId, storeId, file, docMetadata);
  }

  public static Either<ProblemDetail, List<DocumentCreateRequest>>
      toDocumentCreateRequestBatchStrict(
          final List<Part> parts,
          final String storeId,
          final ObjectMapper objectMapper,
          final List<GeneratedDocumentMetadataStrictContract> metadataList) {
    return toDocumentCreateRequestBatch(
        parts,
        storeId,
        objectMapper,
        metadataList == null
            ? null
            : metadataList.stream()
                .map(
                    m ->
                        m == null
                            ? null
                            : new DocumentMetadata()
                                .contentType(m.contentType())
                                .fileName(m.fileName())
                                .expiresAt(m.expiresAt())
                                .size(m.size())
                                .processDefinitionId(m.processDefinitionId())
                                .processInstanceKey(m.processInstanceKey())
                                .customProperties(m.customProperties()))
                .toList());
  }

  public static Either<ProblemDetail, DocumentLinkParams> toDocumentLinkParams(
      final GeneratedDocumentLinkRequestStrictContract request) {
    return getResult(
        validateDocumentLinkParams(
            request != null ? new DocumentLinkRequest().timeToLive(request.timeToLive()) : null),
        () ->
            new DocumentLinkParams(
                request != null ? Duration.ofMillis(request.timeToLive()) : Duration.ZERO));
  }

  public static Either<ProblemDetail, ProcessInstanceCancelRequest> toCancelProcessInstance(
      final long processInstanceKey,
      final GeneratedCancelProcessInstanceRequestStrictContract request) {
    final Long operationReference = request != null ? request.operationReference() : null;
    return getResult(
        validateCancelProcessInstanceRequest(
            request != null
                ? new CancelProcessInstanceRequest().operationReference(operationReference)
                : null),
        () -> new ProcessInstanceCancelRequest(processInstanceKey, operationReference));
  }

  public static Either<ProblemDetail, io.camunda.search.filter.ProcessInstanceFilter>
      toRequiredProcessInstanceFilter(final GeneratedProcessInstanceFilterStrictContract request) {
    final var filter = SearchQueryFilterMapper.toRequiredProcessInstanceFilter(request);
    if (filter.isLeft()) {
      return Either.left(createProblemDetail(filter.getLeft()).get());
    }
    return Either.right(filter.get());
  }

  public static Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest>
      toProcessInstanceMigrationBatchOperationRequest(
          final GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract request) {
    // Validate and map filter using strict contract mapper (avoids convertToProtocol)
    final var filterResult = toRequiredProcessInstanceFilter(request.filter());
    if (filterResult.isLeft()) {
      return Either.left(filterResult.getLeft());
    }

    final var migPlan = request.migrationPlan();
    final List<String> violations = new ArrayList<>();
    if (migPlan == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("migrationPlan"));
      return Either.left(createProblemDetail(violations).get());
    }
    if (migPlan.targetProcessDefinitionKey() == null
        || migPlan.targetProcessDefinitionKey().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("targetProcessDefinitionKey"));
    }
    if (migPlan.mappingInstructions() == null || migPlan.mappingInstructions().isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingInstructions"));
    }
    if (!violations.isEmpty()) {
      return Either.left(createProblemDetail(violations).get());
    }

    return Either.right(
        new ProcessInstanceMigrateBatchOperationRequest(
            filterResult.get(),
            KeyUtil.keyToLong(migPlan.targetProcessDefinitionKey()),
            migPlan.mappingInstructions().stream()
                .map(
                    mi ->
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId(mi.sourceElementId())
                            .setTargetElementId(mi.targetElementId()))
                .toList()));
  }

  public static Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest>
      toProcessInstanceModifyBatchOperationRequest(
          final GeneratedProcessInstanceModificationBatchOperationRequestStrictContract request) {
    // Validate and map filter using strict contract mapper (avoids convertToProtocol)
    final var filterResult = toRequiredProcessInstanceFilter(request.filter());
    if (filterResult.isLeft()) {
      return Either.left(filterResult.getLeft());
    }

    final List<String> violations = new ArrayList<>();
    if (request.moveInstructions() == null || request.moveInstructions().isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("moveInstructions"));
    }
    if (!violations.isEmpty()) {
      return Either.left(createProblemDetail(violations).get());
    }

    return Either.right(
        new ProcessInstanceModifyBatchOperationRequest(
            filterResult.get(),
            request.moveInstructions().stream()
                .map(
                    mi ->
                        new ProcessInstanceModificationMoveInstruction()
                            .setSourceElementId(mi.sourceElementId())
                            .setTargetElementId(mi.targetElementId())
                            .setInferAncestorScopeFromSourceHierarchy(true))
                .toList()));
  }

  public static Either<ProblemDetail, ProcessInstanceMigrateRequest> toMigrateProcessInstance(
      final long processInstanceKey,
      final GeneratedProcessInstanceMigrationInstructionStrictContract request) {
    final var protoRequest =
        new ProcessInstanceMigrationInstruction()
            .targetProcessDefinitionKey(request.targetProcessDefinitionKey())
            .operationReference(request.operationReference())
            .mappingInstructions(
                request.mappingInstructions().stream()
                    .map(
                        mi ->
                            new io.camunda.gateway.protocol.model
                                    .MigrateProcessInstanceMappingInstruction()
                                .sourceElementId(mi.sourceElementId())
                                .targetElementId(mi.targetElementId()))
                    .toList());
    return toMigrateProcessInstance(processInstanceKey, protoRequest);
  }

  public static Either<ProblemDetail, ProcessInstanceModifyRequest> toModifyProcessInstance(
      final long processInstanceKey,
      final GeneratedProcessInstanceModificationInstructionStrictContract request) {
    final List<String> violations = new ArrayList<>();
    final var activateInstructions =
        mapActivateInstructionsFromContract(request.activateInstructions());
    final var moveInstructions =
        mapMoveInstructionsFromContract(request.moveInstructions(), violations);
    final var terminateInstructions =
        mapTerminateInstructionsFromContract(request.terminateInstructions(), violations);
    return getResult(
        createProblemDetail(violations),
        () ->
            new ProcessInstanceModifyRequest(
                processInstanceKey,
                activateInstructions,
                moveInstructions,
                terminateInstructions,
                request.operationReference()));
  }

  private static List<ProcessInstanceModificationActivateInstruction>
      mapActivateInstructionsFromContract(
          final List<GeneratedProcessInstanceModificationActivateInstructionStrictContract>
              instructions) {
    if (instructions == null) {
      return List.of();
    }
    return instructions.stream()
        .map(
            instruction -> {
              final var mapped = new ProcessInstanceModificationActivateInstruction();
              mapped
                  .setElementId(instruction.elementId())
                  .setAncestorScopeKey(getAncestorKey(instruction.ancestorElementInstanceKey()));
              if (instruction.variableInstructions() != null) {
                instruction.variableInstructions().stream()
                    .map(RequestMapper::mapVariableInstructionFromContract)
                    .forEach(mapped::addVariableInstruction);
              }
              return mapped;
            })
        .toList();
  }

  private static ProcessInstanceModificationVariableInstruction mapVariableInstructionFromContract(
      final GeneratedModifyProcessInstanceVariableInstructionStrictContract variable) {
    return new ProcessInstanceModificationVariableInstruction()
        .setElementId(variable.scopeId() != null ? variable.scopeId() : "")
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variable.variables())));
  }

  private static List<ProcessInstanceModificationMoveInstruction> mapMoveInstructionsFromContract(
      final List<GeneratedProcessInstanceModificationMoveInstructionStrictContract> instructions,
      final List<String> violations) {
    if (instructions == null) {
      return List.of();
    }
    return instructions.stream()
        .map(
            instruction -> {
              final var mapped =
                  new ProcessInstanceModificationMoveInstruction()
                      .setTargetElementId(instruction.targetElementId());
              if (instruction.sourceElementInstruction() instanceof final Map<?, ?> sourceMap) {
                if (sourceMap.containsKey("sourceElementId")) {
                  mapped.setSourceElementId(String.valueOf(sourceMap.get("sourceElementId")));
                } else if (sourceMap.containsKey("sourceElementInstanceKey")) {
                  mapped.setSourceElementInstanceKey(
                      KeyUtil.keyToLong(String.valueOf(sourceMap.get("sourceElementInstanceKey"))));
                }
              }
              mapAncestorScopeFromContract(
                  instruction.ancestorScopeInstruction(), mapped, violations);
              if (instruction.variableInstructions() != null) {
                instruction.variableInstructions().stream()
                    .map(RequestMapper::mapVariableInstructionFromContract)
                    .forEach(mapped::addVariableInstruction);
              }
              return mapped;
            })
        .toList();
  }

  private static void mapAncestorScopeFromContract(
      final Object ancestorScope,
      final ProcessInstanceModificationMoveInstruction mapped,
      final List<String> violations) {
    if (ancestorScope == null) {
      mapped.setAncestorScopeKey(-1);
    } else if (ancestorScope instanceof final Map<?, ?> ancestorMap) {
      final var scopeType =
          ancestorMap.containsKey("ancestorScopeType")
              ? String.valueOf(ancestorMap.get("ancestorScopeType"))
              : "";
      switch (scopeType) {
        case "direct" ->
            mapped.setAncestorScopeKey(
                getAncestorKey(
                    ancestorMap.containsKey("ancestorElementInstanceKey")
                        ? String.valueOf(ancestorMap.get("ancestorElementInstanceKey"))
                        : null));
        case "sourceParent" -> mapped.setUseSourceParentKeyAsAncestorScopeKey(true);
        case "inferred" -> mapped.setInferAncestorScopeFromSourceHierarchy(true);
        default ->
            violations.add(
                ("Cannot map value '%s' for type 'ancestorScopeInstruction'. "
                        + "Use any of the following values: [direct, inferred, sourceParent].")
                    .formatted(scopeType));
      }
    }
  }

  private static List<ProcessInstanceModificationTerminateInstruction>
      mapTerminateInstructionsFromContract(
          final List<Object> instructions, final List<String> violations) {
    if (instructions == null) {
      return List.of();
    }
    return instructions.stream()
        .map(
            obj -> {
              final var mapped = new ProcessInstanceModificationTerminateInstruction();
              if (obj instanceof final Map<?, ?> map) {
                final boolean hasElementId = map.containsKey("elementId");
                final boolean hasKey = map.containsKey("elementInstanceKey");
                if (hasElementId && hasKey) {
                  violations.add("Only one of [elementId, elementInstanceKey] is allowed.");
                } else if (!hasElementId && !hasKey) {
                  violations.add("At least one of [elementId, elementInstanceKey] is required.");
                } else if (hasKey) {
                  mapped.setElementInstanceKey(
                      KeyUtil.keyToLong(String.valueOf(map.get("elementInstanceKey"))));
                } else {
                  mapped.setElementId(String.valueOf(map.get("elementId")));
                }
              }
              return mapped;
            })
        .toList();
  }

  public static Either<ProblemDetail, PublicationMessageRequest> toMessagePublicationRequest(
      final GeneratedMessagePublicationRequestStrictContract request,
      final boolean multiTenancyEnabled,
      final int maxNameFieldLength) {
    final var pubRequest =
        new MessagePublicationRequest()
            .name(request.name())
            .correlationKey(request.correlationKey())
            .timeToLive(request.timeToLive())
            .messageId(request.messageId())
            .variables(request.variables())
            .tenantId(request.tenantId());
    return toMessagePublicationRequest(pubRequest, multiTenancyEnabled, maxNameFieldLength);
  }

  public static Either<ProblemDetail, CorrelateMessageRequest> toMessageCorrelationRequest(
      final GeneratedMessageCorrelationRequestStrictContract request,
      final boolean multiTenancyEnabled,
      final int maxNameFieldLength) {
    final var corrRequest =
        new MessageCorrelationRequest()
            .name(request.name())
            .correlationKey(request.correlationKey())
            .variables(request.variables())
            .tenantId(request.tenantId());
    return toMessageCorrelationRequest(corrRequest, multiTenancyEnabled, maxNameFieldLength);
  }

  public static Either<ProblemDetail, EvaluateConditionalRequest> toEvaluateConditionalRequest(
      final GeneratedConditionalEvaluationInstructionStrictContract request,
      final boolean multiTenancyEnabled) {
    final var condRequest =
        new ConditionalEvaluationInstruction()
            .tenantId(request.tenantId())
            .processDefinitionKey(request.processDefinitionKey())
            .variables(request.variables());
    return toEvaluateConditionalRequest(condRequest, multiTenancyEnabled);
  }

  public static Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest>
      toAdHocSubProcessActivateActivitiesRequest(
          final String adHocSubProcessInstanceKey,
          final GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract request) {
    final var adHocRequest =
        new AdHocSubProcessActivateActivitiesInstruction()
            .cancelRemainingInstances(request.cancelRemainingInstances());
    final var elements =
        request.elements().stream()
            .map(
                e ->
                    new io.camunda.gateway.protocol.model.AdHocSubProcessActivateActivityReference()
                        .elementId(e.elementId())
                        .variables(e.variables()))
            .toList();
    adHocRequest.setElements(elements);
    return toAdHocSubProcessActivateActivitiesRequest(adHocSubProcessInstanceKey, adHocRequest);
  }

  public static Either<ProblemDetail, DecisionInstanceFilter> toRequiredDecisionInstanceFilter(
      final GeneratedDecisionInstanceFilterStrictContract request) {
    if (request == null) {
      return Either.left(
          createProblemDetail(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter"))).get());
    }
    final var result = DecisionInstanceFilterMapper.toDecisionInstanceFilter(request);
    if (result.equals(io.camunda.search.filter.FilterBuilders.decisionInstance().build())) {
      return Either.left(
          createProblemDetail(
                  List.of(ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted("filter criteria")))
              .get());
    }
    return Either.right(result);
  }
}
