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
import io.camunda.webapps.schema.entities.metrics.JobMetricsBatchEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JobBatchMetricsExportedHandler
    implements ExportHandler<JobMetricsBatchEntity, JobMetricsBatchRecordValue> {

  private static final int CANCELED_INDEX = 0;
  private static final int COMPLETED_INDEX = 1;
  private static final int CREATED_INDEX = 2;
  private static final int ERROR_THROWN_INDEX = 3;
  private static final int FAILED_INDEX = 4;
  private static final int MIGRATED_INDEX = 5;
  private static final int RETRIES_UPDATED_INDEX = 6;
  private static final int TIMED_OUT_INDEX = 7;
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
    final var map = record.getValue().getMetricsByTypeAndTenant();
    return map.keySet().stream()
        .flatMap(
            s ->
                map.get(s).getJobWorkerCounters().keySet().stream()
                    .map(s1 -> record.getKey() + "_" + s + "_" + s1))
        .toList();
  }

  @Override
  public JobMetricsBatchEntity createNewEntity(final String id) {
    return new JobMetricsBatchEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<JobMetricsBatchRecordValue> record, final JobMetricsBatchEntity entity) {
    final var metricsByTypeAndTenant = record.getValue().getMetricsByTypeAndTenant();
    final var idParts = entity.getId().split("_");
    final var jobTypeIndex = Integer.parseInt(idParts[1]);
    final var tenantIndex = Integer.parseInt(idParts[2]);
    final var workerIndex = Optional.ofNullable(idParts[3]).orElse("");
    entity
        .setJobType(record.getValue().getEncodedStrings().get(jobTypeIndex))
        .setTenantId(record.getValue().getEncodedStrings().get(tenantIndex));
    if (Objects.equals(workerIndex, "")) {
      entity.setWorker("");
      final List<StatusMetricValue> statusMetricValue =
          metricsByTypeAndTenant.get(jobTypeIndex + "_" + tenantIndex).getJobTypeCounters().stream()
              .filter(statusMetricValue1 -> statusMetricValue1.getCount() > 0)
              .toList();
      for (int i = 0; i < statusMetricValue.size(); i++) {
        final var metric = statusMetricValue.get(i);
        switch (i) {
          case CANCELED_INDEX,
              MIGRATED_INDEX,
              TIMED_OUT_INDEX,
              RETRIES_UPDATED_INDEX,
              FAILED_INDEX,
              ERROR_THROWN_INDEX ->
              entity.setFailedCount(metric.getCount());
          case COMPLETED_INDEX -> entity.setCompletedCount(metric.getCount());
          case CREATED_INDEX -> entity.setCreatedCount(metric.getCount());
          default -> {}
        }
      }
    } else {
      final var workerMetrics =
          metricsByTypeAndTenant
              .get(jobTypeIndex + "_" + tenantIndex)
              .getJobWorkerCounters()
              .get(workerIndex);
      entity.setWorker(record.getValue().getEncodedStrings().get(Integer.parseInt(workerIndex)));
      for (int i = 0; i < workerMetrics.size(); i++) {
        final var metric = workerMetrics.get(i);
        switch (i) {
          case CANCELED_INDEX,
              MIGRATED_INDEX,
              TIMED_OUT_INDEX,
              RETRIES_UPDATED_INDEX,
              FAILED_INDEX,
              ERROR_THROWN_INDEX ->
              entity.setFailedCount(metric.getCount());
          case COMPLETED_INDEX -> entity.setCompletedCount(metric.getCount());
          case CREATED_INDEX -> entity.setCreatedCount(metric.getCount());
          default -> {}
        }
      }
    }
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
