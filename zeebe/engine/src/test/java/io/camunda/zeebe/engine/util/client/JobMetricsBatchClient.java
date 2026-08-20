/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.LongFunction;

public final class JobMetricsBatchClient {

  private static final LongFunction<Record<JobMetricsBatchRecordValue>> SUCCESS_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.jobMetricsBatchRecords()
              .onlyEvents()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();

  private final JobMetricsBatchRecord jobMetricsBatchRecord;
  private final CommandWriter writer;
  private final LongFunction<Record<JobMetricsBatchRecordValue>> expectation = SUCCESS_SUPPLIER;

  public JobMetricsBatchClient(final CommandWriter writer) {
    jobMetricsBatchRecord = new JobMetricsBatchRecord();
    this.writer = writer;
  }

  public Record<JobMetricsBatchRecordValue> export() {
    final long position = writer.writeCommand(JobMetricsBatchIntent.EXPORT, jobMetricsBatchRecord);
    return expectation.apply(position);
  }
}
