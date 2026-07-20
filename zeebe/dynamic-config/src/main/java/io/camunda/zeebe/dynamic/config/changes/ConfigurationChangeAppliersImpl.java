/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExporterStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.*;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;

public class ConfigurationChangeAppliersImpl implements ConfigurationChangeAppliers {

  private final PartitionChangeExecutor partitionChangeExecutor;
  private final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor;
  private final PartitionScalingChangeExecutor partitionScalingChangeExecutor;
  private final ClusterChangeExecutor clusterChangeExecutor;
  private final ModeChangeExecutor modeChangeExecutor;

  public ConfigurationChangeAppliersImpl(
      final PartitionChangeExecutor partitionChangeExecutor,
      final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      final ClusterChangeExecutor clusterChangeExecutor,
      final ModeChangeExecutor modeChangeExecutor) {
    this.partitionChangeExecutor = partitionChangeExecutor;
    this.clusterMembershipChangeExecutor = clusterMembershipChangeExecutor;
    this.partitionScalingChangeExecutor = partitionScalingChangeExecutor;
    this.clusterChangeExecutor = clusterChangeExecutor;
    this.modeChangeExecutor = modeChangeExecutor;
  }

  @Override
  public ClusterOperationApplier getApplier(final ClusterConfigurationChangeOperation operation) {
    return switch (operation) {
      case final PartitionJoinOperation joinOperation ->
          new PartitionJoinApplier(
              joinOperation.partitionId(),
              joinOperation.priority(),
              joinOperation.memberId(),
              partitionChangeExecutor);
      case final PartitionLeaveOperation leaveOperation ->
          new PartitionLeaveApplier(
              leaveOperation.partitionId(),
              leaveOperation.memberId(),
              leaveOperation.minimumAllowedReplicas(),
              partitionChangeExecutor);
      case final MemberJoinOperation memberJoinOperation ->
          new MemberJoinApplier(memberJoinOperation.memberId(), clusterMembershipChangeExecutor);
      case final MemberLeaveOperation memberLeaveOperation ->
          new MemberLeaveApplier(memberLeaveOperation.memberId(), clusterMembershipChangeExecutor);
      case final PartitionReconfigurePriorityOperation reconfigurePriorityOperation ->
          new PartitionReconfigurePriorityApplier(
              reconfigurePriorityOperation.partitionId(),
              reconfigurePriorityOperation.priority(),
              reconfigurePriorityOperation.memberId(),
              partitionChangeExecutor);
      case final PartitionForceReconfigureOperation forceReconfigureOperation ->
          new PartitionForceReconfigureApplier(
              forceReconfigureOperation.partitionId(),
              forceReconfigureOperation.memberId(),
              forceReconfigureOperation.members(),
              partitionChangeExecutor);
      case final MemberRemoveOperation memberRemoveOperation ->
          // Reuse MemberLeaveApplier, only difference is that the member applying the operation is
          // not the member that is leaving
          new MemberLeaveApplier(
              memberRemoveOperation.memberToRemove(), clusterMembershipChangeExecutor);
      case final PartitionDisableExporterOperation disableExporterOperation ->
          new PartitionDisableExporterApplier(
              disableExporterOperation.partitionId(),
              disableExporterOperation.memberId(),
              disableExporterOperation.exporterId(),
              partitionChangeExecutor);
      case final PartitionEnableExporterOperation enableExporterOperation ->
          new PartitionEnableExporterApplier(
              enableExporterOperation.partitionId(),
              enableExporterOperation.memberId(),
              enableExporterOperation.exporterId(),
              enableExporterOperation.initializeFrom(),
              partitionChangeExecutor);
      case final PartitionDeleteExporterOperation deleteExporterOperation ->
          new PartitionDeleteExporterApplier(
              deleteExporterOperation.partitionId(),
              deleteExporterOperation.memberId(),
              deleteExporterOperation.exporterId(),
              partitionChangeExecutor);
      case final PartitionBootstrapOperation bootstrapOperation ->
          new PartitionBootstrapApplier(bootstrapOperation, partitionChangeExecutor);
      case final DeleteHistoryOperation deleteHistoryOperation ->
          new DeleteHistoryApplier(deleteHistoryOperation.memberId(), clusterChangeExecutor);
      case final ScaleUpOperation scaleUpOperation ->
          switch (scaleUpOperation) {
            case StartPartitionScaleUp(
                    final var ignoredMemberId,
                    final var desiredPartitionCount) ->
                new StartPartitionScaleUpApplier(
                    partitionScalingChangeExecutor, desiredPartitionCount);
            case final AwaitRedistributionCompletion awaitRedistributionCompletion ->
                new AwaitRedistributionCompletionApplier(
                    partitionScalingChangeExecutor, awaitRedistributionCompletion);
            case final AwaitRelocationCompletion relocation ->
                new AwaitRelocationCompletionApplier(partitionScalingChangeExecutor, relocation);
          };
      case final UpdateRoutingState updateRoutingState ->
          new UpdateRoutingStateApplier(updateRoutingState, partitionScalingChangeExecutor);
      case final UpdateIncarnationNumberOperation updateIncarnationNumberOperation ->
          new UpdateIncarnationNumberApplier();
      case final PreScalingOperation preScalingOperation ->
          new PreScalingApplier(
              preScalingOperation.memberId(),
              preScalingOperation.clusterMembers(),
              clusterChangeExecutor);
      case final PostScalingOperation postScalingOperation ->
          new PostScalingApplier(
              postScalingOperation.memberId(),
              postScalingOperation.clusterMembers(),
              clusterChangeExecutor);
      case final UpdatePartitionDistributorConfigOperation updateDistributorConfig ->
          new UpdatePartitionDistributorConfigApplier(updateDistributorConfig);
      case final ModeChangeOperation modeChangeOperation ->
          switch (modeChangeOperation.mode()) {
            case RECOVERING ->
                new EnterRecoveryApplier(modeChangeOperation.memberId(), modeChangeExecutor);
            case PROCESSING ->
                new ExitRecoveryApplier(modeChangeOperation.memberId(), modeChangeExecutor);
          };
      case final AwaitModeChangeOperation awaitModeChangeOperation ->
          new AwaitModeChangeApplier(
              awaitModeChangeOperation.memberId(),
              awaitModeChangeOperation.mode(),
              modeChangeExecutor);
      case final ExporterStateChangeOperation exporterStateChangeOperation ->
          new ExporterStateChangeApplier(
              exporterStateChangeOperation.memberId(),
              exporterStateChangeOperation.state(),
              partitionChangeExecutor);
    };
  }
}
