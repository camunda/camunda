/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionRequirementsMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluatedDecision;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluatedDecisionInput;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluatedDecisionOutput;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FormMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MatchedDecisionRule;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutResponse;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.agrona.DirectBuffer;

public final class ResponseMapper {

  public static DeployProcessResponse toDeployProcessResponse(
      final long key, final DeploymentRecord brokerResponse) {
    final DeployProcessResponse.Builder responseBuilder =
        DeployProcessResponse.newBuilder().setKey(key);

    brokerResponse
        .processesMetadata()
        .forEach(
            process ->
                responseBuilder
                    .addProcessesBuilder()
                    .setBpmnProcessId(bufferAsString(process.getBpmnProcessIdBuffer()))
                    .setVersion(process.getVersion())
                    .setProcessDefinitionKey(process.getKey())
                    .setTenantId(process.getTenantId())
                    .setResourceName(bufferAsString(process.getResourceNameBuffer())));

    return responseBuilder.build();
  }

  public static DeployResourceResponse toDeployResourceResponse(
      final long key, final DeploymentRecord brokerResponse) {
    final var responseBuilder =
        DeployResourceResponse.newBuilder().setKey(key).setTenantId(brokerResponse.getTenantId());

    brokerResponse.processesMetadata().stream()
        .map(
            process ->
                ProcessMetadata.newBuilder()
                    .setBpmnProcessId(process.getBpmnProcessId())
                    .setVersion(process.getVersion())
                    .setProcessDefinitionKey(process.getKey())
                    .setResourceName(process.getResourceName())
                    .setTenantId(process.getTenantId())
                    .build())
        .forEach(process -> responseBuilder.addDeploymentsBuilder().setProcess(process));

    brokerResponse.decisionsMetadata().stream()
        .map(
            decision ->
                DecisionMetadata.newBuilder()
                    .setDmnDecisionId(decision.getDecisionId())
                    .setDmnDecisionName(decision.getDecisionName())
                    .setVersion(decision.getVersion())
                    .setDecisionKey(decision.getDecisionKey())
                    .setDmnDecisionRequirementsId(decision.getDecisionRequirementsId())
                    .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
                    .setTenantId(decision.getTenantId())
                    .build())
        .forEach(decision -> responseBuilder.addDeploymentsBuilder().setDecision(decision));

    brokerResponse.decisionRequirementsMetadata().stream()
        .map(
            drg ->
                DecisionRequirementsMetadata.newBuilder()
                    .setDmnDecisionRequirementsId(drg.getDecisionRequirementsId())
                    .setDmnDecisionRequirementsName(drg.getDecisionRequirementsName())
                    .setVersion(drg.getDecisionRequirementsVersion())
                    .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
                    .setResourceName(drg.getResourceName())
                    .setTenantId(drg.getTenantId())
                    .build())
        .forEach(drg -> responseBuilder.addDeploymentsBuilder().setDecisionRequirements(drg));

    brokerResponse.formMetadata().stream()
        .map(
            form ->
                FormMetadata.newBuilder()
                    .setFormId(form.getFormId())
                    .setVersion(form.getVersion())
                    .setFormKey(form.getFormKey())
                    .setResourceName(form.getResourceName())
                    .setTenantId(form.getTenantId())
                    .build())
        .forEach(form -> responseBuilder.addDeploymentsBuilder().setForm(form));

    return responseBuilder.build();
  }

  public static PublishMessageResponse toPublishMessageResponse(
      final long key, final MessageRecord brokerResponse) {
    return PublishMessageResponse.newBuilder()
        .setKey(key)
        .setTenantId(brokerResponse.getTenantId())
        .build();
  }

  public static UpdateJobRetriesResponse toUpdateJobRetriesResponse(
      final long key, final JobRecord brokerResponse) {
    return UpdateJobRetriesResponse.getDefaultInstance();
  }

  public static UpdateJobTimeoutResponse toUpdateJobTimeoutResponse(
      final long key, final JobRecord brokerResponse) {
    return UpdateJobTimeoutResponse.getDefaultInstance();
  }

  public static FailJobResponse toFailJobResponse(final long key, final JobRecord brokerResponse) {
    return FailJobResponse.getDefaultInstance();
  }

  public static ThrowErrorResponse toThrowErrorResponse(
      final long key, final JobRecord brokerResponse) {
    return ThrowErrorResponse.getDefaultInstance();
  }

  public static CompleteJobResponse toCompleteJobResponse(
      final long key, final JobRecord brokerResponse) {
    return CompleteJobResponse.getDefaultInstance();
  }

  public static CreateProcessInstanceResponse toCreateProcessInstanceResponse(
      final long key, final ProcessInstanceCreationRecord brokerResponse) {
    return CreateProcessInstanceResponse.newBuilder()
        .setProcessDefinitionKey(brokerResponse.getProcessDefinitionKey())
        .setBpmnProcessId(bufferAsString(brokerResponse.getBpmnProcessIdBuffer()))
        .setVersion(brokerResponse.getVersion())
        .setTenantId(brokerResponse.getTenantId())
        .setProcessInstanceKey(brokerResponse.getProcessInstanceKey())
        .build();
  }

  public static CreateProcessInstanceWithResultResponse toCreateProcessInstanceWithResultResponse(
      final long key, final ProcessInstanceResultRecord brokerResponse) {
    return CreateProcessInstanceWithResultResponse.newBuilder()
        .setProcessDefinitionKey(brokerResponse.getProcessDefinitionKey())
        .setBpmnProcessId(bufferAsString(brokerResponse.getBpmnProcessIdBuffer()))
        .setVersion(brokerResponse.getVersion())
        .setTenantId(brokerResponse.getTenantId())
        .setProcessInstanceKey(brokerResponse.getProcessInstanceKey())
        .setVariables(bufferAsJson(brokerResponse.getVariablesBuffer()))
        .build();
  }

  public static EvaluateDecisionResponse toEvaluateDecisionResponse(
      final long key, final DecisionEvaluationRecord brokerResponse) {

    final EvaluateDecisionResponse.Builder responseBuilder =
        EvaluateDecisionResponse.newBuilder()
            .setDecisionId(brokerResponse.getDecisionId())
            .setDecisionKey(brokerResponse.getDecisionKey())
            .setDecisionName(brokerResponse.getDecisionName())
            .setDecisionVersion(brokerResponse.getDecisionVersion())
            .setDecisionRequirementsId(brokerResponse.getDecisionRequirementsId())
            .setDecisionRequirementsKey(brokerResponse.getDecisionRequirementsKey())
            .setTenantId(brokerResponse.getTenantId());

    for (final EvaluatedDecisionValue intermediateDecision :
        brokerResponse.getEvaluatedDecisions()) {
      final EvaluatedDecision.Builder evaluatedDecisionBuilder =
          EvaluatedDecision.newBuilder()
              .setDecisionId(intermediateDecision.getDecisionId())
              .setDecisionKey(intermediateDecision.getDecisionKey())
              .setDecisionName(intermediateDecision.getDecisionName())
              .setDecisionVersion(intermediateDecision.getDecisionVersion())
              .setDecisionType(intermediateDecision.getDecisionType())
              .setDecisionOutput(intermediateDecision.getDecisionOutput())
              .setTenantId(intermediateDecision.getTenantId());

      intermediateDecision.getEvaluatedInputs().stream()
          .map(
              evaluatedInput ->
                  EvaluatedDecisionInput.newBuilder()
                      .setInputId(evaluatedInput.getInputId())
                      .setInputName(evaluatedInput.getInputName())
                      .setInputValue(evaluatedInput.getInputValue())
                      .build())
          .forEach(evaluatedInput -> evaluatedDecisionBuilder.addEvaluatedInputs(evaluatedInput));

      intermediateDecision.getMatchedRules().stream()
          .map(
              matchedRule -> {
                final MatchedDecisionRule.Builder matchedRuleBuilder =
                    MatchedDecisionRule.newBuilder()
                        .setRuleId(matchedRule.getRuleId())
                        .setRuleIndex(matchedRule.getRuleIndex());

                matchedRule.getEvaluatedOutputs().stream()
                    .map(
                        evaluatedOutput ->
                            EvaluatedDecisionOutput.newBuilder()
                                .setOutputId(evaluatedOutput.getOutputId())
                                .setOutputName(evaluatedOutput.getOutputName())
                                .setOutputValue(evaluatedOutput.getOutputValue())
                                .build())
                    .forEach(
                        evaluatedOutput -> matchedRuleBuilder.addEvaluatedOutputs(evaluatedOutput));

                return matchedRuleBuilder.build();
              })
          .forEach(
              matchedDecisionRule -> evaluatedDecisionBuilder.addMatchedRules(matchedDecisionRule));

      responseBuilder.addEvaluatedDecisions(evaluatedDecisionBuilder.build());
    }

    if (brokerResponse.getFailedDecisionId().isEmpty()) {
      return responseBuilder.setDecisionOutput(brokerResponse.getDecisionOutput()).build();
    } else {
      return responseBuilder
          .setFailedDecisionId(brokerResponse.getFailedDecisionId())
          .setFailureMessage(brokerResponse.getEvaluationFailureMessage())
          .build();
    }
  }

  public static CancelProcessInstanceResponse toCancelProcessInstanceResponse(
      final long key, final ProcessInstanceRecord brokerResponse) {
    return CancelProcessInstanceResponse.getDefaultInstance();
  }

  public static SetVariablesResponse toSetVariablesResponse(
      final long key, final VariableDocumentRecord brokerResponse) {
    return SetVariablesResponse.newBuilder().setKey(key).build();
  }

  /**
   * While converting the broker response to the gRPC response, the response size is checked. If the
   * response size exceeds the maximum response size, exceeding jobs are added to the list of
   * exceeding jobs to be reactivated.
   *
   * <p>This is because the jobs returned from the broker is in MessagePack format and while
   * converting them to gRPC response the size of the response may increase (e.g. we do JSON and
   * String conversions see: {@link #toActivatedJob(long, JobRecord)}). That will cause the response
   * size to exceed the maximum response size allowed by the gateway and the gateway will log a
   * Stream Error indicating that streaming out the activated jobs failed.
   *
   * <p>If we do not respect the actual max response size, Zeebe Java Client rejects the response
   * containing the activated jobs and the client cancels the channel/stream/connection as well.
   * Leaving failed jobs non-activatable until their configured timeout.
   *
   * @param key the key of the request
   * @param brokerResponse the broker response
   * @param maxResponseSize the maximum size of the response
   * @return a pair of the response and a list of jobs that could not be included in the response
   *     because the response size exceeded the maximum response size
   */
  public static JobActivationResult toActivateJobsResponse(
      final long key, final JobBatchRecord brokerResponse, final long maxResponseSize) {
    final Iterator<LongValue> jobKeys = brokerResponse.jobKeys().iterator();
    final Iterator<JobRecord> jobs = brokerResponse.jobs().iterator();

    long currentResponseSize = 0L;
    final List<ActivatedJob> sizeExceedingJobs = new ArrayList<>();
    final List<ActivatedJob> responseJobs = new ArrayList<>();

    while (jobKeys.hasNext() && jobs.hasNext()) {
      final LongValue jobKey = jobKeys.next();
      final JobRecord job = jobs.next();
      final ActivatedJob activatedJob = toActivatedJob(jobKey.getValue(), job);

      final int activatedJobSize = activatedJob.getSerializedSize();
      if (currentResponseSize + activatedJobSize <= maxResponseSize) {
        responseJobs.add(activatedJob);
        currentResponseSize += activatedJobSize;
      } else {
        sizeExceedingJobs.add(activatedJob);
      }
    }

    ActivateJobsResponse response =
        ActivateJobsResponse.newBuilder().addAllJobs(responseJobs).build();
    // Response size can still exceed the maximum response size because of the metadata added on
    // building the response. Therefore, we check the response size again and if the response size
    // is still exceeding the maximum response size, we remove the last added job from the response
    // and add it to the list of jobs to be reactivated.
    // We do this until the response size is below the maximum response size.
    while (!responseJobs.isEmpty() && response.getSerializedSize() > maxResponseSize) {
      sizeExceedingJobs.add(responseJobs.removeLast());
      response = ActivateJobsResponse.newBuilder().addAllJobs(responseJobs).build();
    }

    return new JobActivationResult(response, sizeExceedingJobs);
  }

  public static ActivatedJob toActivatedJob(
      final io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob brokerResponse) {
    final long jobKey = brokerResponse.jobKey();
    final JobRecord job = brokerResponse.jobRecord();

    return toActivatedJob(jobKey, job);
  }

  private static ActivatedJob toActivatedJob(final long jobKey, final JobRecord job) {
    return ActivatedJob.newBuilder()
        .setKey(jobKey)
        .setType(bufferAsString(job.getTypeBuffer()))
        .setBpmnProcessId(job.getBpmnProcessId())
        .setElementId(job.getElementId())
        .setProcessInstanceKey(job.getProcessInstanceKey())
        .setProcessDefinitionVersion(job.getProcessDefinitionVersion())
        .setProcessDefinitionKey(job.getProcessDefinitionKey())
        .setElementInstanceKey(job.getElementInstanceKey())
        .setCustomHeaders(bufferAsJson(job.getCustomHeadersBuffer()))
        .setWorker(bufferAsString(job.getWorkerBuffer()))
        .setRetries(job.getRetries())
        .setDeadline(job.getDeadline())
        .setVariables(bufferAsJson(job.getVariablesBuffer()))
        .setTenantId(job.getTenantId())
        .build();
  }

  public static ResolveIncidentResponse toResolveIncidentResponse(
      final long key, final IncidentRecord incident) {
    return ResolveIncidentResponse.getDefaultInstance();
  }

  public static ModifyProcessInstanceResponse toModifyProcessInstanceResponse(
      final long key, final ProcessInstanceModificationRecord brokerResponse) {
    return ModifyProcessInstanceResponse.getDefaultInstance();
  }

  public static MigrateProcessInstanceResponse toMigrateProcessInstanceResponse(
      final long key, final ProcessInstanceMigrationRecord brokerResponse) {
    return MigrateProcessInstanceResponse.getDefaultInstance();
  }

  public static DeleteResourceResponse toDeleteResourceResponse(
      final long key, final ResourceDeletionRecord brokerResponse) {
    return DeleteResourceResponse.getDefaultInstance();
  }

  public static BroadcastSignalResponse toBroadcastSignalResponse(
      final long key, final SignalRecord brokerResponse) {
    return BroadcastSignalResponse.newBuilder()
        .setKey(key)
        .setTenantId(brokerResponse.getTenantId())
        .build();
  }

  private static String bufferAsJson(final DirectBuffer customHeaders) {
    return MsgPackConverter.convertToJson(bufferAsArray(customHeaders));
  }

  public record JobActivationResult(
      ActivateJobsResponse activateJobsResponse, List<ActivatedJob> jobsToDefer) {}

  @FunctionalInterface
  public interface BrokerResponseMapper<BrokerResponseDto, GrpcResponseT> {
    GrpcResponseT apply(long key, BrokerResponseDto responseDto);
  }
}
