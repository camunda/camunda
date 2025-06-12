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
import io.camunda.zeebe.util.collection.MArray;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class SnapshotMetrics {

  private final MArray<AtomicLong> snapshotChunkCountArray = MArray.of(AtomicLong[]::new, 2);
  private final MArray<AtomicLong> snapshotSizeArray = MArray.of(AtomicLong[]::new, 2);

  private final Clock clock;
  private final MArray<Timer> snapshotPersistDuration;
  private final MArray<DistributionSummary> snapshotFileSize;
  private final MArray<Timer> snapshotDuration;
  private final MArray<Counter> snapshotCount;

  public SnapshotMetrics(final MeterRegistry registry) {
    clock = registry.config().clock();

    snapshotDuration = MArray.of(Timer[]::new, 2);
    snapshotPersistDuration = MArray.of(Timer[]::new, 2);
    snapshotFileSize = MArray.of(DistributionSummary[]::new, 2);
    snapshotCount = MArray.of(Counter[]::new, 2);

    for (final var bool : List.of(true, false)) {
      // init the AtomicLongs
      snapshotChunkCountArray.put(new AtomicLong(), encodeBoolean(bool));
      snapshotSizeArray.put(new AtomicLong(), encodeBoolean(bool));

      // INIT non gauges
      snapshotDuration.put(
          MicrometerUtil.buildTimer(SNAPSHOT_DURATION)
              .tags(SnapshotMetricsDoc.BootstrapKeyNames.tags(bool))
              .register(registry),
          encodeBoolean(bool));
      snapshotPersistDuration.put(
          MicrometerUtil.buildTimer(SNAPSHOT_PERSIST_DURATION)
              .tags(SnapshotMetricsDoc.BootstrapKeyNames.tags(bool))
              .register(registry),
          encodeBoolean(bool));
      snapshotFileSize.put(
          MicrometerUtil.buildSummary(SNAPSHOT_FILE_SIZE)
              .tags(SnapshotMetricsDoc.BootstrapKeyNames.tags(bool))
              .register(registry),
          encodeBoolean(bool));

      snapshotCount.put(
          Counter.builder(SNAPSHOT_COUNT.getName())
              .description(SNAPSHOT_COUNT.getDescription())
              .tags(SnapshotMetricsDoc.BootstrapKeyNames.tags(bool))
              .register(registry),
          encodeBoolean(bool));

      // init gauges
      Gauge.builder(
              SNAPSHOT_CHUNK_COUNT.getName(),
              snapshotChunkCountArray.get(encodeBoolean(bool)),
              Number::longValue)
          .description(SNAPSHOT_CHUNK_COUNT.getDescription())
          .tags(SnapshotMetricsDoc.BootstrapKeyNames.tags(bool))
          .register(registry);
      Gauge.builder(
              SNAPSHOT_SIZE.getName(),
              snapshotSizeArray.get(encodeBoolean(bool)),
              Number::longValue)
          .description(SNAPSHOT_SIZE.getDescription())
          .tags(SnapshotMetricsDoc.BootstrapKeyNames.tags(bool))
          .register(registry);
    }
  }

  void incrementSnapshotCount(final boolean isBootstrap) {
    snapshotCount.get(encodeBoolean(isBootstrap)).increment();
  }

  void observeSnapshotSize(final long sizeInBytes, final boolean isBootstrap) {
    snapshotSizeArray.get(encodeBoolean(isBootstrap)).set(sizeInBytes);
  }

  void observeSnapshotChunkCount(final long count, final boolean isBootstrap) {
    snapshotChunkCountArray.get(encodeBoolean(isBootstrap)).set(count);
  }

  void observeSnapshotFileSize(final long sizeInBytes, final boolean isBootstrap) {
    snapshotFileSize.get(encodeBoolean(isBootstrap)).record(sizeInBytes / 1_000_000f);
  }

  CloseableSilently startTimer(final boolean isBootstrap) {
    return MicrometerUtil.timer(
        snapshotDuration.get(encodeBoolean(isBootstrap)), Timer.start(clock));
  }

  CloseableSilently startPersistTimer(final boolean isBootstrap) {
    return MicrometerUtil.timer(
        snapshotPersistDuration.get(encodeBoolean(isBootstrap)), Timer.start(clock));
  }

  private static boolean decodeBoolean(final int i) {
    return i == 1;
  }

  private static int encodeBoolean(final boolean b) {
    return b ? 1 : 0;
  }
}
