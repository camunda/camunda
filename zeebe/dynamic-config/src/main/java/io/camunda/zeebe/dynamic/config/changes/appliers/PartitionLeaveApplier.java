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
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation}, operating on a single
 * named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code PartitionLeaveApplier} in
 * {@code changes/}, which this does not replace or modify. A partition leave operation is executed
 * when a member wants to stop replicating a partition; this is allowed only when the member is
 * already replicating the partition.
 */
public final class PartitionLeaveApplier implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final MemberId localMemberId;
  private final int minimumAllowedReplicas;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionLeaveApplier(
      final MemberId localMemberId,
      final int partitionId,
      final int minimumAllowedReplicas,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.localMemberId = localMemberId;
    this.partitionId = partitionId;
    this.minimumAllowedReplicas = minimumAllowedReplicas;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {

    if (!currentGlobalConfiguration.hasMember(localMemberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to leave partition, but the local member does not exist in the cluster"));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(localMemberId);
    final var localPartition = localBroker == null ? null : localBroker.getPartition(partitionId);
    if (localPartition == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to leave partition, but the local member does not have the partition %d"
                  .formatted(partitionId)));
    }

    if (localPartition.state() == PartitionState.State.LEAVING) {
      // If partition state is already set to leaving, then we don't need to set it again. This can
      // happen if the node was restarted while applying the leave operation. To ensure that the
      // configuration change can make progress, we do not treat this as an error.
      return Either.right(UnaryOperator.identity());
    }

    final var partitionReplicaCount =
        currentPartitionGroupConfiguration.members().values().stream()
            .filter(broker -> broker.hasPartition(partitionId))
            .count();
    if (partitionReplicaCount <= minimumAllowedReplicas) {
      return Either.left(
          new IllegalStateException(
              "Expected to leave partition, but the partition %d has %d replicas but minimum allowed replicas is %d"
                  .formatted(partitionId, partitionReplicaCount, minimumAllowedReplicas)));
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
        .leave(partitionId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(
                    group ->
                        group.updateMember(
                            localMemberId, broker -> broker.removePartition(partitionId)));
              } else {
                result.completeExceptionally(error);
              }
            });

    return result;
  }
}
