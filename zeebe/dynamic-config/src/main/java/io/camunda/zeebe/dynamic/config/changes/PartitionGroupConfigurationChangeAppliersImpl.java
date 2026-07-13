/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionBootstrapApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionJoinApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionLeaveApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionReconfigurePriorityApplier;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;

public final class PartitionGroupConfigurationChangeAppliersImpl
    implements PartitionGroupConfigurationChangeAppliers {

  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionGroupConfigurationChangeAppliersImpl(
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionChangeExecutor = partitionChangeExecutor;
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
      default ->
          throw new UnsupportedOperationException(
              "No new-model applier implemented yet for %s".formatted(operation));
    };
  }
}
