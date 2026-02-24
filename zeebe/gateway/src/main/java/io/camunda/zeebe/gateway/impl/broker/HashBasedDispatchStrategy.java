/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import static io.camunda.zeebe.protocol.impl.PartitionUtil.getPartitionId;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import java.util.Optional;

/**
 * A dispatch strategy that deterministically routes a request to a partition based on a hash of the
 * given key. This ensures that requests with the same key are always routed to the same partition,
 * enabling per-partition uniqueness validation.
 *
 * <p>This strategy is used for message correlation (by correlation key) and process instance
 * creation (by business ID).
 */
public final class HashBasedDispatchStrategy implements RequestDispatchStrategy {

  private final String key;
  private final String keyDescription;

  /**
   * Creates a new hash-based dispatch strategy.
   *
   * @param key the key to hash for partition selection
   * @param keyDescription a human-readable description of the key (e.g., "correlation key",
   *     "business id") used in error messages
   */
  public HashBasedDispatchStrategy(final String key, final String keyDescription) {
    this.key = key;
    this.keyDescription = keyDescription;
  }

  @Override
  public int determinePartition(final BrokerTopologyManager topologyManager) {
    return topologyManager
        .getClusterConfiguration()
        .routingState()
        .map(this::fromRoutingState)
        .or(() -> Optional.ofNullable(topologyManager.getTopology()).map(this::fromTopology))
        .orElseThrow(
            () ->
                new NoTopologyAvailableException(
                    "Expected to pick partition for request with %s '%s', but no topology is available"
                        .formatted(keyDescription, key)));
  }

  private int fromRoutingState(final RoutingState routingState) {
    return switch (routingState.messageCorrelation()) {
      case HashMod(final int partitionCount) -> getPartitionId(wrapString(key), partitionCount);
    };
  }

  private int fromTopology(final BrokerClusterState topology) {
    final var partitionCount = topology.getPartitionsCount();
    if (partitionCount == 0) {
      throw new NoTopologyAvailableException(
          "Expected to pick partition for request with %s '%s', but topology contains no partitions"
              .formatted(keyDescription, key));
    }
    return getPartitionId(wrapString(key), partitionCount);
  }
}
