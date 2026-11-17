/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.protocol.impl.encoding.AdminResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class FlowControlServiceImplTest {

  private static final String TENANT_A = "tenant-a";
  private static final BrokerMemberId LEADER = BrokerMemberId.from(0);
  private static final BrokerMemberId FOLLOWER = BrokerMemberId.from(1);

  private final BrokerClient client = mock(BrokerClient.class);
  private final BrokerTopologyManager topologyManager = mock(BrokerTopologyManager.class);
  private final BrokerClusterState topology = mock(BrokerClusterState.class);

  @Test
  void shouldReadFromThePartitionGroupOfThePhysicalTenant() {
    // given
    setupTopology(List.of(1, 2));
    final var service = new FlowControlServiceImpl(client);

    // when
    final var configuration = service.get(TENANT_A).join();

    // then - a bare partition id aliases across partition groups, so every request has to name the
    // group it addresses, or it resolves against the default one
    assertThat(configuration).containsOnlyKeys(1, 2);
    assertThat(capturedRequests())
        .extracting(BrokerRequest::getPartitionGroup)
        .containsOnly(TENANT_A);
  }

  @Test
  void shouldWriteToThePartitionGroupOfThePhysicalTenant() {
    // given
    setupTopology(List.of(1));
    when(topology.getFollowersForPartition(1)).thenReturn(Set.of(FOLLOWER));
    final var service = new FlowControlServiceImpl(client);

    // when
    service.set(new FlowControlCfg(), TENANT_A).join();

    // then - the update is broadcast to every member of the partition, and the read it answers with
    // is scoped the same way
    assertThat(capturedRequests())
        .hasSizeGreaterThan(1)
        .extracting(BrokerRequest::getPartitionGroup)
        .containsOnly(TENANT_A);
  }

  @Test
  void shouldFailWhenThePhysicalTenantHasNoLeaderForAPartition() {
    // given - a partition group that is configured but not led anywhere
    setupTopology(List.of(1));
    when(topology.getLeaderForPartition(1)).thenReturn(null);
    final var service = new FlowControlServiceImpl(client);

    // when / then - the error names the tenant, since the partition id alone does not identify it
    assertThatThrownBy(() -> service.get(TENANT_A).join())
        .isInstanceOf(CompletionException.class)
        .hasMessageContaining("No leader for partition 1 of physical tenant " + TENANT_A);
  }

  private void setupTopology(final List<Integer> partitions) {
    when(topology.getPartitions()).thenReturn(partitions);
    partitions.forEach(
        partition -> when(topology.getLeaderForPartition(partition)).thenReturn(LEADER));
    when(topologyManager.getTopology(TENANT_A)).thenReturn(topology);
    when(client.getTopologyManager()).thenReturn(topologyManager);

    final var response = mock(AdminResponse.class);
    when(response.getPayload()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
    doReturn(CompletableFuture.completedFuture(new BrokerResponse<>(response)))
        .when(client)
        .sendRequest(any());
  }

  private List<BrokerRequest> capturedRequests() {
    final ArgumentCaptor<BrokerRequest> captor = ArgumentCaptor.forClass(BrokerRequest.class);
    verify(client, atLeastOnce()).sendRequest(captor.capture());
    return captor.getAllValues();
  }
}
