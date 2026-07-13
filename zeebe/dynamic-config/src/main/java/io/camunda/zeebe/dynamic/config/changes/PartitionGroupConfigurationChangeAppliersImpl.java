/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.changes.appliers.PartitionJoinApplier;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;

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
      default ->
          throw new UnsupportedOperationException(
              "No new-model applier implemented yet for %s".formatted(operation));
    };
  }
}
