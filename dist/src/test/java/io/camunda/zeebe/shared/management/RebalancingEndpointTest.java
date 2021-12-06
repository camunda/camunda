/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.management.BrokerRebalancingService;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.management.GatewayRebalancingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class RebalancingEndpointTest {

  @Test
  void standaloneGatewaySendsRequestToAllPartitions() {
    // given
    final var partitions = List.of(1, 2, 3, 4, 5);
    final var client = setupBrokerClient(partitions);
    final var bridge = mock(SpringGatewayBridge.class);

    when(bridge.getBrokerClient()).thenReturn(Optional.of(client));

    final var service = new GatewayRebalancingService(bridge);
    final var endpoint = new RebalancingEndpoint(service);

    // when
    endpoint.rebalance();

    // then
    verify(client, times(partitions.size())).sendRequest(any());
  }

  @Test
  void embeddedGatewaySendsRequestToAllPartitions() {
    // given
    final var partitions = List.of(1, 2, 3, 4, 5);
    final var client = setupBrokerClient(partitions);

    final var bridge = mock(SpringBrokerBridge.class);

    when(bridge.getBrokerClient()).thenReturn(Optional.of(client));

    final var service = new BrokerRebalancingService(bridge);
    final var endpoint = new RebalancingEndpoint(service);

    // when
    endpoint.rebalance();

    // then
    verify(client, times(partitions.size())).sendRequest(any());
  }

  private BrokerClient setupBrokerClient(final List<Integer> partitions) {
    final var client = mock(BrokerClient.class);
    final var topology = mock(BrokerClusterState.class);
    final var topologyManager = mock(BrokerTopologyManager.class);

    when(topology.getPartitions()).thenReturn(partitions);
    when(topologyManager.getTopology()).thenReturn(topology);
    when(client.getTopologyManager()).thenReturn(topologyManager);
    return client;
  }
}
