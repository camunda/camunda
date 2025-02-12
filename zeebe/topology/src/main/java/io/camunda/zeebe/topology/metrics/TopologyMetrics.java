/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.metrics;

import static io.camunda.zeebe.topology.metrics.TopologyMetricsDoc.*;

import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterChangePlan.Status;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.CompletedChange;
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

  public void updateFromTopology(final ClusterTopology topology) {
    topologyVersion.set(topology.version());
    changeStatus.state(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::status)
            .or(() -> topology.lastChange().map(CompletedChange::status))
            .orElse(Status.COMPLETED));
    changeId.set(topology.pendingChanges().map(ClusterChangePlan::id).orElse(0L));
    changeVersion.set(topology.pendingChanges().map(ClusterChangePlan::version).orElse(0));
    pendingOperations.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::pendingOperations)
            .map(List::size)
            .orElse(0));
    completedOperations.set(
        topology
            .pendingChanges()
            .map(ClusterChangePlan::completedOperations)
            .map(List::size)
            .orElse(0));
  }
}
