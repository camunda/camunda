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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterZoneMigrationRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
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
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.*;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.util.Either;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
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

    final Optional<PartitionDistributorConfig> partitionDistributorConfig =
        encodedClusterTopology.hasPartitionDistributor()
            ? decodePartitionDistributorConfig(encodedClusterTopology.getPartitionDistributor())
            : Optional.empty();

    final Optional<String> clusterId =
        encodedClusterTopology.getClusterId().isEmpty()
            ? Optional.empty()
            : Optional.of(encodedClusterTopology.getClusterId());

    final long incarnationNumber = encodedClusterTopology.getIncarnationNumber();

    return new ClusterConfiguration(
        encodedClusterTopology.getVersion(),
        members,
        completedChange,
        currentChange,
        routingState,
        clusterId,
        incarnationNumber,
        partitionDistributorConfig);
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
            .putAllMembers(members)
            .setIncarnationNumber(clusterConfiguration.incarnationNumber());

    clusterConfiguration
        .lastChange()
        .ifPresent(lastChange -> builder.setLastChange(encodeCompletedChange(lastChange)));
    clusterConfiguration
        .pendingChanges()
        .ifPresent(changePlan -> builder.setCurrentChange(encodeChangePlan(changePlan)));
    clusterConfiguration
        .routingState()
        .ifPresent(routingState -> builder.setRoutingState(encodeRoutingState(routingState)));
    clusterConfiguration
        .partitionDistributorConfig()
        .ifPresent(
            config -> builder.setPartitionDistributor(encodePartitionDistributorConfig(config)));
    clusterConfiguration.clusterId().ifPresent(builder::setClusterId);

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

  ExportingConfig decodeExportingConfig(final Topology.ExportingConfig exporting) {
    return new ExportingConfig(
        decodeExportingState(exporting.getState()),
        exporting.getExportersMap().entrySet().stream()
            .map(e -> Map.entry(e.getKey(), decodeExporterState(e.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
  }

  private ExportingState decodeExportingState(final Topology.ExportingStateEnum exportingState) {
    return switch (exportingState) {
      case EXPORTING_STATE_UNKNOWN -> ExportingState.UNKNOWN;
      case EXPORTING -> ExportingState.EXPORTING;
      case SOFT_PAUSED -> ExportingState.SOFT_PAUSED;
      case PAUSED -> ExportingState.PAUSED;
      case UNRECOGNIZED ->
          throw new IllegalStateException("Unknown exporting state " + exportingState);
    };
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

  private Topology.ExportingConfig encodeExportingConfig(final ExportingConfig exporting) {
    return Topology.ExportingConfig.newBuilder()
        .setState(encodeExportingState(exporting.state()))
        .putAllExporters(
            exporting.exporters().entrySet().stream()
                .map(e -> Map.entry(e.getKey(), encodeExporterState(e.getValue())))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)))
        .build();
  }

  private Topology.ExportingStateEnum encodeExportingState(final ExportingState state) {
    return switch (state) {
      case UNKNOWN -> Topology.ExportingStateEnum.EXPORTING_STATE_UNKNOWN;
      case EXPORTING -> Topology.ExportingStateEnum.EXPORTING;
      case SOFT_PAUSED -> Topology.ExportingStateEnum.SOFT_PAUSED;
      case PAUSED -> Topology.ExportingStateEnum.PAUSED;
    };
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
      case RECOVERING -> Topology.State.RECOVERING;
    };
  }

  private MemberState.State toMemberState(final Topology.State state) {
    return switch (state) {
      case UNRECOGNIZED, UNKNOWN -> MemberState.State.UNINITIALIZED;
      case ACTIVE -> MemberState.State.ACTIVE;
      case JOINING -> MemberState.State.JOINING;
      case LEAVING -> MemberState.State.LEAVING;
      case LEFT -> MemberState.State.LEFT;
      case RECOVERING -> MemberState.State.RECOVERING;
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
      case RECOVERING -> PartitionState.State.RECOVERING;
    };
  }

  private Topology.State toSerializedState(final PartitionState.State state) {
    return switch (state) {
      case UNKNOWN -> Topology.State.UNKNOWN;
      case ACTIVE -> Topology.State.ACTIVE;
      case JOINING -> Topology.State.JOINING;
      case LEAVING -> Topology.State.LEAVING;
      case BOOTSTRAPPING -> Topology.State.BOOTSTRAPPING;
      case RECOVERING -> Topology.State.RECOVERING;
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
      case final UpdateIncarnationNumberOperation msg ->
          builder.setUpdateIncarnationNumber(
              Topology.UpdateIncarnationNumberOperation.newBuilder().build());
      case final PreScalingOperation preScalingOperation ->
          builder.setPreScaling(
              Topology.PreScalingOperation.newBuilder()
                  .addAllClusterMembers(
                      preScalingOperation.clusterMembers().stream().map(MemberId::id).toList())
                  .build());
      case final PostScalingOperation postScalingOperation ->
          builder.setPostScaling(
              Topology.PostScalingOperation.newBuilder()
                  .addAllClusterMembers(
                      postScalingOperation.clusterMembers().stream().map(MemberId::id).toList())
                  .build());
      case final UpdatePartitionDistributorConfigOperation msg ->
          builder.setUpdatePartitionDistributorConfig(
              Topology.UpdatePartitionDistributorConfigOperation.newBuilder()
                  .setConfig(encodePartitionDistributorConfig(msg.config()))
                  .build());
      case final ModeChangeOperation modeChangeOperation ->
          builder.setModeChange(
              Topology.ModeChangeOperation.newBuilder()
                  .setMode(toProtoTopologyMode(modeChangeOperation.mode()))
                  .build());
      case final AwaitModeChangeOperation awaitModeChangeOperation ->
          builder.setAwaitModeChange(
              Topology.AwaitModeChangeOperation.newBuilder()
                  .setMode(toProtoTopologyMode(awaitModeChangeOperation.mode()))
                  .build());
      case final ExportingStateChangeOperation exportingStateChangeOperation ->
          builder.setExporterStateChange(
              Topology.ExporterStateChangeOperation.newBuilder()
                  .setState(encodeExportingState(exportingStateChangeOperation.state()))
                  .build());
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

  Optional<RoutingState> decodeRoutingState(final Topology.RoutingState routingState) {
    if (routingState.equals(Topology.RoutingState.getDefaultInstance())) {
      return Optional.empty();
    } else {
      return Optional.of(
          new RoutingState(
              routingState.getVersion(),
              decodeRequestHandling(
                  routingState.getRequestHandling(), routingState.getActivePartitionsList()),
              decodeMessageCorrelation(routingState.getMessageCorrelation())));
    }
  }

  private RequestHandling decodeRequestHandling(
      final Topology.RequestHandling requestHandling,
      @Deprecated(forRemoval = true) final List<Integer> activePartitionList) {
    return switch (requestHandling.getStrategyCase()) {
      case ALLPARTITIONS ->
          new RequestHandling.AllPartitions(requestHandling.getAllPartitions().getPartitionCount());
      case ACTIVEPARTITIONS ->
          new RequestHandling.ActivePartitions(
              requestHandling.getActivePartitions().getBasePartitionCount(),
              new TreeSet<>(
                  requestHandling.getActivePartitions().getAdditionalActivePartitionsList()),
              new TreeSet<>(requestHandling.getActivePartitions().getInactivePartitionsList()));
      case STRATEGY_NOT_SET -> {
        // This fallback is used during the transition from 8.7 to 8.8, as RequestHandling was
        // introduced in 8.8. It can be removed in 8.9
        if (activePartitionList.isEmpty()) {
          throw new IllegalArgumentException("Unknown request handling type");
        } else {
          yield new RequestHandling.AllPartitions(activePartitionList.size());
        }
      }
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

  Topology.RoutingState encodeRoutingState(final RoutingState routingState) {
    return Topology.RoutingState.newBuilder()
        .setVersion(routingState.version())
        .addAllActivePartitions(routingState.requestHandling().activePartitions())
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

  private static Topology.PartitionDistributorConfig encodePartitionDistributorConfig(
      final PartitionDistributorConfig config) {
    final var builder = Topology.PartitionDistributorConfig.newBuilder();
    switch (config) {
      case final PartitionDistributorConfig.RoundRobinConfig ignored ->
          builder.setRoundRobin(
              Topology.PartitionDistributorConfig.RoundRobinDistributor.getDefaultInstance());
      case final PartitionDistributorConfig.FixedConfig ignored ->
          builder.setFixed(
              Topology.PartitionDistributorConfig.FixedDistributor.getDefaultInstance());
      case final PartitionDistributorConfig.ZoneAwareConfig zoneAware ->
          builder.setZoneAware(
              Topology.PartitionDistributorConfig.ZoneAwareDistributor.newBuilder()
                  .addAllZones(
                      zoneAware.zones().stream()
                          .map(
                              z ->
                                  Topology.PartitionDistributorConfig.ZoneSpec.newBuilder()
                                      .setName(z.name())
                                      .setNumberOfReplicas(z.numberOfReplicas())
                                      .setPriority(z.priority())
                                      .build())
                          .toList())
                  .build());
    }
    return builder.build();
  }

  private static Optional<PartitionDistributorConfig> decodePartitionDistributorConfig(
      final Topology.PartitionDistributorConfig proto) {
    return switch (proto.getKindCase()) {
      case ROUNDROBIN -> Optional.of(new PartitionDistributorConfig.RoundRobinConfig());
      case FIXED -> Optional.of(new PartitionDistributorConfig.FixedConfig());
      case ZONEAWARE ->
          Optional.of(
              new PartitionDistributorConfig.ZoneAwareConfig(
                  proto.getZoneAware().getZonesList().stream()
                      .map(
                          z ->
                              new PartitionDistributorConfig.ZoneSpec(
                                  z.getName(), z.getNumberOfReplicas(), z.getPriority()))
                      .toList()));
      case KIND_NOT_SET -> Optional.empty();
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
              .collect(Collectors.toSet()));
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
    } else if (topologyChangeOperation.hasUpdateIncarnationNumber()) {
      return new UpdateIncarnationNumberOperation(memberId);
    } else if (topologyChangeOperation.hasPreScaling()) {
      final var preScalingOperation = topologyChangeOperation.getPreScaling();
      return new PreScalingOperation(
          memberId,
          preScalingOperation.getClusterMembersList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } else if (topologyChangeOperation.hasPostScaling()) {
      final var postScalingOperation = topologyChangeOperation.getPostScaling();
      return new PostScalingOperation(
          memberId,
          postScalingOperation.getClusterMembersList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } else if (topologyChangeOperation.hasUpdatePartitionDistributorConfig()) {
      final var proto = topologyChangeOperation.getUpdatePartitionDistributorConfig().getConfig();
      return decodePartitionDistributorConfig(proto)
          .map(config -> new UpdatePartitionDistributorConfigOperation(memberId, config))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "UpdatePartitionDistributorConfig operation has empty config"));
    } else if (topologyChangeOperation.hasModeChange()) {
      final var modeChangeProto = topologyChangeOperation.getModeChange();
      return new ModeChangeOperation(memberId, fromProtoTopologyMode(modeChangeProto.getMode()));
    } else if (topologyChangeOperation.hasAwaitModeChange()) {
      final var awaitModeChangeProto = topologyChangeOperation.getAwaitModeChange();
      return new AwaitModeChangeOperation(
          memberId, fromProtoTopologyMode(awaitModeChangeProto.getMode()));
    } else if (topologyChangeOperation.hasExporterStateChange()) {
      final var exporterStateChangeProto = topologyChangeOperation.getExporterStateChange();
      return new ExportingStateChangeOperation(
          memberId, decodeExportingState(exporterStateChangeProto.getState()));
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
        Instant.ofEpochSecond(
            operation.getCompletedAt().getSeconds(), operation.getCompletedAt().getNanos()));
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

    clusterScaleRequest.brokerCount().ifPresent(builder::setNewClusterSize);
    clusterScaleRequest.newReplicationFactor().ifPresent(builder::setNewReplicationFactor);
    clusterScaleRequest.newPartitionCount().ifPresent(builder::setNewPartitionCount);
    clusterScaleRequest.zone().ifPresent(builder::setZone);

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
  public byte[] encodeUpdatePartitionDistributorConfigRequest(
      final UpdatePartitionDistributorConfigRequest request) {
    return Requests.UpdatePartitionDistributorConfigRequest.newBuilder()
        .setConfig(encodePartitionDistributorConfig(request.config()))
        .setDryRun(request.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeClusterZoneMigrationRequest(final ClusterZoneMigrationRequest request) {
    return Requests.ClusterZoneMigrationRequest.newBuilder()
        .setZone(request.zone())
        .setDryRun(request.dryRun())
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
      final Optional<String> zone =
          clusterScaleRequest.hasZone()
              ? Optional.of(clusterScaleRequest.getZone())
              : Optional.empty();
      return new ClusterScaleRequest(
          newClusterSize,
          newPartitionCount,
          newReplicationFactor,
          zone,
          clusterScaleRequest.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    } catch (final IllegalArgumentException e) {
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

  @Override
  public UpdatePartitionDistributorConfigRequest decodeUpdatePartitionDistributorConfigRequest(
      final byte[] bytes) {
    final Requests.UpdatePartitionDistributorConfigRequest proto;
    try {
      proto = Requests.UpdatePartitionDistributorConfigRequest.parseFrom(bytes);
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
    final var config =
        decodePartitionDistributorConfig(proto.getConfig())
            .orElseThrow(
                () ->
                    new DecodingFailed(
                        new IllegalArgumentException(
                            "UpdatePartitionDistributorConfigRequest has empty config")));
    return new UpdatePartitionDistributorConfigRequest(config, proto.getDryRun());
  }

  @Override
  public ClusterZoneMigrationRequest decodeClusterZoneMigrationRequest(final byte[] bytes) {
    final Requests.ClusterZoneMigrationRequest proto;
    try {
      proto = Requests.ClusterZoneMigrationRequest.parseFrom(bytes);
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
    return new ClusterZoneMigrationRequest(proto.getZone(), proto.getDryRun());
  }

  @Override
  public byte[] encodeModeChangeRequest(final ModeChangeRequest modeChangeRequest) {
    return Requests.ModeChangeRequest.newBuilder()
        .setMode(toProtoRequestMode(modeChangeRequest.mode()))
        .setDryRun(modeChangeRequest.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public ModeChangeRequest decodeModeChangeRequest(final byte[] encodedRequest) {
    try {
      final var request = Requests.ModeChangeRequest.parseFrom(encodedRequest);
      return new ModeChangeRequest(toMode(request.getMode()), request.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public byte[] encodeExporterStateChangeRequest(final ExporterStateChangeRequest request) {
    return Requests.ExporterStateChangeRequest.newBuilder()
        .setState(encodeExportingState(request.state()))
        .setDryRun(request.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public ExporterStateChangeRequest decodeExporterStateChangeRequest(final byte[] encodedRequest) {
    try {
      final var request = Requests.ExporterStateChangeRequest.parseFrom(encodedRequest);
      return new ExporterStateChangeRequest(
          decodeExportingState(request.getState()), request.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public byte[] encodeRestoreRequest(final RestoreRequest request) {
    final var builder = Requests.RestoreRequest.newBuilder();
    builder.setPhysicalTenantId(request.physicalTenantId());
    builder.addAllBackupIds(request.backupIds());
    builder.setDatabaseType(request.databaseType());
    builder.setContinuousBackups(request.continuousBackups());
    builder.setDryRun(request.dryRun());
    if (request.from() != null) {
      builder.setFrom(request.from());
    }
    if (request.to() != null) {
      builder.setTo(request.to());
    }

    return builder.build().toByteArray();
  }

  @Override
  public RestoreRequest decodeRestoreRequest(final byte[] encodedRequest) {
    try {
      final var request = Requests.RestoreRequest.parseFrom(encodedRequest);
      final String from = request.hasFrom() ? request.getFrom() : null;
      final String to = request.hasTo() ? request.getTo() : null;
      return new RestoreRequest(
          request.getPhysicalTenantId(),
          request.getBackupIdsList(),
          from,
          to,
          request.getDatabaseType(),
          request.getContinuousBackups(),
          request.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  private static Mode toMode(final Requests.Mode mode) {
    return switch (mode) {
      case RECOVERING -> Mode.RECOVERING;
      case PROCESSING -> Mode.PROCESSING;
      case UNKNOWN, UNRECOGNIZED -> throw new IllegalStateException("Unknown partition mode");
    };
  }

  private static Requests.Mode toProtoRequestMode(final Mode mode) {
    return switch (mode) {
      case RECOVERING -> Requests.Mode.RECOVERING;
      case PROCESSING -> Requests.Mode.PROCESSING;
    };
  }

  private static Topology.Mode toProtoTopologyMode(final Mode mode) {
    return switch (mode) {
      case RECOVERING -> Topology.Mode.MODE_RECOVERING;
      case PROCESSING -> Topology.Mode.MODE_PROCESSING;
    };
  }

  private static Mode fromProtoTopologyMode(final Topology.Mode mode) {
    if (mode == Topology.Mode.MODE_RECOVERING) {
      return Mode.RECOVERING;
    }
    return Mode.PROCESSING;
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
      case INVALID_STATE -> ErrorCode.INVALID_STATE;
      case NOT_FOUND -> ErrorCode.NOT_FOUND;
    };
  }

  private ErrorResponse.ErrorCode decodeErrorCode(final ErrorCode status) {
    return switch (status) {
      case INVALID_REQUEST -> ErrorResponse.ErrorCode.INVALID_REQUEST;
      case OPERATION_NOT_ALLOWED -> ErrorResponse.ErrorCode.OPERATION_NOT_ALLOWED;
      case CONCURRENT_MODIFICATION -> ErrorResponse.ErrorCode.CONCURRENT_MODIFICATION;
      case INVALID_STATE -> ErrorResponse.ErrorCode.INVALID_STATE;
      case NOT_FOUND -> ErrorResponse.ErrorCode.NOT_FOUND;
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

  // ---- New multi-partition-group configuration model (8.10) ----
  // Additive encode/decode for CurrentClusterConfiguration and its sub-types. These are not yet
  // wired into gossip or persistence; they exist for the Phase-3 handoff.

  @Override
  public byte[] encodeCurrentClusterConfiguration(final CurrentClusterConfiguration configuration) {
    return encodeCurrentClusterConfigurationProto(configuration).toByteArray();
  }

  public CurrentClusterConfiguration decodeCurrentClusterConfiguration(final byte[] encoded) {
    return decodeCurrentClusterConfiguration(encoded, 0, encoded.length);
  }

  @Override
  public CurrentClusterConfiguration decodeCurrentClusterConfiguration(
      final byte[] encoded, final int offset, final int length) {
    try {
      return decodeCurrentClusterConfiguration(
          Topology.CurrentClusterConfiguration.parseFrom(ByteBuffer.wrap(encoded, offset, length)));
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  private Topology.CurrentClusterConfiguration encodeCurrentClusterConfigurationProto(
      final CurrentClusterConfiguration configuration) {
    final var partitionGroups =
        configuration.partitionGroups().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey, e -> encodePartitionGroupConfiguration(e.getValue())));
    return Topology.CurrentClusterConfiguration.newBuilder()
        .setVersion(configuration.version())
        .setGlobalConfiguration(encodeGlobalConfiguration(configuration.globalConfiguration()))
        .putAllPartitionGroups(partitionGroups)
        .setPhasedChangeState(encodePhasedChangeState(configuration.phasedChangeState()))
        .build();
  }

  private CurrentClusterConfiguration decodeCurrentClusterConfiguration(
      final Topology.CurrentClusterConfiguration proto) {
    final var partitionGroups =
        proto.getPartitionGroupsMap().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey, e -> decodePartitionGroupConfiguration(e.getValue())));
    return new CurrentClusterConfiguration(
        proto.getVersion(),
        decodeGlobalConfiguration(proto.getGlobalConfiguration()),
        partitionGroups,
        decodePhasedChangeState(proto.getPhasedChangeState()));
  }

  public Topology.GlobalConfiguration encodeGlobalConfiguration(
      final GlobalConfiguration configuration) {
    final var members =
        configuration.members().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().id(), e -> encodeBrokerState(e.getValue())));
    final var builder =
        Topology.GlobalConfiguration.newBuilder()
            .setVersion(configuration.version())
            .putAllMembers(members);
    configuration.clusterId().ifPresent(builder::setClusterId);
    configuration
        .partitionDistributorConfig()
        .ifPresent(
            config -> builder.setPartitionDistributor(encodePartitionDistributorConfig(config)));
    configuration
        .pendingChanges()
        .ifPresent(changePlan -> builder.setPendingChanges(encodeChangePlan(changePlan)));
    configuration
        .lastChange()
        .ifPresent(lastChange -> builder.setLastChange(encodeCompletedChange(lastChange)));
    return builder.build();
  }

  public GlobalConfiguration decodeGlobalConfiguration(final Topology.GlobalConfiguration proto) {
    final var members =
        proto.getMembersMap().entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> MemberId.from(e.getKey()), e -> decodeBrokerState(e.getValue())));
    final Optional<String> clusterId =
        proto.getClusterId().isEmpty() ? Optional.empty() : Optional.of(proto.getClusterId());
    final Optional<PartitionDistributorConfig> partitionDistributorConfig =
        proto.hasPartitionDistributor()
            ? decodePartitionDistributorConfig(proto.getPartitionDistributor())
            : Optional.empty();
    final Optional<ClusterChangePlan> pendingChanges =
        proto.hasPendingChanges()
            ? Optional.of(decodeChangePlan(proto.getPendingChanges()))
            : Optional.empty();
    final Optional<io.camunda.zeebe.dynamic.config.state.CompletedChange> lastChange =
        proto.hasLastChange()
            ? Optional.of(decodeCompletedChange(proto.getLastChange()))
            : Optional.empty();
    return new GlobalConfiguration(
        proto.getVersion(),
        clusterId,
        members,
        partitionDistributorConfig,
        pendingChanges,
        lastChange);
  }

  public Topology.PartitionGroupConfiguration encodePartitionGroupConfiguration(
      final PartitionGroupConfiguration configuration) {
    final var members =
        configuration.members().entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> e.getKey().id(), e -> encodeBrokerPartitionState(e.getValue())));
    final var builder =
        Topology.PartitionGroupConfiguration.newBuilder()
            .setVersion(configuration.version())
            .setIncarnationNumber(configuration.incarnationNumber())
            .putAllMembers(members);
    configuration
        .routingState()
        .ifPresent(routingState -> builder.setRoutingState(encodeRoutingState(routingState)));
    configuration
        .pendingChanges()
        .ifPresent(changePlan -> builder.setPendingChanges(encodeChangePlan(changePlan)));
    configuration
        .lastChange()
        .ifPresent(lastChange -> builder.setLastChange(encodeCompletedChange(lastChange)));
    return builder.build();
  }

  public PartitionGroupConfiguration decodePartitionGroupConfiguration(
      final Topology.PartitionGroupConfiguration proto) {
    final var members =
        proto.getMembersMap().entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> MemberId.from(e.getKey()), e -> decodeBrokerPartitionState(e.getValue())));
    final Optional<RoutingState> routingState =
        proto.hasRoutingState() ? decodeRoutingState(proto.getRoutingState()) : Optional.empty();
    final Optional<ClusterChangePlan> pendingChanges =
        proto.hasPendingChanges()
            ? Optional.of(decodeChangePlan(proto.getPendingChanges()))
            : Optional.empty();
    final Optional<io.camunda.zeebe.dynamic.config.state.CompletedChange> lastChange =
        proto.hasLastChange()
            ? Optional.of(decodeCompletedChange(proto.getLastChange()))
            : Optional.empty();
    return new PartitionGroupConfiguration(
        proto.getVersion(),
        proto.getIncarnationNumber(),
        members,
        routingState,
        pendingChanges,
        lastChange);
  }

  private Topology.BrokerState encodeBrokerState(final BrokerState brokerState) {
    return Topology.BrokerState.newBuilder()
        .setVersion(brokerState.version())
        .setLastUpdated(toTimestamp(brokerState.lastUpdated()))
        .setState(toSerializedBrokerState(brokerState.state()))
        .build();
  }

  private BrokerState decodeBrokerState(final Topology.BrokerState proto) {
    final var lastUpdated = proto.getLastUpdated();
    return new BrokerState(
        proto.getVersion(),
        Instant.ofEpochSecond(lastUpdated.getSeconds(), lastUpdated.getNanos()),
        toBrokerLifecycleState(proto.getState()));
  }

  private Topology.BrokerPartitionState encodeBrokerPartitionState(
      final BrokerPartitionState brokerPartitionState) {
    final var partitions =
        brokerPartitionState.partitions().entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> encodePartitions(e.getValue())));
    return Topology.BrokerPartitionState.newBuilder()
        .setVersion(brokerPartitionState.version())
        .setLastUpdated(toTimestamp(brokerPartitionState.lastUpdated()))
        .putAllPartitions(partitions)
        .setMode(toProtoTopologyMode(brokerPartitionState.mode()))
        .build();
  }

  private BrokerPartitionState decodeBrokerPartitionState(
      final Topology.BrokerPartitionState proto) {
    final var partitions =
        proto.getPartitionsMap().entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> decodePartitionState(e.getValue())));
    final var lastUpdated = proto.getLastUpdated();
    return new BrokerPartitionState(
        proto.getVersion(),
        Instant.ofEpochSecond(lastUpdated.getSeconds(), lastUpdated.getNanos()),
        partitions,
        fromProtoTopologyMode(proto.getMode()));
  }

  private Topology.PhasedChangeState encodePhasedChangeState(final PhasedChangeState state) {
    final var builder = Topology.PhasedChangeState.newBuilder();
    state.pending().ifPresent(plan -> builder.setPendingPlan(encodePhasedChangePlan(plan)));
    state
        .lastChange()
        .ifPresent(lastChange -> builder.setLastChange(encodeCompletedPhasedChange(lastChange)));
    return builder.build();
  }

  private PhasedChangeState decodePhasedChangeState(final Topology.PhasedChangeState proto) {
    final Optional<PhasedChangePlan> pending =
        proto.hasPendingPlan()
            ? Optional.of(decodePhasedChangePlan(proto.getPendingPlan()))
            : Optional.empty();
    final Optional<CompletedPhasedChange> lastChange =
        proto.hasLastChange()
            ? Optional.of(decodeCompletedPhasedChange(proto.getLastChange()))
            : Optional.empty();
    return new PhasedChangeState(pending, lastChange);
  }

  private Topology.PhasedChangePlan encodePhasedChangePlan(final PhasedChangePlan plan) {
    final var builder =
        Topology.PhasedChangePlan.newBuilder()
            .setId(plan.id())
            .setCurrentPhaseIndex(plan.currentPhaseIndex())
            .setStartedAt(toTimestamp(plan.startedAt()));
    plan.phases().forEach(phase -> builder.addPhases(encodePhase(phase)));
    return builder.build();
  }

  private PhasedChangePlan decodePhasedChangePlan(final Topology.PhasedChangePlan proto) {
    final var phases = proto.getPhasesList().stream().map(this::decodePhase).toList();
    final var startedAt = proto.getStartedAt();
    return new PhasedChangePlan(
        proto.getId(),
        proto.getCurrentPhaseIndex(),
        phases,
        Instant.ofEpochSecond(startedAt.getSeconds(), startedAt.getNanos()));
  }

  private Topology.PhasedChangePlanPhase encodePhase(final Phase phase) {
    final var builder = Topology.PhasedChangePlanPhase.newBuilder();
    switch (phase) {
      case final GlobalPhase globalPhase ->
          builder.setGlobalPhase(
              Topology.GlobalPhase.newBuilder()
                  .addAllOperations(
                      globalPhase.operations().stream()
                          .map(this::encodeGlobalChangeOperation)
                          .toList())
                  .build());
      case final PartitionGroupParallelPhase parallelPhase -> {
        final var parallelBuilder = Topology.PartitionGroupParallelPhase.newBuilder();
        parallelPhase
            .groupOperations()
            .forEach(
                (group, operations) ->
                    parallelBuilder.putGroupOperations(
                        group,
                        Topology.PartitionGroupOperationList.newBuilder()
                            .addAllOperations(
                                operations.stream()
                                    .map(this::encodePartitionGroupChangeOperation)
                                    .toList())
                            .build()));
        builder.setPartitionGroupParallelPhase(parallelBuilder.build());
      }
    }
    return builder.build();
  }

  private Phase decodePhase(final Topology.PhasedChangePlanPhase proto) {
    return switch (proto.getPhaseCase()) {
      case GLOBALPHASE ->
          new GlobalPhase(
              proto.getGlobalPhase().getOperationsList().stream()
                  .map(this::decodeGlobalChangeOperation)
                  .toList());
      case PARTITIONGROUPPARALLELPHASE ->
          new PartitionGroupParallelPhase(
              proto.getPartitionGroupParallelPhase().getGroupOperationsMap().entrySet().stream()
                  .collect(
                      Collectors.toMap(
                          Entry::getKey,
                          e ->
                              e.getValue().getOperationsList().stream()
                                  .map(this::decodePartitionGroupChangeOperation)
                                  .toList())));
      case PHASE_NOT_SET -> throw new IllegalStateException("Unknown phase: " + proto);
    };
  }

  private Topology.CompletedPhasedChange encodeCompletedPhasedChange(
      final CompletedPhasedChange completedChange) {
    return Topology.CompletedPhasedChange.newBuilder()
        .setId(completedChange.id())
        .setStatus(toProtoPhasedChangePlanStatus(completedChange.status()))
        .setStartedAt(toTimestamp(completedChange.startedAt()))
        .setCompletedAt(toTimestamp(completedChange.completedAt()))
        .build();
  }

  private CompletedPhasedChange decodeCompletedPhasedChange(
      final Topology.CompletedPhasedChange proto) {
    final var startedAt = proto.getStartedAt();
    final var completedAt = proto.getCompletedAt();
    return new CompletedPhasedChange(
        proto.getId(),
        fromProtoPhasedChangePlanStatus(proto.getStatus()),
        Instant.ofEpochSecond(startedAt.getSeconds(), startedAt.getNanos()),
        Instant.ofEpochSecond(completedAt.getSeconds(), completedAt.getNanos()));
  }

  private Topology.GlobalChangeOperation encodeGlobalChangeOperation(
      final GlobalChangeOperation operation) {
    final var builder =
        Topology.GlobalChangeOperation.newBuilder().setMemberId(operation.memberId().id());
    switch (operation) {
      case final MemberJoinOperation ignored ->
          builder.setMemberJoin(Topology.MemberJoinOperation.newBuilder().build());
      case final MemberLeaveOperation ignored ->
          builder.setMemberLeave(Topology.MemberLeaveOperation.newBuilder().build());
      case final MemberRemoveOperation op ->
          builder.setMemberRemove(
              Topology.MemberRemoveOperation.newBuilder()
                  .setMemberToRemove(op.memberToRemove().id())
                  .build());
      case final PreScalingOperation op ->
          builder.setPreScaling(
              Topology.PreScalingOperation.newBuilder()
                  .addAllClusterMembers(op.clusterMembers().stream().map(MemberId::id).toList())
                  .build());
      case final PostScalingOperation op ->
          builder.setPostScaling(
              Topology.PostScalingOperation.newBuilder()
                  .addAllClusterMembers(op.clusterMembers().stream().map(MemberId::id).toList())
                  .build());
      case final UpdatePartitionDistributorConfigOperation op ->
          builder.setUpdatePartitionDistributorConfig(
              Topology.UpdatePartitionDistributorConfigOperation.newBuilder()
                  .setConfig(encodePartitionDistributorConfig(op.config()))
                  .build());
    }
    return builder.build();
  }

  private GlobalChangeOperation decodeGlobalChangeOperation(
      final Topology.GlobalChangeOperation proto) {
    final var memberId = MemberId.from(proto.getMemberId());
    if (proto.hasMemberJoin()) {
      return new MemberJoinOperation(memberId);
    } else if (proto.hasMemberLeave()) {
      return new MemberLeaveOperation(memberId);
    } else if (proto.hasMemberRemove()) {
      return new MemberRemoveOperation(
          memberId, MemberId.from(proto.getMemberRemove().getMemberToRemove()));
    } else if (proto.hasPreScaling()) {
      return new PreScalingOperation(
          memberId,
          proto.getPreScaling().getClusterMembersList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } else if (proto.hasPostScaling()) {
      return new PostScalingOperation(
          memberId,
          proto.getPostScaling().getClusterMembersList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } else if (proto.hasUpdatePartitionDistributorConfig()) {
      return decodePartitionDistributorConfig(
              proto.getUpdatePartitionDistributorConfig().getConfig())
          .map(config -> new UpdatePartitionDistributorConfigOperation(memberId, config))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "UpdatePartitionDistributorConfig operation has empty config"));
    } else {
      throw new IllegalStateException("Unknown global change operation: " + proto);
    }
  }

  private Topology.PartitionGroupChangeOperation encodePartitionGroupChangeOperation(
      final PartitionGroupOperation operation) {
    final var builder =
        Topology.PartitionGroupChangeOperation.newBuilder().setMemberId(operation.memberId().id());
    switch (operation) {
      case final PartitionJoinOperation op ->
          builder.setPartitionJoin(
              Topology.PartitionJoinOperation.newBuilder()
                  .setPartitionId(op.partitionId())
                  .setPriority(op.priority())
                  .build());
      case final PartitionLeaveOperation op ->
          builder.setPartitionLeave(
              Topology.PartitionLeaveOperation.newBuilder()
                  .setPartitionId(op.partitionId())
                  .setMinimumAllowedReplicas(op.minimumAllowedReplicas())
                  .build());
      case final PartitionReconfigurePriorityOperation op ->
          builder.setPartitionReconfigurePriority(
              Topology.PartitionReconfigurePriorityOperation.newBuilder()
                  .setPartitionId(op.partitionId())
                  .setPriority(op.priority())
                  .build());
      case final PartitionForceReconfigureOperation op ->
          builder.setPartitionForceReconfigure(
              Topology.PartitionForceReconfigureOperation.newBuilder()
                  .setPartitionId(op.partitionId())
                  .addAllMembers(op.members().stream().map(MemberId::id).toList())
                  .build());
      case final PartitionDisableExporterOperation op ->
          builder.setPartitionDisableExporter(
              Topology.PartitionDisableExporterOperation.newBuilder()
                  .setPartitionId(op.partitionId())
                  .setExporterId(op.exporterId())
                  .build());
      case final PartitionDeleteExporterOperation op ->
          builder.setPartitionDeleteExporter(
              Topology.PartitionDeleteExporterOperation.newBuilder()
                  .setPartitionId(op.partitionId())
                  .setExporterId(op.exporterId())
                  .build());
      case final PartitionEnableExporterOperation op ->
          builder.setPartitionEnableExporter(encodeEnabledExporterOperation(op));
      case final PartitionBootstrapOperation op ->
          builder.setPartitionBootstrap(encodePartitionBootstrapOperation(op));
      case final DeleteHistoryOperation ignored ->
          builder.setDeleteHistory(Topology.DeleteHistoryOperation.newBuilder().build());
      case final UpdateIncarnationNumberOperation ignored ->
          builder.setUpdateIncarnationNumber(
              Topology.UpdateIncarnationNumberOperation.newBuilder().build());
      case final ModeChangeOperation op ->
          builder.setModeChange(
              Topology.ModeChangeOperation.newBuilder()
                  .setMode(toProtoTopologyMode(op.mode()))
                  .build());
      case final AwaitModeChangeOperation op ->
          builder.setAwaitModeChange(
              Topology.AwaitModeChangeOperation.newBuilder()
                  .setMode(toProtoTopologyMode(op.mode()))
                  .build());
      case final ExportingStateChangeOperation op ->
          builder.setExporterStateChange(
              Topology.ExporterStateChangeOperation.newBuilder()
                  .setState(encodeExportingState(op.state()))
                  .build());
      case StartPartitionScaleUp(final var ignoredMemberId, final var desiredPartitionCount) ->
          builder.setInitiateScaleUpPartitions(
              Topology.StartPartitionScaleUpOperation.newBuilder()
                  .setDesiredPartitionCount(desiredPartitionCount)
                  .build());
      case final AwaitRedistributionCompletion op ->
          builder.setAwaitRedistributionCompletion(
              Topology.AwaitRedistributionCompletion.newBuilder()
                  .setDesiredPartitionCount(op.desiredPartitionCount())
                  .addAllPartitionsToRedistribute(op.partitionsToRedistribute())
                  .build());
      case final AwaitRelocationCompletion op ->
          builder.setAwaitRelocationCompletion(
              Topology.AwaitRelocationCompletion.newBuilder()
                  .setDesiredPartitionCount(op.desiredPartitionCount())
                  .addAllPartitionsToRelocate(op.partitionsToRelocate())
                  .build());
      case final UpdateRoutingState op -> {
        final var routingBuilder = Topology.UpdateRoutingState.newBuilder();
        op.routingState().ifPresent(s -> routingBuilder.setRoutingState(encodeRoutingState(s)));
        builder.setUpdateRoutingState(routingBuilder);
      }
    }
    return builder.build();
  }

  private PartitionGroupOperation decodePartitionGroupChangeOperation(
      final Topology.PartitionGroupChangeOperation proto) {
    final var memberId = MemberId.from(proto.getMemberId());
    if (proto.hasPartitionJoin()) {
      return new PartitionJoinOperation(
          memberId,
          proto.getPartitionJoin().getPartitionId(),
          proto.getPartitionJoin().getPriority());
    } else if (proto.hasPartitionLeave()) {
      return new PartitionLeaveOperation(
          memberId,
          proto.getPartitionLeave().getPartitionId(),
          proto.getPartitionLeave().getMinimumAllowedReplicas());
    } else if (proto.hasPartitionReconfigurePriority()) {
      return new PartitionReconfigurePriorityOperation(
          memberId,
          proto.getPartitionReconfigurePriority().getPartitionId(),
          proto.getPartitionReconfigurePriority().getPriority());
    } else if (proto.hasPartitionForceReconfigure()) {
      return new PartitionForceReconfigureOperation(
          memberId,
          proto.getPartitionForceReconfigure().getPartitionId(),
          proto.getPartitionForceReconfigure().getMembersList().stream()
              .map(MemberId::from)
              .collect(Collectors.toSet()));
    } else if (proto.hasPartitionDisableExporter()) {
      return new PartitionDisableExporterOperation(
          memberId,
          proto.getPartitionDisableExporter().getPartitionId(),
          proto.getPartitionDisableExporter().getExporterId());
    } else if (proto.hasPartitionDeleteExporter()) {
      return new PartitionDeleteExporterOperation(
          memberId,
          proto.getPartitionDeleteExporter().getPartitionId(),
          proto.getPartitionDeleteExporter().getExporterId());
    } else if (proto.hasPartitionEnableExporter()) {
      final var enableExporter = proto.getPartitionEnableExporter();
      final Optional<String> initializeFrom =
          enableExporter.hasInitializeFrom()
              ? Optional.of(enableExporter.getInitializeFrom())
              : Optional.empty();
      return new PartitionEnableExporterOperation(
          memberId,
          enableExporter.getPartitionId(),
          enableExporter.getExporterId(),
          initializeFrom);
    } else if (proto.hasPartitionBootstrap()) {
      final var bootstrap = proto.getPartitionBootstrap();
      final Optional<DynamicPartitionConfig> partitionConfig =
          bootstrap.hasConfig()
              ? Optional.of(decodePartitionConfig(bootstrap.getConfig()))
              : Optional.empty();
      return new PartitionBootstrapOperation(
          memberId,
          bootstrap.getPartitionId(),
          bootstrap.getPriority(),
          partitionConfig,
          bootstrap.getInitializeFromConfig());
    } else if (proto.hasInitiateScaleUpPartitions()) {
      return new StartPartitionScaleUp(
          memberId, proto.getInitiateScaleUpPartitions().getDesiredPartitionCount());
    } else if (proto.hasAwaitRedistributionCompletion()) {
      final var redistribution = proto.getAwaitRedistributionCompletion();
      return new AwaitRedistributionCompletion(
          memberId,
          redistribution.getDesiredPartitionCount(),
          new TreeSet<>(redistribution.getPartitionsToRedistributeList()));
    } else if (proto.hasAwaitRelocationCompletion()) {
      final var relocation = proto.getAwaitRelocationCompletion();
      return new AwaitRelocationCompletion(
          memberId,
          relocation.getDesiredPartitionCount(),
          new TreeSet<>(relocation.getPartitionsToRelocateList()));
    } else if (proto.hasUpdateRoutingState()) {
      return new UpdateRoutingState(
          memberId, decodeRoutingState(proto.getUpdateRoutingState().getRoutingState()));
    } else if (proto.hasDeleteHistory()) {
      return new DeleteHistoryOperation(memberId);
    } else if (proto.hasUpdateIncarnationNumber()) {
      return new UpdateIncarnationNumberOperation(memberId);
    } else if (proto.hasModeChange()) {
      return new ModeChangeOperation(
          memberId, fromProtoTopologyMode(proto.getModeChange().getMode()));
    } else if (proto.hasAwaitModeChange()) {
      return new AwaitModeChangeOperation(
          memberId, fromProtoTopologyMode(proto.getAwaitModeChange().getMode()));
    } else if (proto.hasExporterStateChange()) {
      return new ExportingStateChangeOperation(
          memberId, decodeExportingState(proto.getExporterStateChange().getState()));
    } else {
      throw new IllegalStateException("Unknown partition group change operation: " + proto);
    }
  }

  private Topology.State toSerializedBrokerState(final BrokerState.State state) {
    return switch (state) {
      case UNINITIALIZED -> Topology.State.UNKNOWN;
      case JOINING -> Topology.State.JOINING;
      case ACTIVE -> Topology.State.ACTIVE;
      case LEAVING -> Topology.State.LEAVING;
      case LEFT -> Topology.State.LEFT;
    };
  }

  private BrokerState.State toBrokerLifecycleState(final Topology.State state) {
    return switch (state) {
      case UNRECOGNIZED, UNKNOWN -> BrokerState.State.UNINITIALIZED;
      case JOINING -> BrokerState.State.JOINING;
      case ACTIVE -> BrokerState.State.ACTIVE;
      case LEAVING -> BrokerState.State.LEAVING;
      case LEFT -> BrokerState.State.LEFT;
      case BOOTSTRAPPING, RECOVERING ->
          throw new IllegalStateException(
              "Broker cannot be in %s lifecycle state".formatted(state));
    };
  }

  private Topology.PhasedChangePlanStatus toProtoPhasedChangePlanStatus(
      final PhasedChangePlanStatus status) {
    return switch (status) {
      case COMPLETED -> Topology.PhasedChangePlanStatus.PHASED_CHANGE_COMPLETED;
      case FAILED -> Topology.PhasedChangePlanStatus.PHASED_CHANGE_FAILED;
      case CANCELLED -> Topology.PhasedChangePlanStatus.PHASED_CHANGE_CANCELLED;
    };
  }

  private PhasedChangePlanStatus fromProtoPhasedChangePlanStatus(
      final Topology.PhasedChangePlanStatus status) {
    return switch (status) {
      case PHASED_CHANGE_COMPLETED -> PhasedChangePlanStatus.COMPLETED;
      case PHASED_CHANGE_FAILED -> PhasedChangePlanStatus.FAILED;
      case PHASED_CHANGE_CANCELLED -> PhasedChangePlanStatus.CANCELLED;
      case PHASED_CHANGE_STATUS_UNKNOWN, UNRECOGNIZED ->
          throw new IllegalStateException("Unknown phased change status: " + status);
    };
  }

  private static Timestamp toTimestamp(final Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
