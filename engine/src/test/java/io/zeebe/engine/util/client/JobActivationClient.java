/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.client;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.value.JobBatchRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.function.BiFunction;

public class JobActivationClient {
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

  private final StreamProcessorRule environmentRule;
  private final JobBatchRecord jobBatchRecord;

  private int partitionId;
  private BiFunction<Integer, Long, Record<JobBatchRecordValue>> expectation =
      SUCCESS_EXPECTATION_SUPPLIER;

  public JobActivationClient(StreamProcessorRule environmentRule) {
    this.environmentRule = environmentRule;

    this.jobBatchRecord = new JobBatchRecord();
    jobBatchRecord
        .setTimeout(DEFAULT_TIMEOUT)
        .setWorker(DEFAULT_WORKER)
        .setMaxJobsToActivate(DEFAULT_MAX_ACTIVATE);
    partitionId = DEFAULT_PARTITION;
  }

  public JobActivationClient withType(String type) {
    jobBatchRecord.setType(type);
    return this;
  }

  public JobActivationClient withTimeout(long timeout) {
    jobBatchRecord.setTimeout(timeout);

    return this;
  }

  public JobActivationClient byWorker(String name) {
    jobBatchRecord.setWorker(name);
    return this;
  }

  public JobActivationClient onPartition(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public JobActivationClient withMaxJobsToActivate(int count) {
    jobBatchRecord.setMaxJobsToActivate(count);
    return this;
  }

  public JobActivationClient expectRejection() {
    expectation = REJECTION_EXPECTATION_SUPPLIER;
    return this;
  }

  public Record<JobBatchRecordValue> activate() {
    final long position =
        environmentRule.writeCommandOnPartition(
            partitionId, JobBatchIntent.ACTIVATE, jobBatchRecord);

    return expectation.apply(partitionId, position);
  }
}
