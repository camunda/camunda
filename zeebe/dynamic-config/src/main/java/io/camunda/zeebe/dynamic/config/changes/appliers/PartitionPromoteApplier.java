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
 * Applier for {@code PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation},
 * operating on a single named {@link PartitionGroupConfiguration}. Promotes the local member from a
 * learner to a full voting member of the partition's replication group - the second phase of a
 * two-phase join.
 *
 * <p>The partition's leader accepts the promotion only once the member is caught up on the log, so
 * {@link #apply()} fails until then and is retried by the reconciler's backoff; the retry loop is
 * the catch-up poll. {@code init} must therefore stay free of transient conditions - an init
 * failure is not retried on a timer.
 */
public final class PartitionPromoteApplier implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final MemberId localMemberId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionPromoteApplier(
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

    final boolean localMemberIsActiveInCluster =
        currentGlobalConfiguration.hasMember(localMemberId)
            && currentGlobalConfiguration.getMember(localMemberId).state()
                == BrokerState.State.ACTIVE;
    if (!localMemberIsActiveInCluster) {
      return Either.left(
          new IllegalStateException(
              "Expected to promote member in partition %d, but the local member is not an active member of the cluster"
                  .formatted(partitionId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(localMemberId);
    final var localPartition = localBroker == null ? null : localBroker.getPartition(partitionId);
    if (localPartition == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to promote member in partition %d, but the local member does not have the partition"
                  .formatted(partitionId)));
    }

    return switch (localPartition.state()) {
      // ACTIVE can happen if the node restarted after the promotion completed but before the
      // operation was recorded. The retried promotion is a no-op at the raft layer, so we do not
      // treat this as an error.
      case LEARNER, ACTIVE -> Either.right(UnaryOperator.identity());
      default ->
          Either.left(
              new IllegalStateException(
                  "Expected to promote member in partition %d, but the partition is in state %s"
                      .formatted(partitionId, localPartition.state())));
    };
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .promote(partitionId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(
                    group ->
                        group.updateMember(
                            localMemberId,
                            broker ->
                                broker.updatePartition(partitionId, PartitionState::toActive)));
              } else {
                result.completeExceptionally(error);
              }
            });

    return result;
  }
}
