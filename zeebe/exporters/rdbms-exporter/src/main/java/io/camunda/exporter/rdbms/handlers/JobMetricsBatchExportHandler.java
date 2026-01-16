/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel;
import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel.Builder;
import io.camunda.db.rdbms.write.service.JobMetricsBatchWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsExportState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class JobMetricsBatchExportHandler
    implements RdbmsExportHandler<JobMetricsBatchRecordValue> {

  private final JobMetricsBatchWriter jobMetricsBatchWriter;

  public JobMetricsBatchExportHandler(final JobMetricsBatchWriter jobMetricsBatchWriter) {
    this.jobMetricsBatchWriter = jobMetricsBatchWriter;
  }

  @Override
  public boolean canExport(final Record<JobMetricsBatchRecordValue> record) {
    return JobMetricsBatchIntent.EXPORTED.equals(record.getIntent());
  }

  @Override
  public void export(final Record<JobMetricsBatchRecordValue> record) {
    final var dbModels = map(record);
    dbModels.forEach(jobMetricsBatchWriter::create);
  }

  private java.util.List<JobMetricsBatchDbModel> map(
      final Record<JobMetricsBatchRecordValue> record) {
    final var jobMetricsBatchRecord = record.getValue();
    final var encodedString = jobMetricsBatchRecord.getEncodedStrings();
    return record.getValue().getJobMetrics().stream()
        .map(
            jobMetrics ->
                new Builder()
                    .key(
                        record.getKey()
                            + "-"
                            + jobMetrics.getJobTypeIndex()
                            + "-"
                            + jobMetrics.getTenantIdIndex()
                            + "-"
                            + jobMetrics.getWorkerNameIndex())
                    .startTime(
                        OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(jobMetricsBatchRecord.getBatchStartTime()),
                            ZoneOffset.UTC))
                    .endTime(
                        OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(jobMetricsBatchRecord.getBatchEndTime()),
                            ZoneOffset.UTC))
                    .incompleteBatch(jobMetricsBatchRecord.getRecordSizeLimitExceeded())
                    .tenantId(encodedString.get(jobMetrics.getTenantIdIndex()))
                    .failedCount(
                        jobMetrics
                            .getStatusMetrics()
                            .get(JobMetricsExportState.FAILED.getIndex())
                            .getCount())
                    .lastFailedAt(
                        OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(
                                jobMetrics
                                    .getStatusMetrics()
                                    .get(JobMetricsExportState.FAILED.getIndex())
                                    .getLastUpdatedAt()),
                            ZoneOffset.UTC))
                    .completedCount(
                        jobMetrics
                            .getStatusMetrics()
                            .get(JobMetricsExportState.COMPLETED.getIndex())
                            .getCount())
                    .lastCompletedAt(
                        OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(
                                jobMetrics
                                    .getStatusMetrics()
                                    .get(JobMetricsExportState.COMPLETED.getIndex())
                                    .getLastUpdatedAt()),
                            ZoneOffset.UTC))
                    .createdCount(
                        jobMetrics
                            .getStatusMetrics()
                            .get(JobMetricsExportState.CREATED.getIndex())
                            .getCount())
                    .lastCreatedAt(
                        OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(
                                jobMetrics
                                    .getStatusMetrics()
                                    .get(JobMetricsExportState.CREATED.getIndex())
                                    .getLastUpdatedAt()),
                            ZoneOffset.UTC))
                    .jobType(encodedString.get(jobMetrics.getJobTypeIndex()))
                    .worker(encodedString.get(jobMetrics.getWorkerNameIndex()))
                    .build())
        .toList();
  }
}
