/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.*;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;

public class ConfigurationChangeAppliersImpl implements ConfigurationChangeAppliers {

  private final PartitionChangeExecutor partitionChangeExecutor;
  private final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor;
  private final PartitionScalingChangeExecutor partitionScalingChangeExecutor;
  private final ClusterChangeExecutor clusterChangeExecutor;

  public ConfigurationChangeAppliersImpl(
      final PartitionChangeExecutor partitionChangeExecutor,
      final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      final ClusterChangeExecutor clusterChangeExecutor) {
    this.partitionChangeExecutor = partitionChangeExecutor;
    this.clusterMembershipChangeExecutor = clusterMembershipChangeExecutor;
    this.partitionScalingChangeExecutor = partitionScalingChangeExecutor;
    this.clusterChangeExecutor = clusterChangeExecutor;
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
    };
  }
}
