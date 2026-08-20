/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class RebalancingEndpointTest {

  @Test
  void shouldRebalance() {
    // given
    final var partitions = List.of(1, 2, 3, 4, 5);
    final var client =
        setupBrokerClient(partitions, Set.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));

    final var service =
        new RebalancingService(client, () -> Set.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));
    final var endpoint = new RebalancingEndpoint(service);

    // when
    endpoint.rebalance();

    // then
    final var requestCaptor = ArgumentCaptor.forClass(BrokerRequest.class);
    verify(client, times(partitions.size())).sendRequest(requestCaptor.capture());
    assertThat(requestCaptor.getAllValues())
        .allSatisfy(
            request ->
                assertThat(request.getPartitionGroup())
                    .isEqualTo(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));
  }

  @Test
  void shouldRebalanceAllPartitionGroups() {
    // given
    final var partitions = List.of(1, 2, 3);
    final var physicalTenantIds = Set.of("default", "tenant-a", "tenant-b");
    final var client = setupBrokerClient(partitions, physicalTenantIds);

    final var service = new RebalancingService(client, () -> physicalTenantIds);
    final var endpoint = new RebalancingEndpoint(service);

    // when
    endpoint.rebalance();

    // then
    final var requestCaptor = ArgumentCaptor.forClass(BrokerRequest.class);
    verify(client, times(partitions.size() * physicalTenantIds.size()))
        .sendRequest(requestCaptor.capture());
    assertThat(requestCaptor.getAllValues())
        .extracting(BrokerRequest::getPartitionGroup)
        .containsExactlyInAnyOrder(
            "default",
            "default",
            "default",
            "tenant-a",
            "tenant-a",
            "tenant-a",
            "tenant-b",
            "tenant-b",
            "tenant-b");
  }

  private BrokerClient setupBrokerClient(
      final List<Integer> partitions, final Set<String> physicalTenantIds) {
    final var client = mock(BrokerClient.class);
    final var topology = mock(BrokerClusterState.class);
    final var topologyManager = mock(BrokerTopologyManager.class);

    when(topology.getPartitions()).thenReturn(partitions);
    physicalTenantIds.forEach(
        physicalTenantId ->
            when(topologyManager.getTopology(physicalTenantId)).thenReturn(topology));
    when(client.getTopologyManager()).thenReturn(topologyManager);
    return client;
  }
}
