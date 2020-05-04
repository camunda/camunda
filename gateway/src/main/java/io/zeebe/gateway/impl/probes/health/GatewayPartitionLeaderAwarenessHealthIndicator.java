/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import java.util.function.Supplier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator that signals whether the gateway is aware of partition leaders in the cluster.
 * If the gateway is not aware of any partition leaders, it cannot relay any requests
 */
public class GatewayPartitionLeaderAwarenessHealthIndicator implements HealthIndicator {

  private final Supplier<BrokerClusterState> clusterStateSupplier;

  public GatewayPartitionLeaderAwarenessHealthIndicator(
      final Supplier<BrokerClusterState> clusterStateSupplier) {
    this.clusterStateSupplier = requireNonNull(clusterStateSupplier);
  }

  @Override
  public Health health() {
    final var clusterState = clusterStateSupplier.get();

    if (clusterState == null) {
      return Health.unknown().build();
    } else {
      if (clusterState.getPartitions().stream()
          .filter(
              index -> {
                return clusterState.getLeaderForPartition(index) != BrokerClusterState.NODE_ID_NULL;
              })
          .findAny()
          .isPresent()) {
        return Health.up().build();
      } else {
        return Health.down().build();
      }
    }
  }
}
