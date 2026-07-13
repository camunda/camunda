/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionBootstrapApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionDeleteExporterApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionDisableExporterApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionEnableExporterApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionForceReconfigureApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionJoinApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionLeaveApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionReconfigurePriorityApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.StartPartitionScaleUpApplier;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;

public final class PartitionGroupConfigurationChangeAppliersImpl
    implements PartitionGroupConfigurationChangeAppliers {

  private final PartitionChangeExecutor partitionChangeExecutor;
  private final PartitionScalingChangeExecutor partitionScalingChangeExecutor;

  public PartitionGroupConfigurationChangeAppliersImpl(
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor) {
    this.partitionChangeExecutor = partitionChangeExecutor;
    this.partitionScalingChangeExecutor = partitionScalingChangeExecutor;
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
            default ->
                throw new UnsupportedOperationException(
                    "No new-model applier implemented yet for %s".formatted(op));
          };
      default ->
          throw new UnsupportedOperationException(
              "No new-model applier implemented yet for %s".formatted(operation));
    };
  }
}
