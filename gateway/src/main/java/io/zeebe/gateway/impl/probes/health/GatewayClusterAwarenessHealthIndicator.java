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
 * Health indicator that signals whether the gateway is aware of any nodes in the cluster. If the
 * gateway is not aware of any nodes this indicates a potential network topology problem
 */
public class GatewayClusterAwarenessHealthIndicator implements HealthIndicator {

  private final Supplier<BrokerClusterState> clusterStateSupplier;

  public GatewayClusterAwarenessHealthIndicator(
      final Supplier<BrokerClusterState> clusterStateSupplier) {
    this.clusterStateSupplier = requireNonNull(clusterStateSupplier);
  }

  @Override
  public Health health() {
    final var clusterState = clusterStateSupplier.get();

    if (clusterState == null) {
      return Health.unknown().build();
    } else {
      if (clusterState.getBrokers().isEmpty()) {
        return Health.down().build();
      } else {
        return Health.up().build();
      }
    }
  }
}
