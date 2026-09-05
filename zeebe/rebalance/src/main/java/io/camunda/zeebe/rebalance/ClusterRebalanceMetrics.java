/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.rebalance.ClusterRebalanceMetricsDoc.ClusterRebalanceKeyNames;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/** Metrics for the cluster-wide side of a rebalance (coordinated leadership transfer). */
@NullMarked
public final class ClusterRebalanceMetrics {

  private final MeterRegistry registry;
  private final Map<RebalanceOutcome, Timer> elapsed = new EnumMap<>(RebalanceOutcome.class);
  private final Map<PartitionOutcome, Timer> partitionDurations = new HashMap<>();
  private final Map<PartitionId, StatefulGauge> partitionStates = new HashMap<>();

  public ClusterRebalanceMetrics(final MeterRegistry registry) {
    this.registry = registry;
  }

  /**
   * Publishes the rebalance counts from the moment this member takes the coordinating role, before
   * it has anything to count.
   */
  public void startCoordinating() {
    for (final var outcome : RebalanceOutcome.values()) {
      elapsed.computeIfAbsent(outcome, this::registerElapsed);
    }
  }

  public void observeElapsed(final RebalanceOutcome outcome, final Duration duration) {
    elapsed.computeIfAbsent(outcome, this::registerElapsed).record(duration);
  }

  /**
   * Records the rebalance outcome for one partition, and how long it took.
   *
   * @param result the {@link PartitionRebalanceOutcome} the partition reached
   */
  public void observePartitionDuration(
      final PartitionId partition, final String result, final Duration duration) {
    partitionDurations
        .computeIfAbsent(new PartitionOutcome(partition, result), this::registerPartitionDuration)
        .record(duration);
  }

  /**
   * Publishes where the rebalance stands with each partition, replacing whatever state the last
   * rebalance left behind.
   */
  public void setPartitionStates(final List<PartitionRebalance> partitions) {
    final Map<PartitionId, PartitionRebalanceProgress> progress = new HashMap<>();
    partitions.forEach(
        partition ->
            progress.put(
                new PartitionId(partition.physicalTenantId(), partition.partitionId()),
                partition.progress()));

    final var superseded = partitionStates.entrySet().iterator();
    while (superseded.hasNext()) {
      final var partition = superseded.next();
      if (!progress.containsKey(partition.getKey())) {
        registry.remove(partition.getValue());
        superseded.remove();
      }
    }
    progress.forEach(
        (partition, state) ->
            partitionStates
                .computeIfAbsent(partition, this::registerPartitionState)
                .set(metricValue(state)));
  }

  /**
   * Stops publishing the per-partition rebalance state when the member is no longer the rebalance
   * coordinator.
   */
  public void stopCoordinating() {
    partitionStates.values().forEach(registry::remove);
    partitionStates.clear();
    partitionDurations.values().forEach(registry::remove);
    partitionDurations.clear();
    elapsed.values().forEach(registry::remove);
    elapsed.clear();
  }

  /**
   * Ensure we have a stable ordinal for dashboards to match against (so Java enum reordering
   * doesn't change it).
   */
  private static int metricValue(final PartitionRebalanceProgress progress) {
    return switch (progress) {
      case PENDING -> 1;
      case TRANSFERRING -> 2;
      case COMPLETED -> 3;
    };
  }

  private Timer registerElapsed(final RebalanceOutcome outcome) {
    return Timer.builder(ClusterRebalanceMetricsDoc.REBALANCE_ELAPSED.getName())
        .description(ClusterRebalanceMetricsDoc.REBALANCE_ELAPSED.getDescription())
        .serviceLevelObjectives(ClusterRebalanceMetricsDoc.REBALANCE_ELAPSED.getTimerSLOs())
        .tag(ClusterRebalanceKeyNames.RESULT.asString(), outcome.name())
        .register(registry);
  }

  private Timer registerPartitionDuration(final PartitionOutcome outcome) {
    return Timer.builder(ClusterRebalanceMetricsDoc.PARTITION_DURATION.getName())
        .description(ClusterRebalanceMetricsDoc.PARTITION_DURATION.getDescription())
        .serviceLevelObjectives(ClusterRebalanceMetricsDoc.PARTITION_DURATION.getTimerSLOs())
        .tags(PartitionKeyNames.tags(outcome.partition()))
        .tag(ClusterRebalanceKeyNames.RESULT.asString(), outcome.result())
        .register(registry);
  }

  private StatefulGauge registerPartitionState(final PartitionId partition) {
    return StatefulGauge.builder(ClusterRebalanceMetricsDoc.PARTITION_STATE.getName())
        .description(ClusterRebalanceMetricsDoc.PARTITION_STATE.getDescription())
        .tags(PartitionKeyNames.tags(partition))
        .register(registry);
  }

  private record PartitionOutcome(PartitionId partition, String result) {}
}
