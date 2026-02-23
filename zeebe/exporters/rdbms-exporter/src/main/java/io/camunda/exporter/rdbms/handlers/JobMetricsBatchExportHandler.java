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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.JobMetricsValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

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

  private List<JobMetricsBatchDbModel> map(final Record<JobMetricsBatchRecordValue> record) {
    final var jobMetricsBatchRecord = record.getValue();
    final var encodedString = jobMetricsBatchRecord.getEncodedStrings();
    return record.getValue().getJobMetrics().stream()
        .map(
            jobMetrics ->
                new Builder()
                    .key(
                        String.join(
                            "_",
                            Long.toString(record.getKey()),
                            Integer.toString(jobMetrics.getJobTypeIndex()),
                            Integer.toString(jobMetrics.getTenantIdIndex()),
                            Integer.toString(jobMetrics.getWorkerNameIndex())))
                    .partitionId(record.getPartitionId())
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
                        getLastUpdatedAtForStatus(jobMetrics, JobMetricsExportState.FAILED))
                    .completedCount(
                        jobMetrics
                            .getStatusMetrics()
                            .get(JobMetricsExportState.COMPLETED.getIndex())
                            .getCount())
                    .lastCompletedAt(
                        getLastUpdatedAtForStatus(jobMetrics, JobMetricsExportState.COMPLETED))
                    .createdCount(
                        jobMetrics
                            .getStatusMetrics()
                            .get(JobMetricsExportState.CREATED.getIndex())
                            .getCount())
                    .lastCreatedAt(
                        getLastUpdatedAtForStatus(jobMetrics, JobMetricsExportState.CREATED))
                    .jobType(encodedString.get(jobMetrics.getJobTypeIndex()))
                    .worker(encodedString.get(jobMetrics.getWorkerNameIndex()))
                    .build())
        .toList();
  }

  private OffsetDateTime getLastUpdatedAtForStatus(
      final JobMetricsValue jobMetrics, final JobMetricsExportState jobState) {
    final long lastUpdatedAt =
        jobMetrics.getStatusMetrics().get(jobState.getIndex()).getLastUpdatedAt();
    if (lastUpdatedAt == -1L) {
      return null;
    }
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastUpdatedAt), ZoneOffset.UTC);
  }
}
