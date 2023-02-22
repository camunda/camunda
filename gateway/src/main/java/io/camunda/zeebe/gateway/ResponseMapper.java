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
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MatchedDecisionRule;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import java.util.Iterator;
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
                    .setResourceName(bufferAsString(process.getResourceNameBuffer())));

    return responseBuilder.build();
  }

  public static DeployResourceResponse toDeployResourceResponse(
      final long key, final DeploymentRecord brokerResponse) {
    final var responseBuilder = DeployResourceResponse.newBuilder().setKey(key);

    brokerResponse.processesMetadata().stream()
        .map(
            process ->
                ProcessMetadata.newBuilder()
                    .setBpmnProcessId(process.getBpmnProcessId())
                    .setVersion(process.getVersion())
                    .setProcessDefinitionKey(process.getKey())
                    .setResourceName(process.getResourceName())
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
                    .build())
        .forEach(drg -> responseBuilder.addDeploymentsBuilder().setDecisionRequirements(drg));

    return responseBuilder.build();
  }

  public static PublishMessageResponse toPublishMessageResponse(
      final long key, final Object brokerResponse) {
    return PublishMessageResponse.newBuilder().setKey(key).build();
  }

  public static UpdateJobRetriesResponse toUpdateJobRetriesResponse(
      final long key, final JobRecord brokerResponse) {
    return UpdateJobRetriesResponse.getDefaultInstance();
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
        .setProcessInstanceKey(brokerResponse.getProcessInstanceKey())
        .build();
  }

  public static CreateProcessInstanceWithResultResponse toCreateProcessInstanceWithResultResponse(
      final long key, final ProcessInstanceResultRecord brokerResponse) {
    return CreateProcessInstanceWithResultResponse.newBuilder()
        .setProcessDefinitionKey(brokerResponse.getProcessDefinitionKey())
        .setBpmnProcessId(bufferAsString(brokerResponse.getBpmnProcessIdBuffer()))
        .setVersion(brokerResponse.getVersion())
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
            .setDecisionRequirementsKey(brokerResponse.getDecisionRequirementsKey());

    for (final EvaluatedDecisionValue intermediateDecision :
        brokerResponse.getEvaluatedDecisions()) {
      final EvaluatedDecision.Builder evaluatedDecisionBuilder =
          EvaluatedDecision.newBuilder()
              .setDecisionId(intermediateDecision.getDecisionId())
              .setDecisionKey(intermediateDecision.getDecisionKey())
              .setDecisionName(intermediateDecision.getDecisionName())
              .setDecisionVersion(intermediateDecision.getDecisionVersion())
              .setDecisionType(intermediateDecision.getDecisionType())
              .setDecisionOutput(intermediateDecision.getDecisionOutput());

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

  public static ActivateJobsResponse toActivateJobsResponse(
      final long key, final JobBatchRecord brokerResponse) {
    final ActivateJobsResponse.Builder responseBuilder = ActivateJobsResponse.newBuilder();

    final Iterator<LongValue> jobKeys = brokerResponse.jobKeys().iterator();
    final Iterator<JobRecord> jobs = brokerResponse.jobs().iterator();

    while (jobKeys.hasNext() && jobs.hasNext()) {
      final LongValue jobKey = jobKeys.next();
      final JobRecord job = jobs.next();
      final ActivatedJob activatedJob =
          ActivatedJob.newBuilder()
              .setKey(jobKey.getValue())
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
              .build();

      responseBuilder.addJobs(activatedJob);
    }

    return responseBuilder.build();
  }

  public static ResolveIncidentResponse toResolveIncidentResponse(
      final long key, final IncidentRecord incident) {
    return ResolveIncidentResponse.getDefaultInstance();
  }

  public static ModifyProcessInstanceResponse toModifyProcessInstanceResponse(
      final long key, final ProcessInstanceModificationRecord brokerResponse) {
    return ModifyProcessInstanceResponse.getDefaultInstance();
  }

  public static DeleteResourceResponse toDeleteResourceResponse(
      final long key, final ResourceDeletionRecord brokerResponse) {
    return DeleteResourceResponse.getDefaultInstance();
  }

  public static BroadcastSignalResponse toBroadcastSignalResponse(
      final long key, final SignalRecord brokerResponse) {
    return BroadcastSignalResponse.newBuilder().setKey(key).build();
  }

  private static String bufferAsJson(final DirectBuffer customHeaders) {
    return MsgPackConverter.convertToJson(bufferAsArray(customHeaders));
  }

  @FunctionalInterface
  public interface BrokerResponseMapper<BrokerResponseDto, GrpcResponseT> {
    GrpcResponseT apply(long key, BrokerResponseDto responseDto);
  }
}
