/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.retention;

import io.camunda.zeebe.util.CloseableSilently;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RetentionMetrics implements CloseableSilently {
  static final String NAMESPACE = "camunda.backup.retention";
  static final String RETENTION_LAST_EXECUTION = NAMESPACE + ".last.execution.millis";
  static final String RETENTION_NEXT_EXECUTION = NAMESPACE + ".next.execution.millis";
  static final String BACKUPS_DELETED_ROUND = NAMESPACE + ".backups.deleted.round";
  static final String RANGES_DELETED_ROUND = NAMESPACE + ".ranges.deleted.round";
  static final String EARLIEST_BACKUP_ID = NAMESPACE + ".earliest.backup.id";
  static final String PARTITION_TAG = "partition";

  private final MeterRegistry meterRegistry;
  private final Map<Integer, PartitionMetrics> partitionMetrics = new ConcurrentHashMap<>();
  private Gauge lastExecutionGauge;
  private Gauge nextExecutionGauge;
  private long lastExecution = 0L;
  private long nextExecution = 0L;

  public RetentionMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public PartitionMetrics forPartition(final int partitionId) {
    return partitionMetrics.computeIfAbsent(
        partitionId,
        id -> {
          final PartitionMetrics metrics = new PartitionMetrics();
          registerPartitionGauges(id, metrics);
          return metrics;
        });
  }

  public void register() {
    lastExecutionGauge =
        Gauge.builder(RETENTION_LAST_EXECUTION, () -> lastExecution)
            .description("Timestamp in milliseconds of the last retention execution")
            .register(meterRegistry);

    nextExecutionGauge =
        Gauge.builder(RETENTION_NEXT_EXECUTION, () -> nextExecution)
            .description("Timestamp in milliseconds of the next retention execution")
            .register(meterRegistry);
  }

  private void registerPartitionGauges(final int partitionId, final PartitionMetrics metrics) {
    final String partition = String.valueOf(partitionId);

    Gauge.builder(EARLIEST_BACKUP_ID, () -> metrics.earliestBackupId)
        .description("The ID of the earliest backup retained")
        .tag(PARTITION_TAG, partition)
        .register(meterRegistry);

    Gauge.builder(BACKUPS_DELETED_ROUND, () -> metrics.backupsDeleted)
        .description("Number of backups deleted in the last retention round")
        .tag(PARTITION_TAG, partition)
        .register(meterRegistry);

    Gauge.builder(RANGES_DELETED_ROUND, () -> metrics.rangesDeleted)
        .description("Number of ranges deleted in the last retention round")
        .tag(PARTITION_TAG, partition)
        .register(meterRegistry);
  }

  @Override
  public void close() {
    meterRegistry.remove(lastExecutionGauge);
    meterRegistry.remove(nextExecutionGauge);
    partitionMetrics.keySet().forEach(this::removePartition);
  }

  public void removePartition(final int partitionId) {
    partitionMetrics.remove(partitionId);
    final String partition = String.valueOf(partitionId);
    safeRemoveGauge(EARLIEST_BACKUP_ID, partition);
    safeRemoveGauge(BACKUPS_DELETED_ROUND, partition);
    safeRemoveGauge(RANGES_DELETED_ROUND, partition);
  }

  private void safeRemoveGauge(final String metricName, final String partition) {
    final var gauge = meterRegistry.find(metricName).tag(PARTITION_TAG, partition).gauge();
    if (gauge != null) {
      meterRegistry.remove(gauge);
    }
  }

  public void recordLastExecution(final Instant lastExecution) {
    this.lastExecution = lastExecution.toEpochMilli();
  }

  public void recordNextExecution(final Instant nextExecution) {
    this.nextExecution = nextExecution.toEpochMilli();
  }

  public static class PartitionMetrics {
    private long earliestBackupId = 0L;
    private long backupsDeleted = 0L;
    private long rangesDeleted = 0L;

    public void setEarliestBackupId(final long id) {
      earliestBackupId = id;
    }

    public void setBackupsDeleted(final long count) {
      backupsDeleted = count;
    }

    public void setRangesDeleted(final long count) {
      rangesDeleted = count;
    }
  }
}
