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
 * <p>Nothing is registered until the member coordinates, so a member that never does - which is
 * every member but one - publishes none of these, and the cluster-wide values are not confused by a
 * series per member. From that point the rebalance counts are published whether or not there is
 * anything to count (see {@link #startCoordinating()}), while the per-partition meters wait for a
 * partition to report on, their label sets not being known in advance.
 *
 * <p>The partition states outlive the rebalance that set them, so that what became of each
 * partition can be read after the fact rather than only while the rebalance is in flight. A
 * rebalance is often over within a single scrape - under a second on a small cluster against a
 * scrape interval of thirty - which left nothing to see at all when they were taken down at the
 * end. They are replaced wholesale by the next rebalance and taken down by {@link
 * #stopCoordinating()}, a state this member no longer owns being worse than none.
 *
 * <p>The timers are cumulative and are left alone by all of this, so that a window covering a
 * rebalance still accounts for it after the rebalance has ended or the coordinator has moved.
 */
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
   *
   * <p>A counter that first appears already holding the event that created it hides that event from
   * {@code increase()}, which can only see a change between two samples. A dashboard counting
   * rebalances over a window would therefore miss the first one of each outcome - and one rebalance
   * is exactly the case an operator looks at the dashboard for.
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

  /**
   * Publishes where the rebalance stands with every partition it covers, replacing what the last
   * rebalance left behind. Takes them all at once rather than one at a time so that a partition the
   * previous rebalance covered and this one does not stops being reported, instead of lingering
   * with the state it reached under a rebalance that has been superseded.
   */
  public void setPartitionStates(final Map<PartitionId, PartitionRebalanceState> states) {
    final var superseded = partitionStates.entrySet().iterator();
    while (superseded.hasNext()) {
      final var partition = superseded.next();
      if (!states.containsKey(partition.getKey())) {
        registry.remove(partition.getValue());
        superseded.remove();
      }
    }
    states.forEach(
        (partition, state) ->
            partitionStates
                .computeIfAbsent(partition, this::registerPartitionState)
                .set(metricValue(state)));
  }

  /**
   * Stops publishing what became of each partition, this member no longer being the one that
   * answers for it.
   */
  public void stopCoordinating() {
    partitionStates.values().forEach(registry::remove);
    partitionStates.clear();
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

  /** One partition's timer for one outcome; a partition accumulates one per outcome it reaches. */
  private record PartitionOutcome(PartitionId partition, String result) {}
}
