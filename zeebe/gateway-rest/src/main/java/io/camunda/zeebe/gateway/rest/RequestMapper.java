/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.gateway.rest.util.KeyUtil.tryParseLong;
import static io.camunda.zeebe.gateway.rest.validator.AdHocSubprocessActivityRequestValidator.validateAdHocSubprocessActivationRequest;
import static io.camunda.zeebe.gateway.rest.validator.AdHocSubprocessActivityRequestValidator.validateAdHocSubprocessSearchActivitiesRequest;
import static io.camunda.zeebe.gateway.rest.validator.AuthorizationRequestValidator.validateAuthorizationRequest;
import static io.camunda.zeebe.gateway.rest.validator.ClockValidator.validateClockPinRequest;
import static io.camunda.zeebe.gateway.rest.validator.DocumentValidator.validateDocumentLinkParams;
import static io.camunda.zeebe.gateway.rest.validator.DocumentValidator.validateDocumentMetadata;
import static io.camunda.zeebe.gateway.rest.validator.ElementRequestValidator.validateVariableRequest;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.EvaluateDecisionRequestValidator.validateEvaluateDecisionRequest;
import static io.camunda.zeebe.gateway.rest.validator.JobRequestValidator.validateJobActivationRequest;
import static io.camunda.zeebe.gateway.rest.validator.JobRequestValidator.validateJobErrorRequest;
import static io.camunda.zeebe.gateway.rest.validator.JobRequestValidator.validateJobUpdateRequest;
import static io.camunda.zeebe.gateway.rest.validator.MappingValidator.validateMappingRequest;
import static io.camunda.zeebe.gateway.rest.validator.MessageRequestValidator.validateMessageCorrelationRequest;
import static io.camunda.zeebe.gateway.rest.validator.MessageRequestValidator.validateMessagePublicationRequest;
import static io.camunda.zeebe.gateway.rest.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.zeebe.gateway.rest.validator.MultiTenancyValidator.validateTenantIds;
import static io.camunda.zeebe.gateway.rest.validator.ProcessInstanceRequestValidator.validateCancelProcessInstanceRequest;
import static io.camunda.zeebe.gateway.rest.validator.ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest;
import static io.camunda.zeebe.gateway.rest.validator.ProcessInstanceRequestValidator.validateMigrateProcessInstanceRequest;
import static io.camunda.zeebe.gateway.rest.validator.ProcessInstanceRequestValidator.validateModifyProcessInstanceRequest;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.createProblemDetail;
import static io.camunda.zeebe.gateway.rest.validator.ResourceRequestValidator.validateResourceDeletion;
import static io.camunda.zeebe.gateway.rest.validator.SignalRequestValidator.validateSignalBroadcastRequest;
import static io.camunda.zeebe.gateway.rest.validator.UserTaskRequestValidator.validateAssignmentRequest;
import static io.camunda.zeebe.gateway.rest.validator.UserTaskRequestValidator.validateUpdateRequest;
import static io.camunda.zeebe.gateway.rest.validator.UserValidator.validateUserCreateRequest;
import static io.camunda.zeebe.gateway.rest.validator.UserValidator.validateUserUpdateRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.authentication.entity.CamundaUser;
import io.camunda.authentication.tenant.TenantAttributeHolder;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.filter.AdHocSubprocessActivityFilter;
import io.camunda.search.query.AdHocSubprocessActivityQuery;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authentication.Builder;
import io.camunda.service.AdHocSubprocessActivityServices.AdHocSubprocessActivateActivitiesRequest;
import io.camunda.service.AdHocSubprocessActivityServices.AdHocSubprocessActivateActivitiesRequest.AdHocSubprocessActivateActivityReference;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.AuthorizationServices.UpdateAuthorizationRequest;
import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.DocumentServices.DocumentLinkParams;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageServices.PublicationMessageRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.auth.ClaimTransformer;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivateActivitiesInstruction;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivitySearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.Changeset;
import io.camunda.zeebe.gateway.protocol.rest.ClockPinRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionEvaluationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.DeleteResourceRequest;
import io.camunda.zeebe.gateway.protocol.rest.DocumentLinkRequest;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobErrorRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobFailRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.MessageCorrelationRequest;
import io.camunda.zeebe.gateway.protocol.rest.MessagePublicationRequest;
import io.camunda.zeebe.gateway.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.RoleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.SetVariableRequest;
import io.camunda.zeebe.gateway.protocol.rest.SignalBroadcastRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserUpdateRequest;
import io.camunda.zeebe.gateway.rest.util.KeyUtil;
import io.camunda.zeebe.gateway.rest.validator.DocumentValidator;
import io.camunda.zeebe.gateway.rest.validator.GroupRequestValidator;
import io.camunda.zeebe.gateway.rest.validator.RoleRequestValidator;
import io.camunda.zeebe.gateway.rest.validator.TenantRequestValidator;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.Either;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;

public class RequestMapper {

  public static final String VND_CAMUNDA_API_KEYS_STRING_JSON = "vnd.camunda.api.keys.string+json";
  public static final String MEDIA_TYPE_KEYS_STRING_VALUE =
      "application/" + VND_CAMUNDA_API_KEYS_STRING_JSON;

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

  public static Either<ProblemDetail, UserDTO> toUserUpdateRequest(
      final UserUpdateRequest updateRequest, final String username) {
    return getResult(
        validateUserUpdateRequest(updateRequest),
        () ->
            new UserDTO(
                username,
                updateRequest.getName(),
                updateRequest.getEmail(),
                updateRequest.getPassword()));
  }

  public static Either<ProblemDetail, Long> getPinnedEpoch(final ClockPinRequest pinRequest) {
    return getResult(validateClockPinRequest(pinRequest), pinRequest::getTimestamp);
  }

  public static Either<ProblemDetail, ActivateJobsRequest> toJobsActivationRequest(
      final JobActivationRequest activationRequest, final boolean multiTenancyEnabled) {

    final Either<ProblemDetail, List<String>> validationResponse =
        validateTenantIds(
                getStringListOrEmpty(activationRequest, JobActivationRequest::getTenantIds),
                multiTenancyEnabled,
                "Activate Jobs")
            .flatMap(
                tenantIds ->
                    validateJobActivationRequest(activationRequest)
                        .map(Either::<ProblemDetail, List<String>>left)
                        .orElseGet(() -> Either.right(tenantIds)));

    return validationResponse.map(
        tenantIds ->
            new ActivateJobsRequest(
                activationRequest.getType(),
                activationRequest.getMaxJobsToActivate(),
                tenantIds,
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
                new UpdateJobChangeset(
                    updateRequest.getChangeset().getRetries(),
                    updateRequest.getChangeset().getTimeout())));
  }

  public static Either<ProblemDetail, UpdateRoleRequest> toRoleUpdateRequest(
      final RoleUpdateRequest roleUpdateRequest, final long roleKey) {
    return getResult(
        RoleRequestValidator.validateUpdateRequest(roleUpdateRequest),
        () -> new UpdateRoleRequest(roleKey, roleUpdateRequest.getChangeset().getName()));
  }

  public static Either<ProblemDetail, CreateRoleRequest> toRoleCreateRequest(
      final RoleCreateRequest roleCreateRequest) {
    return getResult(
        RoleRequestValidator.validateCreateRequest(roleCreateRequest),
        () -> new CreateRoleRequest(roleCreateRequest.getName()));
  }

  public static Either<ProblemDetail, CreateGroupRequest> toGroupCreateRequest(
      final GroupCreateRequest groupCreateRequest) {
    return getResult(
        GroupRequestValidator.validateCreateRequest(groupCreateRequest),
        () ->
            new CreateGroupRequest(groupCreateRequest.getGroupId(), groupCreateRequest.getName()));
  }

  public static Either<ProblemDetail, UpdateGroupRequest> toGroupUpdateRequest(
      final GroupUpdateRequest groupUpdateRequest, final long groupKey) {
    return getResult(
        GroupRequestValidator.validateUpdateRequest(groupUpdateRequest),
        () -> new UpdateGroupRequest(groupKey, groupUpdateRequest.getChangeset().getName()));
  }

  public static Either<ProblemDetail, CreateAuthorizationRequest> toCreateAuthorizationRequest(
      final AuthorizationRequest request) {
    return getResult(
        validateAuthorizationRequest(request),
        () ->
            new CreateAuthorizationRequest(
                request.getOwnerId(),
                AuthorizationOwnerType.valueOf(request.getOwnerType().name()),
                request.getResourceId(),
                AuthorizationResourceType.valueOf(request.getResourceType().name()),
                transformPermissionTypes(request.getPermissionTypes())));
  }

  public static Either<ProblemDetail, UpdateAuthorizationRequest> toUpdateAuthorizationRequest(
      final long authorizationKey, final AuthorizationRequest request) {
    return getResult(
        validateAuthorizationRequest(request),
        () ->
            new UpdateAuthorizationRequest(
                authorizationKey,
                request.getOwnerId(),
                AuthorizationOwnerType.valueOf(request.getOwnerType().name()),
                request.getResourceId(),
                AuthorizationResourceType.valueOf(request.getResourceType().name()),
                transformPermissionTypes(request.getPermissionTypes())));
  }

  private static Set<PermissionType> transformPermissionTypes(
      final List<PermissionTypeEnum> permissionTypes) {
    return permissionTypes.stream()
        .map(permission -> PermissionType.valueOf(permission.name()))
        .collect(Collectors.toSet());
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
    final var internalMetadata = toInternalDocumentMetadata(metadata, file);
    return getResult(
        validationResponse,
        () -> new DocumentCreateRequest(documentId, storeId, inputStream, internalMetadata));
  }

  public static Either<ProblemDetail, List<DocumentCreateRequest>> toDocumentCreateRequestBatch(
      final List<Part> parts, final String storeId, final ObjectMapper objectMapper) {
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
            .reduce( // combine violations from each problem detail
                ProblemDetail.forStatus(HttpStatus.BAD_REQUEST),
                (acc, detail) -> {
                  acc.setDetail(acc.getDetail() + ". " + detail.getDetail());
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

  public static Either<ProblemDetail, UserDTO> toUserDTO(final UserRequest request) {
    return getResult(
        validateUserCreateRequest(request),
        () ->
            new UserDTO(
                request.getUsername(),
                request.getName(),
                request.getEmail(),
                request.getPassword()));
  }

  public static Either<ProblemDetail, MappingDTO> toMappingDTO(
      final MappingRuleCreateRequest request) {
    return getResult(
        validateMappingRequest(request),
        () ->
            new MappingDTO(
                request.getClaimName(),
                request.getClaimValue(),
                request.getName(),
                request.getId()));
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

  public static <BrokerResponseT>
      CompletableFuture<ResponseEntity<Object>> executeServiceMethodWithAcceptedResult(
          final Supplier<CompletableFuture<BrokerResponseT>> method) {
    return RequestMapper.executeServiceMethod(method, ignored -> ResponseEntity.accepted().build());
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

  public static Either<ProblemDetail, SetVariablesRequest> toVariableRequest(
      final SetVariableRequest variableRequest, final long elementInstanceKey) {
    return getResult(
        validateVariableRequest(variableRequest),
        () ->
            new SetVariablesRequest(
                elementInstanceKey,
                variableRequest.getVariables(),
                variableRequest.getLocal(),
                variableRequest.getOperationReference()));
  }

  public static Either<ProblemDetail, PublicationMessageRequest> toMessagePublicationRequest(
      final MessagePublicationRequest messagePublicationRequest,
      final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(
                messagePublicationRequest.getTenantId(), multiTenancyEnabled, "Publish Message")
            .flatMap(
                tenantId ->
                    validateMessagePublicationRequest(messagePublicationRequest)
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
    return getResult(
        validateResourceDeletion(deleteRequest),
        () -> new ResourceDeletionRequest(resourceKey, operationReference));
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

  public static Authentication getAuthentication() {
    String authenticatedUsername = null;
    final List<Long> authenticatedRoleKeys = new ArrayList<>();
    final List<String> authorizedTenants = TenantAttributeHolder.getTenantIds();

    final var requestAuthentication = SecurityContextHolder.getContext().getAuthentication();

    final Map<String, Object> claims = new HashMap<>();

    if (requestAuthentication != null) {
      if (requestAuthentication.getPrincipal()
          instanceof final CamundaPrincipal authenticatedPrincipal) {
        authenticatedRoleKeys.addAll(
            authenticatedPrincipal.getAuthenticationContext().roles().stream()
                .map(RoleEntity::roleKey)
                .toList());
        if (authenticatedPrincipal instanceof final CamundaUser user) {
          authenticatedUsername = user.getUsername();
          claims.put(Authorization.AUTHORIZED_USERNAME, authenticatedUsername);
        }
      }

      if (requestAuthentication instanceof final JwtAuthenticationToken jwtAuthenticationToken) {
        jwtAuthenticationToken
            .getTokenAttributes()
            .forEach((key, value) -> ClaimTransformer.applyUserClaim(claims, key, value));
      }
    }

    return new Builder()
        .claims(claims)
        .user(authenticatedUsername)
        .roleKeys(authenticatedRoleKeys)
        .tenants(authorizedTenants)
        .build();
  }

  public static Authentication getAnonymousAuthentication() {
    return new Builder().claims(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true)).build();
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
    return RestErrorMapper.createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), message);
  }

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstruction request, final boolean multiTenancyEnabled) {
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
                    request, ProcessInstanceCreationInstruction::getProcessDefinitionKey, -1L),
                getStringOrEmpty(
                    request, ProcessInstanceCreationInstruction::getProcessDefinitionId),
                getIntOrDefault(
                    request, ProcessInstanceCreationInstruction::getProcessDefinitionVersion, -1),
                getMapOrEmpty(request, ProcessInstanceCreationInstruction::getVariables),
                tenantId,
                request.getAwaitCompletion(),
                request.getRequestTimeout(),
                request.getOperationReference(),
                request.getStartInstructions().stream()
                    .map(
                        instruction ->
                            new io.camunda.zeebe.protocol.impl.record.value.processinstance
                                    .ProcessInstanceCreationStartInstruction()
                                .setElementId(instruction.getElementId()))
                    .toList(),
                request.getFetchVariables()));
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

  public static Either<ProblemDetail, ProcessInstanceModifyRequest> toModifyProcessInstance(
      final long processInstanceKey, final ProcessInstanceModificationInstruction request) {
    return getResult(
        validateModifyProcessInstanceRequest(request),
        () ->
            new ProcessInstanceModifyRequest(
                processInstanceKey,
                mapProcessInstanceModificationActivateInstruction(
                    request.getActivateInstructions()),
                request.getTerminateInstructions().stream()
                    .map(
                        terminateInstruction ->
                            new ProcessInstanceModificationTerminateInstruction()
                                .setElementInstanceKey(
                                    KeyUtil.keyToLong(
                                        terminateInstruction.getElementInstanceKey())))
                    .toList(),
                request.getOperationReference()));
  }

  public static Either<ProblemDetail, DecisionEvaluationRequest> toEvaluateDecisionRequest(
      final DecisionEvaluationInstruction request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.getTenantId(), multiTenancyEnabled, "Evaluate Decision")
            .flatMap(
                tenantId ->
                    validateEvaluateDecisionRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenantId)));
    return validationResponse.map(
        tenantId ->
            new DecisionEvaluationRequest(
                getStringOrEmpty(request, DecisionEvaluationInstruction::getDecisionDefinitionId),
                getKeyOrDefault(
                    request, DecisionEvaluationInstruction::getDecisionDefinitionKey, -1L),
                getMapOrEmpty(request, DecisionEvaluationInstruction::getVariables),
                tenantId));
  }

  public static Either<ProblemDetail, TenantDTO> toTenantCreateDto(
      final TenantCreateRequest tenantCreateRequest) {
    return getResult(
        TenantRequestValidator.validateTenantCreateRequest(tenantCreateRequest),
        () ->
            new TenantDTO(
                null,
                tenantCreateRequest.getTenantId(),
                tenantCreateRequest.getName(),
                tenantCreateRequest.getDescription()));
  }

  public static Either<ProblemDetail, TenantDTO> toTenantUpdateDto(
      final String tenantId, final TenantUpdateRequest tenantUpdateRequest) {
    return getResult(
        TenantRequestValidator.validateTenantUpdateRequest(tenantUpdateRequest),
        () ->
            new TenantDTO(
                null,
                tenantId,
                tenantUpdateRequest.getName(),
                tenantUpdateRequest.getDescription()));
  }

  public static Either<ProblemDetail, AdHocSubprocessActivityQuery> toAdHocSubprocessActivityQuery(
      final AdHocSubprocessActivitySearchQuery request) {
    final var filter = request.getFilter();
    if (filter == null) {
      return Either.left(
          createProblemDetail(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter"))).get());
    }

    final var processDefinitionKey = tryParseLong(filter.getProcessDefinitionKey()).orElse(null);

    return getResult(
        validateAdHocSubprocessSearchActivitiesRequest(filter, processDefinitionKey),
        () ->
            AdHocSubprocessActivityQuery.builder()
                .filter(
                    AdHocSubprocessActivityFilter.builder()
                        .processDefinitionKey(processDefinitionKey)
                        .adHocSubprocessId(filter.getAdHocSubprocessId())
                        .build())
                .build());
  }

  public static Either<ProblemDetail, AdHocSubprocessActivateActivitiesRequest>
      toAdHocSubprocessActivateActivitiesRequest(
          final String adHocSubprocessInstanceKey,
          final AdHocSubprocessActivateActivitiesInstruction request) {
    return getResult(
        validateAdHocSubprocessActivationRequest(request),
        () ->
            new AdHocSubprocessActivateActivitiesRequest(
                adHocSubprocessInstanceKey,
                request.getElements().stream()
                    .map(
                        element ->
                            new AdHocSubprocessActivateActivityReference(element.getElementId()))
                    .toList()));
  }

  private static List<ProcessInstanceModificationActivateInstruction>
      mapProcessInstanceModificationActivateInstruction(
          final List<
                  io.camunda.zeebe.gateway.protocol.rest
                      .ProcessInstanceModificationActivateInstruction>
              instructions) {
    return instructions.stream()
        .map(
            instruction -> {
              final var mappedInstruction = new ProcessInstanceModificationActivateInstruction();
              mappedInstruction
                  .setElementId(instruction.getElementId())
                  .setAncestorScopeKey(
                      KeyUtil.keyToLong(instruction.getAncestorElementInstanceKey()));
              instruction.getVariableInstructions().stream()
                  .map(
                      variable ->
                          new ProcessInstanceModificationVariableInstruction()
                              .setElementId(variable.getScopeId())
                              .setVariables(
                                  new UnsafeBuffer(
                                      MsgPackConverter.convertToMsgPack(variable.getVariables()))))
                  .forEach(mappedInstruction::addVariableInstruction);
              return mappedInstruction;
            })
        .toList();
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

    final JobResult jobResult = new JobResult();
    jobResult.setDenied(getBooleanOrDefault(request, r -> r.getResult().getDenied(), false));
    jobResult.setDeniedReason(getStringOrEmpty(request, r -> r.getResult().getDeniedReason()));

    final var jobResultCorrections = request.getResult().getCorrections();
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

  public record UpdateJobRequest(long jobKey, UpdateJobChangeset changeset) {}

  public record BroadcastSignalRequest(
      String signalName, Map<String, Object> variables, String tenantId) {}

  public record DecisionEvaluationRequest(
      String decisionId, Long decisionKey, Map<String, Object> variables, String tenantId) {}

  public record CreateRoleRequest(String name) {}

  public record UpdateRoleRequest(long roleKey, String name) {}

  public record CreateGroupRequest(String groupId, String name) {}

  public record UpdateGroupRequest(long groupKey, String name) {}
}
