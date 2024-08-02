/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.ClusterOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MessageRoutingConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

public class RelocationStatusApplier implements ClusterOperationApplier {

  private final PartitionChangeExecutor partitionChangeExecutor;

  public RelocationStatusApplier(final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    final var future = new CompletableActorFuture<UnaryOperator<ClusterConfiguration>>();
    partitionChangeExecutor
        .relocationStatus()
        .onComplete(
            (completed, error) -> {
              if (error != null) {
                future.completeExceptionally(error);
              } else if (!completed) {
                // Here we throw exception so that the operation is automatically retied after a
                // delay
                // TODO: find a better way to handle this
                future.completeExceptionally(new RuntimeException("Relocation is not completed"));

              } else {
                // update cluster configuration
                future.complete(this::updateRouting);
              }
            });
    return future;
  }

  private ClusterConfiguration updateRouting(final ClusterConfiguration configuration) {
    // TODO: Here we assume the new routing is based on the new partition count. We should be able
    // to specify different routing logic.
    final var newMessageRoutingConfig =
        MessageRoutingConfiguration.fixed(configuration.partitionCount());
    return configuration.updateRouting(
        routingState -> routingState.setMessageRoutingConfiguration(newMessageRoutingConfig));
  }
}
