/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.metrics.RebalanceMetricsDoc.RebalanceKeyNames;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Metrics for coordinated leadership transfer (rebalancing). Transfer and pause are partition-level
 * concepts, not leader-term-level ones, so this is owned by {@link io.atomix.raft.impl.RaftContext}
 * for its whole lifetime rather than by each {@link io.atomix.raft.roles.LeaderRole} term: a
 * successful transfer steps the leader down before its completion continuation records the
 * transfer-duration metric, so a role-scoped instance would either record against meters a
 * role-scoped close() already removed, or re-register orphaned ones; it would also reset
 * operational history on every election. Only the paused gauge reflects the current role, reset by
 * the outgoing {@code LeaderRole} on stop.
 */
public class RebalanceMetrics extends RaftMetrics {

  private final MeterRegistry meterRegistry;
  private final Timer partitionPauseDuration;
  private final StatefulGauge partitionPaused;
  private final Map<LeadershipTransferResult, Timer> partitionTransferDuration =
      new EnumMap<>(LeadershipTransferResult.class);

  public RebalanceMetrics(final String partitionName, final MeterRegistry meterRegistry) {
    super(partitionName);
    this.meterRegistry = meterRegistry;
    partitionPauseDuration =
        Timer.builder(RebalanceMetricsDoc.PARTITION_PAUSE_DURATION.getName())
            .description(RebalanceMetricsDoc.PARTITION_PAUSE_DURATION.getDescription())
            .serviceLevelObjectives(RebalanceMetricsDoc.PARTITION_PAUSE_DURATION.getTimerSLOs())
            .tag(PartitionKeyNames.PARTITION.asString(), partition)
            .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), partitionGroupName)
            .register(meterRegistry);
    partitionPaused =
        StatefulGauge.builder(RebalanceMetricsDoc.PARTITION_PAUSED.getName())
            .description(RebalanceMetricsDoc.PARTITION_PAUSED.getDescription())
            .tag(PartitionKeyNames.PARTITION.asString(), partition)
            .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), partitionGroupName)
            .register(meterRegistry);
  }

  public void observePauseDuration(final Duration duration) {
    partitionPauseDuration.record(duration);
  }

  /**
   * Sets the paused gauge to 1 only while write admission is frozen and processing is paused for
   * the transfer, and back to 0 once those restrictions are removed or leadership is lost.
   */
  public void setPartitionPaused(final boolean paused) {
    partitionPaused.set(paused ? 1 : 0);
  }

  public void observeTransferDuration(
      final LeadershipTransferResult result, final Duration duration) {
    partitionTransferDuration
        .computeIfAbsent(result, this::registerTransferDuration)
        .record(duration);
  }

  private Timer registerTransferDuration(final LeadershipTransferResult result) {
    return Timer.builder(RebalanceMetricsDoc.PARTITION_TRANSFER_DURATION.getName())
        .description(RebalanceMetricsDoc.PARTITION_TRANSFER_DURATION.getDescription())
        .serviceLevelObjectives(RebalanceMetricsDoc.PARTITION_TRANSFER_DURATION.getTimerSLOs())
        .tag(RebalanceKeyNames.RESULT.asString(), result.name())
        .tag(PartitionKeyNames.PARTITION.asString(), partition)
        .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), partitionGroupName)
        .register(meterRegistry);
  }
}
