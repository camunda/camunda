/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import static io.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import java.util.Iterator;
import org.agrona.DirectBuffer;

public class ResponseMapper {

  public static DeployWorkflowResponse toDeployWorkflowResponse(
      long key, DeploymentRecord brokerResponse) {
    final DeployWorkflowResponse.Builder responseBuilder =
        DeployWorkflowResponse.newBuilder().setKey(key);

    brokerResponse
        .workflows()
        .forEach(
            workflow ->
                responseBuilder
                    .addWorkflowsBuilder()
                    .setBpmnProcessId(bufferAsString(workflow.getBpmnProcessIdBuffer()))
                    .setVersion(workflow.getVersion())
                    .setWorkflowKey(workflow.getKey())
                    .setResourceName(bufferAsString(workflow.getResourceNameBuffer())));

    return responseBuilder.build();
  }

  public static PublishMessageResponse toPublishMessageResponse(long key, Object brokerResponse) {
    return PublishMessageResponse.getDefaultInstance();
  }

  public static UpdateJobRetriesResponse toUpdateJobRetriesResponse(
      long key, JobRecord brokerResponse) {
    return UpdateJobRetriesResponse.getDefaultInstance();
  }

  public static FailJobResponse toFailJobResponse(long key, JobRecord brokerResponse) {
    return FailJobResponse.getDefaultInstance();
  }

  public static CompleteJobResponse toCompleteJobResponse(long key, JobRecord brokerResponse) {
    return CompleteJobResponse.getDefaultInstance();
  }

  public static CreateWorkflowInstanceResponse toCreateWorkflowInstanceResponse(
      long key, WorkflowInstanceCreationRecord brokerResponse) {
    return CreateWorkflowInstanceResponse.newBuilder()
        .setWorkflowKey(brokerResponse.getWorkflowKey())
        .setBpmnProcessId(bufferAsString(brokerResponse.getBpmnProcessIdBuffer()))
        .setVersion(brokerResponse.getVersion())
        .setWorkflowInstanceKey(brokerResponse.getWorkflowInstanceKey())
        .build();
  }

  public static CancelWorkflowInstanceResponse toCancelWorkflowInstanceResponse(
      long key, WorkflowInstanceRecord brokerResponse) {
    return CancelWorkflowInstanceResponse.getDefaultInstance();
  }

  public static SetVariablesResponse toSetVariablesResponse(
      long key, VariableDocumentRecord brokerResponse) {
    return SetVariablesResponse.getDefaultInstance();
  }

  public static ActivateJobsResponse toActivateJobsResponse(
      long key, JobBatchRecord brokerResponse) {
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
              .setWorkflowInstanceKey(job.getWorkflowInstanceKey())
              .setWorkflowDefinitionVersion(job.getWorkflowDefinitionVersion())
              .setWorkflowKey(job.getWorkflowKey())
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
      long key, IncidentRecord incident) {
    return ResolveIncidentResponse.getDefaultInstance();
  }

  private static String bufferAsJson(DirectBuffer customHeaders) {
    return MsgPackConverter.convertToJson(bufferAsArray(customHeaders));
  }

  @FunctionalInterface
  public interface BrokerResponseMapper<BrokerResponseDto, GrpcResponse> {
    GrpcResponse apply(long key, BrokerResponseDto responseDto);
  }
}
