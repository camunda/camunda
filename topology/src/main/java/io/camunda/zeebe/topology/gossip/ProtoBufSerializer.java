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
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.PartitionState;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ProtoBufSerializer implements ClusterTopologyGossipSerializer {

  @Override
  public byte[] encode(final ClusterTopologyGossipState gossipState) {
    final var builder = Topology.GossipState.newBuilder();

    final ClusterTopology topologyToEncode = gossipState.getClusterTopology();
    if (topologyToEncode != null) {
      final Topology.ClusterTopology clusterTopology = encodeClusterTopology(topologyToEncode);
      builder.setClusterTopology(clusterTopology);
    }

    final var message = builder.build();
    return message.toByteArray();
  }

  @Override
  public ClusterTopologyGossipState decode(final byte[] encodedState) {
    final Topology.GossipState gossipState;

    try {
      gossipState = Topology.GossipState.parseFrom(encodedState);

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

  private Topology.ClusterTopology encodeClusterTopology(
      final io.camunda.zeebe.topology.state.ClusterTopology clusterTopology) {
    final var members =
        clusterTopology.members().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().id(), e -> encodeMemberState(e.getValue())));
    return Topology.ClusterTopology.newBuilder()
        .setVersion(clusterTopology.version())
        .putAllMembers(members)
        .build();
  }

  private io.camunda.zeebe.topology.state.ClusterTopology decodeClusterTopology(
      final Topology.ClusterTopology encodedClusterTopology) {

    final var members =
        encodedClusterTopology.getMembersMap().entrySet().stream()
            .map(e -> Map.entry(MemberId.from(e.getKey()), decodeMemberState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    return new io.camunda.zeebe.topology.state.ClusterTopology(
        encodedClusterTopology.getVersion(), members, ClusterChangePlan.empty());
  }

  private io.camunda.zeebe.topology.state.MemberState decodeMemberState(
      final Topology.MemberState memberState) {
    final var partitions =
        memberState.getPartitionsMap().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), decodePartitionState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    return new io.camunda.zeebe.topology.state.MemberState(
        memberState.getVersion(), toMemberState(memberState.getState()), partitions);
  }

  private io.camunda.zeebe.topology.state.PartitionState decodePartitionState(
      final Topology.PartitionState partitionState) {
    return new io.camunda.zeebe.topology.state.PartitionState(
        toPartitionState(partitionState.getState()), partitionState.getPriority());
  }

  private Topology.MemberState encodeMemberState(
      final io.camunda.zeebe.topology.state.MemberState memberState) {
    final var partitions =
        memberState.partitions().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), encodePartitions(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    return Topology.MemberState.newBuilder()
        .setVersion(memberState.version())
        .setState(toSerializedState(memberState.state()))
        .putAllPartitions(partitions)
        .build();
  }

  private Topology.PartitionState encodePartitions(final PartitionState partitionState) {
    return Topology.PartitionState.newBuilder()
        .setState(toSerializedState(partitionState.state()))
        .setPriority(partitionState.priority())
        .build();
  }

  private Topology.State toSerializedState(
      final io.camunda.zeebe.topology.state.MemberState.State state) {
    return switch (state) {
      case UNINITIALIZED -> Topology.State.UNKNOWN;
      case ACTIVE -> Topology.State.ACTIVE;
      case JOINING -> Topology.State.JOINING;
      case LEAVING -> Topology.State.LEAVING;
      case LEFT -> Topology.State.LEFT;
    };
  }

  private io.camunda.zeebe.topology.state.MemberState.State toMemberState(
      final Topology.State state) {
    return switch (state) {
      case UNRECOGNIZED, UNKNOWN -> io.camunda.zeebe.topology.state.MemberState.State.UNINITIALIZED;
      case ACTIVE -> io.camunda.zeebe.topology.state.MemberState.State.ACTIVE;
      case JOINING -> io.camunda.zeebe.topology.state.MemberState.State.JOINING;
      case LEAVING -> io.camunda.zeebe.topology.state.MemberState.State.LEAVING;
      case LEFT -> io.camunda.zeebe.topology.state.MemberState.State.LEFT;
    };
  }

  private io.camunda.zeebe.topology.state.PartitionState.State toPartitionState(
      final Topology.State state) {
    return switch (state) {
      case UNRECOGNIZED, UNKNOWN, LEFT -> PartitionState.State.UNKNOWN;
      case ACTIVE -> PartitionState.State.ACTIVE;
      case JOINING -> PartitionState.State.JOINING;
      case LEAVING -> PartitionState.State.LEAVING;
    };
  }

  private Topology.State toSerializedState(
      final io.camunda.zeebe.topology.state.PartitionState.State state) {
    return switch (state) {
      case UNKNOWN -> Topology.State.UNKNOWN;
      case ACTIVE -> Topology.State.ACTIVE;
      case JOINING -> Topology.State.JOINING;
      case LEAVING -> Topology.State.LEAVING;
    };
  }
}
