/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.changes.NoopTopologyChangeAppliers.NoopApplier;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionOperation;

public class TopologyChangeAppliersImpl implements TopologyChangeAppliers {

  private final PartitionTopologyChangeExecutor partitionTopologyChangeExecutor;
  private final MemberId localMemberId;

  public TopologyChangeAppliersImpl(
      final PartitionTopologyChangeExecutor partitionTopologyChangeExecutor,
      final MemberId localMemberId) {
    this.partitionTopologyChangeExecutor = partitionTopologyChangeExecutor;
    this.localMemberId = localMemberId;
  }

  @Override
  public OperationApplier getApplier(final TopologyChangeOperation operation) {
    if (operation.operation().isPartitionOperation()) {
      return getPartitionApplier((PartitionOperation) operation.operation());
    }
    return new NoopApplier();
  }

  private OperationApplier getPartitionApplier(final PartitionOperation operation) {
    return switch (operation.operationType()) {
      case JOIN -> new PartitionJoinApplier(
          operation.partitionId(),
          operation.priority().orElse(-1),
          localMemberId,
          partitionTopologyChangeExecutor);
      default -> new NoopApplier();
    };
  }
}
