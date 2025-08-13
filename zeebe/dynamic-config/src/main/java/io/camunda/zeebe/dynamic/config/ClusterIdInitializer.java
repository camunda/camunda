/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Optional;
import java.util.UUID;

/**
 * Initializes the routing state of the cluster configuration if the partition scaling feature is
 * enabled. All members will initialize the same routing state (as long as their statically
 * configured partition counts match).
 */
public class ClusterIdInitializer implements ClusterConfigurationModifier {

  private final String clusterId;

  public ClusterIdInitializer(final String clusterId) {
    // if not cluster id is configured a new one is generated.
    this.clusterId = Optional.ofNullable(clusterId).orElse(UUID.randomUUID().toString());
  }

  @Override
  public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
    // If the cluster ID is already set, we do not need to modify the configuration.
    if (configuration.clusterId().isPresent()) {
      return CompletableActorFuture.completed(configuration);
    }

    final var withClusterId =
        new ClusterConfiguration(
            configuration.version(),
            configuration.members(),
            configuration.lastChange(),
            configuration.pendingChanges(),
            configuration.routingState(),
            Optional.ofNullable(clusterId));
    return CompletableActorFuture.completed(withClusterId);
  }
}
