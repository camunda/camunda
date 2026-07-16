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
 * Health indicator that signals whether the gateway is aware of any nodes in the cluster. This
 * aggregates across all partition groups (physical tenants): the indicator reports UP as soon as
 * any physical tenant has a known broker, so that a gateway serving multiple physical tenants stays
 * ready even if only some of them are reachable. If the gateway is not aware of any nodes in any
 * physical tenant this indicates a potential network topology problem.
 */
public class ClusterAwarenessHealthIndicator implements HealthIndicator {

  private final Supplier<Map<String, BrokerClusterState>> clusterStatesSupplier;

  public ClusterAwarenessHealthIndicator(
      final Supplier<Map<String, BrokerClusterState>> clusterStatesSupplier) {
    this.clusterStatesSupplier = requireNonNull(clusterStatesSupplier);
  }

  @Override
  public Health health() {
    final var clusterStates = clusterStatesSupplier.get();

    final var hasKnownBroker =
        clusterStates.values().stream().anyMatch(state -> !state.getBrokers().isEmpty());

    if (hasKnownBroker) {
      return Health.up().build();
    } else {
      return Health.down().build();
    }
  }
}
