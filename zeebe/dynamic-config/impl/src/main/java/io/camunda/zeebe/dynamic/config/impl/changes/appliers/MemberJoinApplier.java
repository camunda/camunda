/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.impl.changes.ClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.impl.changes.GlobalConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * Applier for {@code GlobalChangeOperation.MemberJoinOperation}, operating on {@link
 * GlobalConfiguration}.
 *
 * <p>{@link GlobalConfiguration#addMember} and {@link GlobalConfiguration#updateMember} each fail
 * on the wrong precondition, so this applier is responsible for choosing between them: {@link
 * #init} adds the member, and {@link #apply()} updates it.
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
      final var currentState = currentGlobalConfiguration.getMember(memberId).state();
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
