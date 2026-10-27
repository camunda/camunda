/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.CloseableSilently;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class SchedulerMetrics implements CloseableSilently {
  static final String NAMESPACE = "camunda.checkpoint.scheduler";
  static final String LAST_CHECKPOINT_GAUGE_NAME = NAMESPACE + ".last.checkpoint.time.millis";
  static final String LAST_CHECKPOINT_ID_GAUGE_NAME = NAMESPACE + ".last.checkpoint.id";
  static final String NEXT_CHECKPOINT_GAUGE_NAME = NAMESPACE + ".next.checkpoint.time.millis";
  static final String TYPE_TAG = "type";

  private final MeterRegistry meterRegistry;
  private final Set<Id> registeredMeterIds = new HashSet<>();

  private long nextCheckpointTimestamp = 0L;
  private long nextBackupTimestamp = 0L;
  private long lastBackupTimestamp = 0L;
  private long lastCheckpointTimestamp = 0L;
  private long lastCheckpointId = 0L;
  private long lastBackupId = 0L;

  public SchedulerMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void register() {
    registerGauge(
        LAST_CHECKPOINT_GAUGE_NAME,
        "Timestamp in milliseconds of the last checkpoint created",
        CheckpointType.MARKER,
        () -> lastCheckpointTimestamp);

    registerGauge(
        LAST_CHECKPOINT_GAUGE_NAME,
        "Timestamp in milliseconds of the last backup created",
        CheckpointType.SCHEDULED_BACKUP,
        () -> lastBackupTimestamp);

    registerGauge(
        NEXT_CHECKPOINT_GAUGE_NAME,
        "Next scheduler's expected checkpoint execution delay",
        CheckpointType.MARKER,
        () -> nextCheckpointTimestamp);

    registerGauge(
        NEXT_CHECKPOINT_GAUGE_NAME,
        "Next scheduler's expected backup execution delay",
        CheckpointType.SCHEDULED_BACKUP,
        () -> nextBackupTimestamp);

    registerGauge(
        LAST_CHECKPOINT_ID_GAUGE_NAME,
        "ID of the last checkpoint created",
        CheckpointType.MARKER,
        () -> lastCheckpointId);

    registerGauge(
        LAST_CHECKPOINT_ID_GAUGE_NAME,
        "ID of the last backup created",
        CheckpointType.SCHEDULED_BACKUP,
        () -> lastBackupId);
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

  @Override
  public void close() {
    registeredMeterIds.forEach(meterRegistry::remove);
  }

  private void registerGauge(
      final String name,
      final String description,
      final CheckpointType type,
      final Supplier<Long> valueSupplier) {

    final var meterId =
        Gauge.builder(name, valueSupplier)
            .description(description)
            .tag(TYPE_TAG, type.name())
            .strongReference(true)
            .register(meterRegistry)
            .getId();

    registeredMeterIds.add(meterId);
  }
}
