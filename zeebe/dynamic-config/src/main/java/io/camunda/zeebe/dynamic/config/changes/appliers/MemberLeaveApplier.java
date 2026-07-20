/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code GlobalChangeOperation.MemberLeaveOperation} and {@code
 * MemberRemoveOperation}, operating on {@link GlobalConfiguration}. Mirrors the legacy {@code
 * MemberLeaveApplier} in {@code changes/}, which this does not replace or modify; also reused for
 * both operations, since the only difference between them is which member applies the operation.
 *
 * <p>The legacy applier additionally rejects the operation if the member still has partitions
 * assigned, since the flat {@code ClusterConfiguration} carries both broker lifecycle and partition
 * assignment together. In the new model that assignment is split out per group into {@code
 * PartitionGroupConfiguration}, so this check needs every group, not just one — {@link #init}
 * therefore receives the whole {@link CurrentClusterConfiguration} rather than a single group.
 */
public final class MemberLeaveApplier implements GlobalConfigurationChangeApplier {

  private final MemberId memberId;
  private final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor;

  public MemberLeaveApplier(
      final MemberId memberId,
      final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor) {
    this.memberId = memberId;
    this.clusterMembershipChangeExecutor = clusterMembershipChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<GlobalConfiguration>> init(
      final CurrentClusterConfiguration currentClusterConfiguration) {
    final GlobalConfiguration globalConfiguration =
        currentClusterConfiguration.globalConfiguration();
    if (!globalConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to remove member %s, but the member is not part of the cluster"
                  .formatted(memberId)));
    }

    if (globalConfiguration.getMember(memberId).state() == State.LEFT) {
      return Either.left(
          new IllegalStateException(
              "Expected to remove member %s, but the member is already in state LEFT"
                  .formatted(memberId)));
    }

    final var partitionsByGroup = new TreeMap<String, Object>();
    currentClusterConfiguration
        .partitionGroups()
        .forEach(
            (groupId, group) -> {
              final var broker = group.getMember(memberId);
              if (broker != null && !broker.partitions().isEmpty()) {
                partitionsByGroup.put(groupId, broker.partitions());
              }
            });
    if (!partitionsByGroup.isEmpty()) {
      return Either.left(
          new IllegalStateException(
              "Expected to remove member %s, but the member still has partitions assigned. Partitions by group: %s"
                  .formatted(memberId, partitionsByGroup)));
    }

    return Either.right(
        config -> config.updateMember(memberId, broker -> broker.setState(State.LEAVING)));
  }

  @Override
  public ActorFuture<UnaryOperator<GlobalConfiguration>> apply() {
    final var future = new CompletableActorFuture<UnaryOperator<GlobalConfiguration>>();
    clusterMembershipChangeExecutor
        .removeBroker(memberId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                future.complete(
                    config -> config.updateMember(memberId, broker -> broker.setState(State.LEFT)));
              } else {
                future.completeExceptionally(error);
              }
            });

    return future;
  }
}
