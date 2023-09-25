/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.ClusterTopologyAssert;
import io.camunda.zeebe.topology.state.MemberState.State;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClusterTopologyTest {
  @Test
  void canInitializeClusterWithPreExistingMembers() {
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
    ClusterTopologyAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(1, State.ACTIVE)
        .member(1)
        .hasPartitionWithState(1, PartitionState.active(1));
    ClusterTopologyAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(2, State.ACTIVE)
        .member(2)
        .hasPartitionWithState(2, PartitionState.active(1));
    ClusterTopologyAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(3, State.ACTIVE)
        .member(3)
        .hasPartitionWithState(3, PartitionState.active(1));
  }

  @Test
  void shouldMergeConcurrentUpdatesToMembers() {
    // given
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

    ClusterTopologyAssert.assertThatClusterTopology(mergedTopologyOne)
        .hasMemberWithState(1, State.ACTIVE)
        .member(1)
        .hasPartitionWithState(1, PartitionState.active(1));
    ClusterTopologyAssert.assertThatClusterTopology(mergedTopologyOne)
        .hasMemberWithState(2, State.ACTIVE)
        .member(2)
        .hasPartitionWithState(2, PartitionState.active(1));

    assertThat(mergedTopologyTwo).isEqualTo(mergedTopologyOne);
  }

  @Test
  void shouldAddANewMember() {
    // given
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
    ClusterTopologyAssert.assertThatClusterTopology(updateTopology)
        .describedAs("Update topology must have the new member and the old members")
        .hasMemberWithState(3, State.JOINING)
        .hasMemberWithState(1, State.ACTIVE)
        .hasMemberWithState(2, State.ACTIVE);

    // when
    topologyOnAnotherMember = topologyOnAnotherMember.merge(updateTopology);

    // then
    assertThat(topologyOnAnotherMember).isEqualTo(updateTopology);
  }

  @Test
  void shouldAdvanceClusterTopologyChanges() {
    // given
    final var initialTopology =
        ClusterTopology.init()
            .addMember(member(1), MemberState.uninitialized())
            .startTopologyChange(
                List.of(
                    new PartitionLeaveOperation(member(1), 1),
                    new PartitionLeaveOperation(member(2), 2)));

    // when
    final var updatedTopology =
        initialTopology.advanceTopologyChange(member(1), MemberState::toActive);

    // then
    ClusterTopologyAssert.assertThatClusterTopology(updatedTopology)
        .hasMemberWithState(1, State.ACTIVE)
        .hasPendingOperationsWithSize(1);
  }

  @Test
  void shouldIncrementVersionWhenChangeIsCompleted() {
    // given
    final var initialTopology =
        ClusterTopology.init()
            .addMember(member(1), MemberState.initializeAsActive(Map.of()))
            .startTopologyChange(List.of(new PartitionLeaveOperation(member(1), 1)));

    // when
    final var updatedTopology =
        initialTopology.advanceTopologyChange(member(1), MemberState::toLeft);

    // then
    ClusterTopologyAssert.assertThatClusterTopology(updatedTopology)
        .doesNotHaveMember(1)
        .hasPendingOperationsWithSize(0)
        .hasVersion(initialTopology.version() + 1);
  }

  @Test
  void shouldMergeClusterTopologyChanges() {
    final var initialTopology =
        ClusterTopology.init()
            .addMember(member(1), MemberState.uninitialized())
            .startTopologyChange(
                List.of(
                    new PartitionLeaveOperation(member(1), 1),
                    new PartitionLeaveOperation(member(2), 2)));

    // when
    final var updatedTopology =
        initialTopology.advanceTopologyChange(member(1), MemberState::toActive);

    final var mergedTopology = initialTopology.merge(updatedTopology);

    // then
    assertThat(mergedTopology).isEqualTo(updatedTopology);
  }

  private MemberId member(final int id) {
    return MemberId.from(Integer.toString(id));
  }
}
