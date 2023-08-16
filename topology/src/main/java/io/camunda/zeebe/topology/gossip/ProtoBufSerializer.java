/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.gossip;

import com.google.protobuf.InvalidProtocolBufferException;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.protocol.Topology;
import io.camunda.zeebe.topology.protocol.Topology.MClusterTopology;
import io.camunda.zeebe.topology.protocol.Topology.MGossipState;
import io.camunda.zeebe.topology.protocol.Topology.MMemberState;
import io.camunda.zeebe.topology.protocol.Topology.MPartitionState;
import io.camunda.zeebe.topology.protocol.Topology.MState;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.PartitionState;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ProtoBufSerializer implements ClusterTopologyGossipSerializer {

  @Override
  public byte[] encode(final ClusterTopologyGossipState gossipState) {
    final var builder = MGossipState.newBuilder();

    final ClusterTopology topologyToEncode = gossipState.getClusterTopology();
    if (topologyToEncode != null) {
      final Topology.MClusterTopology clusterTopology = encodeClusterTopology(topologyToEncode);
      builder.setClusterTopology(clusterTopology);
    }

    final var message = builder.build();
    return message.toByteArray();
  }

  @Override
  public ClusterTopologyGossipState decode(final byte[] encodedState) {
    final MGossipState gossipState;

    try {
      gossipState = MGossipState.parseFrom(encodedState);

    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
    final ClusterTopologyGossipState clusterTopologyGossipState = new ClusterTopologyGossipState();

    if (gossipState.hasClusterTopology()) {
      clusterTopologyGossipState.setClusterTopology(
          decodeClusterTopology(gossipState.getClusterTopology()));
    }
    return clusterTopologyGossipState;
  }

  private MClusterTopology encodeClusterTopology(
      final io.camunda.zeebe.topology.state.ClusterTopology clusterTopology) {
    final var members =
        clusterTopology.members().entrySet().stream()
            .map(e -> Map.entry(Integer.valueOf(e.getKey().id()), encodeMemberState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    return MClusterTopology.newBuilder()
        .setVersion(clusterTopology.version())
        .putAllMembers(members)
        .build();
  }

  private io.camunda.zeebe.topology.state.ClusterTopology decodeClusterTopology(
      final MClusterTopology encodedClusterTopology) {

    final var members =
        encodedClusterTopology.getMembersMap().entrySet().stream()
            .map(
                e ->
                    Map.entry(
                        MemberId.from(String.valueOf(e.getKey())), decodeMemberState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    return new io.camunda.zeebe.topology.state.ClusterTopology(
        encodedClusterTopology.getVersion(), members, ClusterChangePlan.empty());
  }

  private io.camunda.zeebe.topology.state.MemberState decodeMemberState(
      final MMemberState memberState) {
    final var partitions =
        memberState.getPartitionsMap().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), decodePartitionState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    return new io.camunda.zeebe.topology.state.MemberState(
        memberState.getVersion(), toMemberState(memberState.getState()), partitions);
  }

  private io.camunda.zeebe.topology.state.PartitionState decodePartitionState(
      final MPartitionState partitionState) {
    return new io.camunda.zeebe.topology.state.PartitionState(
        toPartitionState(partitionState.getState()), partitionState.getPriority());
  }

  private MMemberState encodeMemberState(
      final io.camunda.zeebe.topology.state.MemberState memberState) {
    final var partitions =
        memberState.partitions().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), encodePartitions(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    return MMemberState.newBuilder()
        .setVersion(memberState.version())
        .setState(toSerializedState(memberState.state()))
        .putAllPartitions(partitions)
        .build();
  }

  private MPartitionState encodePartitions(final PartitionState partitionState) {
    return MPartitionState.newBuilder()
        .setState(toSerializedState(partitionState.state()))
        .setPriority(partitionState.priority())
        .build();
  }

  private MState toSerializedState(final io.camunda.zeebe.topology.state.MemberState.State state) {
    return switch (state) {
      case UNINITIALIZED -> MState.UNKNOWN;
      case ACTIVE -> MState.ACTIVE;
      case JOINING -> MState.JOINING;
      case LEAVING -> MState.LEAVING;
      case LEFT -> MState.LEFT;
    };
  }

  private io.camunda.zeebe.topology.state.MemberState.State toMemberState(final MState state) {
    return switch (state) {
      case UNRECOGNIZED, UNKNOWN -> io.camunda.zeebe.topology.state.MemberState.State.UNINITIALIZED;
      case ACTIVE -> io.camunda.zeebe.topology.state.MemberState.State.ACTIVE;
      case JOINING -> io.camunda.zeebe.topology.state.MemberState.State.JOINING;
      case LEAVING -> io.camunda.zeebe.topology.state.MemberState.State.LEAVING;
      case LEFT -> io.camunda.zeebe.topology.state.MemberState.State.LEFT;
    };
  }

  private io.camunda.zeebe.topology.state.PartitionState.State toPartitionState(
      final MState state) {
    return switch (state) {
      case UNRECOGNIZED, UNKNOWN, LEFT -> PartitionState.State.UNKNOWN;
      case ACTIVE -> PartitionState.State.ACTIVE;
      case JOINING -> PartitionState.State.JOINING;
      case LEAVING -> PartitionState.State.LEAVING;
    };
  }

  private MState toSerializedState(
      final io.camunda.zeebe.topology.state.PartitionState.State state) {
    return switch (state) {
      case UNKNOWN -> MState.UNKNOWN;
      case ACTIVE -> MState.ACTIVE;
      case JOINING -> MState.JOINING;
      case LEAVING -> MState.LEAVING;
    };
  }
}
