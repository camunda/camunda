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
import io.camunda.zeebe.dynamic.config.changes.ClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code GlobalChangeOperation.MemberJoinOperation}, operating on {@link
 * GlobalConfiguration} instead of the legacy {@code ClusterConfiguration}. Mirrors the legacy
 * {@code MemberJoinApplier} in {@code changes/}, which this does not replace or modify.
 *
 * <p>Unlike the legacy {@code ClusterConfiguration#updateMember}, which upserts (adds if absent,
 * updates if present) via a single method, {@link GlobalConfiguration#addMember} and {@link
 * GlobalConfiguration#updateMember} are split and each fail on the wrong precondition. This applier
 * is therefore responsible for choosing between them, rather than that choice being hidden inside a
 * shared update method as in the legacy model.
 */
public final class MemberJoinApplier implements GlobalConfigurationChangeApplier {

  private final MemberId memberId;
  private final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor;

  public MemberJoinApplier(
      final MemberId memberId,
      final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor) {
    this.memberId = memberId;
    this.clusterMembershipChangeExecutor = clusterMembershipChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<GlobalConfiguration>> init(
      final CurrentClusterConfiguration currentClusterConfiguration) {
    final var currentGlobalConfiguration = currentClusterConfiguration.globalConfiguration();
    if (currentGlobalConfiguration.hasMember(memberId)) {
      final var currentState =
          requireNonNull(currentGlobalConfiguration.getMember(memberId)).state();
      if (currentState != State.JOINING) {
        return Either.left(
            new IllegalStateException(
                "Expected to join member %s, but the member is already part of the cluster"
                    .formatted(memberId)));
      }
      // Already JOINING: this can happen if the node restarted while applying the join operation.
      // To ensure that the configuration change can make progress, we do not treat this as an
      // error.
      return Either.right(UnaryOperator.identity());
    }

    return Either.right(
        config -> config.addMember(memberId, BrokerState.uninitialized().setState(State.JOINING)));
  }

  @Override
  public ActorFuture<UnaryOperator<GlobalConfiguration>> apply() {
    final var future = new CompletableActorFuture<UnaryOperator<GlobalConfiguration>>();
    clusterMembershipChangeExecutor
        .addBroker(memberId)
        .onComplete(
            (ignored, error) -> {
              if (error == null) {
                future.complete(
                    config ->
                        config.updateMember(memberId, broker -> broker.setState(State.ACTIVE)));
              } else {
                future.completeExceptionally(error);
              }
            });
    return future;
  }
}
