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
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Health indicator that signals whether the gateway is aware of any nodes in the cluster. If the
 * gateway is not aware of any nodes this indicates a potential network topology problem
 */
public class ClusterAwarenessHealthIndicator implements HealthIndicator {

  private final Supplier<Optional<BrokerClusterState>> clusterStateSupplier;

  public ClusterAwarenessHealthIndicator(
      final Supplier<Optional<BrokerClusterState>> clusterStateSupplier) {
    this.clusterStateSupplier = requireNonNull(clusterStateSupplier);
  }

  @Override
  public Health health() {
    final var optClusterState = clusterStateSupplier.get();

    if (optClusterState.isEmpty()) {
      return Health.down().build();
    } else {
      if (optClusterState.get().getBrokers().isEmpty()) {
        return Health.down().build();
      } else {
        return Health.up().build();
      }
    }
  }
}
