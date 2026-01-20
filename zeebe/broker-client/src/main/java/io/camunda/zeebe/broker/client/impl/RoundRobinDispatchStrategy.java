/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Return the next partition using a round-robin strategy, but skips the partitions where there is
 * no leader at the moment.
 */
public final class RoundRobinDispatchStrategy implements RequestDispatchStrategy {

  /** Holds the current partition ring. Starts off uninitialized and is updated on every request. */
  private final AtomicReference<VersionedPartitionRing> partitionRing =
      new AtomicReference<>(VersionedPartitionRing.uninitialized());

  private final AtomicInteger offset = new AtomicInteger(0);

  @Override
  public int determinePartition(
      final String partitionGroup, final BrokerTopologyManager topologyManager) {
    final BrokerClusterState topology = topologyManager.getTopology();

    if (topology == null || !topology.isInitialized()) {
      return BrokerClusterState.PARTITION_ID_NULL;
    }

    final var partitions = updatePartitionRing(topologyManager);

    for (int i = 0; i < topology.getPartitionsCount(); i++) {
      final int partition = partitions.partitionAtOffset(offset.getAndIncrement());
      if (topology.getLeaderForPartition(new PartitionId(partitionGroup, partition))
          != BrokerClusterState.NODE_ID_NULL) {
        return partition;
      }
    }

    return BrokerClusterState.PARTITION_ID_NULL;
  }

  /**
   * Updates the partition ring. This either initializes the partition ring to span over all
   * statically configured partitions when routing state is not available (i.e. partition scaling is
   * not enabled) or creates a new partition ring to span over the active partitions from the latest
   * routing state.
   */
  private PartitionRing updatePartitionRing(final BrokerTopologyManager topologyManager) {
    final var routingState = topologyManager.getClusterConfiguration().routingState();
    final long expectedVersion =
        routingState.map(RoutingState::version).orElse(VersionedPartitionRing.NO_ROUTING_STATE);

    var currentValue = partitionRing.get();
    if (currentValue.version() >= expectedVersion) {
      return currentValue.partitions();
    }

    final var newPartitionRing =
        routingState
            .map(RoutingState::requestHandling)
            .map(RequestHandling::activePartitions)
            .map(PartitionRing::of)
            .orElseGet(() -> PartitionRing.all(topologyManager.getTopology().getPartitionsCount()));
    final var newValue = new VersionedPartitionRing(expectedVersion, newPartitionRing);

    while (currentValue.version() < expectedVersion) {
      currentValue = partitionRing.compareAndExchange(currentValue, newValue);
    }

    return newPartitionRing;
  }

  record VersionedPartitionRing(long version, PartitionRing partitions) {
    static final long NOT_INITIALIZED = -2;
    static final long NO_ROUTING_STATE = -1;

    private static VersionedPartitionRing uninitialized() {
      return new VersionedPartitionRing(NOT_INITIALIZED, null);
    }
  }

  private record PartitionRing(int[] partitions) {
    PartitionRing {
      if (partitions.length == 0) {
        throw new IllegalArgumentException("Partitions must not be empty");
      }
    }

    static PartitionRing all(final int partitionCount) {
      final var partitions = new int[partitionCount];
      for (int i = 0; i < partitionCount; i++) {
        partitions[i] = i + 1;
      }
      return new PartitionRing(partitions);
    }

    static PartitionRing of(final Set<Integer> partitions) {
      final var sorted = partitions.stream().sorted().mapToInt(Integer::intValue).toArray();
      return new PartitionRing(sorted);
    }

    public int partitionAtOffset(final int offset) {
      return partitions[offset % partitions.length];
    }
  }
}
