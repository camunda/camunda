/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossipState;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class ProtoBufSerializerTest {

  final ProtoBufSerializer protoBufSerializer = new ProtoBufSerializer();

  @ParameterizedTest
  @MethodSource("provideClusterTopologies")
  void shouldEncodeAndDecode(final ClusterTopology initialClusterTopology) {
    // given
    final ClusterTopologyGossipState gossipState = new ClusterTopologyGossipState();
    gossipState.setClusterTopology(initialClusterTopology);

    // when
    final var decodedState = protoBufSerializer.decode(protoBufSerializer.encode(gossipState));

    // then
    assertThat(decodedState.getClusterTopology())
        .describedAs("Decoded clusterTopology must be equal to initial one")
        .isEqualTo(initialClusterTopology);
  }

  @Test
  void shouldEncodeAndDecodeClusterTopology() {
    // given
    final var initialClusterTopology = topologyWithTwoMembers();

    // when
    final var decodedClusterTopology =
        protoBufSerializer.decodeClusterTopology(protoBufSerializer.encode(initialClusterTopology));

    // then
    assertThat(decodedClusterTopology)
        .describedAs("Decoded clusterTopology must be equal to initial one")
        .isEqualTo(initialClusterTopology);
  }

  @Test
  void shouldEncodeAndDecodeAddMembersRequest() {
    // given
    final var addMembersRequest =
        new AddMembersRequest(Set.of(MemberId.from("1"), MemberId.from("2")));

    // when
    final var encodedRequest = protoBufSerializer.encode(addMembersRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeAddMembersRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(addMembersRequest);
  }

  private static Stream<ClusterTopology> provideClusterTopologies() {
    return Stream.of(
        topologyWithOneMemberNoPartitions(),
        topologyWithOneJoiningMember(),
        topologyWithOneLeavingMember(),
        topologyWithOneLeftMember(),
        topologyWithOneMemberOneActivePartition(),
        topologyWithOneMemberOneLeavingPartition(),
        topologyWithOneMemberOneJoiningPartition(),
        topologyWithOneMemberTwoPartitions(),
        topologyWithTwoMembers(),
        topologyWithClusterChangePlan(),
        topologyWithClusterChangePlanWithMemberOperations());
  }

  private static ClusterTopology topologyWithOneMemberNoPartitions() {
    return ClusterTopology.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));
  }

  private static ClusterTopology topologyWithOneJoiningMember() {
    return ClusterTopology.init()
        .addMember(MemberId.from("1"), MemberState.uninitialized().toJoining());
  }

  private static ClusterTopology topologyWithOneLeavingMember() {
    return ClusterTopology.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()).toLeaving());
  }

  private static ClusterTopology topologyWithOneLeftMember() {
    return ClusterTopology.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()).toLeft());
  }

  private static ClusterTopology topologyWithOneMemberOneActivePartition() {
    return ClusterTopology.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));
  }

  private static ClusterTopology topologyWithOneMemberOneLeavingPartition() {
    return ClusterTopology.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(Map.of(1, PartitionState.active(1).toLeaving())));
  }

  private static ClusterTopology topologyWithOneMemberOneJoiningPartition() {
    return ClusterTopology.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(Map.of(1, PartitionState.joining(1))));
  }

  private static ClusterTopology topologyWithOneMemberTwoPartitions() {
    return ClusterTopology.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(
                Map.of(1, PartitionState.active(1), 2, PartitionState.active(2).toLeaving())));
  }

  private static ClusterTopology topologyWithTwoMembers() {
    return ClusterTopology.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(
                Map.of(1, PartitionState.joining(1), 2, PartitionState.active(2))))
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()).toLeaving());
  }

  private static ClusterTopology topologyWithClusterChangePlan() {
    final List<TopologyChangeOperation> changes =
        List.of(
            new PartitionLeaveOperation(MemberId.from("1"), 1),
            new PartitionJoinOperation(MemberId.from("2"), 2, 5));
    return ClusterTopology.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
        .startTopologyChange(changes);
  }

  private static ClusterTopology topologyWithClusterChangePlanWithMemberOperations() {
    final List<TopologyChangeOperation> changes =
        List.of(
            new MemberJoinOperation(MemberId.from("2")),
            new MemberLeaveOperation(MemberId.from("1")));
    return ClusterTopology.init()
        .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
        .startTopologyChange(changes);
  }
}
