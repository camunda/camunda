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
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedActivatedJobStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationCreateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationCreatedStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBrokerInfoStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableScopeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCreateProcessInstanceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeleteResourceResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeploymentDecisionRequirementsStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeploymentDecisionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeploymentFormStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeploymentMetadataStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeploymentProcessStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeploymentResourceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeploymentStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentCreationBatchResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentCreationFailureDetailStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentLinkStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentMetadataResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentReferenceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedEvaluateConditionalStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedEvaluateDecisionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedEvaluatedDecisionInputItemStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedEvaluatedDecisionOutputItemStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedEvaluatedDecisionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedExpressionEvaluationStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedExpressionEvaluationWarningItemStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupCreateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupUpdateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobActivationStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobKindEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobListenerEventTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleCreateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleUpdateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMatchedDecisionRuleItemStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessageCorrelationStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessagePublicationStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedPartitionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceReferenceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedResourceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleCreateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleUpdateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSignalBroadcastStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantCreateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantUpdateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTopologyResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserCreateStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskPropertiesStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserUpdateStrictContract;
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

  public static JobActivationResult<GeneratedJobActivationStrictContract> toActivateJobsResponse(
      final io.camunda.zeebe.gateway.impl.job.JobActivationResponse activationResponse) {
    final Iterator<LongValue> jobKeys = activationResponse.brokerResponse().jobKeys().iterator();
    final Iterator<JobRecord> jobs = activationResponse.brokerResponse().jobs().iterator();

    long currentResponseSize = 0L;

    final List<GeneratedActivatedJobStrictContract> sizeExceedingJobs = new ArrayList<>();
    final List<GeneratedActivatedJobStrictContract> responseJobs = new ArrayList<>();

    while (jobKeys.hasNext() && jobs.hasNext()) {
      final LongValue jobKey = jobKeys.next();
      final JobRecord job = jobs.next();
      final GeneratedActivatedJobStrictContract activatedJob =
          toActivatedJob(jobKey.getValue(), job);

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
        new GeneratedJobActivationStrictContract(responseJobs), sizeExceedingJobs);
  }

  static GeneratedActivatedJobStrictContract toActivatedJob(
      final long jobKey, final JobRecord job) {
    final long rootProcessInstanceKey = job.getRootProcessInstanceKey();
    return new GeneratedActivatedJobStrictContract(
        job.getType(),
        job.getBpmnProcessId(),
        job.getProcessDefinitionVersion(),
        job.getElementId(),
        job.getCustomHeadersObjectMap(),
        bufferAsString(job.getWorkerBuffer()),
        job.getRetries(),
        job.getDeadline(),
        job.getVariables(),
        job.getTenantId(),
        KeyUtil.keyToString(jobKey),
        KeyUtil.keyToString(job.getProcessInstanceKey()),
        KeyUtil.keyToString(job.getProcessDefinitionKey()),
        KeyUtil.keyToString(job.getElementInstanceKey()),
        EnumUtil.convert(job.getJobKind(), GeneratedJobKindEnum.class),
        EnumUtil.convert(job.getJobListenerEventType(), GeneratedJobListenerEventTypeEnum.class),
        toUserTaskProperties(job),
        job.getTags(),
        rootProcessInstanceKey > 0 ? KeyUtil.keyToString(rootProcessInstanceKey) : null);
  }

  private static GeneratedUserTaskPropertiesStrictContract toUserTaskProperties(
      final JobRecord job) {
    if (job.getJobKind() != TASK_LISTENER || CollectionUtils.isEmpty(job.getCustomHeaders())) {
      return null;
    }

    final var headers = job.getCustomHeaders();
    final var action = headers.get(Protocol.USER_TASK_ACTION_HEADER_NAME);
    if (action == null) {
      return null;
    }

    return new GeneratedUserTaskPropertiesStrictContract(
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

  public static GeneratedMessageCorrelationStrictContract toMessageCorrelationResponse(
      final MessageCorrelationRecord brokerResponse) {
    return new GeneratedMessageCorrelationStrictContract(
        brokerResponse.getTenantId(),
        KeyUtil.keyToString(brokerResponse.getMessageKey()),
        KeyUtil.keyToString(brokerResponse.getProcessInstanceKey()));
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

  public static GeneratedDocumentCreationBatchResponseStrictContract toDocumentReferenceBatch(
      final List<Either<DocumentErrorResponse, DocumentReferenceResponse>> responses) {
    final var createdDocuments = new ArrayList<GeneratedDocumentReferenceStrictContract>();
    final var failedDocuments =
        new ArrayList<GeneratedDocumentCreationFailureDetailStrictContract>();
    responses.forEach(
        documentResponse ->
            documentResponse.ifRightOrLeft(
                reference -> createdDocuments.add(toDocumentReference(reference)),
                error -> failedDocuments.add(toDocumentCreationFailure(error))));
    return new GeneratedDocumentCreationBatchResponseStrictContract(
        failedDocuments.isEmpty() ? null : failedDocuments,
        createdDocuments.isEmpty() ? null : createdDocuments);
  }

  public static GeneratedDocumentReferenceStrictContract toDocumentReference(
      final DocumentReferenceResponse response) {
    final var internalMetadata = response.metadata();
    final var customProperties =
        internalMetadata.customProperties() != null
            ? internalMetadata.customProperties()
            : Map.<String, Object>of();
    final var externalMetadata =
        new GeneratedDocumentMetadataResponseStrictContract(
            internalMetadata.contentType(),
            internalMetadata.fileName(),
            Optional.ofNullable(internalMetadata.expiresAt()).map(Object::toString).orElse(null),
            internalMetadata.size(),
            internalMetadata.processDefinitionId(),
            KeyUtil.keyToString(internalMetadata.processInstanceKey()),
            customProperties);
    return new GeneratedDocumentReferenceStrictContract(
        "CAMUNDA",
        response.storeId(),
        response.documentId(),
        response.contentHash(),
        externalMetadata);
  }

  private static GeneratedDocumentCreationFailureDetailStrictContract toDocumentCreationFailure(
      final DocumentErrorResponse error) {
    final var problemDetail = mapDocumentErrorToProblem(error.error());
    return new GeneratedDocumentCreationFailureDetailStrictContract(
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

  public static GeneratedDocumentLinkStrictContract toDocumentLinkResponse(
      final DocumentLink documentLink) {
    return new GeneratedDocumentLinkStrictContract(
        documentLink.link(), documentLink.expiresAt().toString());
  }

  public static GeneratedDeploymentStrictContract toDeployResourceResponse(
      final DeploymentRecord brokerResponse) {
    final var deployments = new ArrayList<GeneratedDeploymentMetadataStrictContract>();
    addDeployedProcess(deployments, brokerResponse.getProcessesMetadata());
    addDeployedDecision(deployments, brokerResponse.decisionsMetadata());
    addDeployedDecisionRequirements(deployments, brokerResponse.decisionRequirementsMetadata());
    addDeployedForm(deployments, brokerResponse.formMetadata());
    addDeployedResource(deployments, brokerResponse.resourceMetadata());
    return new GeneratedDeploymentStrictContract(
        KeyUtil.keyToString(brokerResponse.getDeploymentKey()),
        brokerResponse.getTenantId(),
        deployments);
  }

  public static GeneratedDeleteResourceResponseStrictContract toDeleteResourceResponse(
      final ResourceDeletionRecord brokerResponse) {
    GeneratedBatchOperationCreatedStrictContract batchOperation = null;
    if (brokerResponse.isDeleteHistory() && brokerResponse.getBatchOperationKey() > 0) {
      batchOperation =
          new GeneratedBatchOperationCreatedStrictContract(
              KeyUtil.keyToString(brokerResponse.getBatchOperationKey()),
              GeneratedBatchOperationTypeEnum.valueOf(
                  brokerResponse.getBatchOperationType().name()));
    }
    return new GeneratedDeleteResourceResponseStrictContract(
        KeyUtil.keyToString(brokerResponse.getResourceKey()), batchOperation);
  }

  public static GeneratedResourceStrictContract toGetResourceResponse(
      final ResourceRecord resourceRecord) {
    return new GeneratedResourceStrictContract(
        resourceRecord.getResourceName(),
        resourceRecord.getVersion(),
        emptyToNull(resourceRecord.getVersionTag()),
        resourceRecord.getResourceId(),
        resourceRecord.getTenantId(),
        String.valueOf(resourceRecord.getResourceKey()));
  }

  public static String toGetResourceContentResponse(final ResourceRecord resourceRecord) {
    return resourceRecord.getResourceProp();
  }

  public static GeneratedMessagePublicationStrictContract toMessagePublicationResponse(
      final BrokerResponse<MessageRecord> brokerResponse) {
    return new GeneratedMessagePublicationStrictContract(
        brokerResponse.getResponse().getTenantId(), KeyUtil.keyToString(brokerResponse.getKey()));
  }

  private static void addDeployedForm(
      final List<GeneratedDeploymentMetadataStrictContract> deployments,
      final ValueArray<FormMetadataRecord> formMetadataRecords) {
    formMetadataRecords.stream()
        .map(
            form ->
                new GeneratedDeploymentMetadataStrictContract(
                    null,
                    null,
                    null,
                    new GeneratedDeploymentFormStrictContract(
                        form.getFormId(),
                        form.getVersion(),
                        form.getResourceName(),
                        form.getTenantId(),
                        KeyUtil.keyToString(form.getFormKey())),
                    null))
        .forEach(deployments::add);
  }

  private static void addDeployedResource(
      final List<GeneratedDeploymentMetadataStrictContract> deployments,
      final ValueArray<ResourceMetadataRecord> resourceMetadataRecords) {
    resourceMetadataRecords.stream()
        .map(
            resource ->
                new GeneratedDeploymentMetadataStrictContract(
                    null,
                    null,
                    null,
                    null,
                    new GeneratedDeploymentResourceStrictContract(
                        resource.getResourceId(),
                        resource.getResourceName(),
                        resource.getVersion(),
                        resource.getTenantId(),
                        KeyUtil.keyToString(resource.getResourceKey()))))
        .forEach(deployments::add);
  }

  private static void addDeployedDecisionRequirements(
      final List<GeneratedDeploymentMetadataStrictContract> deployments,
      final ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataRecords) {
    decisionRequirementsMetadataRecords.stream()
        .map(
            decisionRequirement ->
                new GeneratedDeploymentMetadataStrictContract(
                    null,
                    null,
                    new GeneratedDeploymentDecisionRequirementsStrictContract(
                        decisionRequirement.getDecisionRequirementsId(),
                        decisionRequirement.getDecisionRequirementsName(),
                        decisionRequirement.getDecisionRequirementsVersion(),
                        decisionRequirement.getResourceName(),
                        decisionRequirement.getTenantId(),
                        KeyUtil.keyToString(decisionRequirement.getDecisionRequirementsKey())),
                    null,
                    null))
        .forEach(deployments::add);
  }

  private static void addDeployedDecision(
      final List<GeneratedDeploymentMetadataStrictContract> deployments,
      final ValueArray<DecisionRecord> decisionRecords) {
    decisionRecords.stream()
        .map(
            decision ->
                new GeneratedDeploymentMetadataStrictContract(
                    null,
                    new GeneratedDeploymentDecisionStrictContract(
                        decision.getDecisionId(),
                        decision.getVersion(),
                        decision.getDecisionName(),
                        decision.getTenantId(),
                        decision.getDecisionRequirementsId(),
                        KeyUtil.keyToString(decision.getDecisionKey()),
                        KeyUtil.keyToString(decision.getDecisionRequirementsKey())),
                    null,
                    null,
                    null))
        .forEach(deployments::add);
  }

  private static void addDeployedProcess(
      final List<GeneratedDeploymentMetadataStrictContract> deployments,
      final List<ProcessMetadataValue> processesMetadata) {
    processesMetadata.stream()
        .map(
            process ->
                new GeneratedDeploymentMetadataStrictContract(
                    new GeneratedDeploymentProcessStrictContract(
                        process.getBpmnProcessId(),
                        process.getVersion(),
                        process.getResourceName(),
                        process.getTenantId(),
                        KeyUtil.keyToString(process.getProcessDefinitionKey())),
                    null,
                    null,
                    null,
                    null))
        .forEach(deployments::add);
  }

  public static GeneratedCreateProcessInstanceStrictContract toCreateProcessInstanceResponse(
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

  public static GeneratedCreateProcessInstanceStrictContract
      toCreateProcessInstanceWithResultResponse(final ProcessInstanceResultRecord brokerResponse) {
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

  private static GeneratedCreateProcessInstanceStrictContract buildCreateProcessInstanceResponse(
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final Integer version,
      final Long processInstanceKey,
      final String tenantId,
      final Map<String, Object> variables,
      final Set<String> tags,
      final String businessId) {
    return new GeneratedCreateProcessInstanceStrictContract(
        bpmnProcessId,
        version,
        tenantId,
        variables != null ? variables : Map.of(),
        KeyUtil.keyToString(processDefinitionKey),
        KeyUtil.keyToString(processInstanceKey),
        tags != null ? tags : Set.of(),
        emptyToNull(businessId));
  }

  public static GeneratedBatchOperationCreatedStrictContract
      toBatchOperationCreatedWithResultResponse(final BatchOperationCreationRecord brokerResponse) {
    return new GeneratedBatchOperationCreatedStrictContract(
        KeyUtil.keyToString(brokerResponse.getBatchOperationKey()),
        GeneratedBatchOperationTypeEnum.valueOf(brokerResponse.getBatchOperationType().name()));
  }

  public static GeneratedSignalBroadcastStrictContract toSignalBroadcastResponse(
      final BrokerResponse<SignalRecord> brokerResponse) {
    return new GeneratedSignalBroadcastStrictContract(
        brokerResponse.getResponse().getTenantId(), KeyUtil.keyToString(brokerResponse.getKey()));
  }

  public static GeneratedEvaluateConditionalStrictContract toConditionalEvaluationResponse(
      final BrokerResponse<ConditionalEvaluationRecord> brokerResponse) {
    final var response = brokerResponse.getResponse();
    final var processInstances =
        response.getStartedProcessInstances().stream()
            .map(
                instance ->
                    new GeneratedProcessInstanceReferenceStrictContract(
                        KeyUtil.keyToString(instance.getProcessDefinitionKey()),
                        KeyUtil.keyToString(instance.getProcessInstanceKey())))
            .toList();

    return new GeneratedEvaluateConditionalStrictContract(
        KeyUtil.keyToString(brokerResponse.getKey()), response.getTenantId(), processInstances);
  }

  public static GeneratedAuthorizationCreateStrictContract toAuthorizationCreateResponse(
      final AuthorizationRecord authorizationRecord) {
    return new GeneratedAuthorizationCreateStrictContract(
        KeyUtil.keyToString(authorizationRecord.getAuthorizationKey()));
  }

  public static GeneratedUserCreateStrictContract toUserCreateResponse(
      final UserRecord userRecord) {
    return new GeneratedUserCreateStrictContract(
        userRecord.getUsername(), userRecord.getName(), userRecord.getEmail());
  }

  public static GeneratedUserUpdateStrictContract toUserUpdateResponse(
      final UserRecord userRecord) {
    return new GeneratedUserUpdateStrictContract(
        userRecord.getUsername(), userRecord.getName(), userRecord.getEmail());
  }

  public static GeneratedRoleCreateStrictContract toRoleCreateResponse(
      final RoleRecord roleRecord) {
    return new GeneratedRoleCreateStrictContract(
        roleRecord.getRoleId(), roleRecord.getName(), roleRecord.getDescription());
  }

  public static GeneratedRoleUpdateStrictContract toRoleUpdateResponse(
      final RoleRecord roleRecord) {
    return new GeneratedRoleUpdateStrictContract(
        roleRecord.getName(), roleRecord.getDescription(), roleRecord.getRoleId());
  }

  public static GeneratedGroupCreateStrictContract toGroupCreateResponse(
      final GroupRecord groupRecord) {
    return new GeneratedGroupCreateStrictContract(
        groupRecord.getGroupId(), groupRecord.getName(), groupRecord.getDescription());
  }

  public static GeneratedGroupUpdateStrictContract toGroupUpdateResponse(
      final GroupRecord groupRecord) {
    return new GeneratedGroupUpdateStrictContract(
        groupRecord.getGroupId(), groupRecord.getName(), groupRecord.getDescription());
  }

  public static GeneratedTenantCreateStrictContract toTenantCreateResponse(
      final TenantRecord record) {
    return new GeneratedTenantCreateStrictContract(
        record.getTenantId(), record.getName(), record.getDescription());
  }

  public static GeneratedTenantUpdateStrictContract toTenantUpdateResponse(
      final TenantRecord record) {
    return new GeneratedTenantUpdateStrictContract(
        record.getTenantId(), record.getName(), record.getDescription());
  }

  public static GeneratedMappingRuleCreateStrictContract toMappingRuleCreateResponse(
      final MappingRuleRecord record) {
    return new GeneratedMappingRuleCreateStrictContract(
        record.getClaimName(), record.getClaimValue(), record.getName(), record.getMappingRuleId());
  }

  public static GeneratedMappingRuleUpdateStrictContract toMappingRuleUpdateResponse(
      final MappingRuleRecord record) {
    return new GeneratedMappingRuleUpdateStrictContract(
        record.getClaimName(), record.getClaimValue(), record.getName(), record.getMappingRuleId());
  }

  public static GeneratedEvaluateDecisionStrictContract toEvaluateDecisionResponse(
      final BrokerResponse<DecisionEvaluationRecord> brokerResponse) {
    final var record = brokerResponse.getResponse();
    return new GeneratedEvaluateDecisionStrictContract(
        record.getDecisionId(),
        KeyUtil.keyToString(record.getDecisionKey()),
        record.getDecisionName(),
        record.getDecisionVersion(),
        KeyUtil.keyToString(brokerResponse.getKey()),
        KeyUtil.keyToString(brokerResponse.getKey()),
        record.getDecisionRequirementsId(),
        KeyUtil.keyToString(record.getDecisionRequirementsKey()),
        buildEvaluatedDecisions(record),
        emptyToNull(record.getFailedDecisionId()),
        emptyToNull(record.getEvaluationFailureMessage()),
        record.getDecisionOutput(),
        record.getTenantId());
  }

  private static List<GeneratedEvaluatedDecisionStrictContract> buildEvaluatedDecisions(
      final DecisionEvaluationRecord decisionEvaluationRecord) {
    return decisionEvaluationRecord.getEvaluatedDecisions().stream()
        .map(
            evaluatedDecision ->
                new GeneratedEvaluatedDecisionStrictContract(
                    evaluatedDecision.getDecisionId(),
                    evaluatedDecision.getDecisionName(),
                    evaluatedDecision.getDecisionVersion(),
                    evaluatedDecision.getDecisionType(),
                    evaluatedDecision.getDecisionOutput(),
                    evaluatedDecision.getTenantId(),
                    buildMatchedRules(evaluatedDecision.getMatchedRules()),
                    buildEvaluatedInputs(evaluatedDecision.getEvaluatedInputs()),
                    KeyUtil.keyToString(evaluatedDecision.getDecisionKey()),
                    evaluatedDecision.getDecisionEvaluationInstanceKey()))
        .toList();
  }

  private static List<GeneratedMatchedDecisionRuleItemStrictContract> buildMatchedRules(
      final List<MatchedRuleValue> matchedRuleValues) {
    return matchedRuleValues.stream()
        .map(
            matchedRuleValue ->
                new GeneratedMatchedDecisionRuleItemStrictContract(
                    matchedRuleValue.getRuleId(),
                    matchedRuleValue.getRuleIndex(),
                    buildEvaluatedOutputs(matchedRuleValue.getEvaluatedOutputs())))
        .toList();
  }

  private static List<GeneratedEvaluatedDecisionOutputItemStrictContract> buildEvaluatedOutputs(
      final List<EvaluatedOutputValue> evaluatedOutputs) {
    return evaluatedOutputs.stream()
        .map(
            evaluatedOutput ->
                new GeneratedEvaluatedDecisionOutputItemStrictContract(
                    evaluatedOutput.getOutputId(),
                    evaluatedOutput.getOutputName(),
                    evaluatedOutput.getOutputValue(),
                    null,
                    null))
        .toList();
  }

  private static List<GeneratedEvaluatedDecisionInputItemStrictContract> buildEvaluatedInputs(
      final List<EvaluatedInputValue> inputValues) {
    return inputValues.stream()
        .map(
            evaluatedInputValue ->
                new GeneratedEvaluatedDecisionInputItemStrictContract(
                    evaluatedInputValue.getInputId(),
                    evaluatedInputValue.getInputName(),
                    evaluatedInputValue.getInputValue()))
        .toList();
  }

  public static GeneratedClusterVariableStrictContract toClusterVariableResponse(
      final ClusterVariableRecord clusterVariableRecord) {
    return new GeneratedClusterVariableStrictContract(
        clusterVariableRecord.getName(),
        clusterVariableRecord.isTenantScoped()
            ? GeneratedClusterVariableScopeEnum.TENANT
            : GeneratedClusterVariableScopeEnum.GLOBAL,
        clusterVariableRecord.isTenantScoped() ? clusterVariableRecord.getTenantId() : null,
        clusterVariableRecord.getValue());
  }

  public static GeneratedExpressionEvaluationStrictContract toExpressionEvaluationResult(
      final ExpressionRecord expressionRecord) {
    return new GeneratedExpressionEvaluationStrictContract(
        expressionRecord.getExpression(),
        expressionRecord.getResultValue(),
        expressionRecord.getWarnings().stream()
            .map(GeneratedExpressionEvaluationWarningItemStrictContract::new)
            .toList());
  }

  public static GeneratedTopologyResponseStrictContract toTopologyResponse(
      final Topology topology) {
    return new GeneratedTopologyResponseStrictContract(
        topology.brokers().stream().map(ResponseMapper::toBrokerInfo).toList(),
        topology.clusterId(),
        topology.clusterSize(),
        topology.partitionsCount(),
        topology.replicationFactor(),
        topology.gatewayVersion(),
        KeyUtil.keyToString(topology.lastCompletedChangeId()));
  }

  private static GeneratedBrokerInfoStrictContract toBrokerInfo(final Broker broker) {
    return new GeneratedBrokerInfoStrictContract(
        broker.nodeId(),
        broker.host(),
        broker.port(),
        broker.partitions().stream().map(ResponseMapper::toPartition).toList(),
        broker.version());
  }

  private static GeneratedPartitionStrictContract toPartition(final Partition partition) {
    return new GeneratedPartitionStrictContract(
        partition.partitionId(),
        partition.role().name().toLowerCase(),
        partition.health().name().toLowerCase());
  }

  private static String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  static class RestJobActivationResult
      implements JobActivationResult<GeneratedJobActivationStrictContract> {

    private final GeneratedJobActivationStrictContract response;
    private final List<GeneratedActivatedJobStrictContract> sizeExceedingJobs;

    RestJobActivationResult(
        final GeneratedJobActivationStrictContract response,
        final List<GeneratedActivatedJobStrictContract> sizeExceedingJobs) {
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
    public GeneratedJobActivationStrictContract getActivateJobsResponse() {
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
