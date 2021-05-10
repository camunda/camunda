/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.LongStream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ActivateJobsStub
    implements RequestStub<BrokerActivateJobsRequest, BrokerResponse<JobBatchRecord>> {

  public static final long JOB_BATCH_KEY = 123;
  public static final int RETRIES = 12;
  public static final long DEADLINE = 123123123L;

  public static final long PROCESS_INSTANCE_KEY = 123L;
  public static final String BPMN_PROCESS_ID = "stubProcess";
  public static final int PROCESS_DEFINITION_VERSION = 23;
  public static final long PROCESS_KEY = 4532L;
  public static final String ELEMENT_ID = "stubActivity";
  public static final long ELEMENT_INSTANCE_KEY = 459L;

  public static final String CUSTOM_HEADERS = "{\"foo\": 12, \"bar\": \"val\"}";
  public static final String VARIABLES = "{\"foo\": 13, \"bar\": \"world\"}";

  public static final DirectBuffer CUSTOM_HEADERS_MSGPACK =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(CUSTOM_HEADERS));
  public static final DirectBuffer VARIABLES_MSGPACK =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(VARIABLES));
  private final Map<String, Integer> availableJobs = new ConcurrentHashMap<>();

  public long getJobBatchKey() {
    return JOB_BATCH_KEY;
  }

  public int getRetries() {
    return RETRIES;
  }

  public long getDeadline() {
    return DEADLINE;
  }

  public String getCustomHeaders() {
    return CUSTOM_HEADERS;
  }

  public String getVariables() {
    return VARIABLES;
  }

  public long getProcessInstanceKey() {
    return PROCESS_INSTANCE_KEY;
  }

  public String getBpmnProcessId() {
    return BPMN_PROCESS_ID;
  }

  public int getProcessDefinitionVersion() {
    return PROCESS_DEFINITION_VERSION;
  }

  public long getProcessDefinitionKey() {
    return PROCESS_KEY;
  }

  public String getElementId() {
    return ELEMENT_ID;
  }

  public long getElementInstanceKey() {
    return ELEMENT_INSTANCE_KEY;
  }

  @Override
  public BrokerResponse<JobBatchRecord> handle(final BrokerActivateJobsRequest request)
      throws Exception {
    final int partitionId = request.getPartitionId();

    final JobBatchRecord requestDto = request.getRequestWriter();

    final JobBatchRecord response = new JobBatchRecord();
    response.setMaxJobsToActivate(requestDto.getMaxJobsToActivate());
    response.setWorker(requestDto.getWorkerBuffer());
    response.setType(requestDto.getTypeBuffer());
    response.setTimeout(requestDto.getTimeout());
    addJobs(
        response,
        partitionId,
        requestDto.getMaxJobsToActivate(),
        requestDto.getTypeBuffer(),
        requestDto.getWorkerBuffer());

    return new BrokerResponse<>(
        response, partitionId, Protocol.encodePartitionId(partitionId, JOB_BATCH_KEY));
  }

  public void addAvailableJobs(final String type, final int amount) {
    availableJobs.put(type, amount);
  }

  private void addJobs(
      final JobBatchRecord response,
      final int partitionId,
      final int amount,
      final DirectBuffer type,
      final DirectBuffer worker) {

    final int availableAmount = availableJobs.computeIfAbsent(bufferAsString(type), k -> 0);
    final int jobsToActivate = Math.min(amount, availableAmount);
    availableJobs.put(bufferAsString(type), availableAmount - jobsToActivate);
    LongStream.range(0, jobsToActivate)
        .forEach(
            key -> {
              response.jobKeys().add().setValue(Protocol.encodePartitionId(partitionId, key));
              response
                  .jobs()
                  .add()
                  .setType(type)
                  .setWorker(worker)
                  .setRetries(RETRIES)
                  .setDeadline(DEADLINE)
                  .setCustomHeaders(CUSTOM_HEADERS_MSGPACK)
                  .setVariables(VARIABLES_MSGPACK)
                  .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
                  .setBpmnProcessId(BPMN_PROCESS_ID)
                  .setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION)
                  .setProcessDefinitionKey(PROCESS_KEY)
                  .setElementId(ELEMENT_ID)
                  .setElementInstanceKey(ELEMENT_INSTANCE_KEY);
            });
  }

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerActivateJobsRequest.class, this);
  }
}
