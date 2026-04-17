/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static io.camunda.gateway.mapping.http.validator.DocumentValidator.validateDocumentMetadata;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantIds;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateCreateProcessInstanceTags;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.createProblemDetail;
import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.mapping.http.validator.AdHocSubProcessActivityRequestValidator;
import io.camunda.gateway.mapping.http.validator.ConditionalEvaluationRequestValidator;
import io.camunda.gateway.mapping.http.validator.DocumentValidator;
import io.camunda.gateway.mapping.http.validator.JobRequestValidator;
import io.camunda.gateway.mapping.http.validator.MessageRequestValidator;
import io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator;
import io.camunda.gateway.mapping.http.validator.SignalRequestValidator;
import io.camunda.gateway.mapping.http.validator.UserTaskRequestValidator;
import io.camunda.gateway.protocol.model.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.ConditionalEvaluationInstruction;
import io.camunda.gateway.protocol.model.DecisionEvaluationById;
import io.camunda.gateway.protocol.model.DecisionEvaluationByKey;
import io.camunda.gateway.protocol.model.DecisionEvaluationInstruction;
import io.camunda.gateway.protocol.model.DeleteResourceRequest;
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
import io.camunda.gateway.protocol.model.ProcessInstanceFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationInstruction;
import io.camunda.gateway.protocol.model.SignalBroadcastRequest;
import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.UserTaskCompletionRequest;
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
import java.util.function.Supplier;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Maps strict contract DTOs to service-layer request types.
 *
 * <h3>Null-coercion convention</h3>
 *
 * <p>Strict contract records generate {@code fooOrDefault()} accessors for nullable fields whose
 * internal representation requires a non-null value (e.g. {@code String → ""}, {@code Map →
 * Map.of()}, {@code List → List.of()}, {@code Integer → 0}, {@code Long → 0L}). Prefer these
 * accessors over inline {@code foo() != null ? foo() : default} when writing new mapper methods.
 */
public class RequestMapper {

  public static final String VND_CAMUNDA_API_KEYS_STRING_JSON = "vnd.camunda.api.keys.string+json";
  public static final String MEDIA_TYPE_KEYS_STRING_VALUE =
      "application/" + VND_CAMUNDA_API_KEYS_STRING_JSON;

  private static final ObjectMapper PROTOCOL_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public static AssignUserTaskRequest toUserTaskUnassignmentRequest(final long userTaskKey) {
    return new AssignUserTaskRequest(userTaskKey, "", "unassign", true);
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
        final DocumentMetadata metadata = metadataList.get(i);
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
    final Map<Part, DocumentMetadata> metadataMap = new HashMap<>();
    for (final var part : parts) {
      final var headerValue = part.getHeader("X-Document-Metadata");
      if (headerValue != null) {
        try {
          metadataMap.put(part, objectMapper.readValue(headerValue, DocumentMetadata.class));
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        metadataMap.put(part, null);
      }
    }

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

  public static <T> Either<ProblemDetail, T> getResult(
      final Optional<ProblemDetail> error, final Supplier<T> resultSupplier) {
    return error
        .<Either<ProblemDetail, T>>map(Either::left)
        .orElseGet(() -> Either.right(resultSupplier.get()));
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
    if (metadata.getExpiresAt().isEmpty() || metadata.getExpiresAt().get().isBlank()) {
      expiresAt = null;
    } else {
      expiresAt = OffsetDateTime.parse(metadata.getExpiresAt().get());
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

  private static ProblemDetail createInternalErrorProblemDetail(
      final IOException e, final String message) {
    return GatewayErrorMapper.createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), message);
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstruction request, final boolean multiTenancyEnabled) {
    return switch (request) {
      case ProcessInstanceCreationInstructionById req ->
          toCreateProcessInstance(req, multiTenancyEnabled);
      case ProcessInstanceCreationInstructionByKey req ->
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
                request.getProcessDefinitionVersion().orElse(-1),
                request.getVariables().orElse(Map.of()),
                tenantId,
                request.getAwaitCompletion().orElse(null),
                request.getRequestTimeout().orElse(null),
                request.getOperationReference().orElse(null),
                request
                    .getStartInstructions()
                    .orElse(
                        List
                            .<io.camunda.gateway.protocol.model
                                    .ProcessInstanceCreationStartInstruction>
                                of())
                    .stream()
                    .map(
                        (io.camunda.gateway.protocol.model.ProcessInstanceCreationStartInstruction
                                si) ->
                            new ProcessInstanceCreationStartInstruction()
                                .setElementId(si.getElementId()))
                    .toList(),
                mapStrictRuntimeInstructions(request.getRuntimeInstructions().orElse(null)),
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
                request.getVariables().orElse(Map.of()),
                tenantId,
                request.getAwaitCompletion().orElse(null),
                request.getRequestTimeout().orElse(null),
                request.getOperationReference().orElse(null),
                request
                    .getStartInstructions()
                    .orElse(
                        List
                            .<io.camunda.gateway.protocol.model
                                    .ProcessInstanceCreationStartInstruction>
                                of())
                    .stream()
                    .map(
                        (io.camunda.gateway.protocol.model.ProcessInstanceCreationStartInstruction
                                si) ->
                            new ProcessInstanceCreationStartInstruction()
                                .setElementId(si.getElementId()))
                    .toList(),
                mapStrictRuntimeInstructions(request.getRuntimeInstructions().orElse(null)),
                request.getFetchVariables().orElse(List.of()),
                request.getTags().orElse(null),
                request.getBusinessId().orElse(null)));
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

  // --- Strict contract overloads for decision evaluation ---

  public static Either<ProblemDetail, DecisionEvaluationRequest> toEvaluateDecisionRequest(
      final DecisionEvaluationInstruction request, final boolean multiTenancyEnabled) {
    return switch (request) {
      case DecisionEvaluationById req -> toEvaluateDecisionRequest(req, multiTenancyEnabled);
      case DecisionEvaluationByKey req -> toEvaluateDecisionRequest(req, multiTenancyEnabled);
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
                request.getVariables().orElse(Map.of()),
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
                request.getVariables().orElse(Map.of()),
                tenantId));
  }

  private static Long getAncestorKey(final String ancestorElementInstanceKey) {
    if (ancestorElementInstanceKey == null) {
      return -1L;
    }
    return KeyUtil.keyToLong(ancestorElementInstanceKey);
  }

  // ---- Strict contract methods (direct field access) ----

  public static CompleteUserTaskRequest toUserTaskCompletionRequest(
      final UserTaskCompletionRequest request, final long userTaskKey) {
    if (request == null) {
      return new CompleteUserTaskRequest(userTaskKey, Map.of(), "");
    }
    return new CompleteUserTaskRequest(
        userTaskKey, request.getVariables().orElse(Map.of()), request.getAction().orElse(""));
  }

  public static Either<ProblemDetail, AssignUserTaskRequest> toUserTaskAssignmentRequest(
      final UserTaskAssignmentRequest request, final long userTaskKey) {
    final String action = request.getAction().orElse("");
    final boolean allowOverride =
        request.getAllowOverride().isEmpty() || request.getAllowOverride().get();
    return getResult(
        UserTaskRequestValidator.validateAssignmentRequest(request),
        () ->
            new AssignUserTaskRequest(
                userTaskKey,
                request.getAssignee().orElse(null),
                action.isBlank() ? "assign" : action,
                allowOverride));
  }

  public static Either<ProblemDetail, UpdateUserTaskRequest> toUserTaskUpdateRequest(
      final UserTaskUpdateRequest request, final long userTaskKey) {
    final var changeset = request != null ? request.getChangeset().orElse(null) : null;
    return getResult(
        UserTaskRequestValidator.validateUpdateRequest(request),
        () -> {
          final var record = new UserTaskRecord();
          if (changeset != null) {
            changeset
                .getCandidateGroups()
                .ifPresent(
                    cg -> {
                      record.setCandidateGroupsList(cg).setCandidateGroupsChanged();
                    });
            changeset
                .getCandidateUsers()
                .ifPresent(
                    cu -> {
                      record.setCandidateUsersList(cu).setCandidateUsersChanged();
                    });
            changeset
                .getDueDate()
                .ifPresent(
                    dd -> {
                      record.setDueDate(dd).setDueDateChanged();
                    });
            changeset
                .getFollowUpDate()
                .ifPresent(
                    fud -> {
                      record.setFollowUpDate(fud).setFollowUpDateChanged();
                    });
            changeset
                .getPriority()
                .ifPresent(
                    p -> {
                      record.setPriority(p).setPriorityChanged();
                    });
          }
          return new UpdateUserTaskRequest(
              userTaskKey, record, request == null ? "" : request.getAction().orElse(""));
        });
  }

  public static Either<ProblemDetail, ActivateJobsRequest> toJobsActivationRequest(
      final JobActivationRequest request, final boolean multiTenancyEnabled) {
    final var validationError = JobRequestValidator.validateJobActivationRequest(request);
    if (validationError.isPresent()) {
      return Either.left(validationError.get());
    }

    final TenantFilter tenantFilter;
    if (request.getTenantFilter().isEmpty()) {
      tenantFilter = TenantFilter.PROVIDED;
    } else {
      final String filterValue = request.getTenantFilter().get().getValue();
      tenantFilter =
          switch (filterValue) {
            case "ASSIGNED" -> TenantFilter.ASSIGNED;
            default -> TenantFilter.PROVIDED;
          };
    }

    if (tenantFilter == TenantFilter.ASSIGNED) {
      if (!multiTenancyEnabled) {
        return Either.left(
            GatewayErrorMapper.createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Expected to handle request Activate Jobs with ASSIGNED tenant filter,"
                    + " but multi-tenancy is disabled",
                INVALID_ARGUMENT.name()));
      }
      return Either.right(
          new ActivateJobsRequest(
              request.getType(),
              request.getMaxJobsToActivate(),
              Collections.emptyList(),
              tenantFilter,
              request.getTimeout(),
              request.getWorker().orElse(""),
              request.getFetchVariable().orElse(List.of()),
              request.getRequestTimeout().orElse(0L)));
    }

    final List<String> providedTenantIds = request.getTenantIds().orElse(List.of());
    final Either<ProblemDetail, List<String>> tenantIdsResult =
        validateTenantIds(providedTenantIds, multiTenancyEnabled, "Activate Jobs");
    if (tenantIdsResult.isLeft()) {
      return Either.left(tenantIdsResult.getLeft());
    }

    return Either.right(
        new ActivateJobsRequest(
            request.getType(),
            request.getMaxJobsToActivate(),
            tenantIdsResult.get(),
            tenantFilter,
            request.getTimeout(),
            request.getWorker().orElse(""),
            request.getFetchVariable().orElse(List.of()),
            request.getRequestTimeout().orElse(0L)));
  }

  public static FailJobRequest toJobFailRequest(final JobFailRequest request, final long jobKey) {
    if (request == null) {
      return new FailJobRequest(jobKey, 0, "", 0L, Map.of());
    }
    return new FailJobRequest(
        jobKey,
        request.getRetries().orElse(0),
        request.getErrorMessage().orElse(""),
        request.getRetryBackOff().orElse(0L),
        request.getVariables().orElse(Map.of()));
  }

  public static Either<ProblemDetail, ErrorJobRequest> toJobErrorRequest(
      final JobErrorRequest request, final long jobKey) {
    return getResult(
        JobRequestValidator.validateJobErrorRequest(request),
        () ->
            new ErrorJobRequest(
                jobKey,
                request.getErrorCode(),
                request.getErrorMessage().orElse(""),
                request.getVariables().orElse(Map.of())));
  }

  public static CompleteJobRequest toJobCompletionRequest(
      final JobCompletionRequest request, final long jobKey) {
    if (request == null) {
      return new CompleteJobRequest(jobKey, Map.of(), new JobResult());
    }
    return new CompleteJobRequest(
        jobKey,
        request.getVariables().orElse(Map.of()),
        toJobResult(request.getResult().orElse(null)));
  }

  /**
   * Converts the raw result Object (a LinkedHashMap at runtime due to the strict contract typing
   * the oneOf field as Object) to a domain JobResult by deserializing through the generated sealed
   * interface.
   */
  private static JobResult toJobResult(final Object rawResult) {
    if (rawResult == null) {
      return new JobResult();
    }
    final var typed =
        PROTOCOL_MAPPER.convertValue(rawResult, io.camunda.gateway.protocol.model.JobResult.class);
    return switch (typed) {
      case JobResultUserTask ut -> toJobResult(ut);
      case JobResultAdHocSubProcess ahsp -> toJobResult(ahsp);
    };
  }

  private static JobResult toJobResult(final JobResultUserTask result) {
    final JobResult jobResult = new JobResult();
    jobResult.setType(JobResultType.from(result.getType().orElse(null)));
    jobResult.setDenied(result.getDenied().orElse(false));
    jobResult.setDeniedReason(result.getDeniedReason().orElse(""));

    final var corrections = result.getCorrections().orElse(null);
    if (corrections == null) {
      return jobResult;
    }

    final JobResultCorrections domainCorrections = new JobResultCorrections();
    final List<String> correctedAttributes = new ArrayList<>();

    corrections
        .getAssignee()
        .ifPresent(
            v -> {
              domainCorrections.setAssignee(v);
              correctedAttributes.add(UserTaskRecord.ASSIGNEE);
            });
    corrections
        .getDueDate()
        .ifPresent(
            v -> {
              domainCorrections.setDueDate(v);
              correctedAttributes.add(UserTaskRecord.DUE_DATE);
            });
    corrections
        .getFollowUpDate()
        .ifPresent(
            v -> {
              domainCorrections.setFollowUpDate(v);
              correctedAttributes.add(UserTaskRecord.FOLLOW_UP_DATE);
            });
    corrections
        .getCandidateUsers()
        .ifPresent(
            v -> {
              domainCorrections.setCandidateUsersList(v);
              correctedAttributes.add(UserTaskRecord.CANDIDATE_USERS);
            });
    corrections
        .getCandidateGroups()
        .ifPresent(
            v -> {
              domainCorrections.setCandidateGroupsList(v);
              correctedAttributes.add(UserTaskRecord.CANDIDATE_GROUPS);
            });
    corrections
        .getPriority()
        .ifPresent(
            v -> {
              domainCorrections.setPriority(v);
              correctedAttributes.add(UserTaskRecord.PRIORITY);
            });

    jobResult.setCorrections(domainCorrections);
    jobResult.setCorrectedAttributes(correctedAttributes);
    return jobResult;
  }

  private static JobResult toJobResult(final JobResultAdHocSubProcess result) {
    final JobResult jobResult = new JobResult();
    jobResult
        .setType(JobResultType.from(result.getType().orElse(null)))
        .setCompletionConditionFulfilled(result.getIsCompletionConditionFulfilled().orElse(false))
        .setCancelRemainingInstances(result.getIsCancelRemainingInstances().orElse(false));
    result
        .getActivateElements()
        .ifPresent(
            elements ->
                elements.stream()
                    .map(
                        element -> {
                          final var activateElement =
                              new JobResultActivateElement()
                                  .setElementId(element.getElementId().orElse(null));
                          element
                              .getVariables()
                              .ifPresent(
                                  vars ->
                                      activateElement.setVariables(
                                          new UnsafeBuffer(
                                              MsgPackConverter.convertToMsgPack(vars))));
                          return activateElement;
                        })
                    .forEach(jobResult::addActivateElement));
    return jobResult;
  }

  public static Either<ProblemDetail, UpdateJobRequest> toJobUpdateRequest(
      final JobUpdateRequest request, final long jobKey) {
    final var cs = request.getChangeset();
    return getResult(
        JobRequestValidator.validateJobUpdateRequest(request),
        () ->
            new UpdateJobRequest(
                jobKey,
                request.getOperationReference().orElse(null),
                new UpdateJobChangeset(
                    cs.getRetries().orElse(null), cs.getTimeout().orElse(null))));
  }

  public static Either<ProblemDetail, BroadcastSignalRequest> toBroadcastSignalRequest(
      final SignalBroadcastRequest request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                request.getTenantId().orElse(null), multiTenancyEnabled, "Broadcast Signal")
            .flatMap(
                tenantId ->
                    SignalRequestValidator.validateSignalBroadcastRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new BroadcastSignalRequest(
                request.getSignalName(), request.getVariables().orElse(null), tenantId));
  }

  public static Either<ProblemDetail, ResourceDeletionRequest> toResourceDeletion(
      final long resourceKey, final DeleteResourceRequest request) {
    final Long operationReference =
        request != null ? request.getOperationReference().orElse(null) : null;
    final boolean deleteHistory =
        request != null && Boolean.TRUE.equals(request.getDeleteHistory().orElse(null));
    return Either.right(
        new ResourceDeletionRequest(resourceKey, operationReference, deleteHistory));
  }

  public static Either<ProblemDetail, DocumentLinkParams> toDocumentLinkParams(
      final DocumentLinkRequest request) {
    if (request == null) {
      return Either.right(new DocumentLinkParams(Duration.ZERO));
    }
    return getResult(
        DocumentValidator.validateDocumentLinkParams(request),
        () -> new DocumentLinkParams(Duration.ofMillis(request.getTimeToLive().orElse(0L))));
  }

  public static Either<ProblemDetail, ProcessInstanceCancelRequest> toCancelProcessInstance(
      final long processInstanceKey, final CancelProcessInstanceRequest request) {
    final Long operationReference =
        request != null ? request.getOperationReference().orElse(null) : null;
    return Either.right(new ProcessInstanceCancelRequest(processInstanceKey, operationReference));
  }

  public static Either<ProblemDetail, io.camunda.search.filter.ProcessInstanceFilter>
      toRequiredProcessInstanceFilter(final ProcessInstanceFilter request) {
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

    final var migPlan = request.getMigrationPlan();
    final List<String> violations = new ArrayList<>();
    if (migPlan == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("migrationPlan"));
      return Either.left(createProblemDetail(violations).get());
    }
    if (migPlan.getTargetProcessDefinitionKey() == null
        || migPlan.getTargetProcessDefinitionKey().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("targetProcessDefinitionKey"));
    }
    if (migPlan.getMappingInstructions() == null || migPlan.getMappingInstructions().isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingInstructions"));
    }
    if (!violations.isEmpty()) {
      return Either.left(createProblemDetail(violations).get());
    }

    return Either.right(
        new ProcessInstanceMigrateBatchOperationRequest(
            filterResult.get(),
            KeyUtil.keyToLong(migPlan.getTargetProcessDefinitionKey()),
            migPlan.getMappingInstructions().stream()
                .map(
                    mi ->
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId(mi.getSourceElementId())
                            .setTargetElementId(mi.getTargetElementId()))
                .toList()));
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
            request.getMoveInstructions().stream()
                .map(
                    mi ->
                        new ProcessInstanceModificationMoveInstruction()
                            .setSourceElementId(mi.getSourceElementId())
                            .setTargetElementId(mi.getTargetElementId())
                            .setInferAncestorScopeFromSourceHierarchy(true))
                .toList()));
  }

  public static Either<ProblemDetail, ProcessInstanceMigrateRequest> toMigrateProcessInstance(
      final long processInstanceKey, final ProcessInstanceMigrationInstruction request) {
    return getResult(
        ProcessInstanceRequestValidator.validateMigrationInstructions(request),
        () ->
            new ProcessInstanceMigrateRequest(
                processInstanceKey,
                KeyUtil.keyToLong(request.getTargetProcessDefinitionKey()),
                request.getMappingInstructions().stream()
                    .map(
                        mi ->
                            new ProcessInstanceMigrationMappingInstruction()
                                .setSourceElementId(mi.getSourceElementId())
                                .setTargetElementId(mi.getTargetElementId()))
                    .toList(),
                request.getOperationReference().orElse(null)));
  }

  public static Either<ProblemDetail, ProcessInstanceModifyRequest> toModifyProcessInstance(
      final long processInstanceKey, final ProcessInstanceModificationInstruction request) {
    final List<String> violations = new ArrayList<>();
    final var activateInstructions =
        mapActivateInstructionsFrom(request.getActivateInstructions().orElse(null));
    final var moveInstructions =
        mapMoveInstructionsFrom(request.getMoveInstructions().orElse(null), violations);
    final var terminateInstructions =
        mapTerminateInstructionsFrom(request.getTerminateInstructions().orElse(null), violations);
    return getResult(
        createProblemDetail(violations),
        () ->
            new ProcessInstanceModifyRequest(
                processInstanceKey,
                activateInstructions,
                moveInstructions,
                terminateInstructions,
                request.getOperationReference().orElse(null)));
  }

  private static List<ProcessInstanceModificationActivateInstruction> mapActivateInstructionsFrom(
      final List<io.camunda.gateway.protocol.model.ProcessInstanceModificationActivateInstruction>
          instructions) {
    if (instructions == null) {
      return List.of();
    }
    return instructions.stream()
        .map(
            instruction -> {
              final var mapped = new ProcessInstanceModificationActivateInstruction();
              mapped
                  .setElementId(instruction.getElementId())
                  .setAncestorScopeKey(
                      getAncestorKey(instruction.getAncestorElementInstanceKey().orElse(null)));
              instruction
                  .getVariableInstructions()
                  .ifPresent(
                      vis ->
                          vis.stream()
                              .map(RequestMapper::mapVariableInstructionFrom)
                              .forEach(mapped::addVariableInstruction));
              return mapped;
            })
        .toList();
  }

  private static ProcessInstanceModificationVariableInstruction mapVariableInstructionFrom(
      final ModifyProcessInstanceVariableInstruction variable) {
    return new ProcessInstanceModificationVariableInstruction()
        .setElementId(variable.getScopeId().orElse(null))
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variable.getVariables())));
  }

  private static List<ProcessInstanceModificationMoveInstruction> mapMoveInstructionsFrom(
      final List<io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveInstruction>
          instructions,
      final List<String> violations) {
    if (instructions == null) {
      return List.of();
    }
    return instructions.stream()
        .map(
            instruction -> {
              final var mapped =
                  new ProcessInstanceModificationMoveInstruction()
                      .setTargetElementId(instruction.getTargetElementId());
              if (instruction.getSourceElementInstruction() instanceof final Map<?, ?> sourceMap) {
                if (sourceMap.containsKey("sourceElementId")) {
                  mapped.setSourceElementId(String.valueOf(sourceMap.get("sourceElementId")));
                } else if (sourceMap.containsKey("sourceElementInstanceKey")) {
                  mapped.setSourceElementInstanceKey(
                      KeyUtil.keyToLong(String.valueOf(sourceMap.get("sourceElementInstanceKey"))));
                }
              }
              mapAncestorScopeFrom(
                  instruction.getAncestorScopeInstruction().orElse(null), mapped, violations);
              instruction
                  .getVariableInstructions()
                  .ifPresent(
                      vis ->
                          vis.stream()
                              .map(RequestMapper::mapVariableInstructionFrom)
                              .forEach(mapped::addVariableInstruction));
              return mapped;
            })
        .toList();
  }

  private static void mapAncestorScopeFrom(
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

  private static List<ProcessInstanceModificationTerminateInstruction> mapTerminateInstructionsFrom(
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
      final MessagePublicationRequest request,
      final boolean multiTenancyEnabled,
      final int maxNameFieldLength) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.getTenantId().orElse(null), multiTenancyEnabled, "Publish Message")
            .flatMap(
                tenantId ->
                    MessageRequestValidator.validateMessagePublicationRequest(
                            request, maxNameFieldLength)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new PublicationMessageRequest(
                request.getName(),
                request.getCorrelationKey().orElse(null),
                request.getTimeToLive().orElse(null),
                request.getMessageId().orElse(""),
                request.getVariables().orElse(Map.of()),
                tenantId));
  }

  public static Either<ProblemDetail, CorrelateMessageRequest> toMessageCorrelationRequest(
      final MessageCorrelationRequest request,
      final boolean multiTenancyEnabled,
      final int maxNameFieldLength) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                request.getTenantId().orElse(null), multiTenancyEnabled, "Correlate Message")
            .flatMap(
                tenantId ->
                    MessageRequestValidator.validateMessageCorrelationRequest(
                            request, maxNameFieldLength)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new CorrelateMessageRequest(
                request.getName(),
                request.getCorrelationKey().orElse(null),
                request.getVariables().orElse(null),
                tenantId));
  }

  public static Either<ProblemDetail, EvaluateConditionalRequest> toEvaluateConditionalRequest(
      final ConditionalEvaluationInstruction request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                request.getTenantId().orElse(null), multiTenancyEnabled, "Evaluate Conditional")
            .flatMap(
                tenantId ->
                    ConditionalEvaluationRequestValidator.validateConditionalEvaluationRequest(
                            request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new EvaluateConditionalRequest(
                tenantId,
                request.getProcessDefinitionKey().map(KeyUtil::keyToLong).orElse(-1L),
                request.getVariables()));
  }

  public static Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest>
      toAdHocSubProcessActivateActivitiesRequest(
          final String adHocSubProcessInstanceKey,
          final AdHocSubProcessActivateActivitiesInstruction request) {
    return getResult(
        AdHocSubProcessActivityRequestValidator.validateAdHocSubProcessActivationRequest(request),
        () ->
            new AdHocSubProcessActivateActivitiesRequest(
                Long.parseLong(adHocSubProcessInstanceKey),
                request.getElements().stream()
                    .map(
                        element ->
                            new AdHocSubProcessActivateActivityReference(
                                element.getElementId(), element.getVariables().orElse(Map.of())))
                    .toList(),
                request.getCancelRemainingInstances().orElse(false)));
  }

  public static Either<ProblemDetail, DecisionInstanceFilter> toRequiredDecisionInstanceFilter(
      final io.camunda.gateway.protocol.model.DecisionInstanceFilter request) {
    final var filter = SearchQueryFilterMapper.toRequiredDecisionInstanceFilter(request);
    if (filter.isLeft()) {
      return Either.left(createProblemDetail(filter.getLeft()).get());
    }
    return Either.right(filter.get());
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
