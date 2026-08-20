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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Health indicator that signals whether the gateway is aware of any nodes in the cluster. This
 * aggregates across all partition groups (physical tenants): the indicator reports UP as soon as
 * any physical tenant has a known broker, so that a gateway serving multiple physical tenants stays
 * ready even if only some of them are reachable. If the gateway is not aware of any nodes in any
 * physical tenant this indicates a potential network topology problem. The details report the
 * number of known brokers per physical tenant.
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

    var hasKnownBroker = false;
    final var details = new HashMap<String, Object>();
    for (final var entry : clusterStates.entrySet()) {
      final var brokers = entry.getValue().getBrokers().size();
      hasKnownBroker |= brokers > 0;
      details.put(entry.getKey(), Map.of("brokers", brokers));
    }

    final var health = hasKnownBroker ? Health.up() : Health.down();
    return health.withDetails(details).build();
  }
}
