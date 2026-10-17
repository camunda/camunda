/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import static io.camunda.zeebe.dynamic.config.metrics.TopologyMetricsDoc.*;

import io.camunda.zeebe.dynamic.config.state.ChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.util.micrometer.EnumMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class TopologyMetrics {

  private final MeterRegistry registry;
  private final AtomicLong topologyVersion;
  private final AtomicLong changeId;
  private final AtomicLong changeVersion;
  private final AtomicLong pendingOperations;
  private final AtomicLong completedOperations;
  private final EnumMeter<Status> changeStatus;

  public TopologyMetrics(final MeterRegistry registry) {
    this.registry = registry;
    topologyVersion = makeGauge(TOPOLOGY_VERSION);
    changeId = makeGauge(CHANGE_ID);
    changeStatus =
        EnumMeter.register(
            ClusterChangePlan.Status.class,
            CHANGE_STATUS,
            TopologyMetricsKeyName.CLUSTER_CHANGE_STATUS,
            registry);
    changeVersion = makeGauge(CHANGE_VERSION);
    pendingOperations = makeGauge(PENDING_OPERATIONS);
    completedOperations = makeGauge(COMPLETED_OPERATIONS);
  }

  private AtomicLong makeGauge(final TopologyMetricsDoc meter) {
    final var value = new AtomicLong();
    Gauge.builder(meter.getName(), value::get)
        .description(meter.getDescription())
        .register(registry);
    return value;
  }

  /**
   * Reports the same values the legacy single-group projection carried: the topology version and
   * the state of the change plan currently active in the global configuration or, failing that, the
   * default partition group — a cluster applies at most one plan that touches either at a time, so
   * that one plan is the cluster's change.
   */
  public void updateFromTopology(final CurrentClusterConfiguration topology) {
    final var globalConfiguration = topology.globalConfiguration();
    final var defaultGroup =
        topology
            .partitionGroups()
            .getOrDefault(
                CurrentClusterConfiguration.DEFAULT_GROUP,
                PartitionGroupConfiguration.empty(topology.version()));
    topologyVersion.set(Math.max(globalConfiguration.version(), defaultGroup.version()));

    final var pendingChanges =
        globalConfiguration
            .pendingChanges()
            .<ChangePlan>map(plan -> plan)
            .filter(ChangePlan::hasPendingChanges)
            .or(
                () ->
                    defaultGroup
                        .pendingChanges()
                        .<ChangePlan>map(plan -> plan)
                        .filter(ChangePlan::hasPendingChanges));
    changeStatus.state(
        pendingChanges
            .map(ChangePlan::status)
            .or(() -> topology.phasedChangeState().lastChange().map(TopologyMetrics::legacyStatus))
            .orElse(Status.COMPLETED));
    changeId.set(pendingChanges.map(ChangePlan::id).orElse(0L));
    changeVersion.set(pendingChanges.map(ChangePlan::version).orElse(0));
    pendingOperations.set(
        pendingChanges.map(ChangePlan::pendingOperations).map(List::size).orElse(0));
    completedOperations.set(
        pendingChanges.map(ChangePlan::completedOperations).map(List::size).orElse(0));
  }

  /** The terminal status as the gauge's {@link ClusterChangePlan.Status} labels it. */
  private static Status legacyStatus(final CompletedPhasedChange change) {
    return switch (change.status()) {
      case COMPLETED -> Status.COMPLETED;
      case FAILED -> Status.FAILED;
      case CANCELLED -> Status.CANCELLED;
    };
  }
}
