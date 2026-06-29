/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.broker.system.monitoring.HealthMetricsDoc.NodesKeyNames;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthNodePosition;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.health.HealthTreeListener;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Projects the health tree into the {@code zeebe_broker_health_nodes} metric: exactly one gauge per
 * node, tagged with the node's {@code id} and {@code path} plus the {@code physicalTenant}/{@code
 * partition} it is attributed to, all derived from the node's {@link HealthNodePosition}.
 *
 * <p>As the single observer of the one tree it cannot drift from the tree, and because there is one
 * node object per node it cannot emit a node twice. All nodes share a single meter registry; the
 * tenant/partition attribution is set per gauge from the position rather than via registry-wide
 * common tags, so no separate per-partition registry is needed.
 */
public final class HealthTreeMetrics implements HealthTreeListener {
  private final MeterRegistry meterRegistry;
  // Keyed by node identity on purpose: a node's display name is unique only among its siblings, so
  // it is not a valid global key. Health monitorables do not override equals/hashCode.
  private final Map<HealthMonitorable, Meter.Id> meters = new IdentityHashMap<>();

  public HealthTreeMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * @param status to be mapped
   * @return a numerical value mapped from status that can be reported by the gauge
   */
  public static int statusValue(final HealthStatus status) {
    return switch (status) {
      case HEALTHY -> 1;
      case UNHEALTHY -> -1;
      case DEAD -> -2;
      case null -> 0;
    };
  }

  @Override
  public synchronized void onNodeRegistered(
      final HealthMonitorable node, final HealthNodePosition position) {
    final var gauge = buildNodeGauge(node, position);
    meters.put(node, gauge.getId());
  }

  @Override
  public synchronized void onNodeRemoved(final HealthMonitorable node) {
    final var meterId = meters.remove(node);
    if (meterId != null) {
      meterRegistry.remove(meterId);
    }
  }

  public synchronized void close() {
    meters.values().forEach(meterRegistry::remove);
    meters.clear();
  }

  private Gauge buildNodeGauge(final HealthMonitorable node, final HealthNodePosition position) {
    final var meterDoc = HealthMetricsDoc.NODES;
    return Gauge.builder(
            meterDoc.getName(),
            node,
            // make sure to not close over anything else than `n`
            n -> n != null ? statusValue(n.getHealthReport().getStatus()) : statusValue(null))
        .description(meterDoc.getDescription())
        .tag(NodesKeyNames.ID.asString(), position.name())
        .tag(NodesKeyNames.PATH.asString(), position.path())
        .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), position.physicalTenant())
        .tag(PartitionKeyNames.PARTITION.asString(), position.partition())
        .register(meterRegistry);
  }
}
