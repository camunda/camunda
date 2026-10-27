/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.metrics;

import static io.camunda.zeebe.backup.metrics.BackupMetadataSyncerMetricsDoc.METADATA_SYNC_DURATION;
import static io.camunda.zeebe.backup.metrics.BackupMetadataSyncerMetricsDoc.METADATA_SYNC_SERIALIZED_SIZE;
import static io.camunda.zeebe.backup.metrics.BackupMetadataSyncerMetricsDoc.METADATA_SYNC_TOTAL;

import io.camunda.zeebe.backup.metrics.BackupMetadataSyncerMetricsDoc.MetricKeyName;
import io.camunda.zeebe.backup.metrics.BackupMetadataSyncerMetricsDoc.SyncResult;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class BackupMetadataSyncerMetrics implements AutoCloseable {

  private final MeterRegistry registry;
  private final Map<Integer, Map<SyncResult, Counter>> syncCounters = new HashMap<>();
  private final Map<Integer, Timer> uploadDurations = new HashMap<>();
  private final Map<Integer, StatefulGauge> serializedSizes = new HashMap<>();

  public BackupMetadataSyncerMetrics(final MeterRegistry meterRegistry) {
    registry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
  }

  public CloseableSilently startSyncTimer(final int partitionId) {
    final var timer = uploadDurations.computeIfAbsent(partitionId, this::registerSyncDuration);
    return MicrometerUtil.timer(timer, Timer.start(registry.config().clock()));
  }

  public void recordSuccessfulSync(final int partitionId, final long serializedSizeBytes) {
    syncCounters
        .computeIfAbsent(partitionId, k -> new HashMap<>())
        .computeIfAbsent(SyncResult.COMPLETED, r -> registerSyncCounter(partitionId, r))
        .increment();

    serializedSizes
        .computeIfAbsent(partitionId, this::registerSerializedSize)
        .set((double) serializedSizeBytes);
  }

  public void recordFailedSync(final int partitionId) {
    syncCounters
        .computeIfAbsent(partitionId, k -> new HashMap<>())
        .computeIfAbsent(SyncResult.FAILED, r -> registerSyncCounter(partitionId, r))
        .increment();
  }

  private Counter registerSyncCounter(final int partitionId, final SyncResult result) {
    return Counter.builder(METADATA_SYNC_TOTAL.getName())
        .description(METADATA_SYNC_TOTAL.getDescription())
        .tag(MetricKeyName.PARTITION.asString(), String.valueOf(partitionId))
        .tag(MetricKeyName.RESULT.asString(), result.getValue())
        .register(registry);
  }

  private Timer registerSyncDuration(final int partitionId) {
    return Timer.builder(METADATA_SYNC_DURATION.getName())
        .description(METADATA_SYNC_DURATION.getDescription())
        .tag(MetricKeyName.PARTITION.asString(), String.valueOf(partitionId))
        .serviceLevelObjectives(METADATA_SYNC_DURATION.getTimerSLOs())
        .register(registry);
  }

  private StatefulGauge registerSerializedSize(final int partitionId) {
    return StatefulGauge.builder(METADATA_SYNC_SERIALIZED_SIZE.getName())
        .description(METADATA_SYNC_SERIALIZED_SIZE.getDescription())
        .tag(MetricKeyName.PARTITION.asString(), String.valueOf(partitionId))
        .register(registry);
  }

  @Override
  public void close() {
    syncCounters.values().stream().flatMap(m -> m.values().stream()).forEach(registry::remove);
    uploadDurations.values().forEach(registry::remove);
    serializedSizes.values().forEach(registry::remove);
  }
}
