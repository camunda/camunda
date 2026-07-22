/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Collection;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation}, operating
 * on a single named {@link PartitionGroupConfiguration} as a whole (not member-scoped). Mirrors the
 * legacy {@code PartitionForceReconfigureApplier} in {@code changes/}, which this does not replace
 * or modify. Force-reconfigures a partition to include only the given members in the replication
 * group.
 */
public final class PartitionForceReconfigureApplier
    implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final MemberId memberId;
  private final Collection<MemberId> members;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionForceReconfigureApplier(
      final MemberId memberId,
      final int partitionId,
      final Collection<MemberId> members,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.memberId = memberId;
    this.members = members;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {

    if (members.isEmpty()) {
      return Either.left(
          new IllegalStateException(
              "Expected to reconfigure partition '%d' via member '%s', but the new configuration is empty"
                  .formatted(partitionId, memberId)));
    }

    if (!members.contains(memberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to reconfigure partition '%d' via member '%s', but the member is not part of the new configuration '%s'"
                  .formatted(partitionId, memberId, members)));
    }

    for (final MemberId member : members) {
      final boolean memberIsActive =
          currentGlobalConfiguration.hasMember(member)
              && requireNonNull(currentGlobalConfiguration.getMember(member)).state()
                  == BrokerState.State.ACTIVE;
      if (!memberIsActive) {
        return Either.left(
            new IllegalStateException(
                "Expected to reconfigure partition '%d' with members '%s', but member '%s' is not active."
                    .formatted(partitionId, members, member)));
      }

      final var memberState = currentPartitionGroupConfiguration.getMember(member);
      if (memberState == null || !memberState.hasPartition(partitionId)) {
        return Either.left(
            new IllegalStateException(
                "Expected to reconfigure partition '%d' with members '%s', but member '%s' does not have the partition."
                    .formatted(partitionId, members, member)));
      }
    }
    // No need to change the state yet
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final var future = new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();
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

  private PartitionGroupConfiguration removePartitionFromNonMembers(
      final PartitionGroupConfiguration group) {
    // remove this partition from the state of non-members
    var updatedGroup = group;
    for (final var member : group.members().keySet()) {
      if (!members.contains(member)
          && requireNonNull(group.getMember(member)).hasPartition(partitionId)) {
        updatedGroup =
            updatedGroup.updateMember(member, broker -> broker.removePartition(partitionId));
      }
    }
    return updatedGroup;
  }
}
