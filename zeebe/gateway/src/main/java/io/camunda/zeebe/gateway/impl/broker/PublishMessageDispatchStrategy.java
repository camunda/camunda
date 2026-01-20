/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import static io.camunda.zeebe.protocol.impl.SubscriptionUtil.getSubscriptionPartitionId;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import java.util.Optional;

public final class PublishMessageDispatchStrategy implements RequestDispatchStrategy {

  private final String correlationKey;

  public PublishMessageDispatchStrategy(final String correlationKey) {
    this.correlationKey = correlationKey;
  }

  @Override
  public int determinePartition(
      final String partitionGroup, final BrokerTopologyManager topologyManager) {
    // TODO: Determine cluster config for partition group
    return topologyManager
        .getClusterConfiguration()
        .routingState()
        .map(this::fromRoutingState)
        .or(() -> Optional.ofNullable(topologyManager.getTopology()).map(this::fromTopology))
        .orElseThrow(
            () ->
                new NoTopologyAvailableException(
                    "Expected to pick partition for message with correlation key '%s', but no topology is available"
                        .formatted(correlationKey)));
  }

  public int fromRoutingState(final RoutingState routingState) {
    return switch (routingState.messageCorrelation()) {
      case HashMod(final int partitionCount) ->
          getSubscriptionPartitionId(wrapString(correlationKey), partitionCount);
    };
  }

  public int fromTopology(final BrokerClusterState topology) {
    final var partitionCount = topology.getPartitionsCount();
    if (partitionCount == 0) {
      throw new NoTopologyAvailableException(
          "Expected to pick partition for message with correlation key '%s', but topology contains no partitions"
              .formatted(correlationKey));
    }
    return getSubscriptionPartitionId(wrapString(correlationKey), partitionCount);
  }
}
