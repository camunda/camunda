/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.changes.appliers.AwaitModeChangeApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.AwaitRedistributionCompletionApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.AwaitRelocationCompletionApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.DeleteHistoryApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.EnterRecoveryApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.ExitRecoveryApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionBootstrapApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionDeleteExporterApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionDisableExporterApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionEnableExporterApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionForceReconfigureApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionJoinApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionLeaveApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionReconfigurePriorityApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.StartPartitionScaleUpApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.UpdateIncarnationNumberApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.UpdateRoutingStateApplier;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;

public final class PartitionGroupConfigurationChangeAppliersImpl
    implements PartitionGroupConfigurationChangeAppliers {

  private final PartitionChangeExecutor partitionChangeExecutor;
  private final PartitionScalingChangeExecutor partitionScalingChangeExecutor;
  private final ClusterChangeExecutor clusterChangeExecutor;
  private final ModeChangeExecutor modeChangeExecutor;

  public PartitionGroupConfigurationChangeAppliersImpl(
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      final ClusterChangeExecutor clusterChangeExecutor,
      final ModeChangeExecutor modeChangeExecutor) {
    this.partitionChangeExecutor = partitionChangeExecutor;
    this.partitionScalingChangeExecutor = partitionScalingChangeExecutor;
    this.clusterChangeExecutor = clusterChangeExecutor;
    this.modeChangeExecutor = modeChangeExecutor;
  }

  @Override
  public PartitionGroupConfigurationChangeApplier getApplier(
      final PartitionGroupOperation operation) {
    return switch (operation) {
      case final PartitionJoinOperation op ->
          new PartitionJoinApplier(
              op.memberId(), op.partitionId(), op.priority(), partitionChangeExecutor);
      case final PartitionLeaveOperation op ->
          new PartitionLeaveApplier(
              op.memberId(),
              op.partitionId(),
              op.minimumAllowedReplicas(),
              partitionChangeExecutor);
      case final PartitionBootstrapOperation op ->
          new PartitionBootstrapApplier(op, partitionChangeExecutor);
      case final PartitionReconfigurePriorityOperation op ->
          new PartitionReconfigurePriorityApplier(
              op.memberId(), op.partitionId(), op.priority(), partitionChangeExecutor);
      case final PartitionForceReconfigureOperation op ->
          new PartitionForceReconfigureApplier(
              op.memberId(), op.partitionId(), op.members(), partitionChangeExecutor);
      case final PartitionEnableExporterOperation op ->
          new PartitionEnableExporterApplier(
              op.memberId(),
              op.partitionId(),
              op.exporterId(),
              op.initializeFrom(),
              partitionChangeExecutor);
      case final PartitionDisableExporterOperation op ->
          new PartitionDisableExporterApplier(
              op.memberId(), op.partitionId(), op.exporterId(), partitionChangeExecutor);
      case final PartitionDeleteExporterOperation op ->
          new PartitionDeleteExporterApplier(
              op.memberId(), op.partitionId(), op.exporterId(), partitionChangeExecutor);
      case final ScaleUpOperation op ->
          switch (op) {
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
      case final UpdateRoutingState op ->
          new UpdateRoutingStateApplier(op, partitionScalingChangeExecutor);
      case final UpdateIncarnationNumberOperation ignored -> new UpdateIncarnationNumberApplier();
      case final DeleteHistoryOperation ignored -> new DeleteHistoryApplier(clusterChangeExecutor);
      case final ModeChangeOperation op ->
          switch (op.mode()) {
            case RECOVERING -> new EnterRecoveryApplier(op.memberId(), modeChangeExecutor);
            case PROCESSING -> new ExitRecoveryApplier(op.memberId(), modeChangeExecutor);
          };
      case final AwaitModeChangeOperation op ->
          new AwaitModeChangeApplier(op.memberId(), op.mode(), modeChangeExecutor);
    };
  }
}
