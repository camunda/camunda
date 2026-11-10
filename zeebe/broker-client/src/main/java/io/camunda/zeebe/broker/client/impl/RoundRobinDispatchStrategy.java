/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import org.jspecify.annotations.Nullable;

/**
 * Return the next partition using a round-robin strategy, but skips the partitions where there is
 * no leader at the moment.
 *
 * <p>Round-robin state is kept per partition group: numeric partition IDs overlap across groups, so
 * sharing one offset or partition ring between groups would let one group's traffic skew another's
 * distribution.
 */
public final class RoundRobinDispatchStrategy implements RequestDispatchStrategy {

  private final ConcurrentMap<String, GroupState> groups = new ConcurrentHashMap<>();
  private final int initialOffset;
  private final BiPredicate<String, Integer> hasLeaderOutsideTopology;

  public RoundRobinDispatchStrategy() {
    this(0);
  }

  public RoundRobinDispatchStrategy(final int initialOffset) {
    this(initialOffset, (group, partition) -> false);
  }

  /**
   * @param hasLeaderOutsideTopology tells whether a partition of a group has a leader that the
   *     topology does not show, for example one that is recorded in the mailbox. Such a partition
   *     can take requests, so it is not skipped. Must not block.
   */
  public RoundRobinDispatchStrategy(
      final int initialOffset, final BiPredicate<String, Integer> hasLeaderOutsideTopology) {
    this.hasLeaderOutsideTopology = hasLeaderOutsideTopology;
    if (initialOffset < 0) {
      throw new IllegalArgumentException(
          "Expected initialOffset to be >= 0, but got %d".formatted(initialOffset));
    }
    this.initialOffset = initialOffset;
  }

  @Override
  public int determinePartition(
      final BrokerTopologyManager topologyManager, final String partitionGroup) {
    final BrokerClusterState topology = topologyManager.getTopology(partitionGroup);

    if (topology == null || !topology.isInitialized()) {
      return BrokerClusterState.PARTITION_ID_NULL;
    }

    final var state =
        groups.computeIfAbsent(partitionGroup, group -> new GroupState(initialOffset));
    final var partitions = updatePartitionRing(topologyManager, partitionGroup, topology, state);

    for (int i = 0; i < topology.getPartitionsCount(); i++) {
      final int partition = partitions.partitionAtOffset(state.offset().getAndIncrement());
      if (topology.getLeaderForPartition(partition) != null
          || hasLeaderOutsideTopology.test(partitionGroup, partition)) {
        return partition;
      }
    }

    return BrokerClusterState.PARTITION_ID_NULL;
  }

  /**
   * Updates the partition ring of the given group. This either initializes the partition ring to
   * span over all statically configured partitions when routing state is not available (i.e.
   * partition scaling is not enabled) or creates a new partition ring to span over the active
   * partitions from the latest routing state.
   */
  private PartitionRing updatePartitionRing(
      final BrokerTopologyManager topologyManager,
      final String partitionGroup,
      final BrokerClusterState topology,
      final GroupState state) {
    final PartitionGroupConfiguration partitionGroupConfiguration =
        topologyManager.getClusterConfiguration().partitionGroup(partitionGroup);
    final var routingState =
        partitionGroupConfiguration == null
            ? Optional.<RoutingState>empty()
            : partitionGroupConfiguration.routingState();
    final long expectedVersion =
        routingState.map(RoutingState::version).orElse(VersionedPartitionRing.NO_ROUTING_STATE);

    var currentValue = state.partitionRing().get();
    if (currentValue.version() >= expectedVersion) {
      return requireNonNull(currentValue.partitions());
    }

    final var newPartitionRing =
        routingState
            .map(RoutingState::requestHandling)
            .map(RequestHandling::activePartitions)
            .map(PartitionRing::of)
            .orElseGet(
                () ->
                    // the configured partition count is currently shared across all groups (see
                    // BrokerTopologyManagerImpl); the leader check in determinePartition skips any
                    // ring slots that don't exist in this group
                    PartitionRing.all(topology.getPartitionsCount()));
    final var newValue = new VersionedPartitionRing(expectedVersion, newPartitionRing);

    while (currentValue.version() < expectedVersion) {
      currentValue = state.partitionRing().compareAndExchange(currentValue, newValue);
    }

    return newPartitionRing;
  }

  record VersionedPartitionRing(long version, @Nullable PartitionRing partitions) {
    static final long NOT_INITIALIZED = -2;
    static final long NO_ROUTING_STATE = -1;

    private static VersionedPartitionRing uninitialized() {
      return new VersionedPartitionRing(NOT_INITIALIZED, null);
    }
  }

  private record GroupState(
      AtomicLong offset, AtomicReference<VersionedPartitionRing> partitionRing) {
    GroupState(final int initialOffset) {
      this(
          new AtomicLong(initialOffset),
          new AtomicReference<>(VersionedPartitionRing.uninitialized()));
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

    public int partitionAtOffset(final long offset) {
      return partitions[Math.floorMod(offset, partitions.length)];
    }
  }
}
