/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossipState;
import io.camunda.zeebe.dynamic.config.protocol.Requests;
import io.camunda.zeebe.dynamic.config.protocol.Requests.ErrorCode;
import io.camunda.zeebe.dynamic.config.protocol.Requests.Response;
import io.camunda.zeebe.dynamic.config.protocol.Requests.TopologyChangeResponse.Builder;
import io.camunda.zeebe.dynamic.config.protocol.Topology;
import io.camunda.zeebe.dynamic.config.protocol.Topology.ChangeStatus;
import io.camunda.zeebe.dynamic.config.protocol.Topology.CompletedChange;
import io.camunda.zeebe.dynamic.config.protocol.Topology.PartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.*;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.util.Either;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ProtoBufSerializer
    implements ClusterConfigurationSerializer, ClusterConfigurationRequestsSerializer {

  @Override
  public byte[] encode(final ClusterConfigurationGossipState gossipState) {
    final var builder = Topology.GossipState.newBuilder();

    final ClusterConfiguration topologyToEncode = gossipState.getClusterConfiguration();
    if (topologyToEncode != null) {
      final Topology.ClusterTopology clusterTopology = encodeClusterTopology(topologyToEncode);
      builder.setClusterTopology(clusterTopology);
    }

    final var message = builder.build();
    return message.toByteArray();
  }

  @Override
  public ClusterConfigurationGossipState decode(final byte[] encodedState) {
    final Topology.GossipState gossipState;

    try {
      gossipState = Topology.GossipState.parseFrom(encodedState);
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
    final ClusterConfigurationGossipState clusterConfigurationGossipState =
        new ClusterConfigurationGossipState();

    if (gossipState.hasClusterTopology()) {
      try {
        clusterConfigurationGossipState.setClusterConfiguration(
            decodeClusterTopology(gossipState.getClusterTopology()));
      } catch (final Exception e) {
        throw new DecodingFailed(
            "Cluster topology could not be deserialized from gossiped state: %s"
                .formatted(gossipState),
            e);
      }
    }
    return clusterConfigurationGossipState;
  }

  @Override
  public byte[] encode(final ClusterConfiguration clusterConfiguration) {
    return encodeClusterTopology(clusterConfiguration).toByteArray();
  }

  @Override
  public ClusterConfiguration decodeClusterTopology(
      final byte[] encodedClusterTopology, final int offset, final int length) {
    try {
      final var topology =
          Topology.ClusterTopology.parseFrom(
              ByteBuffer.wrap(encodedClusterTopology, offset, length));
      return decodeClusterTopology(topology);

    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  private ClusterConfiguration decodeClusterTopology(
      final Topology.ClusterTopology encodedClusterTopology) {

    final var members = decodeMemberStateMap(encodedClusterTopology.getMembersMap());

    final Optional<io.camunda.zeebe.dynamic.config.state.CompletedChange> completedChange =
        encodedClusterTopology.hasLastChange()
            ? Optional.of(decodeCompletedChange(encodedClusterTopology.getLastChange()))
            : Optional.empty();
    final Optional<ClusterChangePlan> currentChange =
        encodedClusterTopology.hasCurrentChange()
            ? Optional.of(decodeChangePlan(encodedClusterTopology.getCurrentChange()))
            : Optional.empty();

    final Optional<RoutingState> routingState =
        encodedClusterTopology.hasRoutingState()
            ? decodeRoutingState(encodedClusterTopology.getRoutingState())
            : Optional.empty();

    return new ClusterConfiguration(
        encodedClusterTopology.getVersion(), members, completedChange, currentChange, routingState);
  }

  private Map<MemberId, io.camunda.zeebe.dynamic.config.state.MemberState> decodeMemberStateMap(
      final Map<String, Topology.MemberState> membersMap) {
    return membersMap.entrySet().stream()
        .map(e -> Map.entry(MemberId.from(e.getKey()), decodeMemberState(e.getValue())))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private Topology.ClusterTopology encodeClusterTopology(
      final ClusterConfiguration clusterConfiguration) {
    final var members = encodeMemberStateMap(clusterConfiguration.members());

    final var builder =
        Topology.ClusterTopology.newBuilder()
            .setVersion(clusterConfiguration.version())
            .putAllMembers(members);

    clusterConfiguration
        .lastChange()
        .ifPresent(lastChange -> builder.setLastChange(encodeCompletedChange(lastChange)));
    clusterConfiguration
        .pendingChanges()
        .ifPresent(changePlan -> builder.setCurrentChange(encodeChangePlan(changePlan)));
    clusterConfiguration
        .routingState()
        .ifPresent(routingState -> builder.setRoutingState(encodeRoutingState(routingState)));

    return builder.build();
  }

  private MemberState decodeMemberState(final Topology.MemberState memberState) {
    final var partitions =
        memberState.getPartitionsMap().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), decodePartitionState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    final Timestamp lastUpdated = memberState.getLastUpdated();
    return new MemberState(
        memberState.getVersion(),
        Instant.ofEpochSecond(lastUpdated.getSeconds(), lastUpdated.getNanos()),
        toMemberState(memberState.getState()),
        partitions);
  }

  private PartitionState decodePartitionState(final Topology.PartitionState partitionState) {
    if (partitionState.hasConfig()) {
      return new PartitionState(
          toPartitionState(partitionState.getState()),
          partitionState.getPriority(),
          decodePartitionConfig(partitionState.getConfig()));
    } else {
      return new PartitionState(
          toPartitionState(partitionState.getState()),
          partitionState.getPriority(),
          DynamicPartitionConfig.uninitialized());
    }
  }

  private DynamicPartitionConfig decodePartitionConfig(final PartitionConfig config) {
    return new DynamicPartitionConfig(decodeExportingConfig(config.getExporting()));
  }

  private ExportersConfig decodeExportingConfig(final Topology.ExportersConfig exporting) {
    return new ExportersConfig(
        exporting.getExportersMap().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), decodeExporterState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
  }

  private ExporterState decodeExporterState(final Topology.ExporterState value) {
    final Optional<String> initializeFrom =
        value.hasInitializedFrom() ? Optional.of(value.getInitializedFrom()) : Optional.empty();
    return switch (value.getState()) {
      case ENABLED ->
          new ExporterState(
              value.getMetadataVersion(), ExporterState.State.ENABLED, initializeFrom);
      case DISABLED ->
          new ExporterState(
              value.getMetadataVersion(), ExporterState.State.DISABLED, initializeFrom);
      case CONFIG_NOT_FOUND ->
          new ExporterState(
              value.getMetadataVersion(), ExporterState.State.CONFIG_NOT_FOUND, initializeFrom);
      case UNRECOGNIZED, ENABLED_DISBALED_UNKNOWN ->
          throw new IllegalStateException("Unknown exporter state " + value.getState());
    };
  }

  private Topology.MemberState encodeMemberState(final MemberState memberState) {
    final var partitions =
        memberState.partitions().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), encodePartitions(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    final Instant lastUpdated = memberState.lastUpdated();
    return Topology.MemberState.newBuilder()
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
    final var builder =
        Topology.PartitionState.newBuilder()
            .setState(toSerializedState(partitionState.state()))
            .setPriority(partitionState.priority());

    if (partitionState.config().isInitialized()) {
      // Do not encode if the config is uninitialized. This is required to handle rolling upgrade to
      // 8.6
      builder.setConfig(encodePartitionConfig(partitionState.config()));
    }
    return builder.build();
  }

  private PartitionConfig encodePartitionConfig(final DynamicPartitionConfig config) {
    return PartitionConfig.newBuilder()
        .setExporting(encodeExportingConfig(config.exporting()))
        .build();
  }

  private Topology.ExportersConfig encodeExportingConfig(final ExportersConfig exporting) {
    return Topology.ExportersConfig.newBuilder()
        .putAllExporters(
            exporting.exporters().entrySet().stream()
                .map(e -> Map.entry(e.getKey(), encodeExporterState(e.getValue())))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)))
        .build();
  }

  private Topology.ExporterState encodeExporterState(final ExporterState value) {
    final var state =
        switch (value.state()) {
          case ENABLED -> Topology.ExporterStateEnum.ENABLED;
          case DISABLED -> Topology.ExporterStateEnum.DISABLED;
          case CONFIG_NOT_FOUND -> Topology.ExporterStateEnum.CONFIG_NOT_FOUND;
        };
    final var builder =
        Topology.ExporterState.newBuilder()
            .setState(state)
            .setMetadataVersion(value.metadataVersion());
    value.initializedFrom().ifPresent(builder::setInitializedFrom);
    return builder.build();
  }

  private Topology.State toSerializedState(final MemberState.State state) {
    return switch (state) {
      case UNINITIALIZED -> Topology.State.UNKNOWN;
      case ACTIVE -> Topology.State.ACTIVE;
      case JOINING -> Topology.State.JOINING;
      case LEAVING -> Topology.State.LEAVING;
      case LEFT -> Topology.State.LEFT;
    };
  }

  private MemberState.State toMemberState(final Topology.State state) {
    return switch (state) {
      case UNRECOGNIZED, UNKNOWN -> MemberState.State.UNINITIALIZED;
      case ACTIVE -> MemberState.State.ACTIVE;
      case JOINING -> MemberState.State.JOINING;
      case LEAVING -> MemberState.State.LEAVING;
      case LEFT -> MemberState.State.LEFT;
      case BOOTSTRAPPING ->
          throw new IllegalStateException("Member cannot be in BOOTSTRAPPING state");
    };
  }

  private PartitionState.State toPartitionState(final Topology.State state) {
    return switch (state) {
      case UNRECOGNIZED, UNKNOWN, LEFT -> PartitionState.State.UNKNOWN;
      case ACTIVE -> PartitionState.State.ACTIVE;
      case JOINING -> PartitionState.State.JOINING;
      case LEAVING -> PartitionState.State.LEAVING;
      case BOOTSTRAPPING -> PartitionState.State.BOOTSTRAPPING;
    };
  }

  private Topology.State toSerializedState(final PartitionState.State state) {
    return switch (state) {
      case UNKNOWN -> Topology.State.UNKNOWN;
      case ACTIVE -> Topology.State.ACTIVE;
      case JOINING -> Topology.State.JOINING;
      case LEAVING -> Topology.State.LEAVING;
      case BOOTSTRAPPING -> Topology.State.BOOTSTRAPPING;
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
      final io.camunda.zeebe.dynamic.config.state.CompletedChange completedChange) {
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
      final ClusterConfigurationChangeOperation operation) {
    final var builder =
        Topology.TopologyChangeOperation.newBuilder().setMemberId(operation.memberId().id());
    switch (operation) {
      case final PartitionJoinOperation joinOperation ->
          builder.setPartitionJoin(
              Topology.PartitionJoinOperation.newBuilder()
                  .setPartitionId(joinOperation.partitionId())
                  .setPriority(joinOperation.priority()));
      case final PartitionLeaveOperation leaveOperation ->
          builder.setPartitionLeave(
              Topology.PartitionLeaveOperation.newBuilder()
                  .setPartitionId(leaveOperation.partitionId())
                  .setMinimumAllowedReplicas(leaveOperation.minimumAllowedReplicas()));
      case final MemberJoinOperation memberJoinOperation ->
          builder.setMemberJoin(Topology.MemberJoinOperation.newBuilder().build());
      case final MemberLeaveOperation memberLeaveOperation ->
          builder.setMemberLeave(Topology.MemberLeaveOperation.newBuilder().build());
      case final PartitionReconfigurePriorityOperation reconfigurePriorityOperation ->
          builder.setPartitionReconfigurePriority(
              Topology.PartitionReconfigurePriorityOperation.newBuilder()
                  .setPartitionId(reconfigurePriorityOperation.partitionId())
                  .setPriority(reconfigurePriorityOperation.priority())
                  .build());
      case final PartitionForceReconfigureOperation forceReconfigureOperation ->
          builder.setPartitionForceReconfigure(
              Topology.PartitionForceReconfigureOperation.newBuilder()
                  .setPartitionId(forceReconfigureOperation.partitionId())
                  .addAllMembers(
                      forceReconfigureOperation.members().stream().map(MemberId::id).toList())
                  .build());
      case final MemberRemoveOperation memberRemoveOperation ->
          builder.setMemberRemove(
              Topology.MemberRemoveOperation.newBuilder()
                  .setMemberToRemove(memberRemoveOperation.memberToRemove().id())
                  .build());
      case final PartitionDisableExporterOperation disableExporterOperation ->
          builder.setPartitionDisableExporter(
              Topology.PartitionDisableExporterOperation.newBuilder()
                  .setPartitionId(disableExporterOperation.partitionId())
                  .setExporterId(disableExporterOperation.exporterId())
                  .build());
      case final PartitionDeleteExporterOperation deleteExporterOperation ->
          builder.setPartitionDeleteExporter(
              Topology.PartitionDeleteExporterOperation.newBuilder()
                  .setPartitionId(deleteExporterOperation.partitionId())
                  .setExporterId(deleteExporterOperation.exporterId())
                  .build());
      case final PartitionEnableExporterOperation enableExporterOperation ->
          builder.setPartitionEnableExporter(
              encodeEnabledExporterOperation(enableExporterOperation));
      case final PartitionBootstrapOperation bootstrapOperation ->
          builder.setPartitionBootstrap(encodePartitionBootstrapOperation(bootstrapOperation));
      case final DeleteHistoryOperation deleteHistoryOperation ->
          builder.setDeleteHistory(Topology.DeleteHistoryOperation.newBuilder().build());
      case StartPartitionScaleUp(final var ignoredMemberId, final var desiredPartitionCount) ->
          builder.setInitiateScaleUpPartitions(
              Topology.StartPartitionScaleUpOperation.newBuilder()
                  .setDesiredPartitionCount(desiredPartitionCount)
                  .build());
      case final AwaitRedistributionCompletion msg ->
          builder.setAwaitRedistributionCompletion(
              Topology.AwaitRedistributionCompletion.newBuilder()
                  .setDesiredPartitionCount(msg.desiredPartitionCount())
                  .addAllPartitionsToRedistribute(msg.partitionsToRedistribute())
                  .build());
      case final AwaitRelocationCompletion msg ->
          builder.setAwaitRelocationCompletion(
              Topology.AwaitRelocationCompletion.newBuilder()
                  .setDesiredPartitionCount(msg.desiredPartitionCount())
                  .addAllPartitionsToRelocate(msg.partitionsToRelocate())
                  .build());
      case final UpdateRoutingState msg -> {
        final var b = Topology.UpdateRoutingState.newBuilder();
        msg.routingState().ifPresent(s -> b.setRoutingState(encodeRoutingState(s)));
        builder.setUpdateRoutingState(b);
      }
    }
    return builder.build();
  }

  private Topology.PartitionBootstrapOperation encodePartitionBootstrapOperation(
      final PartitionBootstrapOperation bootstrapOperation) {
    final var builder =
        Topology.PartitionBootstrapOperation.newBuilder()
            .setPartitionId(bootstrapOperation.partitionId())
            .setPriority(bootstrapOperation.priority())
            .setInitializeFromConfig(bootstrapOperation.initializeFromSnapshot());
    bootstrapOperation
        .config()
        .ifPresent(config -> builder.setConfig(encodePartitionConfig(config)));
    return builder.build();
  }

  private Topology.PartitionEnableExporterOperation encodeEnabledExporterOperation(
      final PartitionChangeOperation.PartitionEnableExporterOperation enableExporterOperation) {
    final var enableExporterOperationBuilder =
        Topology.PartitionEnableExporterOperation.newBuilder()
            .setPartitionId(enableExporterOperation.partitionId())
            .setExporterId(enableExporterOperation.exporterId());
    enableExporterOperation
        .initializeFrom()
        .ifPresent(enableExporterOperationBuilder::setInitializeFrom);
    return enableExporterOperationBuilder.build();
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
    final var pendingOperations =
        clusterChangePlan.getPendingOperationsList().stream().map(this::decodeOperation).toList();
    final var completedOperations =
        clusterChangePlan.getCompletedOperationsList().stream()
            .map(this::decodeCompletedOperation)
            .toList();

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

  private Optional<RoutingState> decodeRoutingState(final Topology.RoutingState routingState) {
    if (routingState.equals(Topology.RoutingState.getDefaultInstance())) {
      return Optional.empty();
    } else {
      return Optional.of(
          new RoutingState(
              routingState.getVersion(),
              decodeRequestHandling(routingState.getRequestHandling()),
              decodeMessageCorrelation(routingState.getMessageCorrelation())));
    }
  }

  private RequestHandling decodeRequestHandling(final Topology.RequestHandling requestHandling) {
    return switch (requestHandling.getStrategyCase()) {
      case ALLPARTITIONS ->
          new RequestHandling.AllPartitions(requestHandling.getAllPartitions().getPartitionCount());
      case ACTIVEPARTITIONS ->
          new RequestHandling.ActivePartitions(
              requestHandling.getActivePartitions().getBasePartitionCount(),
              new TreeSet<>(
                  requestHandling.getActivePartitions().getAdditionalActivePartitionsList()),
              new TreeSet<>(requestHandling.getActivePartitions().getInactivePartitionsList()));
      case STRATEGY_NOT_SET -> throw new IllegalArgumentException("Unknown request handling type");
    };
  }

  private MessageCorrelation decodeMessageCorrelation(
      final Topology.MessageCorrelation messageCorrelation) {
    return switch (messageCorrelation.getCorrelationCase()) {
      case HASHMOD ->
          new MessageCorrelation.HashMod(messageCorrelation.getHashMod().getPartitionCount());
      case CORRELATION_NOT_SET ->
          throw new IllegalArgumentException("Unknown message correlation type");
    };
  }

  private Topology.RoutingState encodeRoutingState(final RoutingState routingState) {
    return Topology.RoutingState.newBuilder()
        .setVersion(routingState.version())
        .setRequestHandling(encodeRequestHandling(routingState.requestHandling()))
        .setMessageCorrelation(encodeMessageCorrelation(routingState.messageCorrelation()))
        .build();
  }

  private Topology.RequestHandling encodeRequestHandling(final RequestHandling requestHandling) {
    return switch (requestHandling) {
      case RequestHandling.ActivePartitions(
              final var basePartitionCount,
              final var additionalActivePartitions,
              final var inactivePartitions) ->
          Topology.RequestHandling.newBuilder()
              .setActivePartitions(
                  Topology.RequestHandling.ActivePartitions.newBuilder()
                      .setBasePartitionCount(basePartitionCount)
                      .addAllAdditionalActivePartitions(additionalActivePartitions)
                      .addAllInactivePartitions(inactivePartitions)
                      .build())
              .build();
      case RequestHandling.AllPartitions(final var partitionCount) ->
          Topology.RequestHandling.newBuilder()
              .setAllPartitions(
                  Topology.RequestHandling.AllPartitions.newBuilder()
                      .setPartitionCount(partitionCount)
                      .build())
              .build();
    };
  }

  private Topology.MessageCorrelation encodeMessageCorrelation(
      final MessageCorrelation correlation) {
    return switch (correlation) {
      case MessageCorrelation.HashMod(final var partitionCount) ->
          Topology.MessageCorrelation.newBuilder()
              .setHashMod(
                  Topology.MessageCorrelation.HashMod.newBuilder()
                      .setPartitionCount(partitionCount))
              .build();
    };
  }

  private io.camunda.zeebe.dynamic.config.state.CompletedChange decodeCompletedChange(
      final CompletedChange completedChange) {
    return new io.camunda.zeebe.dynamic.config.state.CompletedChange(
        completedChange.getId(),
        toChangeStatus(completedChange.getStatus()),
        Instant.ofEpochSecond(
            completedChange.getStartedAt().getSeconds(), completedChange.getStartedAt().getNanos()),
        Instant.ofEpochSecond(
            completedChange.getCompletedAt().getSeconds(),
            completedChange.getCompletedAt().getNanos()));
  }

  private ClusterConfigurationChangeOperation decodeOperation(
      final Topology.TopologyChangeOperation topologyChangeOperation) {
    final var memberId = MemberId.from(topologyChangeOperation.getMemberId());
    if (topologyChangeOperation.hasPartitionJoin()) {
      return new PartitionJoinOperation(
          memberId,
          topologyChangeOperation.getPartitionJoin().getPartitionId(),
          topologyChangeOperation.getPartitionJoin().getPriority());
    } else if (topologyChangeOperation.hasPartitionLeave()) {
      return new PartitionLeaveOperation(
          memberId,
          topologyChangeOperation.getPartitionLeave().getPartitionId(),
          topologyChangeOperation.getPartitionLeave().getMinimumAllowedReplicas());
    } else if (topologyChangeOperation.hasMemberJoin()) {
      return new MemberJoinOperation(memberId);
    } else if (topologyChangeOperation.hasMemberLeave()) {
      return new MemberLeaveOperation(memberId);
    } else if (topologyChangeOperation.hasPartitionReconfigurePriority()) {
      return new PartitionReconfigurePriorityOperation(
          memberId,
          topologyChangeOperation.getPartitionReconfigurePriority().getPartitionId(),
          topologyChangeOperation.getPartitionReconfigurePriority().getPriority());
    } else if (topologyChangeOperation.hasPartitionForceReconfigure()) {
      return new PartitionForceReconfigureOperation(
          memberId,
          topologyChangeOperation.getPartitionForceReconfigure().getPartitionId(),
          topologyChangeOperation.getPartitionForceReconfigure().getMembersList().stream()
              .map(MemberId::from)
              .toList());
    } else if (topologyChangeOperation.hasMemberRemove()) {
      return new MemberRemoveOperation(
          memberId, MemberId.from(topologyChangeOperation.getMemberRemove().getMemberToRemove()));
    } else if (topologyChangeOperation.hasPartitionDisableExporter()) {
      return new PartitionDisableExporterOperation(
          memberId,
          topologyChangeOperation.getPartitionDisableExporter().getPartitionId(),
          topologyChangeOperation.getPartitionDisableExporter().getExporterId());
    } else if (topologyChangeOperation.hasPartitionDeleteExporter()) {
      return new PartitionDeleteExporterOperation(
          memberId,
          topologyChangeOperation.getPartitionDeleteExporter().getPartitionId(),
          topologyChangeOperation.getPartitionDeleteExporter().getExporterId());
    } else if (topologyChangeOperation.hasPartitionEnableExporter()) {
      final var enableExporterOperation = topologyChangeOperation.getPartitionEnableExporter();
      final Optional<String> initializeFrom =
          enableExporterOperation.hasInitializeFrom()
              ? Optional.of(enableExporterOperation.getInitializeFrom())
              : Optional.empty();
      return new PartitionEnableExporterOperation(
          memberId,
          enableExporterOperation.getPartitionId(),
          enableExporterOperation.getExporterId(),
          initializeFrom);
    } else if (topologyChangeOperation.hasPartitionBootstrap()) {
      final var bootstrapOperation = topologyChangeOperation.getPartitionBootstrap();
      final Optional<DynamicPartitionConfig> partitionConfig =
          bootstrapOperation.hasConfig()
              ? Optional.of(decodePartitionConfig(bootstrapOperation.getConfig()))
              : Optional.empty();
      return new PartitionBootstrapOperation(
          memberId,
          bootstrapOperation.getPartitionId(),
          bootstrapOperation.getPriority(),
          partitionConfig,
          bootstrapOperation.getInitializeFromConfig());
    } else if (topologyChangeOperation.hasInitiateScaleUpPartitions()) {
      return new StartPartitionScaleUp(
          memberId,
          topologyChangeOperation.getInitiateScaleUpPartitions().getDesiredPartitionCount());
    } else if (topologyChangeOperation.hasDeleteHistory()) {
      return new DeleteHistoryOperation(memberId);
    } else if (topologyChangeOperation.hasAwaitRedistributionCompletion()) {
      final var redistribution = topologyChangeOperation.getAwaitRedistributionCompletion();
      return new AwaitRedistributionCompletion(
          memberId,
          redistribution.getDesiredPartitionCount(),
          new TreeSet<>(redistribution.getPartitionsToRedistributeList()));
    } else if (topologyChangeOperation.hasAwaitRelocationCompletion()) {
      final var relocation = topologyChangeOperation.getAwaitRelocationCompletion();
      return new AwaitRelocationCompletion(
          memberId,
          relocation.getDesiredPartitionCount(),
          new TreeSet<>(relocation.getPartitionsToRelocateList()));
    } else if (topologyChangeOperation.hasUpdateRoutingState()) {
      final var protoRoutingState =
          topologyChangeOperation.getUpdateRoutingState().getRoutingState();
      final var routingState = decodeRoutingState(protoRoutingState);
      return new UpdateRoutingState(memberId, routingState);
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
        .setDryRun(req.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeRemoveMembersRequest(final RemoveMembersRequest req) {
    return Requests.RemoveMembersRequest.newBuilder()
        .addAllMemberIds(req.members().stream().map(MemberId::id).toList())
        .setDryRun(req.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeJoinPartitionRequest(final JoinPartitionRequest req) {
    return Requests.JoinPartitionRequest.newBuilder()
        .setMemberId(req.memberId().id())
        .setPartitionId(req.partitionId())
        .setPriority(req.priority())
        .setDryRun(req.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeLeavePartitionRequest(final LeavePartitionRequest req) {
    return Requests.LeavePartitionRequest.newBuilder()
        .setMemberId(req.memberId().id())
        .setPartitionId(req.partitionId())
        .setDryRun(req.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeReassignPartitionsRequest(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    return Requests.ReassignAllPartitionsRequest.newBuilder()
        .addAllMemberIds(reassignPartitionsRequest.members().stream().map(MemberId::id).toList())
        .setDryRun(reassignPartitionsRequest.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeScaleRequest(final BrokerScaleRequest scaleRequest) {
    final var builder =
        Requests.BrokerScaleRequest.newBuilder()
            .addAllMemberIds(scaleRequest.members().stream().map(MemberId::id).toList())
            .setDryRun(scaleRequest.dryRun());

    scaleRequest.newReplicationFactor().ifPresent(builder::setNewReplicationFactor);

    return builder.build().toByteArray();
  }

  @Override
  public byte[] encodePurgeRequest(final PurgeRequest req) {
    return Requests.PurgeRequest.newBuilder().setDryRun(req.dryRun()).build().toByteArray();
  }

  @Override
  public byte[] encodeCancelChangeRequest(final CancelChangeRequest cancelChangeRequest) {
    return Requests.CancelTopologyChangeRequest.newBuilder()
        .setChangeId(cancelChangeRequest.changeId())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeExporterDisableRequest(final ExporterDisableRequest exporterDisableRequest) {
    return Requests.ExporterDisableRequest.newBuilder()
        .setExporterId(exporterDisableRequest.exporterId())
        .setDryRun(exporterDisableRequest.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeExporterDeleteRequest(final ExporterDeleteRequest exporterDeleteRequest) {
    return Requests.ExporterDeleteRequest.newBuilder()
        .setExporterId(exporterDeleteRequest.exporterId())
        .setDryRun(exporterDeleteRequest.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeExporterEnableRequest(final ExporterEnableRequest exporterEnableRequest) {
    final var builder =
        Requests.ExporterEnableRequest.newBuilder()
            .setExporterId(exporterEnableRequest.exporterId())
            .setDryRun(exporterEnableRequest.dryRun());

    exporterEnableRequest.initializeFrom().ifPresent(builder::setInitializeFrom);

    return builder.build().toByteArray();
  }

  @Override
  public byte[] encodeClusterScaleRequest(final ClusterScaleRequest clusterScaleRequest) {
    final var builder =
        Requests.ClusterScaleRequest.newBuilder().setDryRun(clusterScaleRequest.dryRun());

    clusterScaleRequest.newClusterSize().ifPresent(builder::setNewClusterSize);
    clusterScaleRequest.newReplicationFactor().ifPresent(builder::setNewReplicationFactor);
    clusterScaleRequest.newPartitionCount().ifPresent(builder::setNewPartitionCount);

    return builder.build().toByteArray();
  }

  @Override
  public byte[] encodeClusterPatchRequest(final ClusterPatchRequest clusterPatchRequest) {
    final var builder =
        Requests.ClusterPatchRequest.newBuilder().setDryRun(clusterPatchRequest.dryRun());

    clusterPatchRequest.newPartitionCount().ifPresent(builder::setNewPartitionCount);
    clusterPatchRequest.newReplicationFactor().ifPresent(builder::setNewReplicationFactor);
    clusterPatchRequest.membersToAdd().stream()
        .forEach(memberId -> builder.addMembersToAdd(memberId.id()));
    clusterPatchRequest.membersToRemove().stream()
        .forEach(memberId -> builder.addMembersToRemove(memberId.id()));

    return builder.build().toByteArray();
  }

  @Override
  public byte[] encodeForceRemoveBrokersRequest(
      final ForceRemoveBrokersRequest forceRemoveBrokersRequest) {
    return Requests.ForceRemoveBrokersRequest.newBuilder()
        .addAllMembersToRemove(
            forceRemoveBrokersRequest.membersToRemove().stream().map(MemberId::id).toList())
        .setDryRun(forceRemoveBrokersRequest.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeUpdateRoutingStateRequest(
      final UpdateRoutingStateRequest updateRoutingStateRequest) {
    final var builder =
        Requests.UpdateRoutingStateRequest.newBuilder()
            .setDryRun(updateRoutingStateRequest.dryRun());

    updateRoutingStateRequest
        .routingState()
        .ifPresent(routingState -> builder.setRoutingState(encodeRoutingState(routingState)));

    return builder.build().toByteArray();
  }

  @Override
  public AddMembersRequest decodeAddMembersRequest(final byte[] encodedState) {
    try {
      final var addMemberRequest = Requests.AddMembersRequest.parseFrom(encodedState);
      return new AddMembersRequest(
          addMemberRequest.getMemberIdsList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()),
          addMemberRequest.getDryRun());
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
              .collect(Collectors.toSet()),
          removeMemberRequest.getDryRun());
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
          joinPartitionRequest.getPriority(),
          joinPartitionRequest.getDryRun());
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
          leavePartitionRequest.getPartitionId(),
          leavePartitionRequest.getDryRun());
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
              .collect(Collectors.toSet()),
          reassignPartitionsRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public BrokerScaleRequest decodeScaleRequest(final byte[] encodedState) {
    try {
      final var scaleRequest = Requests.BrokerScaleRequest.parseFrom(encodedState);
      final Optional<Integer> newReplicationFactor =
          scaleRequest.hasNewReplicationFactor()
              ? Optional.of(scaleRequest.getNewReplicationFactor())
              : Optional.empty();
      return new BrokerScaleRequest(
          scaleRequest.getMemberIdsList().stream().map(MemberId::from).collect(Collectors.toSet()),
          newReplicationFactor,
          scaleRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public CancelChangeRequest decodeCancelChangeRequest(final byte[] encodedState) {
    try {
      final var cancelChangeRequest = Requests.CancelTopologyChangeRequest.parseFrom(encodedState);
      return new CancelChangeRequest(cancelChangeRequest.getChangeId());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public ExporterDisableRequest decodeExporterDisableRequest(final byte[] encodedRequest) {
    try {
      final var exporterDisableRequest = Requests.ExporterDisableRequest.parseFrom(encodedRequest);
      return new ExporterDisableRequest(
          exporterDisableRequest.getExporterId(), exporterDisableRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public ExporterDeleteRequest decodeExporterDeleteRequest(final byte[] encodedRequest) {
    try {
      final var exporterDeleteRequest = Requests.ExporterDeleteRequest.parseFrom(encodedRequest);
      return new ExporterDeleteRequest(
          exporterDeleteRequest.getExporterId(), exporterDeleteRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public ExporterEnableRequest decodeExporterEnableRequest(final byte[] encodedRequest) {
    try {
      final var exporterEnableRequest = Requests.ExporterEnableRequest.parseFrom(encodedRequest);
      final Optional<String> initializeFrom =
          exporterEnableRequest.hasInitializeFrom()
              ? Optional.of(exporterEnableRequest.getInitializeFrom())
              : Optional.empty();
      return new ExporterEnableRequest(
          exporterEnableRequest.getExporterId(), initializeFrom, exporterEnableRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public ClusterScaleRequest decodeClusterScaleRequest(final byte[] encodedRequest) {
    try {
      final var clusterScaleRequest = Requests.ClusterScaleRequest.parseFrom(encodedRequest);
      final Optional<Integer> newClusterSize =
          clusterScaleRequest.hasNewClusterSize()
              ? Optional.of(clusterScaleRequest.getNewClusterSize())
              : Optional.empty();
      final Optional<Integer> newReplicationFactor =
          clusterScaleRequest.hasNewReplicationFactor()
              ? Optional.of(clusterScaleRequest.getNewReplicationFactor())
              : Optional.empty();
      final Optional<Integer> newPartitionCount =
          clusterScaleRequest.hasNewPartitionCount()
              ? Optional.of(clusterScaleRequest.getNewPartitionCount())
              : Optional.empty();
      return new ClusterScaleRequest(
          newClusterSize, newPartitionCount, newReplicationFactor, clusterScaleRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public ClusterPatchRequest decodeClusterPatchRequest(final byte[] encodedRequest) {
    try {
      final var clusterPatchRequest = Requests.ClusterPatchRequest.parseFrom(encodedRequest);
      final Optional<Integer> newPartitionCount =
          clusterPatchRequest.hasNewPartitionCount()
              ? Optional.of(clusterPatchRequest.getNewPartitionCount())
              : Optional.empty();
      final Optional<Integer> newReplicationFactor =
          clusterPatchRequest.hasNewReplicationFactor()
              ? Optional.of(clusterPatchRequest.getNewReplicationFactor())
              : Optional.empty();
      return new ClusterPatchRequest(
          clusterPatchRequest.getMembersToAddList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()),
          clusterPatchRequest.getMembersToRemoveList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()),
          newPartitionCount,
          newReplicationFactor,
          clusterPatchRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public ForceRemoveBrokersRequest decodeForceRemoveBrokersRequest(final byte[] encodedRequest) {
    try {
      final var forceRemoveBrokersRequest =
          Requests.ForceRemoveBrokersRequest.parseFrom(encodedRequest);
      return new ForceRemoveBrokersRequest(
          forceRemoveBrokersRequest.getMembersToRemoveList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()),
          forceRemoveBrokersRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public PurgeRequest decodePurgeRequest(final byte[] encodedRequest) {
    try {
      final var purgeRequest = Requests.PurgeRequest.parseFrom(encodedRequest);
      return new PurgeRequest(purgeRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public byte[] encodeResponse(final ClusterConfigurationChangeResponse response) {
    return Response.newBuilder()
        .setTopologyChangeResponse(encodeTopologyChangeResponse(response))
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeResponse(final ClusterConfiguration response) {
    return Response.newBuilder()
        .setClusterTopology(encodeClusterTopology(response))
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeResponse(final ErrorResponse response) {
    return Response.newBuilder()
        .setError(
            Requests.ErrorResponse.newBuilder()
                .setErrorCode(encodeErrorCode(response.code()))
                .setErrorMessage(response.message()))
        .build()
        .toByteArray();
  }

  @Override
  public Either<ErrorResponse, ClusterConfigurationChangeResponse> decodeTopologyChangeResponse(
      final byte[] encodedResponse) {
    try {
      final var response = Response.parseFrom(encodedResponse);
      if (response.hasError()) {
        return Either.left(
            new ErrorResponse(
                decodeErrorCode(response.getError().getErrorCode()),
                response.getError().getErrorMessage()));
      } else if (response.hasTopologyChangeResponse()) {
        return Either.right(decodeTopologyChangeResponse(response.getTopologyChangeResponse()));
      } else {
        throw new DecodingFailed(
            "Response does not have an error or a valid topology change response");
      }

    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public Either<ErrorResponse, ClusterConfiguration> decodeClusterTopologyResponse(
      final byte[] encodedResponse) {
    try {
      final var response = Response.parseFrom(encodedResponse);
      if (response.hasError()) {
        return Either.left(
            new ErrorResponse(
                decodeErrorCode(response.getError().getErrorCode()),
                response.getError().getErrorMessage()));
      } else if (response.hasClusterTopology()) {
        return Either.right(decodeClusterTopology(response.getClusterTopology()));
      } else {
        throw new DecodingFailed("Response does not have an error or a valid cluster topology");
      }
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public UpdateRoutingStateRequest decodeUpdateRoutingStateRequest(final byte[] bytes) {
    final Requests.UpdateRoutingStateRequest proto;
    try {
      proto = Requests.UpdateRoutingStateRequest.parseFrom(bytes);
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
    final var routingState = decodeRoutingState(proto.getRoutingState());
    return new UpdateRoutingStateRequest(routingState, proto.getDryRun());
  }

  public Builder encodeTopologyChangeResponse(
      final ClusterConfigurationChangeResponse clusterConfigurationChangeResponse) {
    final var builder = Requests.TopologyChangeResponse.newBuilder();

    builder
        .setChangeId(clusterConfigurationChangeResponse.changeId())
        .addAllPlannedChanges(
            clusterConfigurationChangeResponse.plannedChanges().stream()
                .map(this::encodeOperation)
                .toList())
        .putAllCurrentTopology(
            encodeMemberStateMap(clusterConfigurationChangeResponse.currentConfiguration()))
        .putAllExpectedTopology(
            encodeMemberStateMap(clusterConfigurationChangeResponse.expectedConfiguration()));

    return builder;
  }

  public ClusterConfigurationChangeResponse decodeTopologyChangeResponse(
      final Requests.TopologyChangeResponse topologyChangeResponse) {
    return new ClusterConfigurationChangeResponse(
        topologyChangeResponse.getChangeId(),
        decodeMemberStateMap(topologyChangeResponse.getCurrentTopologyMap()),
        decodeMemberStateMap(topologyChangeResponse.getExpectedTopologyMap()),
        topologyChangeResponse.getPlannedChangesList().stream()
            .map(this::decodeOperation)
            .toList());
  }

  private ErrorCode encodeErrorCode(final ErrorResponse.ErrorCode status) {
    return switch (status) {
      case INVALID_REQUEST -> ErrorCode.INVALID_REQUEST;
      case OPERATION_NOT_ALLOWED -> ErrorCode.OPERATION_NOT_ALLOWED;
      case CONCURRENT_MODIFICATION -> ErrorCode.CONCURRENT_MODIFICATION;
      case INTERNAL_ERROR -> ErrorCode.INTERNAL_ERROR;
    };
  }

  private ErrorResponse.ErrorCode decodeErrorCode(final ErrorCode status) {
    return switch (status) {
      case INVALID_REQUEST -> ErrorResponse.ErrorCode.INVALID_REQUEST;
      case OPERATION_NOT_ALLOWED -> ErrorResponse.ErrorCode.OPERATION_NOT_ALLOWED;
      case CONCURRENT_MODIFICATION -> ErrorResponse.ErrorCode.CONCURRENT_MODIFICATION;
      case INTERNAL_ERROR, UNRECOGNIZED -> ErrorResponse.ErrorCode.INTERNAL_ERROR;
    };
  }

  private Map<String, Topology.MemberState> encodeMemberStateMap(
      final Map<MemberId, MemberState> topologyChangeResponse) {
    return topologyChangeResponse.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().id(), e -> encodeMemberState(e.getValue())));
  }

  private Topology.ChangeStatus fromTopologyChangeStatus(final ClusterChangePlan.Status status) {
    return switch (status) {
      case IN_PROGRESS -> Topology.ChangeStatus.IN_PROGRESS;
      case COMPLETED -> Topology.ChangeStatus.COMPLETED;
      case FAILED -> Topology.ChangeStatus.FAILED;
      case CANCELLED -> ChangeStatus.CANCELLED;
    };
  }

  private ClusterChangePlan.Status toChangeStatus(final Topology.ChangeStatus status) {
    return switch (status) {
      case IN_PROGRESS -> ClusterChangePlan.Status.IN_PROGRESS;
      case COMPLETED -> ClusterChangePlan.Status.COMPLETED;
      case FAILED -> ClusterChangePlan.Status.FAILED;
      case CANCELLED -> ClusterChangePlan.Status.CANCELLED;
      default -> throw new IllegalStateException("Unknown status: " + status);
    };
  }
}
