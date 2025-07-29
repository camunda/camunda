/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.protocol.record.value.JobKind.TASK_LISTENER;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentLink;
import io.camunda.service.DocumentServices.DocumentContentResponse;
import io.camunda.service.DocumentServices.DocumentErrorResponse;
import io.camunda.service.DocumentServices.DocumentReferenceResponse;
import io.camunda.service.exception.ServiceException;
import io.camunda.util.EnumUtil;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult;
import io.camunda.zeebe.gateway.protocol.rest.ActivatedJobResult;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationCreateResult;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationCreatedResult;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.CreateProcessInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentDecisionRequirementsResult;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentDecisionResult;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentFormResult;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentMetadataResult;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentProcessResult;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentResourceResult;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentResult;
import io.camunda.zeebe.gateway.protocol.rest.DocumentCreationBatchResponse;
import io.camunda.zeebe.gateway.protocol.rest.DocumentCreationFailureDetail;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.protocol.rest.DocumentReference;
import io.camunda.zeebe.gateway.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.EvaluateDecisionResult;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionInputItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateResult;
import io.camunda.zeebe.gateway.protocol.rest.JobKindEnum;
import io.camunda.zeebe.gateway.protocol.rest.JobListenerEventTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleCreateResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleUpdateResult;
import io.camunda.zeebe.gateway.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.zeebe.gateway.protocol.rest.MessageCorrelationResult;
import io.camunda.zeebe.gateway.protocol.rest.MessagePublicationResult;
import io.camunda.zeebe.gateway.protocol.rest.ResourceResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleCreateResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleUpdateResult;
import io.camunda.zeebe.gateway.protocol.rest.SignalBroadcastResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateResult;
import io.camunda.zeebe.gateway.protocol.rest.UserCreateResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskProperties;
import io.camunda.zeebe.gateway.protocol.rest.UserUpdateResult;
import io.camunda.zeebe.gateway.rest.util.KeyUtil;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedOutputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.util.Either;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public final class ResponseMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Logger LOG = LoggerFactory.getLogger(ResponseMapper.class);

  /**
   * Date format <code>uuuu-MM-dd'T'HH:mm:ss.SSS[SSSSSS]Z</code>, always creating
   * millisecond-precision outputs at least, with up to 9 digits of nanosecond precision if present
   * in the date. Examples:
   *
   * <ul>
   *   <li>2020-11-11T10:10.11.123Z
   *   <li>2020-11-11T10:10.00.000Z
   *   <li>2020-11-11T10:10.00.00301Z
   *   <li>2020-11-11T10:10.00.003013456Z
   * </ul>
   */
  private static final DateTimeFormatter DATE_RESPONSE_MAPPER =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .append(DateTimeFormatter.ISO_LOCAL_DATE)
          .appendLiteral('T')
          .appendValue(HOUR_OF_DAY, 2)
          .appendLiteral(':')
          .appendValue(MINUTE_OF_HOUR, 2)
          .appendLiteral(':')
          .appendValue(SECOND_OF_MINUTE, 2)
          .appendFraction(NANO_OF_SECOND, 3, 9, true)
          .parseLenient()
          .appendOffsetId()
          .parseStrict()
          .toFormatter();

  public static String formatDate(final OffsetDateTime date) {
    return date == null ? null : DATE_RESPONSE_MAPPER.format(date);
  }

  public static JobActivationResult<io.camunda.zeebe.gateway.protocol.rest.JobActivationResult>
      toActivateJobsResponse(
          final io.camunda.zeebe.gateway.impl.job.JobActivationResponse activationResponse) {
    final Iterator<LongValue> jobKeys = activationResponse.brokerResponse().jobKeys().iterator();
    final Iterator<JobRecord> jobs = activationResponse.brokerResponse().jobs().iterator();

    long currentResponseSize = 0L;
    final io.camunda.zeebe.gateway.protocol.rest.JobActivationResult response =
        new io.camunda.zeebe.gateway.protocol.rest.JobActivationResult();

    final List<ActivatedJobResult> sizeExceedingJobs = new ArrayList<>();
    final List<ActivatedJobResult> responseJobs = new ArrayList<>();

    while (jobKeys.hasNext() && jobs.hasNext()) {
      final LongValue jobKey = jobKeys.next();
      final JobRecord job = jobs.next();
      final ActivatedJobResult activatedJob = toActivatedJob(jobKey.getValue(), job);

      // This is the message size of the message from the broker, not the size of the REST message
      final int activatedJobSize = job.getLength();
      if (currentResponseSize + activatedJobSize <= activationResponse.maxResponseSize()) {
        responseJobs.add(activatedJob);
        currentResponseSize += activatedJobSize;
      } else {
        sizeExceedingJobs.add(activatedJob);
      }
    }

    response.setJobs(responseJobs);

    return new RestJobActivationResult(response, sizeExceedingJobs);
  }

  private static ActivatedJobResult toActivatedJob(final long jobKey, final JobRecord job) {
    return new ActivatedJobResult()
        .jobKey(KeyUtil.keyToString(jobKey))
        .type(job.getType())
        .processDefinitionId(job.getBpmnProcessId())
        .elementId(job.getElementId())
        .processInstanceKey(KeyUtil.keyToString(job.getProcessInstanceKey()))
        .processDefinitionVersion(job.getProcessDefinitionVersion())
        .processDefinitionKey(KeyUtil.keyToString(job.getProcessDefinitionKey()))
        .elementInstanceKey(KeyUtil.keyToString(job.getElementInstanceKey()))
        .worker(bufferAsString(job.getWorkerBuffer()))
        .retries(job.getRetries())
        .deadline(job.getDeadline())
        .variables(job.getVariables())
        .customHeaders(job.getCustomHeadersObjectMap())
        .userTask(toUserTaskProperties(job))
        .tenantId(job.getTenantId())
        .kind(EnumUtil.convert(job.getJobKind(), JobKindEnum.class))
        .listenerEventType(
            EnumUtil.convert(job.getJobListenerEventType(), JobListenerEventTypeEnum.class));
  }

  private static UserTaskProperties toUserTaskProperties(final JobRecord job) {
    if (job.getJobKind() != TASK_LISTENER || CollectionUtils.isEmpty(job.getCustomHeaders())) {
      return null;
    }

    final var headers = job.getCustomHeaders();
    final var props = new UserTaskProperties();

    props.setAction(headers.get(Protocol.USER_TASK_ACTION_HEADER_NAME));
    props.setAssignee(headers.get(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME));
    props.setCandidateGroups(
        mapStringToList(headers.get(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME)));
    props.setCandidateUsers(
        mapStringToList(headers.get(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME)));
    props.setChangedAttributes(
        mapStringToList(headers.get(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME)));
    props.setDueDate(headers.get(Protocol.USER_TASK_DUE_DATE_HEADER_NAME));
    props.setFollowUpDate(headers.get(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME));
    props.setFormKey(headers.get(Protocol.USER_TASK_FORM_KEY_HEADER_NAME));
    props.setPriority(toIntegerOrNull(headers.get(Protocol.USER_TASK_PRIORITY_HEADER_NAME)));
    props.setUserTaskKey(headers.get(Protocol.USER_TASK_KEY_HEADER_NAME));

    return props;
  }

  public static List<String> mapStringToList(final String input) {
    if (input == null || input.isEmpty()) {
      return List.of();
    }

    try {
      return OBJECT_MAPPER.readValue(input, new TypeReference<>() {});
    } catch (final Exception e) {
      LOG.warn("Failed to map string to list: {}", input, e);
      return List.of();
    }
  }

  public static Integer toIntegerOrNull(final String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (final NumberFormatException e) {
      LOG.warn("Failed to parse Integer from value: {}", value, e);
      return null;
    }
  }

  public static ResponseEntity<Object> toMessageCorrelationResponse(
      final MessageCorrelationRecord brokerResponse) {
    final var response =
        new MessageCorrelationResult()
            .messageKey(KeyUtil.keyToString(brokerResponse.getMessageKey()))
            .tenantId(brokerResponse.getTenantId())
            .processInstanceKey(KeyUtil.keyToString(brokerResponse.getProcessInstanceKey()));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<StreamingResponseBody> toDocumentContentResponse(
      final DocumentContentResponse response) {
    final MediaType mediaType = resolveMediaType(response);
    return ResponseEntity.ok()
        .contentType(mediaType)
        .body(
            bodyStream -> {
              try (final var contentInputStream = response.content()) {
                contentInputStream.transferTo(bodyStream);
              }
            });
  }

  private static MediaType resolveMediaType(final DocumentContentResponse contentResponse) {
    try {
      final var contentType = contentResponse.contentType();
      if (contentType == null) {
        return MediaType.APPLICATION_OCTET_STREAM;
      }
      return MediaType.parseMediaType(contentResponse.contentType());
    } catch (final InvalidMediaTypeException e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  public static ResponseEntity<Object> toDocumentReference(
      final DocumentReferenceResponse response) {
    final var reference = transformDocumentReferenceResponse(response);
    return new ResponseEntity<>(reference, HttpStatus.CREATED);
  }

  public static ResponseEntity<Object> toDocumentReferenceBatch(
      final List<Either<DocumentErrorResponse, DocumentReferenceResponse>> responses) {
    final List<DocumentReferenceResponse> successful =
        responses.stream().filter(Either::isRight).map(Either::get).toList();

    final var response = new DocumentCreationBatchResponse();

    if (successful.size() == responses.size()) {
      final List<DocumentReference> references =
          successful.stream().map(ResponseMapper::transformDocumentReferenceResponse).toList();
      response.setCreatedDocuments(references);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    successful.stream()
        .map(ResponseMapper::transformDocumentReferenceResponse)
        .forEach(response::addCreatedDocumentsItem);

    responses.stream()
        .filter(Either::isLeft)
        .map(Either::getLeft)
        .forEach(
            error -> {
              final var detail = new DocumentCreationFailureDetail();
              final var defaultProblemDetail = mapDocumentErrorToProblem(error.error());
              detail.setDetail(defaultProblemDetail.getDetail());
              detail.setFileName(error.request().metadata().fileName());
              response.addFailedDocumentsItem(detail);
            });
    return ResponseEntity.status(HttpStatus.MULTI_STATUS)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(response);
  }

  public static ProblemDetail mapDocumentErrorToProblem(final ServiceException e) {
    final String detail = e.getMessage();
    final HttpStatusCode status = RestErrorMapper.mapStatus(e.getStatus());
    return RestErrorMapper.createProblemDetail(status, detail, e.getStatus().name());
  }

  private static DocumentReference transformDocumentReferenceResponse(
      final DocumentReferenceResponse response) {
    final var internalMetadata = response.metadata();
    final var externalMetadata =
        new DocumentMetadata()
            .expiresAt(
                Optional.ofNullable(internalMetadata.expiresAt())
                    .map(Object::toString)
                    .orElse(null))
            .fileName(internalMetadata.fileName())
            .size(internalMetadata.size())
            .contentType(internalMetadata.contentType())
            .processDefinitionId(internalMetadata.processDefinitionId())
            .processInstanceKey(KeyUtil.keyToString(internalMetadata.processInstanceKey()));
    Optional.ofNullable(internalMetadata.customProperties())
        .ifPresent(map -> map.forEach(externalMetadata::putCustomPropertiesItem));
    return new DocumentReference()
        .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
        .documentId(response.documentId())
        .storeId(response.storeId())
        .contentHash(response.contentHash())
        .metadata(externalMetadata);
  }

  public static ResponseEntity<Object> toDocumentLinkResponse(final DocumentLink documentLink) {
    final var externalDocumentLink = new io.camunda.zeebe.gateway.protocol.rest.DocumentLink();
    externalDocumentLink.setExpiresAt(documentLink.expiresAt().toString());
    externalDocumentLink.setUrl(documentLink.link());
    return new ResponseEntity<>(externalDocumentLink, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toDeployResourceResponse(
      final DeploymentRecord brokerResponse) {
    final var response =
        new DeploymentResult()
            .deploymentKey(KeyUtil.keyToString(brokerResponse.getDeploymentKey()))
            .tenantId(brokerResponse.getTenantId());
    addDeployedProcess(response, brokerResponse.getProcessesMetadata());
    addDeployedDecision(response, brokerResponse.decisionsMetadata());
    addDeployedDecisionRequirements(response, brokerResponse.decisionRequirementsMetadata());
    addDeployedForm(response, brokerResponse.formMetadata());
    addDeployedResource(response, brokerResponse.resourceMetadata());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toGetResourceResponse(final ResourceRecord resourceRecord) {
    final var response =
        new ResourceResult()
            .resourceName(resourceRecord.getResourceName())
            .version(resourceRecord.getVersion())
            .versionTag(resourceRecord.getVersionTag())
            .resourceId(resourceRecord.getResourceId())
            .tenantId(resourceRecord.getTenantId())
            .resourceKey(String.valueOf(resourceRecord.getResourceKey()));
    return ResponseEntity.ok(response);
  }

  public static ResponseEntity<Object> toGetResourceContentResponse(
      final ResourceRecord resourceRecord) {
    return ResponseEntity.ok(resourceRecord.getResourceProp());
  }

  public static ResponseEntity<Object> toMessagePublicationResponse(
      final BrokerResponse<MessageRecord> brokerResponse) {

    final var response =
        new MessagePublicationResult()
            .messageKey(KeyUtil.keyToString(brokerResponse.getKey()))
            .tenantId(brokerResponse.getResponse().getTenantId());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private static void addDeployedForm(
      final DeploymentResult response, final ValueArray<FormMetadataRecord> formMetadataRecords) {
    formMetadataRecords.stream()
        .map(
            form ->
                new DeploymentFormResult()
                    .formId(form.getFormId())
                    .version(form.getVersion())
                    .formKey(KeyUtil.keyToString(form.getFormKey()))
                    .resourceName(form.getResourceName())
                    .tenantId(form.getTenantId()))
        .map(deploymentForm -> new DeploymentMetadataResult().form(deploymentForm))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedResource(
      final DeploymentResult response,
      final ValueArray<ResourceMetadataRecord> resourceMetadataRecords) {
    resourceMetadataRecords.stream()
        .map(
            resource ->
                new DeploymentResourceResult()
                    .resourceId(resource.getResourceId())
                    .version(resource.getVersion())
                    .resourceKey(KeyUtil.keyToString(resource.getResourceKey()))
                    .resourceName(resource.getResourceName())
                    .tenantId(resource.getTenantId()))
        .map(deploymentForm -> new DeploymentMetadataResult().resource(deploymentForm))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedDecisionRequirements(
      final DeploymentResult response,
      final ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataRecords) {
    decisionRequirementsMetadataRecords.stream()
        .map(
            decisionRequirement ->
                new DeploymentDecisionRequirementsResult()
                    .decisionRequirementsId(decisionRequirement.getDecisionRequirementsId())
                    .version(decisionRequirement.getDecisionRequirementsVersion())
                    .decisionRequirementsName(decisionRequirement.getDecisionRequirementsName())
                    .tenantId(decisionRequirement.getTenantId())
                    .decisionRequirementsKey(
                        KeyUtil.keyToString(decisionRequirement.getDecisionRequirementsKey()))
                    .resourceName(decisionRequirement.getResourceName()))
        .map(
            deploymentDecisionRequirement ->
                new DeploymentMetadataResult().decisionRequirements(deploymentDecisionRequirement))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedDecision(
      final DeploymentResult response, final ValueArray<DecisionRecord> decisionRecords) {
    decisionRecords.stream()
        .map(
            decision ->
                new DeploymentDecisionResult()
                    .decisionDefinitionId(decision.getDecisionId())
                    .version(decision.getVersion())
                    .decisionDefinitionKey(KeyUtil.keyToString(decision.getDecisionKey()))
                    .name(decision.getDecisionName())
                    .tenantId(decision.getTenantId())
                    .decisionRequirementsId(decision.getDecisionRequirementsId())
                    .decisionRequirementsKey(
                        KeyUtil.keyToString(decision.getDecisionRequirementsKey())))
        .map(
            deploymentDecision ->
                new DeploymentMetadataResult().decisionDefinition(deploymentDecision))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedProcess(
      final DeploymentResult response, final List<ProcessMetadataValue> processesMetadata) {
    processesMetadata.stream()
        .map(
            process ->
                new DeploymentProcessResult()
                    .processDefinitionId(process.getBpmnProcessId())
                    .processDefinitionVersion(process.getVersion())
                    .processDefinitionKey(KeyUtil.keyToString(process.getProcessDefinitionKey()))
                    .tenantId(process.getTenantId())
                    .resourceName(process.getResourceName()))
        .map(
            deploymentProcess ->
                new DeploymentMetadataResult().processDefinition(deploymentProcess))
        .forEach(response::addDeploymentsItem);
  }

  public static ResponseEntity<Object> toCreateProcessInstanceResponse(
      final ProcessInstanceCreationRecord brokerResponse) {
    return buildCreateProcessInstanceResponse(
        brokerResponse.getProcessDefinitionKey(),
        brokerResponse.getBpmnProcessId(),
        brokerResponse.getVersion(),
        brokerResponse.getProcessInstanceKey(),
        brokerResponse.getTenantId(),
        null);
  }

  public static ResponseEntity<Object> toCreateProcessInstanceWithResultResponse(
      final ProcessInstanceResultRecord brokerResponse) {
    return buildCreateProcessInstanceResponse(
        brokerResponse.getProcessDefinitionKey(),
        brokerResponse.getBpmnProcessId(),
        brokerResponse.getVersion(),
        brokerResponse.getProcessInstanceKey(),
        brokerResponse.getTenantId(),
        brokerResponse.getVariables());
  }

  private static ResponseEntity<Object> buildCreateProcessInstanceResponse(
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final Integer version,
      final Long processInstanceKey,
      final String tenantId,
      final Map<String, Object> variables) {
    final var response =
        new CreateProcessInstanceResult()
            .processDefinitionKey(KeyUtil.keyToString(processDefinitionKey))
            .processDefinitionId(bpmnProcessId)
            .processDefinitionVersion(version)
            .processInstanceKey(KeyUtil.keyToString(processInstanceKey))
            .tenantId(tenantId);
    if (variables != null) {
      response.variables(variables);
    }

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toBatchOperationCreatedWithResultResponse(
      final BatchOperationCreationRecord brokerResponse) {
    final var response =
        new BatchOperationCreatedResult()
            .batchOperationKey(KeyUtil.keyToString(brokerResponse.getBatchOperationKey()))
            .batchOperationType(
                BatchOperationTypeEnum.valueOf(brokerResponse.getBatchOperationType().name()));
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toSignalBroadcastResponse(
      final BrokerResponse<SignalRecord> brokerResponse) {
    final var response =
        new SignalBroadcastResult()
            .signalKey(KeyUtil.keyToString(brokerResponse.getKey()))
            .tenantId(brokerResponse.getResponse().getTenantId());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toAuthorizationCreateResponse(
      final AuthorizationRecord authorizationRecord) {
    final var response =
        new AuthorizationCreateResult()
            .authorizationKey(KeyUtil.keyToString(authorizationRecord.getAuthorizationKey()));
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  public static ResponseEntity<Object> toUserCreateResponse(final UserRecord userRecord) {
    final var response =
        new UserCreateResult()
            .username(userRecord.getUsername())
            .email(userRecord.getEmail())
            .name(userRecord.getName());
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  public static ResponseEntity<Object> toUserUpdateResponse(final UserRecord userRecord) {
    final var response =
        new UserUpdateResult()
            .username(userRecord.getUsername())
            .email(userRecord.getEmail())
            .name(userRecord.getName());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toRoleCreateResponse(final RoleRecord roleRecord) {
    final var response =
        new RoleCreateResult()
            .roleId(roleRecord.getRoleId())
            .name(roleRecord.getName())
            .description(roleRecord.getDescription());
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  public static ResponseEntity<Object> toRoleUpdateResponse(final RoleRecord roleRecord) {
    final var response =
        new RoleUpdateResult()
            .roleId(roleRecord.getRoleId())
            .description(roleRecord.getDescription())
            .name(roleRecord.getName());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toGroupCreateResponse(final GroupRecord groupRecord) {
    final var response =
        new GroupCreateResult()
            .name(groupRecord.getName())
            .groupId(groupRecord.getGroupId())
            .description(groupRecord.getDescription());
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  public static ResponseEntity<Object> toGroupUpdateResponse(final GroupRecord groupRecord) {
    final var response =
        new GroupUpdateResult()
            .groupId(groupRecord.getGroupId())
            .description(groupRecord.getDescription())
            .name(groupRecord.getName());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toTenantCreateResponse(final TenantRecord record) {
    final var response =
        new TenantCreateResult()
            .tenantId(record.getTenantId())
            .name(record.getName())
            .description(record.getDescription());
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  public static ResponseEntity<Object> toTenantUpdateResponse(final TenantRecord record) {
    final var response =
        new TenantUpdateResult()
            .tenantId(record.getTenantId())
            .name(record.getName())
            .description(record.getDescription());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toMappingRuleCreateResponse(final MappingRuleRecord record) {
    final var response =
        new MappingRuleCreateResult()
            .claimName(record.getClaimName())
            .claimValue(record.getClaimValue())
            .mappingRuleId(record.getMappingRuleId())
            .name(record.getName());
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  public static ResponseEntity<Object> toMappingRuleUpdateResponse(final MappingRuleRecord record) {
    final var response =
        new MappingRuleUpdateResult()
            .claimName(record.getClaimName())
            .claimValue(record.getClaimValue())
            .mappingRuleId(record.getMappingRuleId())
            .name(record.getName());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toEvaluateDecisionResponse(
      final BrokerResponse<DecisionEvaluationRecord> brokerResponse) {
    final var decisionEvaluationRecord = brokerResponse.getResponse();
    final var response =
        new EvaluateDecisionResult()
            .decisionDefinitionId(decisionEvaluationRecord.getDecisionId())
            .decisionDefinitionKey(KeyUtil.keyToString(decisionEvaluationRecord.getDecisionKey()))
            .decisionDefinitionName(decisionEvaluationRecord.getDecisionName())
            .decisionDefinitionVersion(decisionEvaluationRecord.getDecisionVersion())
            .decisionRequirementsId(decisionEvaluationRecord.getDecisionRequirementsId())
            .decisionRequirementsKey(
                KeyUtil.keyToString(decisionEvaluationRecord.getDecisionRequirementsKey()))
            .output(decisionEvaluationRecord.getDecisionOutput())
            .failedDecisionDefinitionId(decisionEvaluationRecord.getFailedDecisionId())
            .failureMessage(decisionEvaluationRecord.getEvaluationFailureMessage())
            .tenantId(decisionEvaluationRecord.getTenantId())
            .decisionInstanceKey(KeyUtil.keyToString(brokerResponse.getKey()));

    buildEvaluatedDecisions(decisionEvaluationRecord, response);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private static void buildEvaluatedDecisions(
      final DecisionEvaluationRecord decisionEvaluationRecord,
      final EvaluateDecisionResult response) {
    decisionEvaluationRecord.getEvaluatedDecisions().stream()
        .map(
            evaluatedDecision ->
                new EvaluatedDecisionResult()
                    .decisionDefinitionKey(KeyUtil.keyToString(evaluatedDecision.getDecisionKey()))
                    .decisionDefinitionId(evaluatedDecision.getDecisionId())
                    .decisionDefinitionName(evaluatedDecision.getDecisionName())
                    .decisionDefinitionVersion(evaluatedDecision.getDecisionVersion())
                    .output(evaluatedDecision.getDecisionOutput())
                    .tenantId(evaluatedDecision.getTenantId())
                    .evaluatedInputs(buildEvaluatedInputs(evaluatedDecision.getEvaluatedInputs()))
                    .matchedRules(buildMatchedRules(evaluatedDecision.getMatchedRules())))
        .forEach(response::addEvaluatedDecisionsItem);
  }

  private static List<MatchedDecisionRuleItem> buildMatchedRules(
      final List<MatchedRuleValue> matchedRuleValues) {
    return matchedRuleValues.stream()
        .map(
            matchedRuleValue ->
                new MatchedDecisionRuleItem()
                    .ruleId(matchedRuleValue.getRuleId())
                    .ruleIndex(matchedRuleValue.getRuleIndex())
                    .evaluatedOutputs(
                        buildEvaluatedOutputs(matchedRuleValue.getEvaluatedOutputs())))
        .toList();
  }

  private static List<EvaluatedDecisionOutputItem> buildEvaluatedOutputs(
      final List<EvaluatedOutputValue> evaluatedOutputs) {
    return evaluatedOutputs.stream()
        .map(
            evaluatedOutput ->
                new EvaluatedDecisionOutputItem()
                    .outputId(evaluatedOutput.getOutputId())
                    .outputName(evaluatedOutput.getOutputName())
                    .outputValue(evaluatedOutput.getOutputValue()))
        .toList();
  }

  private static List<EvaluatedDecisionInputItem> buildEvaluatedInputs(
      final List<EvaluatedInputValue> inputValues) {
    return inputValues.stream()
        .map(
            evaluatedInputValue ->
                new EvaluatedDecisionInputItem()
                    .inputId(evaluatedInputValue.getInputId())
                    .inputName(evaluatedInputValue.getInputName())
                    .inputValue(evaluatedInputValue.getInputValue()))
        .toList();
  }

  static class RestJobActivationResult
      implements JobActivationResult<io.camunda.zeebe.gateway.protocol.rest.JobActivationResult> {

    private final io.camunda.zeebe.gateway.protocol.rest.JobActivationResult response;
    private final List<ActivatedJobResult> sizeExceedingJobs;

    RestJobActivationResult(
        final io.camunda.zeebe.gateway.protocol.rest.JobActivationResult response,
        final List<ActivatedJobResult> sizeExceedingJobs) {
      this.response = response;
      this.sizeExceedingJobs = sizeExceedingJobs;
    }

    @Override
    public int getJobsCount() {
      return response.getJobs().size();
    }

    @Override
    public List<ActivatedJob> getJobs() {
      return response.getJobs().stream()
          .map(j -> new ActivatedJob(KeyUtil.keyToLong(j.getJobKey()), j.getRetries()))
          .toList();
    }

    @Override
    public io.camunda.zeebe.gateway.protocol.rest.JobActivationResult getActivateJobsResponse() {
      return response;
    }

    @Override
    public List<ActivatedJob> getJobsToDefer() {
      final var result = new ArrayList<ActivatedJob>(sizeExceedingJobs.size());
      for (final var job : sizeExceedingJobs) {
        try {
          final var key = job.getJobKey();
          result.add(new ActivatedJob(KeyUtil.keyToLong(key), job.getRetries()));
        } catch (final NumberFormatException ignored) {
          // could happen
          LOG.warn(
              "Expected job key to be numeric, but was {}. The job cannot be returned to the broker, but it will be retried after timeout",
              job.getJobKey());
        }
      }
      return result;
    }
  }
}
