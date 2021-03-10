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
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import java.util.Iterator;
import org.agrona.DirectBuffer;

public final class ResponseMapper {

  public static DeployProcessResponse toDeployProcessResponse(
      final long key, final DeploymentRecord brokerResponse) {
    final DeployProcessResponse.Builder responseBuilder =
        DeployProcessResponse.newBuilder().setKey(key);

    brokerResponse
        .processes()
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

  private static String bufferAsJson(final DirectBuffer customHeaders) {
    return MsgPackConverter.convertToJson(bufferAsArray(customHeaders));
  }

  @FunctionalInterface
  public interface BrokerResponseMapper<BrokerResponseDto, GrpcResponseT> {
    GrpcResponseT apply(long key, BrokerResponseDto responseDto);
  }
}
