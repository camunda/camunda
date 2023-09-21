/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.topology.ClusterTopologyAssert;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.MemberState.State;
import io.camunda.zeebe.topology.state.PartitionState;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TopologyUtilTest {

  private static final String GROUP_NAME = "test";

  @Test
  void shouldGenerateTopologyFromPartitionDistribution() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            PartitionId.from(GROUP_NAME, 1),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(0), 1, member(1), 2, member(2), 3),
            3,
            member(2));
    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            PartitionId.from(GROUP_NAME, 2),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(2), 1, member(1), 2, member(0), 3),
            3,
            member(0));

    final var partitionDistribution = Set.of(partitionTwo, partitionOne);

    // when
    final var topology = TopologyUtil.getClusterTopologyFrom(partitionDistribution);

    // then
    ClusterTopologyAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(0, State.ACTIVE)
        .member(0)
        .hasPartitionWithState(1, PartitionState.active(1))
        .hasPartitionWithState(2, PartitionState.active(3));

    ClusterTopologyAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(1, State.ACTIVE)
        .member(1)
        .hasPartitionWithState(1, PartitionState.active(2))
        .hasPartitionWithState(2, PartitionState.active(2));

    ClusterTopologyAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(2, State.ACTIVE)
        .member(2)
        .hasPartitionWithState(1, PartitionState.active(3))
        .hasPartitionWithState(2, PartitionState.active(1));
  }

  @Test
  void shouldGeneratePartitionDistributionFromTopology() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            PartitionId.from(GROUP_NAME, 1),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(0), 1, member(1), 2, member(2), 3),
            3,
            member(2));
    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            PartitionId.from(GROUP_NAME, 2),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(2), 1, member(1), 2, member(0), 3),
            3,
            member(0));

    final var expected = Set.of(partitionTwo, partitionOne);

    final ClusterTopology topology =
        ClusterTopology.init()
            .addMember(
                member(0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1), 2, PartitionState.active(3))))
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2), 2, PartitionState.active(2))))
            .addMember(
                member(2),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3), 2, PartitionState.active(1))));

    // when
    final var partitionDistribution =
        TopologyUtil.getPartitionDistributionFrom(topology, GROUP_NAME);

    // then
    assertThat(partitionDistribution).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldGeneratePartitionDistributionFromTopologyWithMemberWithNoPartitions() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            PartitionId.from(GROUP_NAME, 1),
            Set.of(member(1), member(0)),
            Map.of(member(0), 1, member(1), 2),
            2,
            member(1));

    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            PartitionId.from(GROUP_NAME, 2),
            Set.of(member(1), member(0)),
            Map.of(member(1), 2, member(0), 3),
            3,
            member(0));

    final var expected = Set.of(partitionTwo, partitionOne);

    final ClusterTopology topology =
        ClusterTopology.init()
            .addMember(
                member(0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1), 2, PartitionState.active(3))))
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2), 2, PartitionState.active(2))))
            .addMember(member(2), MemberState.initializeAsActive(Map.of()).toLeaving());

    // when
    final var partitionDistribution =
        TopologyUtil.getPartitionDistributionFrom(topology, GROUP_NAME);

    // then
    assertThat(partitionDistribution).containsExactlyInAnyOrderElementsOf(expected);
  }

  private MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
