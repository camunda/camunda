/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.metrics.ClusterRebalanceMetricsDoc.ClusterRebalanceKeyNames;
import io.camunda.zeebe.dynamic.config.rebalance.PartitionRebalanceState;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceOutcome;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * What the rebalancing coordinator reports about the rebalances it runs.
 *
 * <p>Every meter is registered the first time it is written, so a member that never coordinates -
 * which is every member but one - publishes none of these, and the cluster-wide values are not
 * confused by a series per member.
 *
 * <p>The gauges only mean anything while a rebalance is running, so {@link #clear()} takes them
 * down when one ends and when a member stops coordinating: a gauge left behind would hold its last
 * value for as long as the process lives, drawing the rebalance as though it were still going. The
 * timers are cumulative and are deliberately left alone by both, so that a window covering a
 * rebalance still accounts for it after the rebalance has ended or the coordinator has moved.
 */
@NullMarked
public final class ClusterRebalanceMetrics {

  private final MeterRegistry registry;
  private final Map<RebalanceOutcome, Timer> elapsed = new EnumMap<>(RebalanceOutcome.class);
  private final Map<PartitionOutcome, Timer> partitionDurations = new HashMap<>();
  private final Map<PartitionId, StatefulGauge> partitionStates = new HashMap<>();
  private final Map<String, StatefulGauge> partitionsPending = new HashMap<>();

  public ClusterRebalanceMetrics(final MeterRegistry registry) {
    this.registry = registry;
  }

  public void observeElapsed(final RebalanceOutcome outcome, final Duration duration) {
    elapsed.computeIfAbsent(outcome, this::registerElapsed).record(duration);
  }

  /**
   * Records what the rebalance made of one partition, and how long it spent finding out.
   *
   * @param result why the rebalance ended up where it did with this partition. Named rather than
   *     typed because the answer comes from either side of the transfer protocol: a {@code
   *     LeadershipTransferResult} where the partition's leader gave a reason, and a {@code
   *     PartitionRebalanceResult} where the coordinator never got one out of it.
   */
  public void observePartitionDuration(
      final PartitionId partition, final String result, final Duration duration) {
    partitionDurations
        .computeIfAbsent(new PartitionOutcome(partition, result), this::registerPartitionDuration)
        .record(duration);
  }

  public void setPartitionState(final PartitionId partition, final PartitionRebalanceState state) {
    partitionStates
        .computeIfAbsent(partition, this::registerPartitionState)
        .set(metricValue(state));
  }

  public void setPartitionsPending(final String physicalTenantId, final long pending) {
    partitionsPending
        .computeIfAbsent(physicalTenantId, this::registerPartitionsPending)
        .set(pending);
  }

  /** Stops publishing what a rebalance is doing, there being no rebalance doing it any more. */
  public void clear() {
    partitionStates.values().forEach(registry::remove);
    partitionStates.clear();
    partitionsPending.values().forEach(registry::remove);
    partitionsPending.clear();
  }

  /**
   * The states are published as one gauge per partition rather than a series per state, so they
   * need distinct values. Spelt out here rather than taken from the enum's ordinal, because a value
   * a dashboard reads should not move when someone reorders the enum.
   */
  private static int metricValue(final PartitionRebalanceState state) {
    return switch (state) {
      case PENDING -> 1;
      case TRANSFERRING -> 2;
      case TRANSFERRED -> 3;
      case SKIPPED -> 4;
      case FAILED -> 5;
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

  private StatefulGauge registerPartitionsPending(final String physicalTenantId) {
    return StatefulGauge.builder(ClusterRebalanceMetricsDoc.PARTITIONS_PENDING.getName())
        .description(ClusterRebalanceMetricsDoc.PARTITIONS_PENDING.getDescription())
        .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
        .register(registry);
  }

  /** One partition's timer for one outcome; a partition accumulates one per outcome it reaches. */
  private record PartitionOutcome(PartitionId partition, String result) {}
}
