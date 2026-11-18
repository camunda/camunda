/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl.changes.appliers;

import io.camunda.zeebe.dynamic.config.impl.changes.ClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.impl.changes.GlobalConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.SystemPartitionRemoveMembersOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * Applies phase 0 of a force-remove plan (system partition design 5.6.3): the executor removes the
 * brokers from the system partition. The operation does not change the members of the
 * configuration; the {@code MemberRemove} operations of a later phase do that. A failure, for
 * example a failed completeness check, fails the operation, and the plan retries it.
 */
public final class SystemPartitionRemoveMembersApplier implements GlobalConfigurationChangeApplier {

  private final SystemPartitionRemoveMembersOperation operation;
  private final ClusterMembershipChangeExecutor executor;

  public SystemPartitionRemoveMembersApplier(
      final SystemPartitionRemoveMembersOperation operation,
      final ClusterMembershipChangeExecutor executor) {
    this.operation = operation;
    this.executor = executor;
  }

  @Override
  public Either<Exception, UnaryOperator<GlobalConfiguration>> init(
      final CurrentClusterConfiguration currentClusterConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<GlobalConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<GlobalConfiguration>>();
    executor
        .removeSystemPartitionMembers(operation.removedBrokers(), operation.acceptPossibleLoss())
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
