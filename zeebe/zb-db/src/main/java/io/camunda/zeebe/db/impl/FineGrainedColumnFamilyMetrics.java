/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static io.camunda.zeebe.db.ColumnFamilyMetricsDoc.*;

import io.camunda.zeebe.db.ColumnFamilyMetrics;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

public final class FineGrainedColumnFamilyMetrics implements ColumnFamilyMetrics {

  private final Timer get;
  private final Timer put;
  private final Timer delete;
  private final Timer iterate;
  private final MeterRegistry registry;

  public <ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
      FineGrainedColumnFamilyMetrics(
          final ColumnFamilyNames columnFamily, final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    final var columnFamilyLabel = columnFamily.name();
    get = createTimer(columnFamilyLabel, OperationType.GET);
    put = createTimer(columnFamilyLabel, OperationType.PUT);
    delete = createTimer(columnFamilyLabel, OperationType.DELETE);
    iterate = createTimer(columnFamilyLabel, OperationType.ITERATE);
  }

  @Override
  public CloseableSilently measureGetLatency() {
    return MicrometerUtil.timer(get, Timer.start(registry));
  }

  @Override
  public CloseableSilently measurePutLatency() {
    return MicrometerUtil.timer(put, Timer.start(registry));
  }

  @Override
  public CloseableSilently measureDeleteLatency() {
    return MicrometerUtil.timer(delete, Timer.start(registry));
  }

  @Override
  public CloseableSilently measureIterateLatency() {
    return MicrometerUtil.timer(iterate, Timer.start(registry));
  }

  private Timer createTimer(final String columnFamily, final OperationType type) {
    return Timer.builder(LATENCY.getName())
        .description(LATENCY.getDescription())
        .serviceLevelObjectives(LATENCY.getTimerSLOs())
        .tags(
            ColumnFamilyMetricsKeyName.COLUMN_FAMILY.asString(),
            columnFamily,
            ColumnFamilyMetricsKeyName.OPERATION.asString(),
            type.getName())
        .register(registry);
  }
}
