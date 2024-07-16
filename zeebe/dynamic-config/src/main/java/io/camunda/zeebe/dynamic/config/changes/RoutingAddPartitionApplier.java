/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.ClusterOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

public class RoutingAddPartitionApplier implements ClusterOperationApplier {

  private final int partitionId;
  private final MemberId memberId;

  public RoutingAddPartitionApplier(final int partitionId, final MemberId memberId) {
    this.partitionId = partitionId;
    this.memberId = memberId;
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {
    // TODO: Validations
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    // TODO: check if partition is ready to be added to routing

    final UnaryOperator<ClusterConfiguration> updater =
        current ->
            current.updateRouting(routingState -> routingState.addActivePartition(partitionId));
    return CompletableActorFuture.completed(updater);
  }
}
