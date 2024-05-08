/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class PublishMessageDispatchStrategy implements RequestDispatchStrategy {

  private final String correlationKey;

  public PublishMessageDispatchStrategy(final String correlationKey) {
    this.correlationKey = correlationKey;
  }

  @Override
  public int determinePartition(final BrokerTopologyManager topologyManager) {
    final var topology = topologyManager.getTopology();
    if (topology == null || topology.getPartitionsCount() == 0) {
      throw new NoTopologyAvailableException(
          String.format(
              "Expected to pick partition for message with correlation key '%s', but no topology is available",
              correlationKey));
    }

    final int partitionsCount = topology.getPartitionsCount();
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(correlationKey), partitionsCount);
  }
}
