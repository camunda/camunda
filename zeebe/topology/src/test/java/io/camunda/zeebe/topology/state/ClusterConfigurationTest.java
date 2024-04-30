/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.ClusterConfigurationAssert;
import io.camunda.zeebe.topology.state.ClusterChangePlan.Status;
import io.camunda.zeebe.topology.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.MemberState.State;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClusterConfigurationTest {
  @Test
  void canInitializeClusterWithPreExistingMembers() {
    // when
    final var topology =
        ClusterConfiguration.init()
            .addMember(
                member(1), MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))))
            .addMember(
                member(2), MemberState.initializeAsActive(Map.of(2, PartitionState.active(1))))
            .addMember(
                member(3), MemberState.initializeAsActive(Map.of(3, PartitionState.active(1))));

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(1, State.ACTIVE)
        .member(1)
        .hasPartitionWithState(1, PartitionState.active(1));
    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(2, State.ACTIVE)
        .member(2)
        .hasPartitionWithState(2, PartitionState.active(1));
    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(3, State.ACTIVE)
        .member(3)
        .hasPartitionWithState(3, PartitionState.active(1));
  }

  @Test
  void canDetermineClusterSizePartitionAndReplicationFactor() {
    // when
    final var topology =
        ClusterConfiguration.init()
            .addMember(
                member(1), MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))))
            .addMember(
                member(2), MemberState.initializeAsActive(Map.of(1, PartitionState.active(2))))
            .addMember(
                member(3), MemberState.initializeAsActive(Map.of(1, PartitionState.active(3))));

    // then
    assertThat(topology.clusterSize()).isEqualTo(3);
    assertThat(topology.partitionCount()).isEqualTo(1);
    assertThat(topology.minReplicationFactor()).isEqualTo(3);
  }

  @Test
  void shouldMergeConcurrentUpdatesToMembers() {
    // given
    final var topology =
        ClusterConfiguration.init()
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

    ClusterConfigurationAssert.assertThatClusterTopology(mergedTopologyOne)
        .hasMemberWithState(1, State.ACTIVE)
        .member(1)
        .hasPartitionWithState(1, PartitionState.active(1));
    ClusterConfigurationAssert.assertThatClusterTopology(mergedTopologyOne)
        .hasMemberWithState(2, State.ACTIVE)
        .member(2)
        .hasPartitionWithState(2, PartitionState.active(1));

    assertThat(mergedTopologyTwo).isEqualTo(mergedTopologyOne);
  }

  @Test
  void shouldAddANewMember() {
    // given
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(
                member(1), MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))))
            .addMember(
                member(2), MemberState.initializeAsActive(Map.of(2, PartitionState.active(1))));

    var topologyOnAnotherMember = ClusterConfiguration.init().merge(initialTopology);

    // when
    final var updateTopology =
        initialTopology.addMember(member(3), MemberState.uninitialized().toJoining());

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(updateTopology)
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
        ClusterConfiguration.init()
            .addMember(member(1), MemberState.uninitialized())
            .startTopologyChange(
                List.of(
                    new PartitionLeaveOperation(member(1), 1),
                    new PartitionLeaveOperation(member(2), 2)));

    // when
    final var updatedTopology =
        initialTopology.advanceTopologyChange(
            t -> t.updateMember(member(1), MemberState::toActive));

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(updatedTopology)
        .hasMemberWithState(1, State.ACTIVE)
        .hasPendingOperationsWithSize(1);
  }

  @Test
  void shouldIncrementVersionWhenChangeIsCompleted() {
    // given
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(member(1), MemberState.initializeAsActive(Map.of()))
            .startTopologyChange(List.of(new PartitionLeaveOperation(member(1), 1)));

    // when
    final var updatedTopology =
        initialTopology.advanceTopologyChange(t -> t.updateMember(member(1), MemberState::toLeft));

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(updatedTopology)
        .doesNotHaveMember(1)
        .hasPendingOperationsWithSize(0)
        .hasVersion(initialTopology.version() + 1);
  }

  @Test
  void shouldMergeClusterTopologyChanges() {
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(member(1), MemberState.uninitialized())
            .startTopologyChange(
                List.of(
                    new PartitionLeaveOperation(member(1), 1),
                    new PartitionLeaveOperation(member(2), 2)));

    // when
    final var updatedTopology =
        initialTopology.advanceTopologyChange(
            t -> t.updateMember(member(1), MemberState::toActive));

    final var mergedTopology = initialTopology.merge(updatedTopology);

    // then
    assertThat(mergedTopology).isEqualTo(updatedTopology);
  }

  @Test
  void shouldAddCompletedClusterTopologyChanges() {
    // given
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(member(1), MemberState.initializeAsActive(Map.of()))
            .startTopologyChange(List.of(new PartitionJoinOperation(member(1), 1, 1)));
    final var changeId = initialTopology.pendingChanges().orElseThrow().id();

    // when
    final var finalTopology =
        initialTopology.advanceTopologyChange(
            t -> t.updateMember(member(1), m -> m.addPartition(1, PartitionState.active(1))));

    // then
    final var expected =
        new ClusterConfiguration(
            2,
            Map.of(member(1), MemberState.initializeAsActive(Map.of(1, PartitionState.active(1)))),
            Optional.of(
                new CompletedChange(changeId, Status.COMPLETED, Instant.now(), Instant.now())),
            Optional.empty());

    ClusterConfigurationAssert.assertThatClusterTopology(finalTopology).hasSameTopologyAs(expected);
  }

  private MemberId member(final int id) {
    return MemberId.from(Integer.toString(id));
  }
}
