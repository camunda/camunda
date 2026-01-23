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

/**
 * The cluster health indicator signals if there are still any healthy partition available, if not
 * then is set as down as no processing is happening. In the details also indicates the health
 * status of all partitions.
 */
public class ClusterHealthIndicator implements HealthIndicator {

  private final Supplier<Optional<BrokerClusterState>> clusterStateSupplier;

  public ClusterHealthIndicator(final Supplier<Optional<BrokerClusterState>> clusterStateSupplier) {
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
        if (!optClusterState.get().getPartitions().isEmpty()) {
          final List<Integer> partitions = optClusterState.get().getPartitions();

          final Map<String, PartitionHealthStatus> partitionDetails =
              getPartitionsHealthStatus(partitions, optClusterState.get());
          return getStatus(partitions.size(), partitionDetails);
        } else {
          return Health.down().build();
        }
      }
    }
  }

  Map<String, PartitionHealthStatus> getPartitionsHealthStatus(
      final List<Integer> partitions, final BrokerClusterState optClusterState) {
    final Map<String, PartitionHealthStatus> partitionDetails = new HashMap<>();
    partitions.forEach(
        partition -> {
          final int broker = optClusterState.getLeaderForPartition(partition);
          final PartitionHealthStatus partitionHealthStatus =
              optClusterState.getPartitionHealth(broker, partition);
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
      return Health.status("DEGRADED").withDetails(partitionDetails).build();
    }
  }
}
