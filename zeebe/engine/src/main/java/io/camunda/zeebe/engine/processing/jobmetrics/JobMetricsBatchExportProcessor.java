/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.jobmetrics;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobMetricsState;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetrics;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.StatusMetrics;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobMetricsBatchExportProcessor implements TypedRecordProcessor<JobMetricsBatchRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(JobMetricsBatchExportProcessor.class);
  // 4MB minus the margin error of the ArrayProperty overhead
  // which depends on its
  // size, see MsgPackWriter.getEncodedArrayHeaderLenght and the overhead of the metadata added
  // later in the RecordBatch (This is an approximation but should be sufficient)
  private final JobMetricsState jobMetricsState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public JobMetricsBatchExportProcessor(
      final JobMetricsState jobMetricsState,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    this.jobMetricsState = jobMetricsState;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<JobMetricsBatchRecord> unused) {

    final JobMetricsBatchRecord exportedRecord = createExportedRecord();

    LOG.debug(
        "Exporting job metrics batch with {} entries, time range: {} - {}",
        exportedRecord.getJobMetrics().size(),
        exportedRecord.getBatchStartTime(),
        exportedRecord.getBatchEndTime());

    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), JobMetricsBatchIntent.EXPORTED, exportedRecord);
  }

  private JobMetricsBatchRecord createExportedRecord() {
    final JobMetricsBatchRecord record = new JobMetricsBatchRecord();

    final Set<String> encodedString = jobMetricsState.getEncodedStrings();
    record.setBatchStartTime(jobMetricsState.getBatchStartTime());
    record.setBatchEndTime(jobMetricsState.getBatchEndTime());
    record.setRecordSizeLimitExceeded(jobMetricsState.isIncompleteBatch());
    record.setEncodedStrings(encodedString);
    jobMetricsState.forEach(
        (jobTypeIndex, tenantIdIndex, workerNameIndex, metrics) -> {
          final JobMetrics jobMetricsEntry = new JobMetrics();
          jobMetricsEntry.setJobTypeIndex(jobTypeIndex);
          jobMetricsEntry.setTenantIdIndex(tenantIdIndex);
          jobMetricsEntry.setWorkerNameIndex(workerNameIndex);
          jobMetricsEntry.setStatusMetrics(
              Arrays.stream(metrics)
                  .map(
                      statusMetrics ->
                          new StatusMetrics()
                              .setCount(statusMetrics.getCount())
                              .setLastUpdatedAt(statusMetrics.getLastUpdatedAt()))
                  .collect(Collectors.toList()));
          record.addJobMetrics(jobMetricsEntry);
        });

    return record;
  }
}
