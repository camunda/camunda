/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.service.TopologyServices.Broker;
import io.camunda.service.TopologyServices.ClusterStatus;
import io.camunda.service.TopologyServices.Health;
import io.camunda.service.TopologyServices.Partition;
import io.camunda.service.TopologyServices.Role;
import io.camunda.service.TopologyServices.Topology;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.util.VersionUtil;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

public class TopologyServiceTest {

  private BrokerClient brokerClient;
  private BrokerClusterState clusterState;
  private TopologyServices services;
  private BrokerTopologyManager topologyManager;

  @BeforeEach
  public void before() {
    brokerClient = mock(BrokerClient.class);
    clusterState = mock(BrokerClusterState.class);
    topologyManager = mock(BrokerTopologyManager.class);
    when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
    when(topologyManager.getTopology()).thenReturn(clusterState);
    services =
        new TopologyServices(
            brokerClient,
            mock(SecurityContextProvider.class),
            null,
            mock(ApiServicesExecutorProvider.class),
            null);
  }

  @Test
  void shouldReturnHealthyWhenAtLeastOnePartitionHasHealthyLeader() {
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

    // when
    final var status = services.getStatus().join();

    // then
    Assertions.assertThat(status).isEqualTo(ClusterStatus.HEALTHY);

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
  void shouldReturnUnhealthyWhenNoPartitionHasHealthyLeader(
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

    // when
    final var status = services.getStatus().join();

    // then
    Assertions.assertThat(status).isEqualTo(ClusterStatus.UNHEALTHY);

    verify(brokerClient).getTopologyManager();
    verify(topologyManager).getTopology();
    verify(clusterState).getPartitions();
    verify(clusterState).getLeaderForPartition(partitionId1);
    verify(clusterState).getPartitionHealth(leaderId1, partitionId1);
    verify(clusterState).getLeaderForPartition(partitionId2);
    verify(clusterState).getPartitionHealth(leaderId2, partitionId2);
  }

  @Test
  void shouldReturnUnhealthyWhenNoPartitionsExist() {
    // given
    when(clusterState.getPartitions()).thenReturn(List.of());

    // when
    final var status = services.getStatus().join();

    // then
    Assertions.assertThat(status).isEqualTo(ClusterStatus.UNHEALTHY);

    verify(brokerClient).getTopologyManager();
    verify(topologyManager).getTopology();
    verify(clusterState).getPartitions();
  }

  @Test
  void shouldReturnUnhealthyWhenNoTopologyExist() {
    // given
    when(topologyManager.getTopology()).thenReturn(null);

    // when
    final var status = services.getStatus().join();

    // then
    Assertions.assertThat(status).isEqualTo(ClusterStatus.UNHEALTHY);

    verify(brokerClient).getTopologyManager();
    verify(topologyManager).getTopology();
    verifyNoInteractions(clusterState);
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

    // when
    final var status = services.getStatus().join();

    // then
    Assertions.assertThat(status).isEqualTo(ClusterStatus.HEALTHY);

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

  @Test
  public void shouldGetTopology() {
    // given
    final var version = VersionUtil.getVersion();
    final var clusterId = "cluster-id";

    when(topologyManager.getTopology()).thenReturn(new TestBrokerClusterState(version, clusterId));

    final var expectedTopology =
        new Topology(
            List.of(
                new Broker(
                    0,
                    "localhost",
                    26501,
                    List.of(new Partition(1, Role.LEADER, Health.HEALTHY)),
                    version),
                new Broker(
                    1,
                    "localhost",
                    26502,
                    List.of(new Partition(1, Role.FOLLOWER, Health.HEALTHY)),
                    version),
                new Broker(
                    2,
                    "localhost",
                    26503,
                    List.of(new Partition(1, Role.INACTIVE, Health.UNHEALTHY)),
                    version)),
            "cluster-id",
            3,
            1,
            3,
            version,
            1L);

    // when
    final var topology = services.getTopology().join();

    // then
    Assertions.assertThat(topology).isEqualTo(expectedTopology);
  }

  @Test
  void shouldReturnEmptyTopology() {
    // given
    final var version = VersionUtil.getVersion();
    final var expectedTopology = new Topology(List.of(), null, null, null, null, version, null);
    Mockito.when(topologyManager.getTopology()).thenReturn(null);

    // when
    final var topology = services.getTopology().join();

    // then
    Assertions.assertThat(topology).isEqualTo(expectedTopology);
  }

  /**
   * Topology stub which returns a static topology with 3 brokers, 1 partition, replication factor
   * 3, where 0 is the leader (healthy), 1 is the follower (healthy), and 2 is inactive (unhealthy).
   */
  private record TestBrokerClusterState(String version, String clusterId)
      implements BrokerClusterState {

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public int getClusterSize() {
      return 3;
    }

    @Override
    public int getPartitionsCount() {
      return 1;
    }

    @Override
    public int getReplicationFactor() {
      return 3;
    }

    @Override
    public int getLeaderForPartition(final int partition) {
      return 0;
    }

    @Override
    public Set<Integer> getFollowersForPartition(final int partition) {
      return Set.of(1);
    }

    @Override
    public Set<Integer> getInactiveNodesForPartition(final int partition) {
      return Set.of(2);
    }

    @Override
    public int getRandomBroker() {
      return ThreadLocalRandom.current().nextInt(0, 3);
    }

    @Override
    public List<Integer> getPartitions() {
      return List.of(1);
    }

    @Override
    public List<Integer> getBrokers() {
      return List.of(0, 1, 2);
    }

    @Override
    public String getBrokerAddress(final int brokerId) {
      return "localhost:" + (26501 + brokerId);
    }

    @Override
    public String getBrokerVersion(final int brokerId) {
      return version;
    }

    @Override
    public PartitionHealthStatus getPartitionHealth(final int brokerId, final int partition) {
      if (partition != 1) {
        return PartitionHealthStatus.NULL_VAL;
      }

      return switch (brokerId) {
        case 0, 1 -> PartitionHealthStatus.HEALTHY;
        case 2 -> PartitionHealthStatus.UNHEALTHY;
        default -> PartitionHealthStatus.NULL_VAL;
      };
    }

    @Override
    public long getLastCompletedChangeId() {
      return 1;
    }

    @Override
    public String getClusterId() {
      return clusterId;
    }
  }
}
