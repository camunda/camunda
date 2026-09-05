/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientTopologyImpl;
import org.junit.jupiter.api.Test;

final class TopologyPartitionLeadersTest {

  private final BrokerClusterState topology = mock(BrokerClusterState.class);
  private final BrokerTopologyManager topologyManager = mock(BrokerTopologyManager.class);
  private final TopologyPartitionLeaders partitionLeaders =
      new TopologyPartitionLeaders(topologyManager);

  @Test
  void shouldReportTheLeaderTheTopologyKnows() {
    // given
    when(topologyManager.getTopology("tenant-a")).thenReturn(topology);
    when(topology.getLeaderForPartition(3)).thenReturn(BrokerMemberId.from("2"));

    // when
    final var leader = partitionLeaders.forGroup("tenant-a").currentLeader(3);

    // then
    assertThat(leader).contains(MemberId.from("2"));
  }

  @Test
  void shouldReportNoLeaderForAPartitionTheKnownTopologyHasNoneFor() {
    // given
    when(topologyManager.getTopology("tenant-a")).thenReturn(topology);
    when(topology.getLeaderForPartition(3)).thenReturn(null);

    // when
    final var leader = partitionLeaders.forGroup("tenant-a").currentLeader(3);

    // then
    assertThat(leader).isEmpty();
  }

  @Test
  void shouldReportNoLeaderWhenTheTopologyIsNotYetKnown() {
    // given
    when(topologyManager.getTopology("tenant-a"))
        .thenReturn(BrokerClientTopologyImpl.uninitialized());

    // when
    final var leader = partitionLeaders.forGroup("tenant-a").currentLeader(3);

    // then
    assertThat(leader).isEmpty();
  }

  @Test
  void shouldObtainTheTopologyOnceForAllLookupsOnTheSameGroupView() {
    // given
    when(topologyManager.getTopology("tenant-a")).thenReturn(topology);
    when(topology.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from("1"));
    when(topology.getLeaderForPartition(2)).thenReturn(BrokerMemberId.from("2"));

    // when
    final var groupLeaders = partitionLeaders.forGroup("tenant-a");
    groupLeaders.currentLeader(1);
    groupLeaders.currentLeader(2);

    // then
    verify(topologyManager, times(1)).getTopology("tenant-a");
  }
}
