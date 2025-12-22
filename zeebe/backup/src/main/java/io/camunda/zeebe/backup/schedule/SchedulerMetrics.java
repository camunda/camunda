/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;

public class SchedulerMetrics {
  private static final String NAMESPACE = "camunda.checkpoint.scheduler";
  private static final String LAST_CHECKPOINT_GAUGE_NAME =
      NAMESPACE + ".last.checkpoint.time.millis";
  private static final String LAST_CHECKPOINT_ID_GAUGE_NAME = NAMESPACE + ".last.checkpoint.id";
  private static final String NEXT_CHECKPOINT_GAUGE_NAME =
      NAMESPACE + ".next.checkpoint.time.millis";

  private long nextCheckpointTimestamp = 0L;
  private long nextBackupTimestamp = 0L;
  private long lastBackupTimestamp = 0L;
  private long lastCheckpointTimestamp = 0L;
  private long lastCheckpointId = 0L;
  private long lastBackupId = 0L;

  public SchedulerMetrics(final MeterRegistry meterRegistry) {

    Gauge.builder(LAST_CHECKPOINT_GAUGE_NAME, () -> lastCheckpointTimestamp)
        .description("Timestamp in milliseconds of the last checkpoint created")
        .tag("type", CheckpointType.MARKER.name())
        .strongReference(true)
        .register(meterRegistry);

    Gauge.builder(LAST_CHECKPOINT_GAUGE_NAME, () -> lastBackupTimestamp)
        .description("Timestamp in milliseconds of the last checkpoint created")
        .tag("type", CheckpointType.SCHEDULED_BACKUP.name())
        .strongReference(true)
        .register(meterRegistry);

    Gauge.builder(NEXT_CHECKPOINT_GAUGE_NAME, () -> nextCheckpointTimestamp)
        .description("Next scheduler's expected checkpoint execution delay in seconds")
        .tag("type", CheckpointType.MARKER.name())
        .strongReference(true)
        .register(meterRegistry);

    Gauge.builder(NEXT_CHECKPOINT_GAUGE_NAME, () -> nextBackupTimestamp)
        .description("Next scheduler's expected backup execution delay in seconds")
        .tag("type", CheckpointType.SCHEDULED_BACKUP.name())
        .strongReference(true)
        .register(meterRegistry);

    Gauge.builder(LAST_CHECKPOINT_ID_GAUGE_NAME, () -> lastCheckpointId)
        .description("ID of the last checkpoint created")
        .tag("type", CheckpointType.MARKER.name())
        .strongReference(true)
        .register(meterRegistry);

    Gauge.builder(LAST_CHECKPOINT_ID_GAUGE_NAME, () -> lastBackupId)
        .description("ID of the last backup created")
        .tag("type", CheckpointType.SCHEDULED_BACKUP.name())
        .strongReference(true)
        .register(meterRegistry);
  }

  public void recordLastCheckpointTime(final Instant timestamp, final CheckpointType type) {
    if (type == CheckpointType.SCHEDULED_BACKUP) {
      lastBackupTimestamp = timestamp.toEpochMilli();
    }
    // since backups lead to checkpoints as well, we always update the last checkpoint time
    lastCheckpointTimestamp = timestamp.toEpochMilli();
  }

  public void recordNextExecution(final Instant timestamp, final CheckpointType type) {
    if (type == CheckpointType.SCHEDULED_BACKUP) {
      nextBackupTimestamp = timestamp.toEpochMilli();
    } else {
      nextCheckpointTimestamp = timestamp.toEpochMilli();
    }
  }

  public void recordLastCheckpointId(final long checkpointId, final CheckpointType type) {
    if (type == CheckpointType.SCHEDULED_BACKUP) {
      lastBackupId = checkpointId;
    }
    // since backups lead to checkpoints as well, we always update the last checkpoint id
    lastCheckpointId = checkpointId;
  }
}
