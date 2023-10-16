/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.serializer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.api.TopologyManagementRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.StatusCode;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossipState;
import io.camunda.zeebe.topology.protocol.Requests;
import io.camunda.zeebe.topology.protocol.Requests.ChangeStatus;
import io.camunda.zeebe.topology.protocol.Topology;
import io.camunda.zeebe.topology.protocol.Topology.MemberState;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ProtoBufSerializer implements ClusterTopologySerializer, TopologyRequestsSerializer {

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

  @Override
  public byte[] encode(final ClusterTopology clusterTopology) {
    return encodeClusterTopology(clusterTopology).toByteArray();
  }

  @Override
  public ClusterTopology decodeClusterTopology(final byte[] encodedClusterTopology) {
    try {
      final var topology = Topology.ClusterTopology.parseFrom(encodedClusterTopology);
      return decodeClusterTopology(topology);

    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  private io.camunda.zeebe.topology.state.ClusterTopology decodeClusterTopology(
      final Topology.ClusterTopology encodedClusterTopology) {

    final var members =
        encodedClusterTopology.getMembersMap().entrySet().stream()
            .map(e -> Map.entry(MemberId.from(e.getKey()), decodeMemberState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    final var changes = decodeChangePlan(encodedClusterTopology.getChanges());

    return new io.camunda.zeebe.topology.state.ClusterTopology(
        encodedClusterTopology.getVersion(), members, changes);
  }

  private Topology.ClusterTopology encodeClusterTopology(
      final io.camunda.zeebe.topology.state.ClusterTopology clusterTopology) {
    final var members =
        clusterTopology.members().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().id(), e -> encodeMemberState(e.getValue())));

    final var encodedChangePlan = encodeChangePlan(clusterTopology.changes());
    return Topology.ClusterTopology.newBuilder()
        .setVersion(clusterTopology.version())
        .putAllMembers(members)
        .setChanges(encodedChangePlan)
        .build();
  }

  private io.camunda.zeebe.topology.state.MemberState decodeMemberState(
      final Topology.MemberState memberState) {
    final var partitions =
        memberState.getPartitionsMap().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), decodePartitionState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    final Timestamp lastUpdated = memberState.getLastUpdated();
    return new io.camunda.zeebe.topology.state.MemberState(
        memberState.getVersion(),
        Instant.ofEpochSecond(lastUpdated.getSeconds(), lastUpdated.getNanos()),
        toMemberState(memberState.getState()),
        partitions);
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
    final Instant lastUpdated = memberState.lastUpdated();
    return MemberState.newBuilder()
        .setVersion(memberState.version())
        .setLastUpdated(
            Timestamp.newBuilder()
                .setSeconds(lastUpdated.getEpochSecond())
                .setNanos(lastUpdated.getNano())
                .build())
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

  private Topology.ClusterChangePlan encodeChangePlan(final ClusterChangePlan changes) {
    final var builder = Topology.ClusterChangePlan.newBuilder().setVersion(changes.version());
    changes
        .pendingOperations()
        .forEach(operation -> builder.addOperation(encodeOperation(operation)));
    return builder.build();
  }

  private Topology.TopologyChangeOperation encodeOperation(
      final io.camunda.zeebe.topology.state.TopologyChangeOperation operation) {
    final var builder =
        Topology.TopologyChangeOperation.newBuilder().setMemberId(operation.memberId().id());
    if (operation instanceof final PartitionJoinOperation joinOperation) {
      builder.setPartitionJoin(
          Topology.PartitionJoinOperation.newBuilder()
              .setPartitionId(joinOperation.partitionId())
              .setPriority(joinOperation.priority()));
    } else if (operation instanceof final PartitionLeaveOperation leaveOperation) {
      builder.setPartitionLeave(
          Topology.PartitionLeaveOperation.newBuilder()
              .setPartitionId(leaveOperation.partitionId()));
    } else if (operation instanceof MemberJoinOperation) {
      builder.setMemberJoin(Topology.MemberJoinOperation.newBuilder().build());
    } else if (operation instanceof MemberLeaveOperation) {
      builder.setMemberLeave(Topology.MemberLeaveOperation.newBuilder().build());
    } else {
      throw new IllegalArgumentException(
          "Unknown operation type: " + operation.getClass().getSimpleName());
    }
    return builder.build();
  }

  private ClusterChangePlan decodeChangePlan(final Topology.ClusterChangePlan clusterChangePlan) {

    final var version = clusterChangePlan.getVersion();
    final var pendingOperations =
        clusterChangePlan.getOperationList().stream().map(this::decodeOperation).toList();

    return new ClusterChangePlan(version, pendingOperations);
  }

  private TopologyChangeOperation decodeOperation(
      final Topology.TopologyChangeOperation topologyChangeOperation) {
    if (topologyChangeOperation.hasPartitionJoin()) {
      return new PartitionJoinOperation(
          MemberId.from(topologyChangeOperation.getMemberId()),
          topologyChangeOperation.getPartitionJoin().getPartitionId(),
          topologyChangeOperation.getPartitionJoin().getPriority());
    } else if (topologyChangeOperation.hasPartitionLeave()) {
      return new PartitionLeaveOperation(
          MemberId.from(topologyChangeOperation.getMemberId()),
          topologyChangeOperation.getPartitionLeave().getPartitionId());
    } else if (topologyChangeOperation.hasMemberJoin()) {
      return new MemberJoinOperation(MemberId.from(topologyChangeOperation.getMemberId()));
    } else if (topologyChangeOperation.hasMemberLeave()) {
      return new MemberLeaveOperation(MemberId.from(topologyChangeOperation.getMemberId()));
    } else {
      // If the node does not know of a type, the exception thrown will prevent
      // ClusterTopologyGossiper from processing the incoming topology. This helps to prevent any
      // incorrect or partial topology to be stored locally and later propagated to other nodes.
      // Ideally, it is better not to any cluster topology change operations execute during a
      // rolling update.
      throw new IllegalStateException("Unknown operation: " + topologyChangeOperation);
    }
  }

  @Override
  public byte[] encodeRequest(final TopologyManagementRequest topologyManagementRequest) {
    return switch (topologyManagementRequest) {
      case AddMembersRequest add -> Requests.AddMemberRequest.newBuilder()
          .addAllMemberIds(add.members().stream().map(MemberId::id).toList())
          .build()
          .toByteArray();
    };
  }

  @Override
  public AddMembersRequest decodeAddMembersRequest(final byte[] encodedState) {
    try {
      final var addMemberRequest = Requests.AddMemberRequest.parseFrom(encodedState);
      return new AddMembersRequest(
          addMemberRequest.getMemberIdsList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public byte[] encode(final TopologyChangeStatus topologyChangeStatus) {
    return Requests.TopologyChangeStatus.newBuilder()
        .setChangeId(topologyChangeStatus.changeId())
        .setStatus(fromTopologyChangeStatus(topologyChangeStatus.status()))
        .build()
        .toByteArray();
  }

  @Override
  public TopologyChangeStatus decodeTopologyChangeStatus(final byte[] encodedTopologyChangeStatus) {
    try {
      final var topologyChangeStatus =
          Requests.TopologyChangeStatus.parseFrom(encodedTopologyChangeStatus);
      return new TopologyChangeStatus(
          topologyChangeStatus.getChangeId(),
          toTopologyChangeStatus(topologyChangeStatus.getStatus()));
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  private StatusCode toTopologyChangeStatus(final ChangeStatus status) {
    return switch (status) {
      case IN_PROGRESS -> StatusCode.IN_PROGRESS;
      case COMPLETED -> StatusCode.COMPLETED;
      case FAILED -> StatusCode.FAILED;
      case UNRECOGNIZED, STATUS_UNKNOWN -> throw new IllegalStateException(
          "Unknown status: " + status);
    };
  }

  private ChangeStatus fromTopologyChangeStatus(final StatusCode status) {
    return switch (status) {
      case IN_PROGRESS -> ChangeStatus.IN_PROGRESS;
      case COMPLETED -> ChangeStatus.COMPLETED;
      case FAILED -> ChangeStatus.FAILED;
    };
  }
}
