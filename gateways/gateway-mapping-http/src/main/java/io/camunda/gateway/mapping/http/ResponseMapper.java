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
import io.camunda.gateway.mapping.http.util.KeyUtil;
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

    return new RestJobActivationResult(
        new io.camunda.gateway.protocol.model.JobActivationResult().jobs(responseJobs),
        sizeExceedingJobs);
  }

  static ActivatedJobResult toActivatedJob(final long jobKey, final JobRecord job) {
    final long rootProcessInstanceKey = job.getRootProcessInstanceKey();
    return new ActivatedJobResult()
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
        .jobKey(KeyUtil.keyToString(jobKey))
        .processInstanceKey(KeyUtil.keyToString(job.getProcessInstanceKey()))
        .processDefinitionKey(KeyUtil.keyToString(job.getProcessDefinitionKey()))
        .elementInstanceKey(KeyUtil.keyToString(job.getElementInstanceKey()))
        .kind(EnumUtil.convert(job.getJobKind(), JobKindEnum.class))
        .listenerEventType(
            EnumUtil.convert(job.getJobListenerEventType(), JobListenerEventTypeEnum.class))
        .tags(job.getTags())
        .userTask(toUserTaskProperties(job))
        .rootProcessInstanceKey(
            rootProcessInstanceKey > 0 ? KeyUtil.keyToString(rootProcessInstanceKey) : null);
  }

  private static UserTaskProperties toUserTaskProperties(final JobRecord job) {
    if (job.getJobKind() != TASK_LISTENER || CollectionUtils.isEmpty(job.getCustomHeaders())) {
      return null;
    }

    final var headers = job.getCustomHeaders();
    final var action = headers.get(Protocol.USER_TASK_ACTION_HEADER_NAME);
    if (action == null) {
      return null;
    }

    return new UserTaskProperties()
        .action(action)
        .assignee(headers.get(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME))
        .candidateGroups(
            mapStringToList(headers.get(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME)))
        .candidateUsers(
            mapStringToList(headers.get(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME)))
        .changedAttributes(
            mapStringToList(headers.get(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME)))
        .dueDate(headers.get(Protocol.USER_TASK_DUE_DATE_HEADER_NAME))
        .followUpDate(headers.get(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME))
        .formKey(headers.get(Protocol.USER_TASK_FORM_KEY_HEADER_NAME))
        .priority(toIntegerOrNull(headers.get(Protocol.USER_TASK_PRIORITY_HEADER_NAME)))
        .userTaskKey(headers.get(Protocol.USER_TASK_KEY_HEADER_NAME));
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

  public static MessageCorrelationResult toMessageCorrelationResponse(
      final MessageCorrelationRecord brokerResponse) {
    return new MessageCorrelationResult()
        .tenantId(brokerResponse.getTenantId())
        .messageKey(KeyUtil.keyToString(brokerResponse.getMessageKey()))
        .processInstanceKey(KeyUtil.keyToString(brokerResponse.getProcessInstanceKey()));
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
    final var createdDocuments = new ArrayList<DocumentReference>();
    final var failedDocuments = new ArrayList<DocumentCreationFailureDetail>();
    responses.forEach(
        documentResponse ->
            documentResponse.ifRightOrLeft(
                reference -> createdDocuments.add(toDocumentReference(reference)),
                error -> failedDocuments.add(toDocumentCreationFailure(error))));
    return new DocumentCreationBatchResponse()
        .failedDocuments(failedDocuments)
        .createdDocuments(createdDocuments);
  }

  public static DocumentReference toDocumentReference(final DocumentReferenceResponse response) {
    final var internalMetadata = response.metadata();
    final var customProperties =
        internalMetadata.customProperties() != null
            ? internalMetadata.customProperties()
            : Map.<String, Object>of();
    final var externalMetadata =
        new DocumentMetadataResponse()
            .contentType(internalMetadata.contentType())
            .fileName(internalMetadata.fileName())
            .size(internalMetadata.size())
            .customProperties(customProperties)
            .expiresAt(
                Optional.ofNullable(internalMetadata.expiresAt())
                    .map(Object::toString)
                    .orElse(null))
            .processDefinitionId(internalMetadata.processDefinitionId())
            .processInstanceKey(KeyUtil.keyToString(internalMetadata.processInstanceKey()));
    return new DocumentReference()
        .camundaDocumentType("camunda")
        .storeId(response.storeId())
        .documentId(response.documentId())
        .contentHash(response.contentHash())
        .metadata(externalMetadata);
  }

  private static DocumentCreationFailureDetail toDocumentCreationFailure(
      final DocumentErrorResponse error) {
    final var problemDetail = mapDocumentErrorToProblem(error.error());
    return new DocumentCreationFailureDetail()
        .fileName(error.request().metadata().fileName())
        .status(problemDetail.getStatus())
        .title(problemDetail.getTitle())
        .detail(problemDetail.getDetail());
  }

  private static ProblemDetail mapDocumentErrorToProblem(final ServiceException e) {
    final String detail = e.getMessage();
    final HttpStatusCode status = GatewayErrorMapper.mapStatus(e.getStatus());
    return GatewayErrorMapper.createProblemDetail(status, detail, e.getStatus().name());
  }

  public static io.camunda.gateway.protocol.model.DocumentLink toDocumentLinkResponse(
      final DocumentLink documentLink) {
    return new io.camunda.gateway.protocol.model.DocumentLink()
        .url(documentLink.link())
        .expiresAt(documentLink.expiresAt().toString());
  }

  public static DeploymentResult toDeployResourceResponse(final DeploymentRecord brokerResponse) {
    final var deployments = new ArrayList<DeploymentMetadataResult>();
    addDeployedProcess(deployments, brokerResponse.getProcessesMetadata());
    addDeployedDecision(deployments, brokerResponse.decisionsMetadata());
    addDeployedDecisionRequirements(deployments, brokerResponse.decisionRequirementsMetadata());
    addDeployedForm(deployments, brokerResponse.formMetadata());
    addDeployedResource(deployments, brokerResponse.resourceMetadata());
    return new DeploymentResult()
        .deploymentKey(KeyUtil.keyToString(brokerResponse.getDeploymentKey()))
        .tenantId(brokerResponse.getTenantId())
        .deployments(deployments);
  }

  public static DeleteResourceResponse toDeleteResourceResponse(
      final ResourceDeletionRecord brokerResponse) {
    BatchOperationCreatedResult batchOperation = null;
    if (brokerResponse.isDeleteHistory() && brokerResponse.getBatchOperationKey() > 0) {
      batchOperation =
          new BatchOperationCreatedResult()
              .batchOperationKey(KeyUtil.keyToString(brokerResponse.getBatchOperationKey()))
              .batchOperationType(
                  BatchOperationTypeEnum.valueOf(brokerResponse.getBatchOperationType().name()));
    }
    return new DeleteResourceResponse()
        .resourceKey(KeyUtil.keyToString(brokerResponse.getResourceKey()))
        .batchOperation(batchOperation);
  }

  public static ResourceResult toGetResourceResponse(final ResourceRecord resourceRecord) {
    return new ResourceResult()
        .resourceName(resourceRecord.getResourceName())
        .version(resourceRecord.getVersion())
        .resourceId(resourceRecord.getResourceId())
        .tenantId(resourceRecord.getTenantId())
        .resourceKey(KeyUtil.keyToString(resourceRecord.getResourceKey()))
        .versionTag(emptyToNull(resourceRecord.getVersionTag()));
  }

  public static String toGetResourceContentResponse(final ResourceRecord resourceRecord) {
    return resourceRecord.getResourceProp();
  }

  public static MessagePublicationResult toMessagePublicationResponse(
      final BrokerResponse<MessageRecord> brokerResponse) {
    return new MessagePublicationResult()
        .tenantId(brokerResponse.getResponse().getTenantId())
        .messageKey(KeyUtil.keyToString(brokerResponse.getKey()));
  }

  private static void addDeployedForm(
      final List<DeploymentMetadataResult> deployments,
      final ValueArray<FormMetadataRecord> formMetadataRecords) {
    formMetadataRecords.stream()
        .map(
            form ->
                new DeploymentMetadataResult()
                    .form(
                        new DeploymentFormResult()
                            .formId(form.getFormId())
                            .version(form.getVersion())
                            .resourceName(form.getResourceName())
                            .tenantId(form.getTenantId())
                            .formKey(KeyUtil.keyToString(form.getFormKey()))))
        .forEach(deployments::add);
  }

  private static void addDeployedResource(
      final List<DeploymentMetadataResult> deployments,
      final ValueArray<ResourceMetadataRecord> resourceMetadataRecords) {
    resourceMetadataRecords.stream()
        .map(
            resource ->
                new DeploymentMetadataResult()
                    .resource(
                        new DeploymentResourceResult()
                            .resourceId(resource.getResourceId())
                            .resourceName(resource.getResourceName())
                            .version(resource.getVersion())
                            .tenantId(resource.getTenantId())
                            .resourceKey(KeyUtil.keyToString(resource.getResourceKey()))))
        .forEach(deployments::add);
  }

  private static void addDeployedDecisionRequirements(
      final List<DeploymentMetadataResult> deployments,
      final ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataRecords) {
    decisionRequirementsMetadataRecords.stream()
        .map(
            decisionRequirement ->
                new DeploymentMetadataResult()
                    .decisionRequirements(
                        new DeploymentDecisionRequirementsResult()
                            .decisionRequirementsId(decisionRequirement.getDecisionRequirementsId())
                            .decisionRequirementsName(
                                decisionRequirement.getDecisionRequirementsName())
                            .version(decisionRequirement.getDecisionRequirementsVersion())
                            .resourceName(decisionRequirement.getResourceName())
                            .tenantId(decisionRequirement.getTenantId())
                            .decisionRequirementsKey(
                                KeyUtil.keyToString(
                                    decisionRequirement.getDecisionRequirementsKey()))))
        .forEach(deployments::add);
  }

  private static void addDeployedDecision(
      final List<DeploymentMetadataResult> deployments,
      final ValueArray<DecisionRecord> decisionRecords) {
    decisionRecords.stream()
        .map(
            decision ->
                new DeploymentMetadataResult()
                    .decisionDefinition(
                        new DeploymentDecisionResult()
                            .decisionDefinitionId(decision.getDecisionId())
                            .version(decision.getVersion())
                            .name(decision.getDecisionName())
                            .tenantId(decision.getTenantId())
                            .decisionRequirementsId(decision.getDecisionRequirementsId())
                            .decisionDefinitionKey(KeyUtil.keyToString(decision.getDecisionKey()))
                            .decisionRequirementsKey(
                                KeyUtil.keyToString(decision.getDecisionRequirementsKey()))))
        .forEach(deployments::add);
  }

  private static void addDeployedProcess(
      final List<DeploymentMetadataResult> deployments,
      final List<ProcessMetadataValue> processesMetadata) {
    processesMetadata.stream()
        .map(
            process ->
                new DeploymentMetadataResult()
                    .processDefinition(
                        new DeploymentProcessResult()
                            .processDefinitionId(process.getBpmnProcessId())
                            .processDefinitionVersion(process.getVersion())
                            .resourceName(process.getResourceName())
                            .tenantId(process.getTenantId())
                            .processDefinitionKey(
                                KeyUtil.keyToString(process.getProcessDefinitionKey()))))
        .forEach(deployments::add);
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
      final Map<String, Object> variables,
      final Set<String> tags,
      final String businessId) {
    return new CreateProcessInstanceResult()
        .processDefinitionId(bpmnProcessId)
        .processDefinitionVersion(version)
        .tenantId(tenantId)
        .variables(variables != null ? variables : Map.of())
        .processDefinitionKey(KeyUtil.keyToString(processDefinitionKey))
        .processInstanceKey(KeyUtil.keyToString(processInstanceKey))
        .tags(tags != null ? tags : Set.of())
        .businessId(emptyToNull(businessId));
  }

  public static BatchOperationCreatedResult toBatchOperationCreatedWithResultResponse(
      final BatchOperationCreationRecord brokerResponse) {
    return new BatchOperationCreatedResult()
        .batchOperationKey(KeyUtil.keyToString(brokerResponse.getBatchOperationKey()))
        .batchOperationType(
            BatchOperationTypeEnum.valueOf(brokerResponse.getBatchOperationType().name()));
  }

  public static SignalBroadcastResult toSignalBroadcastResponse(
      final BrokerResponse<SignalRecord> brokerResponse) {
    return new SignalBroadcastResult()
        .tenantId(brokerResponse.getResponse().getTenantId())
        .signalKey(KeyUtil.keyToString(brokerResponse.getKey()));
  }

  public static EvaluateConditionalResult toConditionalEvaluationResponse(
      final BrokerResponse<ConditionalEvaluationRecord> brokerResponse) {
    final var response = brokerResponse.getResponse();
    final var processInstances =
        response.getStartedProcessInstances().stream()
            .map(
                instance ->
                    new ProcessInstanceReference()
                        .processDefinitionKey(
                            KeyUtil.keyToString(instance.getProcessDefinitionKey()))
                        .processInstanceKey(KeyUtil.keyToString(instance.getProcessInstanceKey())))
            .toList();

    return new EvaluateConditionalResult()
        .conditionalEvaluationKey(KeyUtil.keyToString(brokerResponse.getKey()))
        .tenantId(response.getTenantId())
        .processInstances(processInstances);
  }

  public static AuthorizationCreateResult toAuthorizationCreateResponse(
      final AuthorizationRecord authorizationRecord) {
    return new AuthorizationCreateResult()
        .authorizationKey(KeyUtil.keyToString(authorizationRecord.getAuthorizationKey()));
  }

  public static UserCreateResult toUserCreateResponse(final UserRecord userRecord) {
    return new UserCreateResult()
        .username(userRecord.getUsername())
        .name(userRecord.getName())
        .email(userRecord.getEmail());
  }

  public static UserUpdateResult toUserUpdateResponse(final UserRecord userRecord) {
    return new UserUpdateResult()
        .username(userRecord.getUsername())
        .name(userRecord.getName())
        .email(userRecord.getEmail());
  }

  public static RoleCreateResult toRoleCreateResponse(final RoleRecord roleRecord) {
    return new RoleCreateResult()
        .roleId(roleRecord.getRoleId())
        .name(roleRecord.getName())
        .description(roleRecord.getDescription());
  }

  public static RoleUpdateResult toRoleUpdateResponse(final RoleRecord roleRecord) {
    return new RoleUpdateResult()
        .name(roleRecord.getName())
        .description(roleRecord.getDescription())
        .roleId(roleRecord.getRoleId());
  }

  public static GroupCreateResult toGroupCreateResponse(final GroupRecord groupRecord) {
    return new GroupCreateResult()
        .groupId(groupRecord.getGroupId())
        .name(groupRecord.getName())
        .description(groupRecord.getDescription());
  }

  public static GroupUpdateResult toGroupUpdateResponse(final GroupRecord groupRecord) {
    return new GroupUpdateResult()
        .groupId(groupRecord.getGroupId())
        .name(groupRecord.getName())
        .description(groupRecord.getDescription());
  }

  public static TenantCreateResult toTenantCreateResponse(final TenantRecord record) {
    return new TenantCreateResult()
        .tenantId(record.getTenantId())
        .name(record.getName())
        .description(record.getDescription());
  }

  public static TenantUpdateResult toTenantUpdateResponse(final TenantRecord record) {
    return new TenantUpdateResult()
        .tenantId(record.getTenantId())
        .name(record.getName())
        .description(record.getDescription());
  }

  public static MappingRuleCreateResult toMappingRuleCreateResponse(
      final MappingRuleRecord record) {
    return new MappingRuleCreateResult()
        .claimName(record.getClaimName())
        .claimValue(record.getClaimValue())
        .name(record.getName())
        .mappingRuleId(record.getMappingRuleId());
  }

  public static MappingRuleUpdateResult toMappingRuleUpdateResponse(
      final MappingRuleRecord record) {
    return new MappingRuleUpdateResult()
        .claimName(record.getClaimName())
        .claimValue(record.getClaimValue())
        .name(record.getName())
        .mappingRuleId(record.getMappingRuleId());
  }

  public static EvaluateDecisionResult toEvaluateDecisionResponse(
      final BrokerResponse<DecisionEvaluationRecord> brokerResponse) {
    final var record = brokerResponse.getResponse();
    return new EvaluateDecisionResult()
        .decisionDefinitionId(record.getDecisionId())
        .decisionDefinitionKey(KeyUtil.keyToString(record.getDecisionKey()))
        .decisionDefinitionName(record.getDecisionName())
        .decisionDefinitionVersion(record.getDecisionVersion())
        .decisionEvaluationKey(KeyUtil.keyToString(brokerResponse.getKey()))
        .decisionInstanceKey(KeyUtil.keyToString(brokerResponse.getKey()))
        .decisionRequirementsId(record.getDecisionRequirementsId())
        .decisionRequirementsKey(KeyUtil.keyToString(record.getDecisionRequirementsKey()))
        .evaluatedDecisions(buildEvaluatedDecisions(record))
        .output(record.getDecisionOutput())
        .tenantId(record.getTenantId())
        .failedDecisionDefinitionId(emptyToNull(record.getFailedDecisionId()))
        .failureMessage(emptyToNull(record.getEvaluationFailureMessage()));
  }

  private static List<EvaluatedDecisionResult> buildEvaluatedDecisions(
      final DecisionEvaluationRecord decisionEvaluationRecord) {
    return decisionEvaluationRecord.getEvaluatedDecisions().stream()
        .map(
            evaluatedDecision ->
                new EvaluatedDecisionResult()
                    .decisionDefinitionId(evaluatedDecision.getDecisionId())
                    .decisionDefinitionName(evaluatedDecision.getDecisionName())
                    .decisionDefinitionVersion(evaluatedDecision.getDecisionVersion())
                    .decisionDefinitionType(evaluatedDecision.getDecisionType())
                    .output(evaluatedDecision.getDecisionOutput())
                    .tenantId(evaluatedDecision.getTenantId())
                    .matchedRules(buildMatchedRules(evaluatedDecision.getMatchedRules()))
                    .evaluatedInputs(buildEvaluatedInputs(evaluatedDecision.getEvaluatedInputs()))
                    .decisionDefinitionKey(KeyUtil.keyToString(evaluatedDecision.getDecisionKey()))
                    .decisionEvaluationInstanceKey(
                        evaluatedDecision.getDecisionEvaluationInstanceKey()))
        .toList();
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

  public static ClusterVariableResult toClusterVariableResponse(
      final ClusterVariableRecord clusterVariableRecord) {
    return new ClusterVariableResult()
        .name(clusterVariableRecord.getName())
        .scope(
            clusterVariableRecord.isTenantScoped()
                ? ClusterVariableScopeEnum.TENANT
                : ClusterVariableScopeEnum.GLOBAL)
        .tenantId(
            clusterVariableRecord.isTenantScoped() ? clusterVariableRecord.getTenantId() : null)
        .value(clusterVariableRecord.getValue());
  }

  public static ExpressionEvaluationResult toExpressionEvaluationResult(
      final ExpressionRecord expressionRecord) {
    return new ExpressionEvaluationResult()
        .expression(expressionRecord.getExpression())
        .result(expressionRecord.getResultValue())
        .warnings(
            expressionRecord.getWarnings().stream()
                .map(w -> new ExpressionEvaluationWarningItem().message(w))
                .toList());
  }

  public static TopologyResponse toTopologyResponse(final Topology topology) {
    return new TopologyResponse()
        .brokers(topology.brokers().stream().map(ResponseMapper::toBrokerInfo).toList())
        .clusterId(topology.clusterId())
        .clusterSize(topology.clusterSize())
        .partitionsCount(topology.partitionsCount())
        .replicationFactor(topology.replicationFactor())
        .gatewayVersion(topology.gatewayVersion())
        .lastCompletedChangeId(KeyUtil.keyToString(topology.lastCompletedChangeId()));
  }

  private static BrokerInfo toBrokerInfo(final Broker broker) {
    return new BrokerInfo()
        .nodeId(broker.nodeId())
        .host(broker.host())
        .port(broker.port())
        .partitions(broker.partitions().stream().map(ResponseMapper::toPartition).toList())
        .version(broker.version());
  }

  private static io.camunda.gateway.protocol.model.Partition toPartition(
      final Partition partition) {
    return new io.camunda.gateway.protocol.model.Partition()
        .partitionId(partition.partitionId())
        .role(partition.role().name().toLowerCase())
        .health(partition.health().name().toLowerCase());
  }

  private static String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  static class RestJobActivationResult
      implements JobActivationResult<io.camunda.gateway.protocol.model.JobActivationResult> {

    private final io.camunda.gateway.protocol.model.JobActivationResult response;
    private final List<io.camunda.gateway.protocol.model.ActivatedJobResult> sizeExceedingJobs;

    RestJobActivationResult(
        final io.camunda.gateway.protocol.model.JobActivationResult response,
        final List<io.camunda.gateway.protocol.model.ActivatedJobResult> sizeExceedingJobs) {
      this.response = response;
      this.sizeExceedingJobs = sizeExceedingJobs;
    }

    @Override
    public int getJobsCount() {
      return response.getJobs().size();
    }

    @Override
    public List<JobActivationResult.ActivatedJob> getJobs() {
      return response.getJobs().stream()
          .map(
              j ->
                  new JobActivationResult.ActivatedJob(
                      KeyUtil.keyToLong(j.getJobKey()), j.getRetries()))
          .toList();
    }

    @Override
    public io.camunda.gateway.protocol.model.JobActivationResult getActivateJobsResponse() {
      return response;
    }

    @Override
    public List<JobActivationResult.ActivatedJob> getJobsToDefer() {
      final var result = new ArrayList<JobActivationResult.ActivatedJob>(sizeExceedingJobs.size());
      for (final var job : sizeExceedingJobs) {
        try {
          final var key = job.getJobKey();
          result.add(
              new JobActivationResult.ActivatedJob(KeyUtil.keyToLong(key), job.getRetries()));
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
