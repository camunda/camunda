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
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
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
    final var leader = partitionLeaders.currentLeader("tenant-a", 3);

    // then
    assertThat(leader).contains(MemberId.from("2"));
  }

  @Test
  void shouldReportNoLeaderForAPartitionTheTopologyHasNoneFor() {
    // given
    when(topologyManager.getTopology("tenant-a")).thenReturn(topology);
    when(topology.getLeaderForPartition(3)).thenReturn(null);

    // when
    final var leader = partitionLeaders.currentLeader("tenant-a", 3);

    // then
    assertThat(leader).isEmpty();
  }
}
