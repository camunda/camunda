/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToLong;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToStringOrNull;
import static io.camunda.zeebe.protocol.record.value.JobKind.TASK_LISTENER;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentLink;
import io.camunda.gateway.protocol.model.ActivatedJobResult;
import io.camunda.gateway.protocol.model.AuthorizationCreateResult;
import io.camunda.gateway.protocol.model.BatchOperationCreatedResult;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.gateway.protocol.model.BrokerInfo;
import io.camunda.gateway.protocol.model.ClusterVariableResult;
import io.camunda.gateway.protocol.model.ClusterVariableScopeEnum;
import io.camunda.gateway.protocol.model.CreateProcessInstanceResult;
import io.camunda.gateway.protocol.model.DeleteResourceResponse;
import io.camunda.gateway.protocol.model.DeploymentDecisionRequirementsResult;
import io.camunda.gateway.protocol.model.DeploymentDecisionResult;
import io.camunda.gateway.protocol.model.DeploymentFormResult;
import io.camunda.gateway.protocol.model.DeploymentMetadataResult;
import io.camunda.gateway.protocol.model.DeploymentProcessResult;
import io.camunda.gateway.protocol.model.DeploymentResourceResult;
import io.camunda.gateway.protocol.model.DeploymentResult;
import io.camunda.gateway.protocol.model.DocumentCreationBatchResponse;
import io.camunda.gateway.protocol.model.DocumentCreationFailureDetail;
import io.camunda.gateway.protocol.model.DocumentMetadataResponse;
import io.camunda.gateway.protocol.model.DocumentReference;
import io.camunda.gateway.protocol.model.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.gateway.protocol.model.EvaluateConditionalResult;
import io.camunda.gateway.protocol.model.EvaluateDecisionResult;
import io.camunda.gateway.protocol.model.EvaluatedDecisionInputItem;
import io.camunda.gateway.protocol.model.EvaluatedDecisionOutputItem;
import io.camunda.gateway.protocol.model.EvaluatedDecisionResult;
import io.camunda.gateway.protocol.model.ExpressionEvaluationResult;
import io.camunda.gateway.protocol.model.ExpressionEvaluationWarningItem;
import io.camunda.gateway.protocol.model.GroupCreateResult;
import io.camunda.gateway.protocol.model.GroupUpdateResult;
import io.camunda.gateway.protocol.model.JobKindEnum;
import io.camunda.gateway.protocol.model.JobListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.MappingRuleCreateResult;
import io.camunda.gateway.protocol.model.MappingRuleUpdateResult;
import io.camunda.gateway.protocol.model.MatchedDecisionRuleItem;
import io.camunda.gateway.protocol.model.MessageCorrelationResult;
import io.camunda.gateway.protocol.model.MessagePublicationResult;
import io.camunda.gateway.protocol.model.Partition.HealthEnum;
import io.camunda.gateway.protocol.model.Partition.RoleEnum;
import io.camunda.gateway.protocol.model.ProcessInstanceReference;
import io.camunda.gateway.protocol.model.ResourceResult;
import io.camunda.gateway.protocol.model.RoleCreateResult;
import io.camunda.gateway.protocol.model.RoleUpdateResult;
import io.camunda.gateway.protocol.model.SignalBroadcastResult;
import io.camunda.gateway.protocol.model.TenantCreateResult;
import io.camunda.gateway.protocol.model.TenantUpdateResult;
import io.camunda.gateway.protocol.model.TopologyResponse;
import io.camunda.gateway.protocol.model.UserCreateResult;
import io.camunda.gateway.protocol.model.UserTaskProperties;
import io.camunda.gateway.protocol.model.UserUpdateResult;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.service.DocumentServices.DocumentContentResponse;
import io.camunda.service.DocumentServices.DocumentErrorResponse;
import io.camunda.service.DocumentServices.DocumentReferenceResponse;
import io.camunda.service.TopologyServices.Broker;
import io.camunda.service.TopologyServices.Partition;
import io.camunda.service.TopologyServices.Topology;
import io.camunda.service.exception.ServiceException;
import io.camunda.util.EnumUtil;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.CollectionUtils;

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
    return DATE_RESPONSE_MAPPER.format(date);
  }

  public static @Nullable String formatDateOrNull(final @Nullable OffsetDateTime date) {
    return date == null ? null : DATE_RESPONSE_MAPPER.format(date);
  }

  public static JobActivationResult<io.camunda.gateway.protocol.model.JobActivationResult>
      toActivateJobsResponse(
          final io.camunda.zeebe.gateway.impl.job.JobActivationResponse activationResponse) {
    final Iterator<LongValue> jobKeys = activationResponse.brokerResponse().jobKeys().iterator();
    final Iterator<JobRecord> jobs = activationResponse.brokerResponse().jobs().iterator();

    long currentResponseSize = 0L;

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

    final io.camunda.gateway.protocol.model.JobActivationResult response =
        io.camunda.gateway.protocol.model.JobActivationResult.Builder.create()
            .jobs(responseJobs)
            .build();

    return new RestJobActivationResult(response, sizeExceedingJobs);
  }

  private static ActivatedJobResult toActivatedJob(final long jobKey, final JobRecord job) {
    // rootProcessInstanceKey is only set for process instances created after version 8.9
    final long rootProcessInstanceKey = job.getRootProcessInstanceKey();
    return ActivatedJobResult.Builder.create()
        .type(job.getType())
        .processDefinitionId(job.getBpmnProcessId())
        .processDefinitionVersion(job.getProcessDefinitionVersion())
        .elementId(job.getElementId())
        .customHeaders(job.getCustomHeadersObjectMap())
        .worker(bufferAsString(job.getWorkerBuffer()))
        .retries(job.getRetries())
        .deadline(job.getDeadline())
        .variables(job.getVariables())
        .tenantId(job.getTenantId())
        .jobKey(keyToString(jobKey))
        .processInstanceKey(keyToString(job.getProcessInstanceKey()))
        .processDefinitionKey(keyToString(job.getProcessDefinitionKey()))
        .elementInstanceKey(keyToString(job.getElementInstanceKey()))
        .kind(EnumUtil.convert(job.getJobKind(), JobKindEnum.class))
        .listenerEventType(
            EnumUtil.convert(job.getJobListenerEventType(), JobListenerEventTypeEnum.class))
        .rootProcessInstanceKey(
            rootProcessInstanceKey > 0 ? keyToString(rootProcessInstanceKey) : null)
        .tags(job.getTags())
        .userTask(toUserTaskProperties(job))
        .build();
  }

  private static @Nullable UserTaskProperties toUserTaskProperties(final JobRecord job) {
    if (job.getJobKind() != TASK_LISTENER || CollectionUtils.isEmpty(job.getCustomHeaders())) {
      return null;
    }

    final var headers = job.getCustomHeaders();
    return UserTaskProperties.Builder.create()
        .candidateGroups(
            mapStringToList(headers.get(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME)))
        .candidateUsers(
            mapStringToList(headers.get(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME)))
        .changedAttributes(
            mapStringToList(headers.get(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME)))
        .action(requireNonNull(headers.get(Protocol.USER_TASK_ACTION_HEADER_NAME), "action"))
        .assignee(headers.get(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME))
        .dueDate(headers.get(Protocol.USER_TASK_DUE_DATE_HEADER_NAME))
        .followUpDate(headers.get(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME))
        .formKey(headers.get(Protocol.USER_TASK_FORM_KEY_HEADER_NAME))
        .priority(toIntegerOrNull(headers.get(Protocol.USER_TASK_PRIORITY_HEADER_NAME)))
        .userTaskKey(headers.get(Protocol.USER_TASK_KEY_HEADER_NAME))
        .build();
  }

  public static List<String> mapStringToList(final @Nullable String input) {
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

  public static @Nullable Integer toIntegerOrNull(final @Nullable String value) {
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

  public static MessageCorrelationResult toMessageCorrelationResponse(
      final MessageCorrelationRecord brokerResponse) {
    return MessageCorrelationResult.Builder.create()
        .tenantId(brokerResponse.getTenantId())
        .messageKey(keyToString(brokerResponse.getMessageKey()))
        .processInstanceKey(keyToString(brokerResponse.getProcessInstanceKey()))
        .build();
  }

  public static MediaType resolveMediaType(final DocumentContentResponse contentResponse) {
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

  public static DocumentCreationBatchResponse toDocumentReferenceBatch(
      final List<Either<DocumentErrorResponse, DocumentReferenceResponse>> responses) {
    final List<DocumentReference> createdDocuments = new ArrayList<>();
    final List<DocumentCreationFailureDetail> failedDocuments = new ArrayList<>();
    responses.forEach(
        documentResponse ->
            documentResponse.ifRightOrLeft(
                reference -> createdDocuments.add(toDocumentReference(reference)),
                error -> failedDocuments.add(toDocumentCreationFailure(error))));
    return DocumentCreationBatchResponse.Builder.create()
        .failedDocuments(failedDocuments)
        .createdDocuments(createdDocuments)
        .build();
  }

  public static DocumentReference toDocumentReference(final DocumentReferenceResponse response) {
    final var internalMetadata = response.metadata();
    final var externalMetadata =
        DocumentMetadataResponse.Builder.create()
            .fileName(internalMetadata.fileName())
            .expiresAt(
                Optional.ofNullable(internalMetadata.expiresAt())
                    .map(Object::toString)
                    .orElse(null))
            .size(internalMetadata.size())
            .contentType(internalMetadata.contentType())
            .customProperties(
                Optional.ofNullable(internalMetadata.customProperties()).orElse(Map.of()))
            .processDefinitionId(internalMetadata.processDefinitionId())
            .processInstanceKey(keyToStringOrNull(internalMetadata.processInstanceKey()))
            .build();
    return DocumentReference.Builder.create()
        .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
        .storeId(response.storeId())
        .documentId(response.documentId())
        .contentHash(response.contentHash())
        .metadata(externalMetadata)
        .build();
  }

  private static DocumentCreationFailureDetail toDocumentCreationFailure(
      final DocumentErrorResponse error) {
    final var defaultProblemDetail = mapDocumentErrorToProblem(error.error());
    return DocumentCreationFailureDetail.Builder.create()
        .detail(requireNonNull(defaultProblemDetail.getDetail(), "detail"))
        .fileName(error.request().metadata().fileName())
        .status(defaultProblemDetail.getStatus())
        .title(Objects.requireNonNullElse(defaultProblemDetail.getTitle(), ""))
        .build();
  }

  private static ProblemDetail mapDocumentErrorToProblem(final ServiceException e) {
    final String detail = Objects.requireNonNullElse(e.getMessage(), "");
    final HttpStatusCode status = GatewayErrorMapper.mapStatus(e.getStatus());
    return GatewayErrorMapper.createProblemDetail(status, detail, e.getStatus().name());
  }

  public static io.camunda.gateway.protocol.model.DocumentLink toDocumentLinkResponse(
      final DocumentLink documentLink) {
    return io.camunda.gateway.protocol.model.DocumentLink.Builder.create()
        .url(documentLink.link())
        .expiresAt(documentLink.expiresAt().toString())
        .build();
  }

  public static DeploymentResult toDeployResourceResponse(final DeploymentRecord brokerResponse) {
    final List<DeploymentMetadataResult> deployments = new ArrayList<>();
    deployments.addAll(buildDeployedProcesses(brokerResponse.getProcessesMetadata()));
    deployments.addAll(buildDeployedDecisions(brokerResponse.decisionsMetadata()));
    deployments.addAll(
        buildDeployedDecisionRequirements(brokerResponse.decisionRequirementsMetadata()));
    deployments.addAll(buildDeployedForms(brokerResponse.formMetadata()));
    deployments.addAll(buildDeployedResources(brokerResponse.resourceMetadata()));
    return DeploymentResult.Builder.create()
        .deploymentKey(keyToString(brokerResponse.getDeploymentKey()))
        .tenantId(brokerResponse.getTenantId())
        .deployments(deployments)
        .build();
  }

  public static DeleteResourceResponse toDeleteResourceResponse(
      final ResourceDeletionRecord brokerResponse) {
    final BatchOperationCreatedResult batchOperation;
    if (brokerResponse.isDeleteHistory() && brokerResponse.getBatchOperationKey() > 0) {
      batchOperation =
          BatchOperationCreatedResult.Builder.create()
              .batchOperationKey(keyToString(brokerResponse.getBatchOperationKey()))
              .batchOperationType(
                  BatchOperationTypeEnum.valueOf(brokerResponse.getBatchOperationType().name()))
              .build();
    } else {
      batchOperation = null;
    }
    return DeleteResourceResponse.Builder.create()
        .resourceKey(keyToString(brokerResponse.getResourceKey()))
        .batchOperation(batchOperation)
        .build();
  }

  public static ResourceResult toGetResourceResponse(final ResourceRecord resourceRecord) {
    return ResourceResult.Builder.create()
        .resourceName(resourceRecord.getResourceName())
        .version(resourceRecord.getVersion())
        .versionTag(emptyToNull(resourceRecord.getVersionTag()))
        .resourceId(resourceRecord.getResourceId())
        .tenantId(resourceRecord.getTenantId())
        .resourceKey(String.valueOf(resourceRecord.getResourceKey()))
        .build();
  }

  public static ResourceResult toGetResourceResponse(
      final DeployedResourceEntity deployedResourceEntity) {
    return ResourceResult.Builder.create()
        .resourceName(deployedResourceEntity.resourceName())
        .version(deployedResourceEntity.version())
        .versionTag(emptyToNull(deployedResourceEntity.versionTag()))
        .resourceId(deployedResourceEntity.resourceId())
        .tenantId(deployedResourceEntity.tenantId())
        .resourceKey(String.valueOf(deployedResourceEntity.resourceKey()))
        .build();
  }

  public static String toGetResourceContentResponse(
      final DeployedResourceEntity deployedResourceEntity) {
    return requireNonNull(deployedResourceEntity.resourceContent(), "resourceContent");
  }

  public static MessagePublicationResult toMessagePublicationResponse(
      final BrokerResponse<MessageRecord> brokerResponse) {
    return MessagePublicationResult.Builder.create()
        .tenantId(brokerResponse.getResponse().getTenantId())
        .messageKey(keyToString(brokerResponse.getKey()))
        .build();
  }

  private static List<DeploymentMetadataResult> buildDeployedForms(
      final ValueArray<FormMetadataRecord> formMetadataRecords) {
    return formMetadataRecords.stream()
        .map(
            form ->
                DeploymentMetadataResult.Builder.create()
                    .processDefinition(null)
                    .decisionDefinition(null)
                    .decisionRequirements(null)
                    .form(
                        DeploymentFormResult.Builder.create()
                            .formId(form.getFormId())
                            .formKey(keyToString(form.getFormKey()))
                            .resourceName(form.getResourceName())
                            .tenantId(form.getTenantId())
                            .version(form.getVersion())
                            .build())
                    .resource(null)
                    .build())
        .toList();
  }

  private static List<DeploymentMetadataResult> buildDeployedResources(
      final ValueArray<ResourceMetadataRecord> resourceMetadataRecords) {
    return resourceMetadataRecords.stream()
        .map(
            resource ->
                DeploymentMetadataResult.Builder.create()
                    .processDefinition(null)
                    .decisionDefinition(null)
                    .decisionRequirements(null)
                    .form(null)
                    .resource(
                        DeploymentResourceResult.Builder.create()
                            .resourceId(resource.getResourceId())
                            .resourceKey(keyToString(resource.getResourceKey()))
                            .resourceName(resource.getResourceName())
                            .tenantId(resource.getTenantId())
                            .version(resource.getVersion())
                            .build())
                    .build())
        .toList();
  }

  private static List<DeploymentMetadataResult> buildDeployedDecisionRequirements(
      final ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataRecords) {
    return decisionRequirementsMetadataRecords.stream()
        .map(
            decisionRequirement ->
                DeploymentMetadataResult.Builder.create()
                    .processDefinition(null)
                    .decisionDefinition(null)
                    .decisionRequirements(
                        DeploymentDecisionRequirementsResult.Builder.create()
                            .decisionRequirementsId(decisionRequirement.getDecisionRequirementsId())
                            .decisionRequirementsKey(
                                keyToString(decisionRequirement.getDecisionRequirementsKey()))
                            .decisionRequirementsName(
                                decisionRequirement.getDecisionRequirementsName())
                            .resourceName(decisionRequirement.getResourceName())
                            .tenantId(decisionRequirement.getTenantId())
                            .version(decisionRequirement.getDecisionRequirementsVersion())
                            .build())
                    .form(null)
                    .resource(null)
                    .build())
        .toList();
  }

  private static List<DeploymentMetadataResult> buildDeployedDecisions(
      final ValueArray<DecisionRecord> decisionRecords) {
    return decisionRecords.stream()
        .map(
            decision ->
                DeploymentMetadataResult.Builder.create()
                    .processDefinition(null)
                    .decisionDefinition(
                        DeploymentDecisionResult.Builder.create()
                            .decisionDefinitionId(decision.getDecisionId())
                            .decisionDefinitionKey(keyToString(decision.getDecisionKey()))
                            .decisionRequirementsId(decision.getDecisionRequirementsId())
                            .decisionRequirementsKey(
                                keyToString(decision.getDecisionRequirementsKey()))
                            .name(decision.getDecisionName())
                            .tenantId(decision.getTenantId())
                            .version(decision.getVersion())
                            .build())
                    .decisionRequirements(null)
                    .form(null)
                    .resource(null)
                    .build())
        .toList();
  }

  private static List<DeploymentMetadataResult> buildDeployedProcesses(
      final List<ProcessMetadataValue> processesMetadata) {
    return processesMetadata.stream()
        .map(
            process ->
                DeploymentMetadataResult.Builder.create()
                    .processDefinition(
                        DeploymentProcessResult.Builder.create()
                            .processDefinitionId(process.getBpmnProcessId())
                            .processDefinitionVersion(process.getVersion())
                            .resourceName(process.getResourceName())
                            .processDefinitionKey(keyToString(process.getProcessDefinitionKey()))
                            .tenantId(process.getTenantId())
                            .build())
                    .decisionDefinition(null)
                    .decisionRequirements(null)
                    .form(null)
                    .resource(null)
                    .build())
        .toList();
  }

  public static CreateProcessInstanceResult toCreateProcessInstanceResponse(
      final ProcessInstanceCreationRecord brokerResponse) {
    return buildCreateProcessInstanceResponse(
        brokerResponse.getProcessDefinitionKey(),
        brokerResponse.getBpmnProcessId(),
        brokerResponse.getVersion(),
        brokerResponse.getProcessInstanceKey(),
        brokerResponse.getTenantId(),
        null,
        brokerResponse.getTags(),
        brokerResponse.getBusinessId());
  }

  public static CreateProcessInstanceResult toCreateProcessInstanceWithResultResponse(
      final ProcessInstanceResultRecord brokerResponse) {
    return buildCreateProcessInstanceResponse(
        brokerResponse.getProcessDefinitionKey(),
        brokerResponse.getBpmnProcessId(),
        brokerResponse.getVersion(),
        brokerResponse.getProcessInstanceKey(),
        brokerResponse.getTenantId(),
        brokerResponse.getVariables(),
        brokerResponse.getTags(),
        brokerResponse.getBusinessId());
  }

  private static CreateProcessInstanceResult buildCreateProcessInstanceResponse(
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final Integer version,
      final Long processInstanceKey,
      final String tenantId,
      final @Nullable Map<String, Object> variables,
      final @Nullable Set<String> tags,
      final String businessId) {
    return CreateProcessInstanceResult.Builder.create()
        .processDefinitionId(bpmnProcessId)
        .processDefinitionKey(keyToString(processDefinitionKey))
        .processDefinitionVersion(version)
        .tenantId(tenantId)
        .variables(variables != null ? variables : Map.of())
        .processInstanceKey(keyToString(processInstanceKey))
        .tags(tags != null ? tags : Set.of())
        // defaults to an empty string on the originating record
        // the conversion to null ensures response contract compliance
        .businessId(emptyToNull(businessId))
        .build();
  }

  public static BatchOperationCreatedResult toBatchOperationCreatedWithResultResponse(
      final BatchOperationCreationRecord brokerResponse) {
    return BatchOperationCreatedResult.Builder.create()
        .batchOperationKey(keyToString(brokerResponse.getBatchOperationKey()))
        .batchOperationType(
            BatchOperationTypeEnum.valueOf(brokerResponse.getBatchOperationType().name()))
        .build();
  }

  public static SignalBroadcastResult toSignalBroadcastResponse(
      final BrokerResponse<SignalRecord> brokerResponse) {
    return SignalBroadcastResult.Builder.create()
        .tenantId(brokerResponse.getResponse().getTenantId())
        .signalKey(keyToString(brokerResponse.getKey()))
        .build();
  }

  public static EvaluateConditionalResult toConditionalEvaluationResponse(
      final BrokerResponse<ConditionalEvaluationRecord> brokerResponse) {
    final var response = brokerResponse.getResponse();
    final var processInstances =
        response.getStartedProcessInstances().stream()
            .map(
                instance ->
                    ProcessInstanceReference.Builder.create()
                        .processDefinitionKey(keyToString(instance.getProcessDefinitionKey()))
                        .processInstanceKey(keyToString(instance.getProcessInstanceKey()))
                        .build())
            .toList();

    return EvaluateConditionalResult.Builder.create()
        .processInstances(processInstances)
        .conditionalEvaluationKey(keyToString(brokerResponse.getKey()))
        .tenantId(response.getTenantId())
        .build();
  }

  public static AuthorizationCreateResult toAuthorizationCreateResponse(
      final AuthorizationRecord authorizationRecord) {
    return AuthorizationCreateResult.Builder.create()
        .authorizationKey(keyToString(authorizationRecord.getAuthorizationKey()))
        .build();
  }

  public static UserCreateResult toUserCreateResponse(final UserRecord userRecord) {
    return UserCreateResult.Builder.create()
        .username(userRecord.getUsername())
        .name(userRecord.getName())
        .email(userRecord.getEmail())
        .build();
  }

  public static UserUpdateResult toUserUpdateResponse(final UserRecord userRecord) {
    return UserUpdateResult.Builder.create()
        .username(userRecord.getUsername())
        .name(userRecord.getName())
        .email(userRecord.getEmail())
        .build();
  }

  public static RoleCreateResult toRoleCreateResponse(final RoleRecord roleRecord) {
    return RoleCreateResult.Builder.create()
        .roleId(roleRecord.getRoleId())
        .name(roleRecord.getName())
        .description(roleRecord.getDescription())
        .build();
  }

  public static RoleUpdateResult toRoleUpdateResponse(final RoleRecord roleRecord) {
    return RoleUpdateResult.Builder.create()
        .roleId(roleRecord.getRoleId())
        .name(roleRecord.getName())
        .description(roleRecord.getDescription())
        .build();
  }

  public static GroupCreateResult toGroupCreateResponse(final GroupRecord groupRecord) {
    return GroupCreateResult.Builder.create()
        .groupId(groupRecord.getGroupId())
        .name(groupRecord.getName())
        .description(groupRecord.getDescription())
        .build();
  }

  public static GroupUpdateResult toGroupUpdateResponse(final GroupRecord groupRecord) {
    return GroupUpdateResult.Builder.create()
        .groupId(groupRecord.getGroupId())
        .name(groupRecord.getName())
        .description(groupRecord.getDescription())
        .build();
  }

  public static TenantCreateResult toTenantCreateResponse(final TenantRecord record) {
    return TenantCreateResult.Builder.create()
        .tenantId(record.getTenantId())
        .name(record.getName())
        .description(record.getDescription())
        .build();
  }

  public static TenantUpdateResult toTenantUpdateResponse(final TenantRecord record) {
    return TenantUpdateResult.Builder.create()
        .tenantId(record.getTenantId())
        .name(record.getName())
        .description(record.getDescription())
        .build();
  }

  public static MappingRuleCreateResult toMappingRuleCreateResponse(
      final MappingRuleRecord record) {
    return MappingRuleCreateResult.Builder.create()
        .claimName(record.getClaimName())
        .claimValue(record.getClaimValue())
        .name(record.getName())
        .mappingRuleId(record.getMappingRuleId())
        .build();
  }

  public static MappingRuleUpdateResult toMappingRuleUpdateResponse(
      final MappingRuleRecord record) {
    return MappingRuleUpdateResult.Builder.create()
        .claimName(record.getClaimName())
        .claimValue(record.getClaimValue())
        .name(record.getName())
        .mappingRuleId(record.getMappingRuleId())
        .build();
  }

  public static EvaluateDecisionResult toEvaluateDecisionResponse(
      final BrokerResponse<DecisionEvaluationRecord> brokerResponse) {
    final var decisionEvaluationRecord = brokerResponse.getResponse();
    final var evaluatedDecisions = buildEvaluatedDecisions(decisionEvaluationRecord);
    return EvaluateDecisionResult.Builder.create()
        .decisionDefinitionId(decisionEvaluationRecord.getDecisionId())
        .decisionDefinitionKey(keyToString(decisionEvaluationRecord.getDecisionKey()))
        .decisionDefinitionName(decisionEvaluationRecord.getDecisionName())
        .decisionDefinitionVersion(decisionEvaluationRecord.getDecisionVersion())
        .decisionEvaluationKey(keyToString(brokerResponse.getKey()))
        .decisionInstanceKey(keyToString(brokerResponse.getKey()))
        .decisionRequirementsId(decisionEvaluationRecord.getDecisionRequirementsId())
        .decisionRequirementsKey(keyToString(decisionEvaluationRecord.getDecisionRequirementsKey()))
        .evaluatedDecisions(evaluatedDecisions)
        // these optional fields default to an empty string on the originating record
        // the conversion to null ensures response contract compliance
        .failedDecisionDefinitionId(emptyToNull(decisionEvaluationRecord.getFailedDecisionId()))
        .failureMessage(emptyToNull(decisionEvaluationRecord.getEvaluationFailureMessage()))
        .output(decisionEvaluationRecord.getDecisionOutput())
        .tenantId(decisionEvaluationRecord.getTenantId())
        .build();
  }

  private static List<EvaluatedDecisionResult> buildEvaluatedDecisions(
      final DecisionEvaluationRecord decisionEvaluationRecord) {
    return decisionEvaluationRecord.getEvaluatedDecisions().stream()
        .map(
            evaluatedDecision ->
                EvaluatedDecisionResult.Builder.create()
                    .decisionDefinitionType(evaluatedDecision.getDecisionType())
                    .evaluatedInputs(buildEvaluatedInputs(evaluatedDecision.getEvaluatedInputs()))
                    .matchedRules(buildMatchedRules(evaluatedDecision.getMatchedRules()))
                    .decisionDefinitionId(evaluatedDecision.getDecisionId())
                    .decisionDefinitionKey(keyToString(evaluatedDecision.getDecisionKey()))
                    .decisionDefinitionName(evaluatedDecision.getDecisionName())
                    .decisionDefinitionVersion(evaluatedDecision.getDecisionVersion())
                    .decisionEvaluationInstanceKey(
                        evaluatedDecision.getDecisionEvaluationInstanceKey())
                    .output(evaluatedDecision.getDecisionOutput())
                    .tenantId(evaluatedDecision.getTenantId())
                    .build())
        .toList();
  }

  private static List<MatchedDecisionRuleItem> buildMatchedRules(
      final List<MatchedRuleValue> matchedRuleValues) {
    return matchedRuleValues.stream()
        .map(
            matchedRuleValue ->
                MatchedDecisionRuleItem.Builder.create()
                    .evaluatedOutputs(
                        buildEvaluatedOutputs(
                            matchedRuleValue.getEvaluatedOutputs(),
                            matchedRuleValue.getRuleId(),
                            matchedRuleValue.getRuleIndex()))
                    .ruleId(matchedRuleValue.getRuleId())
                    .ruleIndex(matchedRuleValue.getRuleIndex())
                    .build())
        .toList();
  }

  private static List<EvaluatedDecisionOutputItem> buildEvaluatedOutputs(
      final List<EvaluatedOutputValue> evaluatedOutputs,
      final @Nullable String ruleId,
      final int ruleIndex) {
    return evaluatedOutputs.stream()
        .map(
            evaluatedOutput ->
                EvaluatedDecisionOutputItem.Builder.create()
                    .ruleId(ruleId)
                    .ruleIndex(ruleIndex)
                    .outputId(evaluatedOutput.getOutputId())
                    .outputName(evaluatedOutput.getOutputName())
                    .outputValue(evaluatedOutput.getOutputValue())
                    .build())
        .toList();
  }

  private static List<EvaluatedDecisionInputItem> buildEvaluatedInputs(
      final List<EvaluatedInputValue> inputValues) {
    return inputValues.stream()
        .map(
            evaluatedInputValue ->
                EvaluatedDecisionInputItem.Builder.create()
                    .inputId(evaluatedInputValue.getInputId())
                    .inputName(evaluatedInputValue.getInputName())
                    .inputValue(evaluatedInputValue.getInputValue())
                    .build())
        .toList();
  }

  public static ClusterVariableResult toClusterVariableResponse(
      final ClusterVariableRecord clusterVariableRecord) {
    final ClusterVariableScopeEnum scope =
        clusterVariableRecord.isTenantScoped()
            ? ClusterVariableScopeEnum.TENANT
            : ClusterVariableScopeEnum.GLOBAL;
    final String tenantId =
        clusterVariableRecord.isTenantScoped() ? clusterVariableRecord.getTenantId() : null;
    return ClusterVariableResult.Builder.create()
        .name(clusterVariableRecord.getName())
        .scope(scope)
        .tenantId(tenantId)
        .value(clusterVariableRecord.getValue())
        .build();
  }

  public static ExpressionEvaluationResult toExpressionEvaluationResult(
      final ExpressionRecord expressionRecord) {
    return ExpressionEvaluationResult.Builder.create()
        .expression(expressionRecord.getExpression())
        .result(expressionRecord.getResultValue())
        .warnings(
            expressionRecord.getWarnings().stream()
                .map(
                    warning ->
                        ExpressionEvaluationWarningItem.Builder.create().message(warning).build())
                .toList())
        .build();
  }

  public static TopologyResponse toTopologyResponse(final Topology topology) {
    final var brokers = topology.brokers().stream().map(ResponseMapper::toBrokerInfo).toList();
    return TopologyResponse.Builder.create()
        .brokers(brokers)
        .clusterSize(topology.clusterSize())
        .partitionsCount(topology.partitionsCount())
        .replicationFactor(topology.replicationFactor())
        .gatewayVersion(topology.gatewayVersion())
        .lastCompletedChangeId(keyToString(topology.lastCompletedChangeId()))
        .clusterId(topology.clusterId())
        .build();
  }

  private static BrokerInfo toBrokerInfo(final Broker broker) {
    final var partitions = buildPartitions(broker.partitions());
    return BrokerInfo.Builder.create()
        .nodeId(broker.nodeId())
        .host(broker.host())
        .port(broker.port())
        .partitions(partitions)
        .version(broker.version())
        .build();
  }

  private static List<io.camunda.gateway.protocol.model.Partition> buildPartitions(
      final List<Partition> partitions) {
    return partitions.stream()
        .map(
            partition ->
                io.camunda.gateway.protocol.model.Partition.Builder.create()
                    .partitionId(partition.partitionId())
                    .role(EnumUtil.convert(partition.role(), RoleEnum.class))
                    .health(EnumUtil.convert(partition.health(), HealthEnum.class))
                    .build())
        .toList();
  }

  private static @Nullable String emptyToNull(final @Nullable String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  static class RestJobActivationResult
      implements JobActivationResult<io.camunda.gateway.protocol.model.JobActivationResult> {

    private final io.camunda.gateway.protocol.model.JobActivationResult response;
    private final List<ActivatedJobResult> sizeExceedingJobs;

    RestJobActivationResult(
        final io.camunda.gateway.protocol.model.JobActivationResult response,
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
          .map(j -> new ActivatedJob(keyToLong(j.getJobKey()), j.getRetries()))
          .toList();
    }

    @Override
    public io.camunda.gateway.protocol.model.JobActivationResult getActivateJobsResponse() {
      return response;
    }

    @Override
    public List<ActivatedJob> getJobsToDefer() {
      final var result = new ArrayList<ActivatedJob>(sizeExceedingJobs.size());
      for (final var job : sizeExceedingJobs) {
        try {
          final var key = job.getJobKey();
          result.add(new ActivatedJob(keyToLong(key), job.getRetries()));
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
