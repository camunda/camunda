/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator that signals whether the gateway is aware of partition leaders in the cluster.
 * If the gateway is not aware of any partition leaders, it cannot relay any requests
 */
public class PartitionLeaderAwarenessHealthIndicator implements HealthIndicator {

  private final Supplier<Optional<BrokerClusterState>> clusterStateSupplier;

  public PartitionLeaderAwarenessHealthIndicator(
      final Supplier<Optional<BrokerClusterState>> clusterStateSupplier) {
    this.clusterStateSupplier = requireNonNull(clusterStateSupplier);
  }

  @Override
  public Health health() {
    final var optClusterState = clusterStateSupplier.get();

    if (optClusterState.isEmpty()) {
      return Health.down().build();
    } else {
      final var clusterState = optClusterState.get();
      if (clusterState.getPartitions().stream()
          .anyMatch(
              index ->
                  clusterState.getLeaderForPartition(index) != BrokerClusterState.NODE_ID_NULL)) {
        return Health.up().build();
      } else {
        return Health.down().build();
      }
    }
  }
}
