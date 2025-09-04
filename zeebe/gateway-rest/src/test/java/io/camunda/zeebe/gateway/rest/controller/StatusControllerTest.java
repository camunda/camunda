/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(StatusController.class)
class StatusControllerTest extends RestControllerTest {

  static final String STATUS_URL = "/v2/status";

  @MockitoBean BrokerClient brokerClient;
  @MockitoBean BrokerTopologyManager topologyManager;
  @Mock BrokerClusterState clusterState;

  @BeforeEach
  void setUp() {
    when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
    when(topologyManager.getTopology()).thenReturn(clusterState);
  }

  @Test
  void shouldReturnNoContentWhenAtLeastOnePartitionHasHealthyLeader() {
    // given
    final int partitionId1 = 1;
    final int partitionId2 = 2;
    final int leaderId1 = 1;
    final int leaderId2 = 2;

    when(clusterState.getPartitions()).thenReturn(List.of(partitionId1, partitionId2));
    when(clusterState.getLeaderForPartition(partitionId1)).thenReturn(leaderId1);
    when(clusterState.getLeaderForPartition(partitionId2)).thenReturn(leaderId2);
    when(clusterState.getPartitionHealth(leaderId1, partitionId1))
        .thenReturn(PartitionHealthStatus.HEALTHY);
    when(clusterState.getPartitionHealth(leaderId2, partitionId2))
        .thenReturn(PartitionHealthStatus.UNHEALTHY);

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    verify(brokerClient).getTopologyManager();
    verify(topologyManager).getTopology();
    verify(clusterState).getPartitions();
    verify(clusterState).getLeaderForPartition(partitionId1);
    verify(clusterState).getPartitionHealth(leaderId1, partitionId1);
  }

  @ParameterizedTest
  @EnumSource(
      value = PartitionHealthStatus.class,
      names = {"HEALTHY"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldReturnServiceUnavailableWhenNoPartitionHasHealthyLeader(
      final PartitionHealthStatus unhealthyStatus) {
    // given
    final int partitionId1 = 1;
    final int partitionId2 = 2;
    final int leaderId1 = 1;
    final int leaderId2 = 2;

    when(clusterState.getPartitions()).thenReturn(List.of(partitionId1, partitionId2));
    when(clusterState.getLeaderForPartition(partitionId1)).thenReturn(leaderId1);
    when(clusterState.getLeaderForPartition(partitionId2)).thenReturn(leaderId2);
    when(clusterState.getPartitionHealth(leaderId1, partitionId1)).thenReturn(unhealthyStatus);
    when(clusterState.getPartitionHealth(leaderId2, partitionId2)).thenReturn(unhealthyStatus);

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(SERVICE_UNAVAILABLE)
        .expectBody()
        .isEmpty();

    verify(brokerClient).getTopologyManager();
    verify(topologyManager).getTopology();
    verify(clusterState).getPartitions();
    verify(clusterState).getLeaderForPartition(partitionId1);
    verify(clusterState).getPartitionHealth(leaderId1, partitionId1);
    verify(clusterState).getLeaderForPartition(partitionId2);
    verify(clusterState).getPartitionHealth(leaderId2, partitionId2);
  }

  @Test
  void shouldReturnServiceUnavailableWhenNoPartitionsExist() {
    // given
    when(clusterState.getPartitions()).thenReturn(List.of());

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(SERVICE_UNAVAILABLE)
        .expectBody()
        .isEmpty();

    verify(brokerClient).getTopologyManager();
    verify(topologyManager).getTopology();
    verify(clusterState).getPartitions();
  }

  @Test
  void shouldHandleMixedPartitionHealthStatuses() {
    // given
    final int partitionId1 = 1;
    final int partitionId2 = 2;
    final int partitionId3 = 3;
    final int leaderId1 = 1;
    final int leaderId2 = 2;
    final int leaderId3 = 3;

    when(clusterState.getPartitions())
        .thenReturn(List.of(partitionId1, partitionId2, partitionId3));
    when(clusterState.getLeaderForPartition(partitionId1)).thenReturn(leaderId1);
    when(clusterState.getLeaderForPartition(partitionId2)).thenReturn(leaderId2);
    when(clusterState.getLeaderForPartition(partitionId3)).thenReturn(leaderId3);
    when(clusterState.getPartitionHealth(leaderId1, partitionId1))
        .thenReturn(PartitionHealthStatus.DEAD);
    when(clusterState.getPartitionHealth(leaderId2, partitionId2))
        .thenReturn(PartitionHealthStatus.UNHEALTHY);
    when(clusterState.getPartitionHealth(leaderId3, partitionId3))
        .thenReturn(PartitionHealthStatus.HEALTHY);

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    verify(brokerClient).getTopologyManager();
    verify(topologyManager).getTopology();
    verify(clusterState).getPartitions();
    verify(clusterState).getLeaderForPartition(partitionId1);
    verify(clusterState).getPartitionHealth(leaderId1, partitionId1);
    verify(clusterState).getLeaderForPartition(partitionId2);
    verify(clusterState).getPartitionHealth(leaderId2, partitionId2);
    verify(clusterState).getLeaderForPartition(partitionId3);
    verify(clusterState).getPartitionHealth(leaderId3, partitionId3);
  }
}
