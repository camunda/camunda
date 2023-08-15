/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.MemberState.State;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClusterTopologyTest {
  @Test
  void canInitializeClusterWithPreExistingMembers() {
    // given
    final var expectedTopology =
        new ClusterTopology(
            0,
            Map.of(
                member(1),
                withActivePartition(0, 1),
                member(2),
                withActivePartition(0, 2),
                member(3),
                withActivePartition(0, 3)),
            new ClusterChangePlan());
    // when
    final var topology =
        ClusterTopology.init()
            .addMember(
                member(1), MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))))
            .addMember(
                member(2), MemberState.initializeAsActive(Map.of(2, PartitionState.active(1))))
            .addMember(
                member(3), MemberState.initializeAsActive(Map.of(3, PartitionState.active(1))));

    // then
    assertThat(topology).isEqualTo(expectedTopology);
  }

  @Test
  void shouldMergeConcurrentUpdatesToMembers() {
    // given
    final var expectedMergedTopology =
        new ClusterTopology(
            0,
            Map.of(member(1), withActivePartition(1, 1), member(2), withActivePartition(1, 2)),
            new ClusterChangePlan());
    final var topology =
        ClusterTopology.init()
            .addMember(
                member(1), MemberState.initializeAsActive(Map.of(1, PartitionState.joining(1))))
            .addMember(
                member(2), MemberState.initializeAsActive(Map.of(2, PartitionState.joining(1))));

    // update topology in one member
    final var topologyInMemberOne =
        topology.updateMember(member(1), m -> m.updatePartition(1, PartitionState::toActive));
    // update topology in the other member concurrently
    final var topologyInMemberTwo =
        topology.updateMember(member(2), m -> m.updatePartition(2, PartitionState::toActive));

    // when
    final var mergedTopologyOne = topologyInMemberOne.merge(topologyInMemberTwo);
    final var mergedTopologyTwo = topologyInMemberTwo.merge(topologyInMemberOne);

    // then
    assertThat(mergedTopologyOne).isEqualTo(expectedMergedTopology);
    assertThat(mergedTopologyTwo).isEqualTo(expectedMergedTopology);
  }

  @Test
  void shouldAddANewMember() {
    // given
    final var expectedTopology =
        new ClusterTopology(
            0,
            Map.of(
                member(1),
                withActivePartition(0, 1),
                member(2),
                withActivePartition(0, 2),
                member(3),
                new MemberState(1, State.JOINING, Map.of())),
            new ClusterChangePlan());

    final var initialTopology =
        ClusterTopology.init()
            .addMember(
                member(1), MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))))
            .addMember(
                member(2), MemberState.initializeAsActive(Map.of(2, PartitionState.active(1))));

    var topologyOnAnotherMember = ClusterTopology.init().merge(initialTopology);

    // when
    final var updateTopology =
        initialTopology.addMember(member(3), MemberState.uninitialized().toJoining());

    // then
    assertThat(updateTopology).isEqualTo(expectedTopology);

    // when
    topologyOnAnotherMember = topologyOnAnotherMember.merge(updateTopology);

    // then
    assertThat(topologyOnAnotherMember).isEqualTo(expectedTopology);
  }

  private MemberId member(final int id) {
    return MemberId.from(Integer.toString(id));
  }

  private MemberState withActivePartition(final int version, final int partitionId) {
    return new MemberState(
        version,
        State.ACTIVE,
        Map.of(partitionId, new PartitionState(PartitionState.State.ACTIVE, 1)));
  }
}
