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
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/**
 * The cluster health indicator signals if there are still any healthy partition available, if not
 * then is set as down as no processing is happening. It aggregates across all partition groups
 * (physical tenants): UP when every physical tenant is fully healthy, DOWN when no physical tenant
 * can process work, DEGRADED otherwise. The details report, per physical tenant, its status and the
 * health status of all its partitions.
 */
public class ClusterHealthIndicator implements HealthIndicator {

  private static final Status DEGRADED = new Status("DEGRADED");

  private final Supplier<Map<String, BrokerClusterState>> clusterStatesSupplier;

  public ClusterHealthIndicator(
      final Supplier<Map<String, BrokerClusterState>> clusterStatesSupplier) {
    this.clusterStatesSupplier = requireNonNull(clusterStatesSupplier);
  }

  @Override
  public Health health() {
    final var clusterStates = clusterStatesSupplier.get();
    if (clusterStates.isEmpty()) {
      return Health.down().build();
    }

    var allUp = true;
    var allDown = true;
    final var details = new HashMap<String, Object>();
    for (final var entry : clusterStates.entrySet()) {
      final var groupHealth = groupHealth(entry.getValue());
      allUp &= Status.UP.equals(groupHealth.getStatus());
      allDown &= Status.DOWN.equals(groupHealth.getStatus());
      details.put(
          entry.getKey(),
          Map.of(
              "status", groupHealth.getStatus().getCode(),
              "partitions", groupHealth.getDetails()));
    }

    final Health.Builder health;
    if (allUp) {
      health = Health.up();
    } else if (allDown) {
      health = Health.down();
    } else {
      health = Health.status(DEGRADED);
    }
    return health.withDetails(details).build();
  }

  private Health groupHealth(final BrokerClusterState clusterState) {
    if (clusterState.getBrokers().isEmpty() || clusterState.getPartitions().isEmpty()) {
      return Health.down().build();
    }
    final List<Integer> partitions = clusterState.getPartitions();
    final Map<String, PartitionHealthStatus> partitionDetails =
        getPartitionsHealthStatus(partitions, clusterState);
    return getStatus(partitions.size(), partitionDetails);
  }

  Map<String, PartitionHealthStatus> getPartitionsHealthStatus(
      final List<Integer> partitions, final BrokerClusterState optClusterState) {
    final Map<String, PartitionHealthStatus> partitionDetails = new HashMap<>();
    partitions.forEach(
        partition -> {
          final var broker = optClusterState.getLeaderForPartition(partition);
          final PartitionHealthStatus partitionHealthStatus =
              broker != null
                  ? Optional.ofNullable(optClusterState.getPartitionHealth(broker, partition))
                      .orElse(PartitionHealthStatus.NULL_VAL)
                  : PartitionHealthStatus.NULL_VAL;
          partitionDetails.put(String.format("Partition %d", partition), partitionHealthStatus);
        });
    return partitionDetails;
  }

  private Health getStatus(
      final int partitionCount, final Map<String, PartitionHealthStatus> partitionDetails) {
    final var unhealthyPartitionsCount =
        partitionDetails.values().stream()
            .filter(p -> p == PartitionHealthStatus.UNHEALTHY || p == PartitionHealthStatus.DEAD)
            .count();
    final var healthyPartitionsCount =
        partitionDetails.values().stream().filter(p -> p == PartitionHealthStatus.HEALTHY).count();
    final var hasOnlyUnhealthyPartitions = unhealthyPartitionsCount == partitionDetails.size();
    final var hasOnlyHealthyPartitions = healthyPartitionsCount == partitionCount;

    if (hasOnlyUnhealthyPartitions) {
      return Health.down().withDetails(partitionDetails).build();
    } else if (hasOnlyHealthyPartitions) {
      return Health.up().withDetails(partitionDetails).build();
    } else {
      return Health.status(DEGRADED).withDetails(partitionDetails).build();
    }
  }
}
