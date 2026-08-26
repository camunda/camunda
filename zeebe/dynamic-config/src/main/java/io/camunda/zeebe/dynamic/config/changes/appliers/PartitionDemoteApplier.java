/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * Applier for {@code PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation},
 * operating on a single named {@link PartitionGroupConfiguration}. Demotes the local member to a
 * non-voting member of the partition's replication group - the first phase of a two-phase leave, so
 * that the subsequent leave commits without the departing member's participation.
 *
 * <p>The partition state moves to {@code LEAVING} already at the demotion, which the leave
 * applier's re-entry branch accepts; the leave removes the partition entry.
 */
public final class PartitionDemoteApplier implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final MemberId localMemberId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionDemoteApplier(
      final MemberId localMemberId,
      final int partitionId,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.localMemberId = localMemberId;
    this.partitionId = partitionId;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {

    if (!currentGlobalConfiguration.hasMember(localMemberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to demote member in partition %d, but the local member does not exist in the cluster"
                  .formatted(partitionId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(localMemberId);
    final var localPartition = localBroker == null ? null : localBroker.getPartition(partitionId);
    if (localPartition == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to demote member in partition %d, but the local member does not have the partition"
                  .formatted(partitionId)));
    }

    if (localPartition.state() == PartitionState.State.LEAVING) {
      // The node restarted while applying the demote operation. The retried demotion is a no-op at
      // the raft layer, so we do not treat this as an error.
      return Either.right(UnaryOperator.identity());
    }

    // A non-empty replication group without any voting member could neither elect a leader nor
    // commit, and the leader rejects such a configuration - the demotion could never succeed and
    // the change would be stuck. Transformers only emit a demotion when another active member
    // remains; this guards against plans that violate that.
    final var otherActiveReplicaExists =
        currentPartitionGroupConfiguration.members().entrySet().stream()
            .filter(entry -> !entry.getKey().equals(localMemberId))
            .map(entry -> entry.getValue().getPartition(partitionId))
            .anyMatch(
                partition -> partition != null && partition.state() == PartitionState.State.ACTIVE);
    if (!otherActiveReplicaExists) {
      return Either.left(
          new IllegalStateException(
              "Expected to demote member in partition %d, but no other member has the partition in active state"
                  .formatted(partitionId)));
    }

    return Either.right(
        group ->
            group.updateMember(
                localMemberId,
                broker -> broker.updatePartition(partitionId, PartitionState::toLeaving)));
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .demote(partitionId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(UnaryOperator.identity());
              } else {
                result.completeExceptionally(error);
              }
            });

    return result;
  }
}
