/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * The cluster health indicator signals if there are still any healthy partition available, if not
 * then is set as down as no processing is happening. In the details also indicates the health
 * status of all partitions.
 */
public class ClusterHealthIndicator implements HealthIndicator {

  private final Supplier<Optional<BrokerClusterState>> clusterStateSupplier;
  private boolean processing = false;

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
          processing = false;
          final List<Integer> partitions = optClusterState.get().getPartitions();

          final Map<String, PartitionHealthStatus> partitionDetails =
              getPartitionsHealthStatus(partitions, optClusterState);

          if (!processing) {
            return Health.down().withDetails(partitionDetails).build();
          } else {
            return Health.up().withDetails(partitionDetails).build();
          }
        }
        return Health.down().build();
      }
    }
  }

  Map<String, PartitionHealthStatus> getPartitionsHealthStatus(
      final List<Integer> partitions, final Optional<BrokerClusterState> optClusterState) {
    final Map<String, PartitionHealthStatus> partitionDetails = new HashMap<>();
    partitions.forEach(
        partition -> {
          final int broker = optClusterState.get().getLeaderForPartition(partition);
          final PartitionHealthStatus partitionHealthStatus =
              optClusterState.get().getPartitionHealth(broker, partition);
          partitionDetails.put(String.format("Partition %d", partition), partitionHealthStatus);
          if (partitionHealthStatus == PartitionHealthStatus.HEALTHY) {
            processing = true;
          }
        });
    return partitionDetails;
  }
}
