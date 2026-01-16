/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.jobmetricsbatch.JobMetricsBatchEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

public class JobBatchMetricsExportedHandler
    implements ExportHandler<JobMetricsBatchEntity, JobMetricsBatchRecordValue> {

  // Status metric indices matching JobMetricsExportState enum
  private static final int CREATED_INDEX = 0;
  private static final int COMPLETED_INDEX = 1;
  private static final int FAILED_INDEX = 2;

  private final String indexName;

  public JobBatchMetricsExportedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.JOB_METRICS_BATCH;
  }

  @Override
  public Class<JobMetricsBatchEntity> getEntityType() {
    return JobMetricsBatchEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<JobMetricsBatchRecordValue> record) {
    return JobMetricsBatchIntent.EXPORTED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<JobMetricsBatchRecordValue> record) {
    return record.getValue().getJobMetrics().stream()
        .flatMap(
            jobMetricsValue -> {
              final var jobTypeIndex = jobMetricsValue.getJobTypeIndex();
              final var tenantIdIndex = jobMetricsValue.getTenantIdIndex();
              final var workerNameIndex = jobMetricsValue.getWorkerNameIndex();
              return Stream.of(
                  record.getKey()
                      + "_"
                      + jobTypeIndex
                      + "_"
                      + tenantIdIndex
                      + "_"
                      + workerNameIndex);
            })
        .toList();
  }

  @Override
  public JobMetricsBatchEntity createNewEntity(final String id) {
    return new JobMetricsBatchEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<JobMetricsBatchRecordValue> record, final JobMetricsBatchEntity entity) {
    final var jobMetricsBatchRecord = record.getValue();
    final var encodedStrings = jobMetricsBatchRecord.getEncodedStrings();
    final var idParts = entity.getId().split("_");
    final var jobTypeIndex = Integer.parseInt(idParts[1]);
    final var tenantIndex = Integer.parseInt(idParts[2]);
    final var workerIndex = Integer.parseInt(idParts[3]);

    final var jobMetrics =
        jobMetricsBatchRecord.getJobMetrics().stream()
            .filter(
                m ->
                    m.getJobTypeIndex() == jobTypeIndex
                        && m.getTenantIdIndex() == tenantIndex
                        && m.getWorkerNameIndex() == workerIndex)
            .findFirst()
            .orElseThrow();

    entity
        .setStartTime(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(jobMetricsBatchRecord.getBatchStartTime()), ZoneOffset.UTC))
        .setEndTime(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(jobMetricsBatchRecord.getBatchEndTime()), ZoneOffset.UTC))
        .setIncompleteBatch(jobMetricsBatchRecord.getRecordSizeLimitExceeded())
        .setTenantId(encodedStrings.get(jobMetrics.getTenantIdIndex()))
        .setFailedCount(jobMetrics.getStatusMetrics().get(FAILED_INDEX).getCount())
        .setLastFailedAt(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(
                    jobMetrics.getStatusMetrics().get(FAILED_INDEX).getLastUpdatedAt()),
                ZoneOffset.UTC))
        .setCompletedCount(jobMetrics.getStatusMetrics().get(COMPLETED_INDEX).getCount())
        .setLastCompletedAt(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(
                    jobMetrics.getStatusMetrics().get(COMPLETED_INDEX).getLastUpdatedAt()),
                ZoneOffset.UTC))
        .setCreatedCount(jobMetrics.getStatusMetrics().get(CREATED_INDEX).getCount())
        .setLastCreatedAt(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(
                    jobMetrics.getStatusMetrics().get(CREATED_INDEX).getLastUpdatedAt()),
                ZoneOffset.UTC))
        .setJobType(encodedStrings.get(jobMetrics.getJobTypeIndex()))
        .setWorker(encodedStrings.get(jobMetrics.getWorkerNameIndex()));
  }

  @Override
  public void flush(final JobMetricsBatchEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
