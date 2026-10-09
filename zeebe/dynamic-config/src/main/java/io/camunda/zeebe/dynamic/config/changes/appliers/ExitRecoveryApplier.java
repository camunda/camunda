/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code PartitionGroupOperation.ModeChangeOperation} targeting {@link
 * Mode#PROCESSING}, operating on a single named {@link PartitionGroupConfiguration}. Mirrors the
 * legacy {@code ExitRecoveryApplier} in {@code changes/}, which this does not replace or modify.
 * See {@link EnterRecoveryApplier} for the cluster-lifecycle vs. per-group-mode split rationale.
 *
 * <p>Writes only {@code memberId}'s own entry. {@link #apply()} sets its mode to {@link
 * Mode#PROCESSING}, and {@link #init} drops the partitions the group does not route over, which a
 * restore from a backup with fewer partitions leaves behind: they hold no data and must not be
 * started empty when the broker leaves recovery. That drop happens in {@link #init} rather than
 * {@link #apply()} because {@code apply()} is what starts the partitions, from the configuration as
 * it is by then. It only applies to a stable routing, {@code AllPartitions(n)}: while a scale up
 * adds partitions the routing is unstable, and a broker leaving recovery without a restore holds
 * exactly the partitions it routes over, so neither is affected.
 *
 * <p>{@code RestoreRequestTransformer} relies on that scoping to give every broker its own mode
 * change, ordered after the routing state update and unordered against each other. A write added
 * here that reached another member, or the group as a whole, would need dependency edges there
 * ordering it against the other brokers' mode changes.
 */
public final class ExitRecoveryApplier implements PartitionGroupConfigurationChangeApplier {

  private final MemberId memberId;
  private final ModeChangeExecutor modeChangeExecutor;

  public ExitRecoveryApplier(final MemberId memberId, final ModeChangeExecutor modeChangeExecutor) {
    this.memberId = memberId;
    this.modeChangeExecutor = modeChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    final boolean localMemberIsActiveInCluster =
        currentGlobalConfiguration.hasMember(memberId)
            && currentGlobalConfiguration.getMember(memberId).state() == BrokerState.State.ACTIVE;
    if (!localMemberIsActiveInCluster) {
      return Either.left(
          new IllegalStateException(
              "Expected to exit recovery for member %s, but the member is not an active member of the cluster"
                  .formatted(memberId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(memberId);
    if (localBroker == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to exit recovery for member %s, but the member is not part of this partition group"
                  .formatted(memberId)));
    }

    // Already PROCESSING: this can happen if the node restarted while applying this operation.
    // To ensure that the configuration change can make progress, we do not treat this as an
    // error.
    return Either.right(this::withoutUnroutedPartitions);
  }

  /**
   * {@code group} without the local member's partitions above the count the group routes requests
   * over, once that routing is stable. Read from {@code group} when applied, not when planned, so
   * that it sees the routing a restore has just written.
   */
  private PartitionGroupConfiguration withoutUnroutedPartitions(
      final PartitionGroupConfiguration group) {
    final var routedPartitionCount =
        group
            .routingState()
            .map(RoutingState::requestHandling)
            .filter(AllPartitions.class::isInstance)
            .map(AllPartitions.class::cast)
            .map(AllPartitions::partitionCount);
    final var localBroker = group.getMember(memberId);
    if (routedPartitionCount.isEmpty() || localBroker == null) {
      return group;
    }
    final var unrouted =
        localBroker.partitions().keySet().stream()
            .filter(partitionId -> partitionId > routedPartitionCount.get())
            .toList();
    if (unrouted.isEmpty()) {
      return group;
    }
    return group.updateMember(
        memberId,
        broker -> {
          var updated = broker;
          for (final var partitionId : unrouted) {
            updated = updated.removePartition(partitionId);
          }
          return updated;
        });
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> result =
        new CompletableActorFuture<>();
    modeChangeExecutor
        .exitRecovery()
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(
                    group ->
                        group.updateMember(memberId, broker -> broker.setMode(Mode.PROCESSING)));
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }
}
