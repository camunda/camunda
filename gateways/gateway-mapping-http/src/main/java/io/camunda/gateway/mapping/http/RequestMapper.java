/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static io.camunda.gateway.mapping.http.validator.DocumentValidator.validateDocumentMetadata;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ALL_REQUIRED_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantIds;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateCreateProcessInstanceTags;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.createProblemDetail;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;
import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.gateway.mapping.http.search.contract.DecisionInstanceFilterMapper;
import io.camunda.gateway.mapping.http.search.contract.ProcessInstanceFilterMapper;
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
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobResultAdHocSubProcessStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobResultUserTaskStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobStrictContract;
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
import io.camunda.zeebe.gateway.rest.CamundaProblemDetail;
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
  // and the generated sealed interface relies on registered deserializers (e.g. oneOf sub-types).
  private static final ObjectMapper PROTOCOL_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public static final String VND_CAMUNDA_API_KEYS_STRING_JSON = "vnd.camunda.api.keys.string+json";
  public static final String MEDIA_TYPE_KEYS_STRING_VALUE =
      "application/" + VND_CAMUNDA_API_KEYS_STRING_JSON;

  public static AssignUserTaskRequest toUserTaskUnassignmentRequest(final long userTaskKey) {
    return new AssignUserTaskRequest(userTaskKey, "", "unassign", true);
  }

  public static Either<ProblemDetail, DocumentCreateRequest> toDocumentCreateRequest(
      final String documentId,
      final String storeId,
      final Part file,
      final GeneratedDocumentMetadataStrictContract metadata) {
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
      final List<GeneratedDocumentMetadataStrictContract> metadataList) {

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
        final GeneratedDocumentMetadataStrictContract metadata = metadataList.get(i);
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
    final Map<Part, GeneratedDocumentMetadataStrictContract> metadataMap = new HashMap<>();
    for (final var part : parts) {
      final var headerValue = part.getHeader("X-Document-Metadata");
      if (headerValue != null) {
        try {
          metadataMap.put(
              part,
              objectMapper.readValue(headerValue, GeneratedDocumentMetadataStrictContract.class));
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
      final GeneratedDocumentMetadataStrictContract metadata, final Part file) {

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
    if (metadata.expiresAt() == null || metadata.expiresAt().isBlank()) {
      expiresAt = null;
    } else {
      expiresAt = OffsetDateTime.parse(metadata.expiresAt());
    }
    final var fileName =
        Optional.ofNullable(metadata.fileName()).orElse(file.getSubmittedFileName());
    final var contentType =
        Optional.ofNullable(metadata.contentType()).orElse(file.getContentType());

    return new DocumentMetadataModel(
        contentType,
        fileName,
        expiresAt,
        file.getSize(),
        metadata.processDefinitionId(),
        KeyUtil.keyToLong(metadata.processInstanceKey()),
        metadata.customProperties());
  }

  private static ProblemDetail createInternalErrorProblemDetail(
      final IOException e, final String message) {
    return GatewayErrorMapper.createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), message);
  }

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
    final String action = request.action() == null ? "" : request.action();
    final boolean allowOverride = request.allowOverride() == null || request.allowOverride();
    return getResult(
        validate(
            violations -> {
              if (request.assignee() == null || request.assignee().isBlank()) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("assignee"));
              }
            }),
        () ->
            new AssignUserTaskRequest(
                userTaskKey,
                request.assignee(),
                action.isBlank() ? "assign" : action,
                allowOverride));
  }

  public static Either<ProblemDetail, UpdateUserTaskRequest> toUserTaskUpdateRequest(
      final GeneratedUserTaskUpdateRequestStrictContract request, final long userTaskKey) {
    final var changeset = request != null ? request.changeset() : null;
    final boolean changesetEmpty =
        changeset == null
            || (changeset.followUpDate() == null
                && changeset.dueDate() == null
                && changeset.candidateGroups() == null
                && changeset.candidateUsers() == null
                && changeset.priority() == null);
    return getResult(
        validate(
            violations -> {
              if (request == null || (request.action() == null && changesetEmpty)) {
                violations.add(ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET);
              }
              if (!changesetEmpty) {
                validateDate(changeset.dueDate(), "due date", violations);
                validateDate(changeset.followUpDate(), "follow-up date", violations);
              }
            }),
        () -> {
          final var record = new UserTaskRecord();
          if (changeset != null) {
            if (changeset.candidateGroups() != null) {
              record
                  .setCandidateGroupsList(changeset.candidateGroups())
                  .setCandidateGroupsChanged();
            }
            if (changeset.candidateUsers() != null) {
              record.setCandidateUsersList(changeset.candidateUsers()).setCandidateUsersChanged();
            }
            if (changeset.dueDate() != null) {
              record.setDueDate(changeset.dueDate()).setDueDateChanged();
            }
            if (changeset.followUpDate() != null) {
              record.setFollowUpDate(changeset.followUpDate()).setFollowUpDateChanged();
            }
            if (changeset.priority() != null) {
              record.setPriority(changeset.priority()).setPriorityChanged();
            }
          }
          return new UpdateUserTaskRequest(
              userTaskKey,
              record,
              request == null || request.action() == null ? "" : request.action());
        });
  }

  public static Either<ProblemDetail, ActivateJobsRequest> toJobsActivationRequest(
      final GeneratedJobActivationRequestStrictContract request,
      final boolean multiTenancyEnabled) {
    final var validationError =
        validate(
            violations -> {
              if (request.type() == null || request.type().isBlank()) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("type"));
              }
              if (request.timeout() == null) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("timeout"));
              } else if (request.timeout() < 1) {
                violations.add(
                    ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                        "timeout", request.timeout(), "greater than 0"));
              }
              if (request.maxJobsToActivate() == null) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("maxJobsToActivate"));
              } else if (request.maxJobsToActivate() < 1) {
                violations.add(
                    ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                        "maxJobsToActivate", request.maxJobsToActivate(), "greater than 0"));
              }
            });
    if (validationError.isPresent()) {
      return Either.left(validationError.get());
    }

    final TenantFilter tenantFilter;
    if (request.tenantFilter() == null) {
      tenantFilter = TenantFilter.PROVIDED;
    } else {
      final String filterValue = request.tenantFilter().getValue();
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
              request.type(),
              request.maxJobsToActivate(),
              Collections.emptyList(),
              tenantFilter,
              request.timeout(),
              request.worker() != null ? request.worker() : "",
              request.fetchVariable() != null ? request.fetchVariable() : List.of(),
              request.requestTimeout() != null ? request.requestTimeout() : 0L));
    }

    final List<String> providedTenantIds =
        request.tenantIds() != null ? request.tenantIds() : List.of();
    final Either<ProblemDetail, List<String>> tenantIdsResult =
        validateTenantIds(providedTenantIds, multiTenancyEnabled, "Activate Jobs");
    if (tenantIdsResult.isLeft()) {
      return Either.left(tenantIdsResult.getLeft());
    }

    return Either.right(
        new ActivateJobsRequest(
            request.type(),
            request.maxJobsToActivate(),
            tenantIdsResult.get(),
            tenantFilter,
            request.timeout(),
            request.worker() != null ? request.worker() : "",
            request.fetchVariable() != null ? request.fetchVariable() : List.of(),
            request.requestTimeout() != null ? request.requestTimeout() : 0L));
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
    return getResult(
        validate(
            violations -> {
              if (request.errorCode() == null || request.errorCode().isBlank()) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("errorCode"));
              }
            }),
        () ->
            new ErrorJobRequest(
                jobKey,
                request.errorCode(),
                request.errorMessage() != null ? request.errorMessage() : "",
                request.variables() != null ? request.variables() : Map.of()));
  }

  public static CompleteJobRequest toJobCompletionRequest(
      final GeneratedJobCompletionRequestStrictContract request, final long jobKey) {
    final Map<String, Object> variables =
        request != null && request.variables() != null ? request.variables() : Map.of();
    final var jobResult = toJobResult(request != null ? request.result() : null);
    return new CompleteJobRequest(jobKey, variables, jobResult);
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
    final var typed = PROTOCOL_MAPPER.convertValue(rawResult, GeneratedJobStrictContract.class);
    return switch (typed) {
      case GeneratedJobResultUserTaskStrictContract ut -> toJobResult(ut);
      case GeneratedJobResultAdHocSubProcessStrictContract ahsp -> toJobResult(ahsp);
    };
  }

  private static JobResult toJobResult(final GeneratedJobResultUserTaskStrictContract result) {
    final JobResult jobResult = new JobResult();
    jobResult.setType(JobResultType.from(result.type()));
    jobResult.setDenied(result.denied() != null ? result.denied() : false);
    jobResult.setDeniedReason(result.deniedReason() != null ? result.deniedReason() : "");

    final var corrections = result.corrections();
    if (corrections == null) {
      return jobResult;
    }

    final JobResultCorrections domainCorrections = new JobResultCorrections();
    final List<String> correctedAttributes = new ArrayList<>();

    if (corrections.assignee() != null) {
      domainCorrections.setAssignee(corrections.assignee());
      correctedAttributes.add(UserTaskRecord.ASSIGNEE);
    }
    if (corrections.dueDate() != null) {
      domainCorrections.setDueDate(corrections.dueDate());
      correctedAttributes.add(UserTaskRecord.DUE_DATE);
    }
    if (corrections.followUpDate() != null) {
      domainCorrections.setFollowUpDate(corrections.followUpDate());
      correctedAttributes.add(UserTaskRecord.FOLLOW_UP_DATE);
    }
    if (corrections.candidateUsers() != null) {
      domainCorrections.setCandidateUsersList(corrections.candidateUsers());
      correctedAttributes.add(UserTaskRecord.CANDIDATE_USERS);
    }
    if (corrections.candidateGroups() != null) {
      domainCorrections.setCandidateGroupsList(corrections.candidateGroups());
      correctedAttributes.add(UserTaskRecord.CANDIDATE_GROUPS);
    }
    if (corrections.priority() != null) {
      domainCorrections.setPriority(corrections.priority());
      correctedAttributes.add(UserTaskRecord.PRIORITY);
    }

    jobResult.setCorrections(domainCorrections);
    jobResult.setCorrectedAttributes(correctedAttributes);
    return jobResult;
  }

  private static JobResult toJobResult(
      final GeneratedJobResultAdHocSubProcessStrictContract result) {
    final JobResult jobResult = new JobResult();
    jobResult
        .setType(JobResultType.from(result.type()))
        .setCompletionConditionFulfilled(result.isCompletionConditionFulfilled())
        .setCancelRemainingInstances(result.isCancelRemainingInstances());
    if (result.activateElements() != null) {
      result.activateElements().stream()
          .map(
              element -> {
                final var activateElement =
                    new JobResultActivateElement().setElementId(element.elementId());
                if (element.variables() != null) {
                  activateElement.setVariables(
                      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(element.variables())));
                }
                return activateElement;
              })
          .forEach(jobResult::addActivateElement);
    }
    return jobResult;
  }

  public static Either<ProblemDetail, UpdateJobRequest> toJobUpdateRequest(
      final GeneratedJobUpdateRequestStrictContract request, final long jobKey) {
    final var cs = request.changeset();
    return getResult(
        validate(
            violations -> {
              if (cs == null || (cs.retries() == null && cs.timeout() == null)) {
                violations.add(
                    ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(List.of("retries", "timeout")));
              }
            }),
        () ->
            new UpdateJobRequest(
                jobKey,
                request.operationReference(),
                new UpdateJobChangeset(cs.retries(), cs.timeout())));
  }

  public static Either<ProblemDetail, BroadcastSignalRequest> toBroadcastSignalRequest(
      final GeneratedSignalBroadcastRequestStrictContract request,
      final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.tenantId(), multiTenancyEnabled, "Broadcast Signal")
            .flatMap(
                tenantId ->
                    validate(
                            violations -> {
                              if (request.signalName() == null) {
                                violations.add(
                                    ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("signalName"));
                              }
                            })
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new BroadcastSignalRequest(request.signalName(), request.variables(), tenantId));
  }

  public static Either<ProblemDetail, ResourceDeletionRequest> toResourceDeletion(
      final long resourceKey, final GeneratedDeleteResourceRequestStrictContract request) {
    final Long operationReference = request != null ? request.operationReference() : null;
    final boolean deleteHistory = request != null && Boolean.TRUE.equals(request.deleteHistory());
    return Either.right(
        new ResourceDeletionRequest(resourceKey, operationReference, deleteHistory));
  }

  public static Either<ProblemDetail, DocumentLinkParams> toDocumentLinkParams(
      final GeneratedDocumentLinkRequestStrictContract request) {
    if (request == null) {
      return Either.right(new DocumentLinkParams(Duration.ZERO));
    }
    return getResult(
        validate(
            violations -> {
              if (request.timeToLive() <= 0) {
                violations.add(
                    ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                        "timeToLive", request.timeToLive(), "greater than 0"));
              }
            }),
        () -> new DocumentLinkParams(Duration.ofMillis(request.timeToLive())));
  }

  public static Either<ProblemDetail, ProcessInstanceCancelRequest> toCancelProcessInstance(
      final long processInstanceKey,
      final GeneratedCancelProcessInstanceRequestStrictContract request) {
    final Long operationReference = request != null ? request.operationReference() : null;
    return Either.right(new ProcessInstanceCancelRequest(processInstanceKey, operationReference));
  }

  public static Either<ProblemDetail, io.camunda.search.filter.ProcessInstanceFilter>
      toRequiredProcessInstanceFilter(final GeneratedProcessInstanceFilterStrictContract request) {
    final var filter = ProcessInstanceFilterMapper.toRequiredProcessInstanceFilter(request);
    if (filter.isLeft()) {
      return Either.left(createProblemDetail(filter.getLeft()).get());
    }
    return Either.right(filter.get());
  }

  public static Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest>
      toProcessInstanceMigrationBatchOperationRequest(
          final GeneratedProcessInstanceMigrationBatchOperationRequestStrictContract request) {
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
    return getResult(
        validate(
            violations -> {
              if (request.mappingInstructions() == null
                  || request.mappingInstructions().isEmpty()) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingInstructions"));
              } else {
                final boolean allValid =
                    request.mappingInstructions().stream()
                        .allMatch(
                            mi ->
                                (mi.sourceElementId() != null && !mi.sourceElementId().isEmpty())
                                    && (mi.targetElementId() != null
                                        && !mi.targetElementId().isEmpty()));
                if (!allValid) {
                  violations.add(
                      ERROR_MESSAGE_ALL_REQUIRED_FIELD.formatted(
                          List.of("sourceElementId", "targetElementId")));
                }
              }
            }),
        () ->
            new ProcessInstanceMigrateRequest(
                processInstanceKey,
                request.targetProcessDefinitionKey() != null
                    ? Long.parseLong(request.targetProcessDefinitionKey())
                    : -1L,
                request.mappingInstructions().stream()
                    .map(
                        mi ->
                            new ProcessInstanceMigrationMappingInstruction()
                                .setSourceElementId(mi.sourceElementId())
                                .setTargetElementId(mi.targetElementId()))
                    .toList(),
                request.operationReference()));
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
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.tenantId(), multiTenancyEnabled, "Publish Message")
            .flatMap(
                tenantId ->
                    validate(
                            violations -> {
                              if (request.name() == null || request.name().isBlank()) {
                                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
                              }
                              if (request.correlationKey() != null
                                  && request.correlationKey().length() > maxNameFieldLength) {
                                violations.add(
                                    ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                                        "correlationKey", maxNameFieldLength));
                              }
                            })
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new PublicationMessageRequest(
                request.name(),
                request.correlationKey(),
                request.timeToLive(),
                request.messageId() != null ? request.messageId() : "",
                request.variables() != null ? request.variables() : Map.of(),
                tenantId));
  }

  public static Either<ProblemDetail, CorrelateMessageRequest> toMessageCorrelationRequest(
      final GeneratedMessageCorrelationRequestStrictContract request,
      final boolean multiTenancyEnabled,
      final int maxNameFieldLength) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.tenantId(), multiTenancyEnabled, "Correlate Message")
            .flatMap(
                tenantId ->
                    validate(
                            violations -> {
                              if (request.name() == null || request.name().isBlank()) {
                                violations.add(
                                    ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("messageName"));
                              }
                              if (request.correlationKey() != null
                                  && request.correlationKey().length() > maxNameFieldLength) {
                                violations.add(
                                    ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(
                                        "correlationKey", maxNameFieldLength));
                              }
                            })
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new CorrelateMessageRequest(
                request.name(), request.correlationKey(), request.variables(), tenantId));
  }

  public static Either<ProblemDetail, EvaluateConditionalRequest> toEvaluateConditionalRequest(
      final GeneratedConditionalEvaluationInstructionStrictContract request,
      final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.tenantId(), multiTenancyEnabled, "Evaluate Conditional")
            .flatMap(
                tenantId ->
                    validate(
                            violations -> {
                              if (request.variables() == null || request.variables().isEmpty()) {
                                violations.add(
                                    ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("variables"));
                              }
                            })
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new EvaluateConditionalRequest(
                tenantId,
                request.processDefinitionKey() != null
                    ? Long.parseLong(request.processDefinitionKey())
                    : -1L,
                request.variables()));
  }

  public static Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest>
      toAdHocSubProcessActivateActivitiesRequest(
          final String adHocSubProcessInstanceKey,
          final GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract request) {
    return getResult(
        validate(
            violations -> {
              if (request.elements() == null || request.elements().isEmpty()) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elements"));
              } else {
                for (int i = 0; i < request.elements().size(); i++) {
                  final var elementId = request.elements().get(i).elementId();
                  if (elementId == null || elementId.isBlank()) {
                    violations.add(
                        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(
                            "elements[%d].elementId".formatted(i)));
                  }
                }
              }
            }),
        () ->
            new AdHocSubProcessActivateActivitiesRequest(
                Long.parseLong(adHocSubProcessInstanceKey),
                request.elements().stream()
                    .map(
                        element ->
                            new AdHocSubProcessActivateActivityReference(
                                element.elementId(),
                                element.variables() != null ? element.variables() : Map.of()))
                    .toList(),
                request.cancelRemainingInstances() != null && request.cancelRemainingInstances()));
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
