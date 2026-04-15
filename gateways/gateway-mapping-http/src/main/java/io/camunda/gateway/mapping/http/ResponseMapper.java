/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static io.camunda.zeebe.protocol.record.value.JobKind.TASK_LISTENER;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentLink;
import io.camunda.gateway.mapping.http.search.contract.generated.ActivatedJobContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationCreateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationCreatedContract;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.BrokerInfoContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ClusterVariableContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ClusterVariableScopeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.CreateProcessInstanceContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DeleteResourceResponseContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DeploymentContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DeploymentDecisionContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DeploymentDecisionRequirementsContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DeploymentFormContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DeploymentMetadataContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DeploymentProcessContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DeploymentResourceContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DocumentCreationBatchResponseContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DocumentCreationFailureDetailContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DocumentLinkContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DocumentMetadataResponseContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DocumentReferenceContract;
import io.camunda.gateway.mapping.http.search.contract.generated.EvaluateConditionalContract;
import io.camunda.gateway.mapping.http.search.contract.generated.EvaluateDecisionContract;
import io.camunda.gateway.mapping.http.search.contract.generated.EvaluatedDecisionContract;
import io.camunda.gateway.mapping.http.search.contract.generated.EvaluatedDecisionInputItemContract;
import io.camunda.gateway.mapping.http.search.contract.generated.EvaluatedDecisionOutputItemContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ExpressionEvaluationContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ExpressionEvaluationWarningItemContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupCreateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GroupUpdateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobActivationContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobKindEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.JobListenerEventTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.MappingRuleCreateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MappingRuleUpdateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MatchedDecisionRuleItemContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MessageCorrelationContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MessagePublicationContract;
import io.camunda.gateway.mapping.http.search.contract.generated.PartitionContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessInstanceReferenceContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ResourceContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleCreateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.RoleUpdateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.SignalBroadcastContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantCreateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantUpdateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TopologyResponseContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserCreateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserTaskPropertiesContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UserUpdateContract;
import io.camunda.gateway.mapping.http.util.KeyUtil;
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
import java.util.Optional;
import java.util.Set;
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
    return date == null ? null : DATE_RESPONSE_MAPPER.format(date);
  }

  public static JobActivationResult<JobActivationContract> toActivateJobsResponse(
      final io.camunda.zeebe.gateway.impl.job.JobActivationResponse activationResponse) {
    final Iterator<LongValue> jobKeys = activationResponse.brokerResponse().jobKeys().iterator();
    final Iterator<JobRecord> jobs = activationResponse.brokerResponse().jobs().iterator();

    long currentResponseSize = 0L;

    final List<ActivatedJobContract> sizeExceedingJobs = new ArrayList<>();
    final List<ActivatedJobContract> responseJobs = new ArrayList<>();

    while (jobKeys.hasNext() && jobs.hasNext()) {
      final LongValue jobKey = jobKeys.next();
      final JobRecord job = jobs.next();
      final ActivatedJobContract activatedJob = toActivatedJob(jobKey.getValue(), job);

      // This is the message size of the message from the broker, not the size of the REST message
      final int activatedJobSize = job.getLength();
      if (currentResponseSize + activatedJobSize <= activationResponse.maxResponseSize()) {
        responseJobs.add(activatedJob);
        currentResponseSize += activatedJobSize;
      } else {
        sizeExceedingJobs.add(activatedJob);
      }
    }

    return new RestJobActivationResult(new JobActivationContract(responseJobs), sizeExceedingJobs);
  }

  static ActivatedJobContract toActivatedJob(final long jobKey, final JobRecord job) {
    final long rootProcessInstanceKey = job.getRootProcessInstanceKey();
    return ActivatedJobContract.builder()
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
        .jobKey(jobKey)
        .processInstanceKey(job.getProcessInstanceKey())
        .processDefinitionKey(job.getProcessDefinitionKey())
        .elementInstanceKey(job.getElementInstanceKey())
        .kind(EnumUtil.convert(job.getJobKind(), JobKindEnum.class))
        .listenerEventType(
            EnumUtil.convert(job.getJobListenerEventType(), JobListenerEventTypeEnum.class))
        .tags(job.getTags())
        .userTask(toUserTaskProperties(job))
        .rootProcessInstanceKey(rootProcessInstanceKey > 0 ? rootProcessInstanceKey : null)
        .build();
  }

  private static UserTaskPropertiesContract toUserTaskProperties(final JobRecord job) {
    if (job.getJobKind() != TASK_LISTENER || CollectionUtils.isEmpty(job.getCustomHeaders())) {
      return null;
    }

    final var headers = job.getCustomHeaders();
    final var action = headers.get(Protocol.USER_TASK_ACTION_HEADER_NAME);
    if (action == null) {
      return null;
    }

    return new UserTaskPropertiesContract(
        action,
        headers.get(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME),
        mapStringToList(headers.get(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME)),
        mapStringToList(headers.get(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME)),
        mapStringToList(headers.get(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME)),
        headers.get(Protocol.USER_TASK_DUE_DATE_HEADER_NAME),
        headers.get(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME),
        headers.get(Protocol.USER_TASK_FORM_KEY_HEADER_NAME),
        toIntegerOrNull(headers.get(Protocol.USER_TASK_PRIORITY_HEADER_NAME)),
        headers.get(Protocol.USER_TASK_KEY_HEADER_NAME));
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

  public static MessageCorrelationContract toMessageCorrelationResponse(
      final MessageCorrelationRecord brokerResponse) {
    return MessageCorrelationContract.builder()
        .tenantId(brokerResponse.getTenantId())
        .messageKey(brokerResponse.getMessageKey())
        .processInstanceKey(brokerResponse.getProcessInstanceKey())
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

  public static DocumentCreationBatchResponseContract toDocumentReferenceBatch(
      final List<Either<DocumentErrorResponse, DocumentReferenceResponse>> responses) {
    final var createdDocuments = new ArrayList<DocumentReferenceContract>();
    final var failedDocuments = new ArrayList<DocumentCreationFailureDetailContract>();
    responses.forEach(
        documentResponse ->
            documentResponse.ifRightOrLeft(
                reference -> createdDocuments.add(toDocumentReference(reference)),
                error -> failedDocuments.add(toDocumentCreationFailure(error))));
    return new DocumentCreationBatchResponseContract(failedDocuments, createdDocuments);
  }

  public static DocumentReferenceContract toDocumentReference(
      final DocumentReferenceResponse response) {
    final var internalMetadata = response.metadata();
    final var customProperties =
        internalMetadata.customProperties() != null
            ? internalMetadata.customProperties()
            : Map.<String, Object>of();
    final var externalMetadata =
        DocumentMetadataResponseContract.builder()
            .contentType(internalMetadata.contentType())
            .fileName(internalMetadata.fileName())
            .size(internalMetadata.size())
            .customProperties(customProperties)
            .expiresAt(
                Optional.ofNullable(internalMetadata.expiresAt())
                    .map(Object::toString)
                    .orElse(null))
            .processDefinitionId(internalMetadata.processDefinitionId())
            .processInstanceKey(internalMetadata.processInstanceKey())
            .build();
    return new DocumentReferenceContract(
        "camunda",
        response.storeId(),
        response.documentId(),
        response.contentHash(),
        externalMetadata);
  }

  private static DocumentCreationFailureDetailContract toDocumentCreationFailure(
      final DocumentErrorResponse error) {
    final var problemDetail = mapDocumentErrorToProblem(error.error());
    return new DocumentCreationFailureDetailContract(
        error.request().metadata().fileName(),
        problemDetail.getStatus(),
        problemDetail.getTitle(),
        problemDetail.getDetail());
  }

  private static ProblemDetail mapDocumentErrorToProblem(final ServiceException e) {
    final String detail = e.getMessage();
    final HttpStatusCode status = GatewayErrorMapper.mapStatus(e.getStatus());
    return GatewayErrorMapper.createProblemDetail(status, detail, e.getStatus().name());
  }

  public static DocumentLinkContract toDocumentLinkResponse(final DocumentLink documentLink) {
    return new DocumentLinkContract(documentLink.link(), documentLink.expiresAt().toString());
  }

  public static DeploymentContract toDeployResourceResponse(final DeploymentRecord brokerResponse) {
    final var deployments = new ArrayList<DeploymentMetadataContract>();
    addDeployedProcess(deployments, brokerResponse.getProcessesMetadata());
    addDeployedDecision(deployments, brokerResponse.decisionsMetadata());
    addDeployedDecisionRequirements(deployments, brokerResponse.decisionRequirementsMetadata());
    addDeployedForm(deployments, brokerResponse.formMetadata());
    addDeployedResource(deployments, brokerResponse.resourceMetadata());
    return DeploymentContract.builder()
        .deploymentKey(brokerResponse.getDeploymentKey())
        .tenantId(brokerResponse.getTenantId())
        .deployments(deployments)
        .build();
  }

  public static DeleteResourceResponseContract toDeleteResourceResponse(
      final ResourceDeletionRecord brokerResponse) {
    BatchOperationCreatedContract batchOperation = null;
    if (brokerResponse.isDeleteHistory() && brokerResponse.getBatchOperationKey() > 0) {
      batchOperation =
          new BatchOperationCreatedContract(
              KeyUtil.keyToString(brokerResponse.getBatchOperationKey()),
              BatchOperationTypeEnum.valueOf(brokerResponse.getBatchOperationType().name()));
    }
    return DeleteResourceResponseContract.builder()
        .resourceKey(brokerResponse.getResourceKey())
        .batchOperation(batchOperation)
        .build();
  }

  public static ResourceContract toGetResourceResponse(final ResourceRecord resourceRecord) {
    return ResourceContract.builder()
        .resourceName(resourceRecord.getResourceName())
        .version(resourceRecord.getVersion())
        .resourceId(resourceRecord.getResourceId())
        .tenantId(resourceRecord.getTenantId())
        .resourceKey(resourceRecord.getResourceKey())
        .versionTag(emptyToNull(resourceRecord.getVersionTag()))
        .build();
  }

  public static String toGetResourceContentResponse(final ResourceRecord resourceRecord) {
    return resourceRecord.getResourceProp();
  }

  public static MessagePublicationContract toMessagePublicationResponse(
      final BrokerResponse<MessageRecord> brokerResponse) {
    return MessagePublicationContract.builder()
        .tenantId(brokerResponse.getResponse().getTenantId())
        .messageKey(brokerResponse.getKey())
        .build();
  }

  private static void addDeployedForm(
      final List<DeploymentMetadataContract> deployments,
      final ValueArray<FormMetadataRecord> formMetadataRecords) {
    formMetadataRecords.stream()
        .map(
            form ->
                new DeploymentMetadataContract(
                    null,
                    null,
                    null,
                    DeploymentFormContract.builder()
                        .formId(form.getFormId())
                        .version(form.getVersion())
                        .resourceName(form.getResourceName())
                        .tenantId(form.getTenantId())
                        .formKey(form.getFormKey())
                        .build(),
                    null))
        .forEach(deployments::add);
  }

  private static void addDeployedResource(
      final List<DeploymentMetadataContract> deployments,
      final ValueArray<ResourceMetadataRecord> resourceMetadataRecords) {
    resourceMetadataRecords.stream()
        .map(
            resource ->
                new DeploymentMetadataContract(
                    null,
                    null,
                    null,
                    null,
                    DeploymentResourceContract.builder()
                        .resourceId(resource.getResourceId())
                        .resourceName(resource.getResourceName())
                        .version(resource.getVersion())
                        .tenantId(resource.getTenantId())
                        .resourceKey(resource.getResourceKey())
                        .build()))
        .forEach(deployments::add);
  }

  private static void addDeployedDecisionRequirements(
      final List<DeploymentMetadataContract> deployments,
      final ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataRecords) {
    decisionRequirementsMetadataRecords.stream()
        .map(
            decisionRequirement ->
                new DeploymentMetadataContract(
                    null,
                    null,
                    DeploymentDecisionRequirementsContract.builder()
                        .decisionRequirementsId(decisionRequirement.getDecisionRequirementsId())
                        .decisionRequirementsName(decisionRequirement.getDecisionRequirementsName())
                        .version(decisionRequirement.getDecisionRequirementsVersion())
                        .resourceName(decisionRequirement.getResourceName())
                        .tenantId(decisionRequirement.getTenantId())
                        .decisionRequirementsKey(decisionRequirement.getDecisionRequirementsKey())
                        .build(),
                    null,
                    null))
        .forEach(deployments::add);
  }

  private static void addDeployedDecision(
      final List<DeploymentMetadataContract> deployments,
      final ValueArray<DecisionRecord> decisionRecords) {
    decisionRecords.stream()
        .map(
            decision ->
                new DeploymentMetadataContract(
                    null,
                    DeploymentDecisionContract.builder()
                        .decisionDefinitionId(decision.getDecisionId())
                        .version(decision.getVersion())
                        .name(decision.getDecisionName())
                        .tenantId(decision.getTenantId())
                        .decisionRequirementsId(decision.getDecisionRequirementsId())
                        .decisionDefinitionKey(decision.getDecisionKey())
                        .decisionRequirementsKey(decision.getDecisionRequirementsKey())
                        .build(),
                    null,
                    null,
                    null))
        .forEach(deployments::add);
  }

  private static void addDeployedProcess(
      final List<DeploymentMetadataContract> deployments,
      final List<ProcessMetadataValue> processesMetadata) {
    processesMetadata.stream()
        .map(
            process ->
                new DeploymentMetadataContract(
                    DeploymentProcessContract.builder()
                        .processDefinitionId(process.getBpmnProcessId())
                        .processDefinitionVersion(process.getVersion())
                        .resourceName(process.getResourceName())
                        .tenantId(process.getTenantId())
                        .processDefinitionKey(process.getProcessDefinitionKey())
                        .build(),
                    null,
                    null,
                    null,
                    null))
        .forEach(deployments::add);
  }

  public static CreateProcessInstanceContract toCreateProcessInstanceResponse(
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

  public static CreateProcessInstanceContract toCreateProcessInstanceWithResultResponse(
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

  private static CreateProcessInstanceContract buildCreateProcessInstanceResponse(
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final Integer version,
      final Long processInstanceKey,
      final String tenantId,
      final Map<String, Object> variables,
      final Set<String> tags,
      final String businessId) {
    return CreateProcessInstanceContract.builder()
        .processDefinitionId(bpmnProcessId)
        .processDefinitionVersion(version)
        .tenantId(tenantId)
        .variables(variables != null ? variables : Map.of())
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .tags(tags != null ? tags : Set.of())
        .businessId(emptyToNull(businessId))
        .build();
  }

  public static BatchOperationCreatedContract toBatchOperationCreatedWithResultResponse(
      final BatchOperationCreationRecord brokerResponse) {
    return new BatchOperationCreatedContract(
        KeyUtil.keyToString(brokerResponse.getBatchOperationKey()),
        BatchOperationTypeEnum.valueOf(brokerResponse.getBatchOperationType().name()));
  }

  public static SignalBroadcastContract toSignalBroadcastResponse(
      final BrokerResponse<SignalRecord> brokerResponse) {
    return SignalBroadcastContract.builder()
        .tenantId(brokerResponse.getResponse().getTenantId())
        .signalKey(brokerResponse.getKey())
        .build();
  }

  public static EvaluateConditionalContract toConditionalEvaluationResponse(
      final BrokerResponse<ConditionalEvaluationRecord> brokerResponse) {
    final var response = brokerResponse.getResponse();
    final var processInstances =
        response.getStartedProcessInstances().stream()
            .map(
                instance ->
                    ProcessInstanceReferenceContract.builder()
                        .processDefinitionKey(instance.getProcessDefinitionKey())
                        .processInstanceKey(instance.getProcessInstanceKey())
                        .build())
            .toList();

    return EvaluateConditionalContract.builder()
        .conditionalEvaluationKey(brokerResponse.getKey())
        .tenantId(response.getTenantId())
        .processInstances(processInstances)
        .build();
  }

  public static AuthorizationCreateContract toAuthorizationCreateResponse(
      final AuthorizationRecord authorizationRecord) {
    return AuthorizationCreateContract.builder()
        .authorizationKey(authorizationRecord.getAuthorizationKey())
        .build();
  }

  public static UserCreateContract toUserCreateResponse(final UserRecord userRecord) {
    return new UserCreateContract(
        userRecord.getUsername(), userRecord.getName(), userRecord.getEmail());
  }

  public static UserUpdateContract toUserUpdateResponse(final UserRecord userRecord) {
    return new UserUpdateContract(
        userRecord.getUsername(), userRecord.getName(), userRecord.getEmail());
  }

  public static RoleCreateContract toRoleCreateResponse(final RoleRecord roleRecord) {
    return new RoleCreateContract(
        roleRecord.getRoleId(), roleRecord.getName(), roleRecord.getDescription());
  }

  public static RoleUpdateContract toRoleUpdateResponse(final RoleRecord roleRecord) {
    return new RoleUpdateContract(
        roleRecord.getName(), roleRecord.getDescription(), roleRecord.getRoleId());
  }

  public static GroupCreateContract toGroupCreateResponse(final GroupRecord groupRecord) {
    return new GroupCreateContract(
        groupRecord.getGroupId(), groupRecord.getName(), groupRecord.getDescription());
  }

  public static GroupUpdateContract toGroupUpdateResponse(final GroupRecord groupRecord) {
    return new GroupUpdateContract(
        groupRecord.getGroupId(), groupRecord.getName(), groupRecord.getDescription());
  }

  public static TenantCreateContract toTenantCreateResponse(final TenantRecord record) {
    return new TenantCreateContract(
        record.getTenantId(), record.getName(), record.getDescription());
  }

  public static TenantUpdateContract toTenantUpdateResponse(final TenantRecord record) {
    return new TenantUpdateContract(
        record.getTenantId(), record.getName(), record.getDescription());
  }

  public static MappingRuleCreateContract toMappingRuleCreateResponse(
      final MappingRuleRecord record) {
    return new MappingRuleCreateContract(
        record.getClaimName(), record.getClaimValue(), record.getName(), record.getMappingRuleId());
  }

  public static MappingRuleUpdateContract toMappingRuleUpdateResponse(
      final MappingRuleRecord record) {
    return new MappingRuleUpdateContract(
        record.getClaimName(), record.getClaimValue(), record.getName(), record.getMappingRuleId());
  }

  public static EvaluateDecisionContract toEvaluateDecisionResponse(
      final BrokerResponse<DecisionEvaluationRecord> brokerResponse) {
    final var record = brokerResponse.getResponse();
    return EvaluateDecisionContract.builder()
        .decisionDefinitionId(record.getDecisionId())
        .decisionDefinitionKey(record.getDecisionKey())
        .decisionDefinitionName(record.getDecisionName())
        .decisionDefinitionVersion(record.getDecisionVersion())
        .decisionEvaluationKey(brokerResponse.getKey())
        .decisionInstanceKey(brokerResponse.getKey())
        .decisionRequirementsId(record.getDecisionRequirementsId())
        .decisionRequirementsKey(record.getDecisionRequirementsKey())
        .evaluatedDecisions(buildEvaluatedDecisions(record))
        .output(record.getDecisionOutput())
        .tenantId(record.getTenantId())
        .failedDecisionDefinitionId(emptyToNull(record.getFailedDecisionId()))
        .failureMessage(emptyToNull(record.getEvaluationFailureMessage()))
        .build();
  }

  private static List<EvaluatedDecisionContract> buildEvaluatedDecisions(
      final DecisionEvaluationRecord decisionEvaluationRecord) {
    return decisionEvaluationRecord.getEvaluatedDecisions().stream()
        .map(
            evaluatedDecision ->
                EvaluatedDecisionContract.builder()
                    .decisionDefinitionId(evaluatedDecision.getDecisionId())
                    .decisionDefinitionName(evaluatedDecision.getDecisionName())
                    .decisionDefinitionVersion(evaluatedDecision.getDecisionVersion())
                    .decisionDefinitionType(evaluatedDecision.getDecisionType())
                    .output(evaluatedDecision.getDecisionOutput())
                    .tenantId(evaluatedDecision.getTenantId())
                    .matchedRules(buildMatchedRules(evaluatedDecision.getMatchedRules()))
                    .evaluatedInputs(buildEvaluatedInputs(evaluatedDecision.getEvaluatedInputs()))
                    .decisionDefinitionKey(evaluatedDecision.getDecisionKey())
                    .decisionEvaluationInstanceKey(
                        evaluatedDecision.getDecisionEvaluationInstanceKey())
                    .build())
        .toList();
  }

  private static List<MatchedDecisionRuleItemContract> buildMatchedRules(
      final List<MatchedRuleValue> matchedRuleValues) {
    return matchedRuleValues.stream()
        .map(
            matchedRuleValue ->
                new MatchedDecisionRuleItemContract(
                    matchedRuleValue.getRuleId(),
                    matchedRuleValue.getRuleIndex(),
                    buildEvaluatedOutputs(matchedRuleValue.getEvaluatedOutputs())))
        .toList();
  }

  private static List<EvaluatedDecisionOutputItemContract> buildEvaluatedOutputs(
      final List<EvaluatedOutputValue> evaluatedOutputs) {
    return evaluatedOutputs.stream()
        .map(
            evaluatedOutput ->
                new EvaluatedDecisionOutputItemContract(
                    evaluatedOutput.getOutputId(),
                    evaluatedOutput.getOutputName(),
                    evaluatedOutput.getOutputValue(),
                    null,
                    null))
        .toList();
  }

  private static List<EvaluatedDecisionInputItemContract> buildEvaluatedInputs(
      final List<EvaluatedInputValue> inputValues) {
    return inputValues.stream()
        .map(
            evaluatedInputValue ->
                new EvaluatedDecisionInputItemContract(
                    evaluatedInputValue.getInputId(),
                    evaluatedInputValue.getInputName(),
                    evaluatedInputValue.getInputValue()))
        .toList();
  }

  public static ClusterVariableContract toClusterVariableResponse(
      final ClusterVariableRecord clusterVariableRecord) {
    return new ClusterVariableContract(
        clusterVariableRecord.getName(),
        clusterVariableRecord.isTenantScoped()
            ? ClusterVariableScopeEnum.TENANT
            : ClusterVariableScopeEnum.GLOBAL,
        clusterVariableRecord.isTenantScoped() ? clusterVariableRecord.getTenantId() : null,
        clusterVariableRecord.getValue());
  }

  public static ExpressionEvaluationContract toExpressionEvaluationResult(
      final ExpressionRecord expressionRecord) {
    return new ExpressionEvaluationContract(
        expressionRecord.getExpression(),
        expressionRecord.getResultValue(),
        expressionRecord.getWarnings().stream()
            .map(ExpressionEvaluationWarningItemContract::new)
            .toList());
  }

  public static TopologyResponseContract toTopologyResponse(final Topology topology) {
    return new TopologyResponseContract(
        topology.brokers().stream().map(ResponseMapper::toBrokerInfo).toList(),
        topology.clusterId(),
        topology.clusterSize(),
        topology.partitionsCount(),
        topology.replicationFactor(),
        topology.gatewayVersion(),
        KeyUtil.keyToString(topology.lastCompletedChangeId()));
  }

  private static BrokerInfoContract toBrokerInfo(final Broker broker) {
    return new BrokerInfoContract(
        broker.nodeId(),
        broker.host(),
        broker.port(),
        broker.partitions().stream().map(ResponseMapper::toPartition).toList(),
        broker.version());
  }

  private static PartitionContract toPartition(final Partition partition) {
    return new PartitionContract(
        partition.partitionId(),
        partition.role().name().toLowerCase(),
        partition.health().name().toLowerCase());
  }

  private static String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  static class RestJobActivationResult implements JobActivationResult<JobActivationContract> {

    private final JobActivationContract response;
    private final List<ActivatedJobContract> sizeExceedingJobs;

    RestJobActivationResult(
        final JobActivationContract response, final List<ActivatedJobContract> sizeExceedingJobs) {
      this.response = response;
      this.sizeExceedingJobs = sizeExceedingJobs;
    }

    @Override
    public int getJobsCount() {
      return response.jobs().size();
    }

    @Override
    public List<ActivatedJob> getJobs() {
      return response.jobs().stream()
          .map(j -> new ActivatedJob(KeyUtil.keyToLong(j.jobKey()), j.retries()))
          .toList();
    }

    @Override
    public JobActivationContract getActivateJobsResponse() {
      return response;
    }

    @Override
    public List<ActivatedJob> getJobsToDefer() {
      final var result = new ArrayList<ActivatedJob>(sizeExceedingJobs.size());
      for (final var job : sizeExceedingJobs) {
        try {
          final var key = job.jobKey();
          result.add(new ActivatedJob(KeyUtil.keyToLong(key), job.retries()));
        } catch (final NumberFormatException ignored) {
          // could happen
          LOG.warn(
              "Expected job key to be numeric, but was {}. The job cannot be returned to the broker, but it will be retried after timeout",
              job.jobKey());
        }
      }
      return result;
    }
  }
}
