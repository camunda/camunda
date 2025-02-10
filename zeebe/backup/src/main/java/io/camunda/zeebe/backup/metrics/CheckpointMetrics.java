/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.metrics;

import static io.camunda.zeebe.backup.metrics.CheckpointMetricsDoc.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class CheckpointMetrics {
  private final MeterRegistry registry;
  private final EnumMap<CheckpointMetricsResultValue, Counter> checkpointRecords =
      new EnumMap<>(CheckpointMetricsResultValue.class);
  private final AtomicLong checkpointPosition = new AtomicLong(0L);
  private final AtomicLong checkpointId = new AtomicLong(0L);

  public CheckpointMetrics(final MeterRegistry meterRegistry) {
    registry = Objects.requireNonNull(meterRegistry, "registry cannot be null");

    Gauge.builder(CHECKPOINT_POSITION.getName(), checkpointPosition::get)
        .description(CHECKPOINT_POSITION.getDescription())
        .register(meterRegistry);

    Gauge.builder(CHECKPOINT_POSITION.getName(), checkpointPosition::get)
        .description(CHECKPOINT_POSITION.getDescription())
        .register(meterRegistry);
  }

  public void created(final long checkpointId, final long checkpointPosition) {
    setCheckpointId(checkpointId, checkpointPosition);
    checkpointRecords
        .computeIfAbsent(CheckpointMetricsResultValue.CREATED, this::registerCheckpointRecords)
        .increment();
  }

  public void setCheckpointId(final long checkpointId, final long checkpointPosition) {
    this.checkpointId.set(checkpointId);
    this.checkpointPosition.set(checkpointPosition);
  }

  public void ignored() {
    checkpointRecords
        .computeIfAbsent(CheckpointMetricsResultValue.IGNORED, this::registerCheckpointRecords)
        .increment();
  }

  private Counter registerCheckpointRecords(final CheckpointMetricsResultValue value) {
    return Counter.builder(CHECKPOINTS_RECORDS.getName())
        .description(CHECKPOINTS_RECORDS.getDescription())
        .tags(CheckpointMetricsKeyName.RESULT.asString(), value.value())
        .register(registry);
  }
}
