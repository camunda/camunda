/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;

public final class RecordToWrite {

  private static final long DEFAULT_KEY = 1;

  private final RecordMetadata recordMetadata;
  private UnifiedRecordValue unifiedRecordValue;

  private long key = DEFAULT_KEY;
  private int sourceIndex = -1;

  private RecordToWrite(final RecordMetadata recordMetadata) {
    this.recordMetadata = recordMetadata;
  }

  public static RecordToWrite command() {
    final RecordMetadata recordMetadata = new RecordMetadata();
    return new RecordToWrite(recordMetadata.recordType(RecordType.COMMAND));
  }

  public static RecordToWrite event() {
    final RecordMetadata recordMetadata = new RecordMetadata();
    return new RecordToWrite(recordMetadata.recordType(RecordType.EVENT));
  }

  public RecordToWrite job(final JobIntent intent) {
    return job(intent, new JobRecord().setType("type").setRetries(3).setWorker("worker"));
  }

  public RecordToWrite jobBatch(final JobBatchIntent intent) {
    recordMetadata.valueType(ValueType.JOB_BATCH).intent(intent);

    unifiedRecordValue =
        new JobBatchRecord()
            .setWorker("worker")
            .setTimeout(10_000L)
            .setType("type")
            .setMaxJobsToActivate(1);
    return this;
  }

  public RecordToWrite job(final JobIntent intent, final JobRecordValue value) {
    recordMetadata.valueType(ValueType.JOB).intent(intent);
    unifiedRecordValue = (JobRecord) value;
    return this;
  }

  public RecordToWrite workflowInstance(
      final WorkflowInstanceIntent intent, final WorkflowInstanceRecordValue value) {
    recordMetadata.valueType(ValueType.WORKFLOW_INSTANCE).intent(intent);
    unifiedRecordValue = (WorkflowInstanceRecord) value;
    return this;
  }

  public RecordToWrite causedBy(final int index) {
    sourceIndex = index;
    return this;
  }

  public RecordToWrite key(final long key) {
    this.key = key;
    return this;
  }

  public long getKey() {
    return key;
  }

  public RecordMetadata getRecordMetadata() {
    return recordMetadata;
  }

  public UnifiedRecordValue getUnifiedRecordValue() {
    return unifiedRecordValue;
  }

  public int getSourceIndex() {
    return sourceIndex;
  }
}
