/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.metrics;

import static io.camunda.zeebe.backup.metrics.CheckpointMetricsDoc.*;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class CheckpointMetrics {
  private final MeterRegistry registry;
  private final Map<CheckpointMetricsResultValue, Counter> checkpointRecords =
      new EnumMap<>(CheckpointMetricsResultValue.class);
  private final StatefulGauge checkpointPosition;
  private final StatefulGauge checkpointId;

  public CheckpointMetrics(final MeterRegistry meterRegistry) {
    registry = Objects.requireNonNull(meterRegistry, "registry cannot be null");

    checkpointPosition =
        StatefulGauge.builder(CHECKPOINT_POSITION.getName())
            .description(CHECKPOINT_POSITION.getDescription())
            .register(meterRegistry);

    checkpointId =
        StatefulGauge.builder(CHECKPOINT_ID.getName())
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
        .tag(CheckpointMetricsKeyName.RESULT.asString(), value.value())
        .register(registry);
  }
}
