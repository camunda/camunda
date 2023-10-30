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
import io.camunda.zeebe.topology.api.TopologyChangeResponse;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ScaleRequest;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossipState;
import io.camunda.zeebe.topology.protocol.Requests;
import io.camunda.zeebe.topology.protocol.Topology;
import io.camunda.zeebe.topology.protocol.Topology.CompletedChange;
import io.camunda.zeebe.topology.protocol.Topology.MemberState;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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

    final var members = decodeMemberStateMap(encodedClusterTopology.getMembersMap());

    final Optional<io.camunda.zeebe.topology.state.CompletedChange> completedChange =
        encodedClusterTopology.hasLastChange()
            ? Optional.of(decodeCompletedChange(encodedClusterTopology.getLastChange()))
            : Optional.empty();
    final Optional<ClusterChangePlan> currentChange =
        encodedClusterTopology.hasCurrentChange()
            ? Optional.of(decodeChangePlan(encodedClusterTopology.getCurrentChange()))
            : Optional.empty();

    return new io.camunda.zeebe.topology.state.ClusterTopology(
        encodedClusterTopology.getVersion(), members, completedChange, currentChange);
  }

  private Map<MemberId, io.camunda.zeebe.topology.state.MemberState> decodeMemberStateMap(
      final Map<String, MemberState> membersMap) {
    return membersMap.entrySet().stream()
        .map(e -> Map.entry(MemberId.from(e.getKey()), decodeMemberState(e.getValue())))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private Topology.ClusterTopology encodeClusterTopology(
      final io.camunda.zeebe.topology.state.ClusterTopology clusterTopology) {
    final var members = encodeMemberStateMap(clusterTopology.members());

    final var builder =
        Topology.ClusterTopology.newBuilder()
            .setVersion(clusterTopology.version())
            .putAllMembers(members);

    clusterTopology
        .lastChange()
        .ifPresent(lastChange -> builder.setLastChange(encodeCompletedChange(lastChange)));
    clusterTopology
        .pendingChanges()
        .ifPresent(changePlan -> builder.setCurrentChange(encodeChangePlan(changePlan)));

    return builder.build();
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
    final var builder =
        Topology.ClusterChangePlan.newBuilder()
            .setVersion(changes.version())
            .setId(changes.id())
            .setStatus(fromTopologyChangeStatus(changes.status()))
            .setStartedAt(
                Timestamp.newBuilder()
                    .setSeconds(changes.startedAt().getEpochSecond())
                    .setNanos(changes.startedAt().getNano())
                    .build());
    changes
        .pendingOperations()
        .forEach(operation -> builder.addPendingOperations(encodeOperation(operation)));
    changes
        .completedOperations()
        .forEach(operation -> builder.addCompletedOperations(encodeCompletedOperation(operation)));

    return builder.build();
  }

  private CompletedChange encodeCompletedChange(
      final io.camunda.zeebe.topology.state.CompletedChange completedChange) {
    final var builder = Topology.CompletedChange.newBuilder();
    builder
        .setId(completedChange.id())
        .setStatus(fromTopologyChangeStatus(completedChange.status()))
        .setCompletedAt(
            Timestamp.newBuilder()
                .setSeconds(completedChange.completedAt().getEpochSecond())
                .setNanos(completedChange.completedAt().getNano())
                .build())
        .setStartedAt(
            Timestamp.newBuilder()
                .setSeconds(completedChange.startedAt().getEpochSecond())
                .setNanos(completedChange.startedAt().getNano())
                .build());

    return builder.build();
  }

  private Topology.TopologyChangeOperation encodeOperation(
      final io.camunda.zeebe.topology.state.TopologyChangeOperation operation) {
    final var builder =
        Topology.TopologyChangeOperation.newBuilder().setMemberId(operation.memberId().id());
    switch (operation) {
      case final PartitionJoinOperation joinOperation -> builder.setPartitionJoin(
          Topology.PartitionJoinOperation.newBuilder()
              .setPartitionId(joinOperation.partitionId())
              .setPriority(joinOperation.priority()));
      case final PartitionLeaveOperation leaveOperation -> builder.setPartitionLeave(
          Topology.PartitionLeaveOperation.newBuilder()
              .setPartitionId(leaveOperation.partitionId()));
      case final MemberJoinOperation memberJoinOperation -> builder.setMemberJoin(
          Topology.MemberJoinOperation.newBuilder().build());
      case final MemberLeaveOperation memberLeaveOperation -> builder.setMemberLeave(
          Topology.MemberLeaveOperation.newBuilder().build());
      case final PartitionReconfigurePriorityOperation reconfigurePriorityOperation -> builder
          .setPartitionReconfigurePriority(
              Topology.PartitionReconfigurePriorityOperation.newBuilder()
                  .setPartitionId(reconfigurePriorityOperation.partitionId())
                  .setPriority(reconfigurePriorityOperation.priority())
                  .build());
      default -> throw new IllegalArgumentException(
          "Unknown operation type: " + operation.getClass().getSimpleName());
    }
    return builder.build();
  }

  private Topology.CompletedTopologyChangeOperation encodeCompletedOperation(
      final ClusterChangePlan.CompletedOperation completedOperation) {
    return Topology.CompletedTopologyChangeOperation.newBuilder()
        .setOperation(encodeOperation(completedOperation.operation()))
        .setCompletedAt(
            Timestamp.newBuilder()
                .setSeconds(completedOperation.completedAt().getEpochSecond())
                .setNanos(completedOperation.completedAt().getNano())
                .build())
        .build();
  }

  private ClusterChangePlan decodeChangePlan(final Topology.ClusterChangePlan clusterChangePlan) {

    final var version = clusterChangePlan.getVersion();
    final var pendingOperations =
        clusterChangePlan.getPendingOperationsList().stream()
            .map(this::decodeOperation)
            .collect(Collectors.toList());
    final var completedOperations =
        clusterChangePlan.getCompletedOperationsList().stream()
            .map(this::decodeCompletedOperation)
            .collect(Collectors.toList());

    return new ClusterChangePlan(
        clusterChangePlan.getId(),
        clusterChangePlan.getVersion(),
        toChangeStatus(clusterChangePlan.getStatus()),
        Instant.ofEpochSecond(
            clusterChangePlan.getStartedAt().getSeconds(),
            clusterChangePlan.getStartedAt().getNanos()),
        completedOperations,
        pendingOperations);
  }

  private io.camunda.zeebe.topology.state.CompletedChange decodeCompletedChange(
      final CompletedChange completedChange) {
    return new io.camunda.zeebe.topology.state.CompletedChange(
        completedChange.getId(),
        toChangeStatus(completedChange.getStatus()),
        Instant.ofEpochSecond(
            completedChange.getStartedAt().getSeconds(), completedChange.getStartedAt().getNanos()),
        Instant.ofEpochSecond(
            completedChange.getCompletedAt().getSeconds(),
            completedChange.getCompletedAt().getNanos()));
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
    } else if (topologyChangeOperation.hasPartitionReconfigurePriority()) {
      return new PartitionReconfigurePriorityOperation(
          MemberId.from(topologyChangeOperation.getMemberId()),
          topologyChangeOperation.getPartitionReconfigurePriority().getPartitionId(),
          topologyChangeOperation.getPartitionReconfigurePriority().getPriority());
    } else {
      // If the node does not know of a type, the exception thrown will prevent
      // ClusterTopologyGossiper from processing the incoming topology. This helps to prevent any
      // incorrect or partial topology to be stored locally and later propagated to other nodes.
      // Ideally, it is better not to any cluster topology change operations execute during a
      // rolling update.
      throw new IllegalStateException("Unknown operation: " + topologyChangeOperation);
    }
  }

  private CompletedOperation decodeCompletedOperation(
      final Topology.CompletedTopologyChangeOperation operation) {
    return new CompletedOperation(
        decodeOperation(operation.getOperation()),
        Instant.ofEpochSecond(operation.getCompletedAt().getSeconds()));
  }

  @Override
  public byte[] encodeAddMembersRequest(final AddMembersRequest req) {
    return Requests.AddMembersRequest.newBuilder()
        .addAllMemberIds(req.members().stream().map(MemberId::id).toList())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeRemoveMembersRequest(final RemoveMembersRequest req) {
    return Requests.RemoveMembersRequest.newBuilder()
        .addAllMemberIds(req.members().stream().map(MemberId::id).toList())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeJoinPartitionRequest(final JoinPartitionRequest req) {
    return Requests.JoinPartitionRequest.newBuilder()
        .setMemberId(req.memberId().id())
        .setPartitionId(req.partitionId())
        .setPriority(req.priority())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeLeavePartitionRequest(final LeavePartitionRequest req) {
    return Requests.LeavePartitionRequest.newBuilder()
        .setMemberId(req.memberId().id())
        .setPartitionId(req.partitionId())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeReassignPartitionsRequest(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    return Requests.ReassignAllPartitionsRequest.newBuilder()
        .addAllMemberIds(reassignPartitionsRequest.members().stream().map(MemberId::id).toList())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeScaleRequest(final ScaleRequest scaleRequest) {
    return Requests.ScaleRequest.newBuilder()
        .addAllMemberIds(scaleRequest.members().stream().map(MemberId::id).toList())
        .build()
        .toByteArray();
  }

  @Override
  public AddMembersRequest decodeAddMembersRequest(final byte[] encodedState) {
    try {
      final var addMemberRequest = Requests.AddMembersRequest.parseFrom(encodedState);
      return new AddMembersRequest(
          addMemberRequest.getMemberIdsList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public RemoveMembersRequest decodeRemoveMembersRequest(final byte[] encodedState) {
    try {
      final var removeMemberRequest = Requests.RemoveMembersRequest.parseFrom(encodedState);
      return new RemoveMembersRequest(
          removeMemberRequest.getMemberIdsList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public JoinPartitionRequest decodeJoinPartitionRequest(final byte[] encodedState) {
    try {
      final var joinPartitionRequest = Requests.JoinPartitionRequest.parseFrom(encodedState);
      return new JoinPartitionRequest(
          MemberId.from(joinPartitionRequest.getMemberId()),
          joinPartitionRequest.getPartitionId(),
          joinPartitionRequest.getPriority());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public LeavePartitionRequest decodeLeavePartitionRequest(final byte[] encodedState) {
    try {
      final var leavePartitionRequest = Requests.LeavePartitionRequest.parseFrom(encodedState);
      return new LeavePartitionRequest(
          MemberId.from(leavePartitionRequest.getMemberId()),
          leavePartitionRequest.getPartitionId());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public ReassignPartitionsRequest decodeReassignPartitionsRequest(final byte[] encodedState) {
    try {
      final var reassignPartitionsRequest =
          Requests.ReassignAllPartitionsRequest.parseFrom(encodedState);
      return new ReassignPartitionsRequest(
          reassignPartitionsRequest.getMemberIdsList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public ScaleRequest decodeScaleRequest(final byte[] encodedState) {
    try {
      final var scaleRequest = Requests.ScaleRequest.parseFrom(encodedState);
      return new ScaleRequest(
          scaleRequest.getMemberIdsList().stream().map(MemberId::from).collect(Collectors.toSet()));
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public byte[] encode(final TopologyChangeResponse topologyChangeResponse) {
    final var builder = Requests.TopologyChangeResponse.newBuilder();

    builder
        .setChangeId(topologyChangeResponse.changeId())
        .addAllPlannedChanges(
            topologyChangeResponse.plannedChanges().stream().map(this::encodeOperation).toList())
        .putAllCurrentTopology(encodeMemberStateMap(topologyChangeResponse.currentTopology()))
        .putAllExpectedTopology(encodeMemberStateMap(topologyChangeResponse.expectedTopology()));

    return builder.build().toByteArray();
  }

  @Override
  public TopologyChangeResponse decodeTopologyChangeResponse(
      final byte[] encodedTopologyChangeResponse) {
    try {
      final var topologyChangeResponse =
          Requests.TopologyChangeResponse.parseFrom(encodedTopologyChangeResponse);
      return new TopologyChangeResponse(
          topologyChangeResponse.getChangeId(),
          decodeMemberStateMap(topologyChangeResponse.getCurrentTopologyMap()),
          decodeMemberStateMap(topologyChangeResponse.getExpectedTopologyMap()),
          topologyChangeResponse.getPlannedChangesList().stream()
              .map(this::decodeOperation)
              .collect(Collectors.toList()));
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  private Map<String, MemberState> encodeMemberStateMap(
      final Map<MemberId, io.camunda.zeebe.topology.state.MemberState> topologyChangeResponse) {
    return topologyChangeResponse.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().id(), e -> encodeMemberState(e.getValue())));
  }

  private Topology.ChangeStatus fromTopologyChangeStatus(final ClusterChangePlan.Status status) {
    return switch (status) {
      case IN_PROGRESS -> Topology.ChangeStatus.IN_PROGRESS;
      case COMPLETED -> Topology.ChangeStatus.COMPLETED;
      case FAILED -> Topology.ChangeStatus.FAILED;
    };
  }

  private ClusterChangePlan.Status toChangeStatus(final Topology.ChangeStatus status) {
    return switch (status) {
      case IN_PROGRESS -> ClusterChangePlan.Status.IN_PROGRESS;
      case COMPLETED -> ClusterChangePlan.Status.COMPLETED;
      case FAILED -> ClusterChangePlan.Status.FAILED;
      default -> throw new IllegalStateException("Unknown status: " + status);
    };
  }
}
