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
import io.camunda.zeebe.util.Either;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Applier for the PostScalingOperation. This applier finalizes a member after scaling by invoking
 * the post-scaling callback on the ClusterChangeExecutor.
 */
final class PostScalingApplier implements ClusterOperationApplier {
  private final MemberId memberId;
  private final Set<MemberId> clusterMembers;
  private final ClusterChangeExecutor clusterChangeExecutor;

  public PostScalingApplier(
      final MemberId memberId,
      final Set<MemberId> clusterMembers,
      final ClusterChangeExecutor clusterChangeExecutor) {
    this.memberId = memberId;
    this.clusterMembers = clusterMembers;
    this.clusterChangeExecutor = clusterChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {
    // Validate that the member applying this operation is part of the cluster
    if (!currentClusterConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              "Cannot apply post-scaling operation: member "
                  + memberId
                  + " is not part of the current cluster configuration."));
    }
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    // Post-scaling is called after all scaling operations complete.
    // We always notify the nodeIdProvider of the final cluster size
    // regardless of whether it was scale-up or scale-down.
    return clusterChangeExecutor
        .postScaling(clusterMembers)
        .thenApply(ignore -> UnaryOperator.identity());
  }
}
