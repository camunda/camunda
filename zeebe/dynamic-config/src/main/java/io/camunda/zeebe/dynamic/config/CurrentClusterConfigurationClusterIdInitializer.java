/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * New-model counterpart of {@link ClusterIdInitializer}: assigns the cluster id to {@link
 * GlobalConfiguration} when it is not already set.
 */
@NullMarked
public class CurrentClusterConfigurationClusterIdInitializer
    extends ClusterConfigurationModifier.CoordinatorOnly<CurrentClusterConfiguration> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CurrentClusterConfigurationClusterIdInitializer.class);
  private final String clusterId;

  public CurrentClusterConfigurationClusterIdInitializer(
      @Nullable final String clusterId, final MemberId memberId) {
    super(memberId);
    this.clusterId = Optional.ofNullable(clusterId).orElse(UUID.randomUUID().toString());
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> modify(
      final CurrentClusterConfiguration configuration) {
    final var existing = configuration.clusterId();
    if (existing.isPresent()) {
      if (!existing.get().equals(clusterId)) {
        LOGGER.warn(
            "Cluster ID is already set to '{}', but the configured cluster ID is '{}'. Using the existing cluster ID.",
            existing.get(),
            clusterId);
      }
      return CompletableActorFuture.completed(configuration);
    }

    return CompletableActorFuture.completed(
        configuration.updateGlobalConfiguration(global -> global.setClusterId(clusterId)));
  }
}
