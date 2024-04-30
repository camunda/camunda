/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
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
  public ClusterOperationApplier getApplier(final TopologyChangeOperation operation) {
    return switch (operation) {
      case final PartitionJoinOperation joinOperation ->
          new PartitionJoinApplier(
              joinOperation.partitionId(),
              joinOperation.priority(),
              joinOperation.memberId(),
              partitionChangeExecutor);
      case final PartitionLeaveOperation leaveOperation ->
          new PartitionLeaveApplier(
              leaveOperation.partitionId(), leaveOperation.memberId(), partitionChangeExecutor);
      case final MemberJoinOperation memberJoinOperation ->
          new MemberJoinApplier(memberJoinOperation.memberId(), topologyMembershipChangeExecutor);
      case final MemberLeaveOperation memberLeaveOperation ->
          new MemberLeaveApplier(memberLeaveOperation.memberId(), topologyMembershipChangeExecutor);
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
              memberRemoveOperation.memberToRemove(), topologyMembershipChangeExecutor);
      case null, default -> new FailingApplier(operation);
    };
  }

  static class FailingApplier implements MemberOperationApplier {

    private final TopologyChangeOperation operation;

    public FailingApplier(final TopologyChangeOperation operation) {
      this.operation = operation;
    }

    @Override
    public MemberId memberId() {
      return operation.memberId();
    }

    @Override
    public Either<Exception, UnaryOperator<MemberState>> initMemberState(
        final ClusterTopology currentClusterTopology) {
      return Either.left(new UnknownOperationException(operation));
    }

    @Override
    public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
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
