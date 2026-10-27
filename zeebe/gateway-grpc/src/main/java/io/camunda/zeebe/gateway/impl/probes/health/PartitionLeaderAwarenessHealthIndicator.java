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
 * Health indicator that signals whether the gateway is aware of partition leaders in the cluster.
 * This aggregates across all partition groups (physical tenants): the indicator reports UP as soon
 * as any physical tenant has a partition with a known leader, so that a gateway serving multiple
 * physical tenants stays ready even if only some of them can currently be served. If the gateway is
 * not aware of any partition leaders in any physical tenant, it cannot relay any requests. The
 * details report the number of known partitions and partitions with a leader per physical tenant.
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

    var hasKnownLeader = false;
    final var details = new HashMap<String, Object>();
    for (final var entry : clusterStates.entrySet()) {
      final var clusterState = entry.getValue();
      final var partitions = clusterState.getPartitions();
      final var partitionsWithLeader =
          partitions.stream()
              .filter(index -> clusterState.getLeaderForPartition(index) != null)
              .count();
      hasKnownLeader |= partitionsWithLeader > 0;
      details.put(
          entry.getKey(),
          Map.of("partitions", partitions.size(), "partitionsWithLeader", partitionsWithLeader));
    }

    final var health = hasKnownLeader ? Health.up() : Health.down();
    return health.withDetails(details).build();
  }
}
