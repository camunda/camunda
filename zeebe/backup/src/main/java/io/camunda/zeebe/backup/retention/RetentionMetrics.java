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

public class RetentionMetrics implements CloseableSilently {
  static final String NAMESPACE = "camunda.backup.retention";
  static final String RETENTION_LAST_EXECUTION = NAMESPACE + ".last.execution.millis";
  static final String RETENTION_NEXT_EXECUTION = NAMESPACE + ".next.execution.millis";
  static final String BACKUPS_DELETED_ROUND = NAMESPACE + ".backups.deleted.round";
  static final String RANGES_DELETED_ROUND = NAMESPACE + ".ranges.deleted.round";
  static final String EARLIEST_BACKUP_ID = NAMESPACE + ".earliest.backup.id";

  private final MeterRegistry meterRegistry;
  private Gauge backupsDeletedGauge;
  private Gauge rangesDeletedGauge;
  private Gauge lastExecutionGauge;
  private Gauge nextExecutionGauge;
  private Gauge earliestBackupGauge;
  private long lastExecutionTimestamp = 0L;
  private long nextExecutionTimestamp = 0L;
  private long earliestBackupId = 0L;
  private long backupsDeleted = 0L;
  private long rangesDeleted = 0L;

  public RetentionMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void register() {
    lastExecutionGauge =
        Gauge.builder(RETENTION_LAST_EXECUTION, () -> lastExecutionTimestamp)
            .description("Timestamp in milliseconds of the last retention execution")
            .register(meterRegistry);

    nextExecutionGauge =
        Gauge.builder(RETENTION_NEXT_EXECUTION, () -> nextExecutionTimestamp)
            .description("Timestamp in milliseconds of the next retention execution")
            .register(meterRegistry);

    earliestBackupGauge =
        Gauge.builder(EARLIEST_BACKUP_ID, () -> earliestBackupId)
            .description("The ID of the earliest backup retained")
            .register(meterRegistry);

    backupsDeletedGauge =
        Gauge.builder(BACKUPS_DELETED_ROUND, () -> backupsDeleted)
            .description("Number of backups deleted in the last retention round")
            .register(meterRegistry);

    rangesDeletedGauge =
        Gauge.builder(RANGES_DELETED_ROUND, () -> rangesDeleted)
            .description("Number of ranges deleted in the last retention round")
            .register(meterRegistry);
  }

  public void recordLastExecution(final Instant timestamp) {
    lastExecutionTimestamp = timestamp.toEpochMilli();
  }

  public void recordNextExecution(final Instant timestamp) {
    nextExecutionTimestamp = timestamp.toEpochMilli();
  }

  public void recordEarliestBackupId(final long backupId) {
    earliestBackupId = backupId;
  }

  public void recordBackupsDeleted(final long deletedBackupsCount) {
    backupsDeleted = deletedBackupsCount;
  }

  public void recordRangesDeleted(final long rangesDeletedCount) {
    rangesDeleted = rangesDeletedCount;
  }

  @Override
  public void close() {
    meterRegistry.remove(lastExecutionGauge);
    meterRegistry.remove(nextExecutionGauge);
    meterRegistry.remove(earliestBackupGauge);
    meterRegistry.remove(backupsDeletedGauge);
    meterRegistry.remove(rangesDeletedGauge);
  }
}
