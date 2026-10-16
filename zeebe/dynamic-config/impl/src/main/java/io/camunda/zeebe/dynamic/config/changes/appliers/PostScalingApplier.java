/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code GlobalChangeOperation.PostScalingOperation}, operating on {@link
 * GlobalConfiguration}. Mirrors the legacy {@code PostScalingApplier} in {@code changes/}, which
 * this does not replace or modify. Finalizes a member after scaling by invoking the post-scaling
 * callback on the {@link ClusterChangeExecutor}.
 */
public final class PostScalingApplier implements GlobalConfigurationChangeApplier {

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
  public Either<Exception, UnaryOperator<GlobalConfiguration>> init(
      final CurrentClusterConfiguration currentClusterConfiguration) {
    final var currentGlobalConfiguration = currentClusterConfiguration.globalConfiguration();
    if (!currentGlobalConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              "Cannot apply post-scaling operation: member "
                  + memberId
                  + " is not part of the current cluster configuration."));
    }
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<GlobalConfiguration>> apply() {
    // Post-scaling is called after all scaling operations complete.
    // We always notify the nodeIdProvider of the final cluster size
    // regardless of whether it was scale-up or scale-down.
    return clusterChangeExecutor
        .postScaling(clusterMembers)
        .thenApply(ignore -> UnaryOperator.identity());
  }
}
