/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the broker address a request should be sent to. Only {@link #leaderOrAnyRecovery} may
 * route to a node that has no elected leader but is in recovery mode — every other mode requires an
 * elected leader or picks a random broker.
 */
@NullMarked
final class BrokerAddressProvider implements Supplier<@Nullable String> {

  private final BrokerTopologyManager topologyManager;
  private final String partitionGroup;
  private final Function<BrokerClusterState, @Nullable BrokerMemberId> nodeIdSelector;

  private BrokerAddressProvider(
      final BrokerTopologyManager topologyManager,
      final String partitionGroup,
      final Function<BrokerClusterState, @Nullable BrokerMemberId> nodeIdSelector) {
    this.topologyManager = topologyManager;
    this.partitionGroup = partitionGroup;
    this.nodeIdSelector = nodeIdSelector;
  }

  /** Resolves a fixed broker, e.g. one explicitly requested by the caller. */
  static BrokerAddressProvider fixed(
      final BrokerTopologyManager topologyManager,
      final String partitionGroup,
      final Function<BrokerClusterState, @Nullable BrokerMemberId> nodeIdSelector) {
    return new BrokerAddressProvider(topologyManager, partitionGroup, nodeIdSelector);
  }

  /** Resolves a random broker of the partition group. */
  static BrokerAddressProvider randomBroker(
      final BrokerTopologyManager topologyManager, final String partitionGroup) {
    return new BrokerAddressProvider(
        topologyManager, partitionGroup, BrokerClusterState::getRandomBroker);
  }

  /** Resolves the elected leader of the given partition. */
  static BrokerAddressProvider leader(
      final BrokerTopologyManager topologyManager,
      final String partitionGroup,
      final int partitionId) {
    return new BrokerAddressProvider(
        topologyManager, partitionGroup, state -> state.getLeaderForPartition(partitionId));
  }

  /**
   * Resolves the elected leader of the given partition, falling back to a node in recovery mode
   * when no leader is elected. Only requests understood by the recovery-mode API should use this.
   */
  static BrokerAddressProvider leaderOrAnyRecovery(
      final BrokerTopologyManager topologyManager,
      final String partitionGroup,
      final int partitionId) {
    return new BrokerAddressProvider(
        topologyManager,
        partitionGroup,
        state -> {
          final var leader = state.getLeaderForPartition(partitionId);
          if (leader != null) {
            return leader;
          }
          return findRecoveringNode(topologyManager, state, partitionId);
        });
  }

  @Override
  public @Nullable String get() {
    final BrokerClusterState topology = topologyManager.getTopology(partitionGroup);
    if (topology != null) {
      final var brokerId = nodeIdSelector.apply(topology);
      if (brokerId != null) {
        return topology.getBrokerAddress(brokerId);
      }
    }
    return null;
  }

  /**
   * Returns the {@link BrokerMemberId} of a recovering inactive node for the given partition, or
   * {@code null} if no such node exists.
   */
  private static @Nullable BrokerMemberId findRecoveringNode(
      final BrokerTopologyManager topologyManager,
      final BrokerClusterState topology,
      final int partitionId) {
    final var clusterConfiguration = topologyManager.getClusterConfiguration();
    return topology.getInactiveNodesForPartition(partitionId).stream()
        .filter(
            node -> {
              final var member = clusterConfiguration.getMember(node.memberId());
              return member != null && member.state() == State.RECOVERING;
            })
        .findFirst()
        .orElse(null);
  }
}
