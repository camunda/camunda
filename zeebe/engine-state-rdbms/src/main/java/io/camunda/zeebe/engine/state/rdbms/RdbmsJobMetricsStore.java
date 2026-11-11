/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsStateImpl;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsStore;
import io.camunda.zeebe.engine.state.rdbms.JobMetricMapper.JobMetricBatchRow;
import io.camunda.zeebe.engine.state.rdbms.JobMetricMapper.JobMetricKey;
import io.camunda.zeebe.engine.state.rdbms.JobMetricMapper.JobMetricRow;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.Comparator;
import java.util.List;

/**
 * Stores the job metrics of the current batch in a relational database, through the partition's
 * {@link EntitySession}: the writes are staged until the commit, the reads see them.
 *
 * <p>The metrics of a job type, tenant and worker are a row, so an increment reads and writes one
 * row, which the session collapses when the combination is incremented more than once in a
 * transaction. The named numbers of the batch and the number of rows are the single batch row of
 * the partition; the number is kept with the rows, in the same transaction, so counting needs no
 * query.
 */
public final class RdbmsJobMetricsStore implements JobMetricsStore {

  /** The partition has one batch row; the partition is implicit in the statements. */
  static final int BATCH = 0;

  static final EntityType<JobMetricKey, JobMetricRow> JOB_METRIC =
      new EntityType<>(JobMetricMapper.class, "JobMetric", "job metric", JobMetricRow::key);

  static final EntityType<Integer, JobMetricBatchRow> JOB_METRIC_BATCH =
      new EntityType<>(JobMetricMapper.class, "JobMetricBatch", "job metric batch", row -> BATCH);

  private static final Comparator<JobMetricRow> BY_KEY =
      Comparator.comparing(JobMetricRow::jobType)
          .thenComparing(JobMetricRow::tenantId)
          .thenComparing(JobMetricRow::workerName);

  private final EntitySession session;
  private final int partitionId;

  RdbmsJobMetricsStore(final EntitySession session) {
    this.session = session;
    partitionId = session.partitionId();
  }

  @Override
  public int countKeys() {
    return batch().keyCount();
  }

  @Override
  public boolean containsKey(final String jobType, final String tenantId, final String workerName) {
    return session.get(JOB_METRIC, new JobMetricKey(jobType, tenantId, workerName)) != null;
  }

  @Override
  public void increment(
      final String jobType,
      final String tenantId,
      final String workerName,
      final JobMetricsExportState status,
      final long timestamp) {
    final var key = new JobMetricKey(jobType, tenantId, workerName);
    final var row = session.get(JOB_METRIC, key);
    if (row == null) {
      session.insert(JOB_METRIC, JobMetricRow.empty(key).incremented(status, timestamp));
      saveBatch(batch().withKeyCount(batch().keyCount() + 1));
    } else {
      session.update(JOB_METRIC, row.incremented(status, timestamp));
    }
  }

  @Override
  public void forEach(final MetricsVisitor visitor) {
    for (final var row : rows()) {
      visitor.accept(row.jobType(), row.tenantId(), row.workerName(), row.statusMetrics());
    }
  }

  @Override
  public long getMetadata(final String key) {
    final var batch = batch();
    return switch (key) {
      case JobMetricsStateImpl.META_BATCH_STARTING_TIME -> batch.startTime();
      case JobMetricsStateImpl.META_BATCH_ENDING_TIME -> batch.endTime();
      case JobMetricsStateImpl.META_SIZE_LIMITS_EXCEEDED -> batch.sizeLimitExceeded() ? 1L : 0L;
      default -> 0L;
    };
  }

  @Override
  public void setMetadata(final String key, final long value) {
    final var batch = batch();
    saveBatch(
        switch (key) {
          case JobMetricsStateImpl.META_BATCH_STARTING_TIME -> batch.withStartTime(value);
          case JobMetricsStateImpl.META_BATCH_ENDING_TIME -> batch.withEndTime(value);
          case JobMetricsStateImpl.META_SIZE_LIMITS_EXCEEDED ->
              batch.withSizeLimitExceeded(value == 1L);
          default -> throw new IllegalArgumentException("Unknown job metrics metadata key: " + key);
        });
  }

  @Override
  public void deleteMetrics() {
    for (final var row : rows()) {
      session.delete(JOB_METRIC, row.key());
    }
    saveBatch(batch().withKeyCount(0));
  }

  /** The metrics of the batch, committed and staged. */
  private List<JobMetricRow> rows() {
    return session.query(
        JOB_METRIC,
        session.read(JobMetricMapper.class, mapper -> mapper.selectJobMetrics(partitionId)),
        row -> true,
        BY_KEY);
  }

  private JobMetricBatchRow batch() {
    final var row = session.get(JOB_METRIC_BATCH, BATCH);
    return row == null ? JobMetricBatchRow.EMPTY : row;
  }

  private void saveBatch(final JobMetricBatchRow row) {
    if (session.get(JOB_METRIC_BATCH, BATCH) == null) {
      session.insert(JOB_METRIC_BATCH, row);
    } else {
      session.update(JOB_METRIC_BATCH, row);
    }
  }
}
