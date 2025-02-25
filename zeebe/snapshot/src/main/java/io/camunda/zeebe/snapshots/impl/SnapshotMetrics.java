/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static io.camunda.zeebe.snapshots.impl.SnapshotMetricsDoc.SNAPSHOT_CHUNK_COUNT;
import static io.camunda.zeebe.snapshots.impl.SnapshotMetricsDoc.SNAPSHOT_COUNT;
import static io.camunda.zeebe.snapshots.impl.SnapshotMetricsDoc.SNAPSHOT_DURATION;
import static io.camunda.zeebe.snapshots.impl.SnapshotMetricsDoc.SNAPSHOT_FILE_SIZE;
import static io.camunda.zeebe.snapshots.impl.SnapshotMetricsDoc.SNAPSHOT_PERSIST_DURATION;
import static io.camunda.zeebe.snapshots.impl.SnapshotMetricsDoc.SNAPSHOT_SIZE;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;

public final class SnapshotMetrics {

  private final AtomicLong snapshotChunkCount = new AtomicLong();
  private final AtomicLong snapshotSize = new AtomicLong();

  private final Clock clock;
  private final Timer snapshotPersistDuration;
  private final DistributionSummary snapshotFileSize;
  private final Timer snapshotDuration;
  private final Counter snapshotCount;

  public SnapshotMetrics(final MeterRegistry registry) {
    clock = registry.config().clock();

    snapshotDuration = MicrometerUtil.buildTimer(SNAPSHOT_DURATION).register(registry);
    snapshotPersistDuration =
        MicrometerUtil.buildTimer(SNAPSHOT_PERSIST_DURATION).register(registry);
    snapshotFileSize = MicrometerUtil.buildSummary(SNAPSHOT_FILE_SIZE).register(registry);
    snapshotCount =
        Counter.builder(SNAPSHOT_COUNT.getName())
            .description(SNAPSHOT_COUNT.getDescription())
            .register(registry);

    Gauge.builder(SNAPSHOT_CHUNK_COUNT.getName(), snapshotChunkCount, Number::longValue)
        .description(SNAPSHOT_CHUNK_COUNT.getDescription())
        .register(registry);
    Gauge.builder(SNAPSHOT_SIZE.getName(), snapshotSize, Number::longValue)
        .description(SNAPSHOT_SIZE.getDescription())
        .register(registry);
  }

  void incrementSnapshotCount() {
    snapshotCount.increment();
  }

  void observeSnapshotSize(final long sizeInBytes) {
    snapshotSize.set(sizeInBytes);
  }

  void observeSnapshotChunkCount(final long count) {
    snapshotChunkCount.set(count);
  }

  void observeSnapshotFileSize(final long sizeInBytes) {
    snapshotFileSize.record(sizeInBytes / 1_000_000f);
  }

  CloseableSilently startTimer() {
    return MicrometerUtil.timer(snapshotDuration, Timer.start(clock));
  }

  CloseableSilently startPersistTimer() {
    return MicrometerUtil.timer(snapshotPersistDuration, Timer.start(clock));
  }
}
