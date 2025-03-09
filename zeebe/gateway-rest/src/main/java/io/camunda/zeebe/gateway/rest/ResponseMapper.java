/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.DocumentAlreadyExists;
import io.camunda.document.api.DocumentError.DocumentNotFound;
import io.camunda.document.api.DocumentError.InvalidInput;
import io.camunda.document.api.DocumentError.OperationNotSupported;
import io.camunda.document.api.DocumentError.StoreDoesNotExist;
import io.camunda.document.api.DocumentLink;
import io.camunda.service.DocumentServices.DocumentErrorResponse;
import io.camunda.service.DocumentServices.DocumentReferenceResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult;
import io.camunda.zeebe.gateway.protocol.rest.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.rest.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentDecision;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentDecisionRequirements;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentForm;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentMetadata;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentProcess;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentResource;
import io.camunda.zeebe.gateway.protocol.rest.DeploymentResponse;
import io.camunda.zeebe.gateway.protocol.rest.DocumentCreationBatchResponse;
import io.camunda.zeebe.gateway.protocol.rest.DocumentCreationFailureDetail;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.protocol.rest.DocumentReference;
import io.camunda.zeebe.gateway.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.EvaluateDecisionResponse;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionInputItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.zeebe.gateway.protocol.rest.MessageCorrelationResponse;
import io.camunda.zeebe.gateway.protocol.rest.MessagePublicationResponse;
import io.camunda.zeebe.gateway.protocol.rest.ResourceResult;
import io.camunda.zeebe.gateway.protocol.rest.SignalBroadcastResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserCreateResponse;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedOutputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public final class ResponseMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ResponseMapper.class);

  public static JobActivationResult<JobActivationResponse> toActivateJobsResponse(
      final io.camunda.zeebe.gateway.impl.job.JobActivationResponse activationResponse) {
    final Iterator<LongValue> jobKeys = activationResponse.brokerResponse().jobKeys().iterator();
    final Iterator<JobRecord> jobs = activationResponse.brokerResponse().jobs().iterator();

    long currentResponseSize = 0L;
    final JobActivationResponse response = new JobActivationResponse();

    final List<ActivatedJob> sizeExceedingJobs = new ArrayList<>();
    final List<ActivatedJob> responseJobs = new ArrayList<>();

    while (jobKeys.hasNext() && jobs.hasNext()) {
      final LongValue jobKey = jobKeys.next();
      final JobRecord job = jobs.next();
      final ActivatedJob activatedJob = toActivatedJob(jobKey.getValue(), job);

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

  private static ActivatedJob toActivatedJob(final long jobKey, final JobRecord job) {
    return new ActivatedJob()
        .jobKey(jobKey)
        .type(job.getType())
        .processDefinitionId(job.getBpmnProcessId())
        .elementId(job.getElementId())
        .processInstanceKey(job.getProcessInstanceKey())
        .processDefinitionVersion(job.getProcessDefinitionVersion())
        .processDefinitionKey(job.getProcessDefinitionKey())
        .elementInstanceKey(job.getElementInstanceKey())
        .worker(bufferAsString(job.getWorkerBuffer()))
        .retries(job.getRetries())
        .deadline(job.getDeadline())
        .variables(job.getVariables())
        .customHeaders(job.getCustomHeadersObjectMap())
        .tenantId(job.getTenantId());
  }

  public static ResponseEntity<Object> toMessageCorrelationResponse(
      final MessageCorrelationRecord brokerResponse) {
    final var response =
        new MessageCorrelationResponse()
            .messageKey(brokerResponse.getMessageKey())
            .tenantId(brokerResponse.getTenantId())
            .processInstanceKey(brokerResponse.getProcessInstanceKey());
    return new ResponseEntity<>(response, HttpStatus.OK);
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

  public static ProblemDetail mapDocumentErrorToProblem(final DocumentError e) {
    final String detail;
    final HttpStatusCode status;
    switch (e) {
      case final DocumentNotFound notFound -> {
        detail = String.format("Document with id '%s' not found", notFound.documentId());
        status = HttpStatus.NOT_FOUND;
      }
      case final InvalidInput invalidInput -> {
        detail = invalidInput.message();
        status = HttpStatus.BAD_REQUEST;
      }
      case final DocumentAlreadyExists documentAlreadyExists -> {
        detail =
            String.format(
                "Document with id '%s' already exists", documentAlreadyExists.documentId());
        status = HttpStatus.CONFLICT;
      }
      case final StoreDoesNotExist storeDoesNotExist -> {
        detail =
            String.format(
                "Document store with id '%s' does not exist", storeDoesNotExist.storeId());
        status = HttpStatus.BAD_REQUEST;
      }
      case final OperationNotSupported operationNotSupported -> {
        detail = operationNotSupported.message();
        status = HttpStatus.METHOD_NOT_ALLOWED;
      }
      default -> {
        detail = "An error occurred: " + e.getClass().getName();
        status = HttpStatus.INTERNAL_SERVER_ERROR;
      }
    }
    return RestErrorMapper.createProblemDetail(status, detail, e.getClass().getSimpleName());
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
            .processInstanceKey(internalMetadata.processInstanceKey());
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
        new DeploymentResponse()
            .deploymentKey(brokerResponse.getDeploymentKey())
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
        new MessagePublicationResponse()
            .messageKey(brokerResponse.getKey())
            .tenantId(brokerResponse.getResponse().getTenantId());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private static void addDeployedForm(
      final DeploymentResponse response, final ValueArray<FormMetadataRecord> formMetadataRecords) {
    formMetadataRecords.stream()
        .map(
            form ->
                new DeploymentForm()
                    .formId(form.getFormId())
                    .version(form.getVersion())
                    .formKey(form.getFormKey())
                    .resourceName(form.getResourceName())
                    .tenantId(form.getTenantId()))
        .map(deploymentForm -> new DeploymentMetadata().form(deploymentForm))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedResource(
      final DeploymentResponse response,
      final ValueArray<ResourceMetadataRecord> resourceMetadataRecords) {
    resourceMetadataRecords.stream()
        .map(
            resource ->
                new DeploymentResource()
                    .resourceId(resource.getResourceId())
                    .version(resource.getVersion())
                    .resourceKey(resource.getResourceKey())
                    .resourceName(resource.getResourceName())
                    .tenantId(resource.getTenantId()))
        .map(deploymentForm -> new DeploymentMetadata().resource(deploymentForm))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedDecisionRequirements(
      final DeploymentResponse response,
      final ValueArray<DecisionRequirementsMetadataRecord> decisionRequirementsMetadataRecords) {
    decisionRequirementsMetadataRecords.stream()
        .map(
            decisionRequirement ->
                new DeploymentDecisionRequirements()
                    .decisionRequirementsId(decisionRequirement.getDecisionRequirementsId())
                    .version(decisionRequirement.getDecisionRequirementsVersion())
                    .decisionRequirementsName(decisionRequirement.getDecisionRequirementsName())
                    .tenantId(decisionRequirement.getTenantId())
                    .decisionRequirementsKey(decisionRequirement.getDecisionRequirementsKey())
                    .resourceName(decisionRequirement.getResourceName()))
        .map(
            deploymentDecisionRequirement ->
                new DeploymentMetadata().decisionRequirements(deploymentDecisionRequirement))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedDecision(
      final DeploymentResponse response, final ValueArray<DecisionRecord> decisionRecords) {
    decisionRecords.stream()
        .map(
            decision ->
                new DeploymentDecision()
                    .decisionDefinitionId(decision.getDecisionId())
                    .version(decision.getVersion())
                    .decisionDefinitionKey(decision.getDecisionKey())
                    .name(decision.getDecisionName())
                    .tenantId(decision.getTenantId())
                    .decisionRequirementsId(decision.getDecisionRequirementsId())
                    .decisionRequirementsKey(decision.getDecisionRequirementsKey()))
        .map(deploymentDecision -> new DeploymentMetadata().decisionDefinition(deploymentDecision))
        .forEach(response::addDeploymentsItem);
  }

  private static void addDeployedProcess(
      final DeploymentResponse response, final List<ProcessMetadataValue> processesMetadata) {
    processesMetadata.stream()
        .map(
            process ->
                new DeploymentProcess()
                    .processDefinitionId(process.getBpmnProcessId())
                    .processDefinitionVersion(process.getVersion())
                    .processDefinitionKey(process.getProcessDefinitionKey())
                    .tenantId(process.getTenantId())
                    .resourceName(process.getResourceName()))
        .map(deploymentProcess -> new DeploymentMetadata().processDefinition(deploymentProcess))
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
        new CreateProcessInstanceResponse()
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionId(bpmnProcessId)
            .processDefinitionVersion(version)
            .processInstanceKey(processInstanceKey)
            .tenantId(tenantId);
    if (variables != null) {
      response.variables(variables);
    }

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toSignalBroadcastResponse(
      final BrokerResponse<SignalRecord> brokerResponse) {
    final var response =
        new SignalBroadcastResponse()
            .signalKey(brokerResponse.getKey())
            .tenantId(brokerResponse.getResponse().getTenantId());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static ResponseEntity<Object> toUserCreateResponse(final UserRecord userRecord) {
    final var response = new UserCreateResponse().userKey(userRecord.getUserKey());
    return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
  }

  public static ResponseEntity<Object> toEvaluateDecisionResponse(
      final BrokerResponse<DecisionEvaluationRecord> brokerResponse) {
    final var decisionEvaluationRecord = brokerResponse.getResponse();
    final var response =
        new EvaluateDecisionResponse()
            .decisionDefinitionId(decisionEvaluationRecord.getDecisionId())
            .decisionDefinitionKey(decisionEvaluationRecord.getDecisionKey())
            .decisionDefinitionName(decisionEvaluationRecord.getDecisionName())
            .decisionDefinitionVersion(decisionEvaluationRecord.getDecisionVersion())
            .decisionRequirementsId(decisionEvaluationRecord.getDecisionRequirementsId())
            .decisionRequirementsKey(decisionEvaluationRecord.getDecisionRequirementsKey())
            .output(decisionEvaluationRecord.getDecisionOutput())
            .failedDecisionDefinitionId(decisionEvaluationRecord.getFailedDecisionId())
            .failureMessage(decisionEvaluationRecord.getEvaluationFailureMessage())
            .tenantId(decisionEvaluationRecord.getTenantId())
            .decisionInstanceKey(brokerResponse.getKey());

    buildEvaluatedDecisions(decisionEvaluationRecord, response);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private static void buildEvaluatedDecisions(
      final DecisionEvaluationRecord decisionEvaluationRecord,
      final EvaluateDecisionResponse response) {
    decisionEvaluationRecord.getEvaluatedDecisions().stream()
        .map(
            evaluatedDecision ->
                new EvaluatedDecisionItem()
                    .decisionDefinitionKey(evaluatedDecision.getDecisionKey())
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

  static class RestJobActivationResult implements JobActivationResult<JobActivationResponse> {

    private final JobActivationResponse response;
    private final List<io.camunda.zeebe.gateway.protocol.rest.ActivatedJob> sizeExceedingJobs;

    RestJobActivationResult(
        final JobActivationResponse response,
        final List<io.camunda.zeebe.gateway.protocol.rest.ActivatedJob> sizeExceedingJobs) {
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
          .map(j -> new ActivatedJob(j.getJobKey(), j.getRetries()))
          .toList();
    }

    @Override
    public JobActivationResponse getActivateJobsResponse() {
      return response;
    }

    @Override
    public List<ActivatedJob> getJobsToDefer() {
      final var result = new ArrayList<ActivatedJob>(sizeExceedingJobs.size());
      for (final var job : sizeExceedingJobs) {
        final var key = job.getJobKey();
        result.add(new ActivatedJob(key, job.getRetries()));
      }
      return result;
    }
  }
}
