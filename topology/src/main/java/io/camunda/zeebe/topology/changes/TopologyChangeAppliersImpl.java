/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

public class TopologyChangeAppliersImpl implements TopologyChangeAppliers {

  private final PartitionChangeExecutor partitionChangeExecutor;
  private final TopologyMembershipChangeExecutor topologyMembershipChangeExecutor;

  public TopologyChangeAppliersImpl(
      final PartitionChangeExecutor partitionChangeExecutor,
      final TopologyMembershipChangeExecutor topologyMembershipChangeExecutor) {
    this.partitionChangeExecutor = partitionChangeExecutor;
    this.topologyMembershipChangeExecutor = topologyMembershipChangeExecutor;
  }

  @Override
  public OperationApplier getApplier(final TopologyChangeOperation operation) {
    if (operation instanceof final PartitionJoinOperation joinOperation) {
      return new PartitionJoinApplier(
          joinOperation.partitionId(),
          joinOperation.priority(),
          joinOperation.memberId(),
          partitionChangeExecutor);
    } else if (operation instanceof final PartitionLeaveOperation leaveOperation) {
      return new PartitionLeaveApplier(
          leaveOperation.partitionId(), leaveOperation.memberId(), partitionChangeExecutor);
    } else if (operation instanceof final MemberJoinOperation memberJoinOperation) {
      return new MemberJoinApplier(
          memberJoinOperation.memberId(), topologyMembershipChangeExecutor);
    } else if (operation instanceof final MemberLeaveOperation memberLeaveOperation) {
      return new MemberLeaveApplier(
          memberLeaveOperation.memberId(), topologyMembershipChangeExecutor);
    } else {
      return new FailingApplier(operation);
    }
  }

  static class FailingApplier implements OperationApplier {

    private final TopologyChangeOperation operation;

    public FailingApplier(final TopologyChangeOperation operation) {
      this.operation = operation;
    }

    @Override
    public Either<Exception, UnaryOperator<MemberState>> init(
        final ClusterTopology currentClusterTopology) {
      return Either.left(new UnknownOperationException(operation));
    }

    @Override
    public ActorFuture<UnaryOperator<MemberState>> apply() {
      return CompletableActorFuture.completedExceptionally(
          new UnknownOperationException(operation));
    }

    private static class UnknownOperationException extends RuntimeException {

      public UnknownOperationException(final TopologyChangeOperation operation) {
        super("Unknown topology change operation " + operation);
      }
    }
  }
}
