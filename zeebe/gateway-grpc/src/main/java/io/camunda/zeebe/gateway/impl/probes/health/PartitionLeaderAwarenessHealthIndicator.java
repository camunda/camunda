/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Health indicator that signals whether the gateway is aware of partition leaders in the cluster.
 * This aggregates across all partition groups (physical tenants): the indicator reports UP as soon
 * as any physical tenant has a partition with a known leader, so that a gateway serving multiple
 * physical tenants stays ready even if only some of them can currently be served. If the gateway is
 * not aware of any partition leaders in any physical tenant, it cannot relay any requests.
 */
public class PartitionLeaderAwarenessHealthIndicator implements HealthIndicator {

  private final Supplier<Map<String, BrokerClusterState>> clusterStatesSupplier;

  public PartitionLeaderAwarenessHealthIndicator(
      final Supplier<Map<String, BrokerClusterState>> clusterStatesSupplier) {
    this.clusterStatesSupplier = requireNonNull(clusterStatesSupplier);
  }

  @Override
  public Health health() {
    final var clusterStates = clusterStatesSupplier.get();

    final var hasKnownLeader =
        clusterStates.values().stream()
            .anyMatch(
                clusterState ->
                    clusterState.getPartitions().stream()
                        .anyMatch(index -> clusterState.getLeaderForPartition(index) != null));

    if (hasKnownLeader) {
      return Health.up().build();
    } else {
      return Health.down().build();
    }
  }
}
