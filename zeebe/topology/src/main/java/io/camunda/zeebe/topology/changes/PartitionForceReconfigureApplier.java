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
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers.ClusterOperationApplier;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState.State;
import io.camunda.zeebe.util.Either;
import java.util.Collection;
import java.util.function.UnaryOperator;

/** Force configure a partition to include only the given members in the replication group. */
final class PartitionForceReconfigureApplier implements ClusterOperationApplier {

  private final int partitionId;
  private final MemberId memberId;
  private final Collection<MemberId> members;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionForceReconfigureApplier(
      final int partitionId,
      final MemberId memberId,
      final Collection<MemberId> members,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.memberId = memberId;
    this.members = members;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterTopology>> init(
      final ClusterTopology currentClusterTopology) {

    if (members.isEmpty()) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to reconfigure partition '%d' via member '%s', but the new configuration is empty",
                  partitionId, memberId)));
    }

    if (!members.contains(memberId)) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to reconfigure partition '%d' via member '%s', but the member is not part of the new configuration '%s'",
                  partitionId, memberId, members)));
    }

    for (final MemberId member : members) {
      final boolean memberIsActive =
          currentClusterTopology.hasMember(member)
              && currentClusterTopology.getMember(member).state() == State.ACTIVE;
      if (!memberIsActive) {
        return Either.left(
            new IllegalStateException(
                String.format(
                    "Expected to reconfigure partition '%d' with members '%s', but member '%s' is not active.",
                    partitionId, members, memberId)));
      }

      final var memberState = currentClusterTopology.getMember(member);
      if (!memberState.hasPartition(partitionId)) {
        return Either.left(
            new IllegalStateException(
                String.format(
                    "Expected to reconfigure partition '%d' with members '%s', but member '%s' does not have the partition.",
                    partitionId, members, memberId)));
      }
    }
    // No need to change the state yet
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterTopology>> apply() {
    final var future = new CompletableActorFuture<UnaryOperator<ClusterTopology>>();
    partitionChangeExecutor
        .forceReconfigure(partitionId, members)
        .onComplete(
            (ignore, error) -> {
              if (error != null) {
                future.completeExceptionally(error);
              } else {
                future.complete(this::removePartitionFromNonMembers);
              }
            });

    return future;
  }

  private ClusterTopology removePartitionFromNonMembers(final ClusterTopology clusterTopology) {
    // remove this partition from the state of non-members
    ClusterTopology updatedTopology = clusterTopology;
    for (final var member : clusterTopology.members().keySet()) {
      if (!members.contains(member)
          && clusterTopology.getMember(member).hasPartition(partitionId)) {
        updatedTopology = updatedTopology.updateMember(member, m -> m.removePartition(partitionId));
      }
    }
    return updatedTopology;
  }
}
