/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers.OperationApplier;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/** A Member join operation is applied when the member is not already part of the topology. */
final class MemberJoinApplier implements OperationApplier {

  private final MemberId memberId;
  private final TopologyMembershipChangeExecutor topologyMembershipChangeExecutor;

  MemberJoinApplier(
      final MemberId memberId,
      final TopologyMembershipChangeExecutor topologyMembershipChangeExecutor) {
    this.memberId = memberId;
    this.topologyMembershipChangeExecutor = topologyMembershipChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> init(
      final ClusterTopology currentClusterTopology) {
    if (currentClusterTopology.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to join member %s, but the member is already part of the topology",
                  memberId)));
    }

    return Either.right(
        ignore ->
            // Member doesn't exist, so create a new state
            MemberState.uninitialized().toJoining());
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> apply() {
    final var future = new CompletableActorFuture<UnaryOperator<MemberState>>();
    topologyMembershipChangeExecutor
        .addBroker(memberId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                future.complete(MemberState::toActive);
              } else {
                future.completeExceptionally(error);
              }
            });
    return future;
  }
}
