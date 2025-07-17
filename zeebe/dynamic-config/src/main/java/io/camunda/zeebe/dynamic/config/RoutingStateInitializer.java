/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Optional;

/**
 * Initializes the routing state of the cluster configuration if the partition scaling feature is
 * enabled. All members will initialize the same routing state (as long as their statically
 * configured partition counts match).
 */
public class RoutingStateInitializer implements ClusterConfigurationModifier {

  private final boolean enablePartitionScaling;
  private final int staticPartitionCount;

  public RoutingStateInitializer(
      final boolean enablePartitionScaling, final int staticPartitionCount) {
    this.enablePartitionScaling = enablePartitionScaling;
    this.staticPartitionCount = staticPartitionCount;
  }

  @Override
  public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
    if (configuration.routingState().isPresent() || !enablePartitionScaling) {
      return CompletableActorFuture.completed(configuration);
    }

    final var routingState = RoutingState.initializeWithPartitionCount(staticPartitionCount);
    final var withRoutingState =
        new ClusterConfiguration(
            configuration.version(),
            configuration.members(),
            configuration.lastChange(),
            configuration.pendingChanges(),
            Optional.of(routingState),
            configuration.clusterId());
    return CompletableActorFuture.completed(withRoutingState);
  }
}
