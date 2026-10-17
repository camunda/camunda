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
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation},
 * operating on a single named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code
 * PartitionReconfigurePriorityApplier} in {@code changes/}, which this does not replace or modify.
 */
public final class PartitionReconfigurePriorityApplier
    implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final int newPriority;
  private final MemberId localMemberId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionReconfigurePriorityApplier(
      final MemberId localMemberId,
      final int partitionId,
      final int newPriority,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.localMemberId = localMemberId;
    this.partitionId = partitionId;
    this.newPriority = newPriority;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    final boolean localMemberIsActiveInCluster =
        currentGlobalConfiguration.hasMember(localMemberId)
            && currentGlobalConfiguration.getMember(localMemberId).state()
                == BrokerState.State.ACTIVE;
    if (!localMemberIsActiveInCluster) {
      return Either.left(
          new IllegalStateException(
              "Expected to change priority of a partition, but the local member is not active"));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(localMemberId);
    final var localPartition = localBroker == null ? null : localBroker.getPartition(partitionId);
    if (localPartition == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to change priority of partition %d, but the local member does not have the partition"
                  .formatted(partitionId)));
    }
    if (localPartition.state() != PartitionState.State.ACTIVE) {
      return Either.left(
          new IllegalStateException(
              "Expected to change priority of partition %d, but the local member has partition in state %s"
                  .formatted(partitionId, localPartition.state())));
    }

    // Nothing to change in the state
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .reconfigurePriority(partitionId, newPriority)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(
                    group ->
                        group.updateMember(
                            localMemberId,
                            broker ->
                                broker.updatePartition(
                                    partitionId,
                                    partitionState ->
                                        new PartitionState(
                                            partitionState.state(),
                                            newPriority,
                                            partitionState.config()))));
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }
}
