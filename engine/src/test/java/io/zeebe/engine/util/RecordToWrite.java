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
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;

public final class RecordToWrite {

  private static final int DEFAULT_KEY = 1;
  private final RecordMetadata recordMetadata;
  private UnifiedRecordValue unifiedRecordValue;
  private int sourceIndex = -1;

  private RecordToWrite(RecordMetadata recordMetadata) {
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

  public RecordToWrite job(JobIntent intent) {
    recordMetadata.valueType(ValueType.JOB).intent(intent);

    final JobRecord jobRecord = new JobRecord();
    jobRecord.setType("type").setRetries(3).setWorker("worker");

    unifiedRecordValue = jobRecord;
    return this;
  }

  public RecordToWrite jobBatch(JobBatchIntent intent) {
    recordMetadata.valueType(ValueType.JOB_BATCH).intent(intent);

    final JobBatchRecord jobBatchRecord =
        new JobBatchRecord()
            .setWorker("worker")
            .setTimeout(10_000L)
            .setType("type")
            .setMaxJobsToActivate(1);

    unifiedRecordValue = jobBatchRecord;
    return this;
  }

  public RecordToWrite causedBy(int index) {
    sourceIndex = index;
    return this;
  }

  public long getKey() {
    return DEFAULT_KEY;
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
