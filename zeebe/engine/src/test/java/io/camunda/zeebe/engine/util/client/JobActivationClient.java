/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

public final class JobActivationClient {
  private static final int DEFAULT_PARTITION = 1;
  private static final long DEFAULT_TIMEOUT = 10000L;
  private static final String DEFAULT_WORKER = "defaultWorker";
  private static final int DEFAULT_MAX_ACTIVATE = 10;

  private static final BiFunction<Integer, Long, Record<JobBatchRecordValue>>
      SUCCESS_EXPECTATION_SUPPLIER =
          (partitionId, position) ->
              RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
                  .withPartitionId(partitionId)
                  .withSourceRecordPosition(position)
                  .getFirst();

  private static final BiFunction<Integer, Long, Record<JobBatchRecordValue>>
      REJECTION_EXPECTATION_SUPPLIER =
          (partitionId, position) ->
              RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATE)
                  .onlyCommandRejections()
                  .withPartitionId(partitionId)
                  .withSourceRecordPosition(position)
                  .getFirst();

  private final CommandWriter writer;
  private final JobBatchRecord jobBatchRecord;

  private int partitionId;
  private Integer requestStreamId;
  private Long requestId;
  private BiFunction<Integer, Long, Record<JobBatchRecordValue>> expectation =
      SUCCESS_EXPECTATION_SUPPLIER;

  public JobActivationClient(final CommandWriter writer) {
    this.writer = writer;

    jobBatchRecord = new JobBatchRecord();
    jobBatchRecord.setTimeout(DEFAULT_TIMEOUT).setMaxJobsToActivate(DEFAULT_MAX_ACTIVATE);
    partitionId = DEFAULT_PARTITION;
  }

  public JobActivationClient withType(final String type) {
    jobBatchRecord.setType(type);
    return this;
  }

  public JobActivationClient withTimeout(final long timeout) {
    jobBatchRecord.setTimeout(timeout);
    return this;
  }

  public JobActivationClient withTenantId(final String tenantId) {
    jobBatchRecord.tenantIds().add().wrap(BufferUtil.wrapString(tenantId));
    return this;
  }

  public JobActivationClient withTenantIds(final List<String> tenantIds) {
    tenantIds.stream().forEach(this::withTenantId);
    return this;
  }

  public JobActivationClient withFetchVariables(final String... fetchVariables) {
    return withFetchVariables(Arrays.asList(fetchVariables));
  }

  public JobActivationClient withFetchVariables(final List<String> fetchVariables) {
    final ValueArray<StringValue> variables = jobBatchRecord.variables();
    fetchVariables.stream()
        .map(BufferUtil::wrapString)
        .forEach(buffer -> variables.add().wrap(buffer));
    return this;
  }

  public JobActivationClient byWorker(final String name) {
    jobBatchRecord.setWorker(name);
    return this;
  }

  public JobActivationClient onPartition(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public JobActivationClient withMaxJobsToActivate(final int count) {
    jobBatchRecord.setMaxJobsToActivate(count);
    return this;
  }

  public JobActivationClient withLease() {
    jobBatchRecord.setWithLease(true);
    return this;
  }

  public JobActivationClient withTenantFilter(final TenantFilter tenantFilter) {
    jobBatchRecord.setTenantFilter(tenantFilter);
    return this;
  }

  public JobActivationClient withRequestStreamId(final int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  public JobActivationClient withRequestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }

  public JobActivationClient expectRejection() {
    expectation = REJECTION_EXPECTATION_SUPPLIER;
    return this;
  }

  public Record<JobBatchRecordValue> activate() {
    if ((requestStreamId == null) != (requestId == null)) {
      throw new IllegalStateException(
          "Expected both request stream id and request id to be set together, or neither, but got "
              + "requestStreamId=%s and requestId=%s".formatted(requestStreamId, requestId));
    }
    // when a request id is set, the command carries request metadata so the engine writes a
    // command response; both variants write on the configured partition
    final long position =
        requestStreamId != null && requestId != null
            ? writer.writeCommandOnPartition(
                partitionId,
                builder ->
                    builder
                        .intent(JobBatchIntent.ACTIVATE)
                        .requestStreamId(requestStreamId)
                        .requestId(requestId)
                        .authorizations()
                        .event(jobBatchRecord))
            : writer.writeCommandOnPartition(partitionId, JobBatchIntent.ACTIVATE, jobBatchRecord);

    return expectation.apply(partitionId, position);
  }

  public Record<JobBatchRecordValue> activate(final String username) {
    final long position =
        writer.writeCommandOnPartition(
            partitionId, JobBatchIntent.ACTIVATE, jobBatchRecord, username);

    return expectation.apply(partitionId, position);
  }
}
