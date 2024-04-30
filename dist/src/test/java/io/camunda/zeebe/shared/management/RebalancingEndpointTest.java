/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RebalancingEndpointTest {

  @Test
  void shouldRebalance() {
    // given
    final var partitions = List.of(1, 2, 3, 4, 5);
    final var client = setupBrokerClient(partitions);

    final var service = new RebalancingService(client);
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
