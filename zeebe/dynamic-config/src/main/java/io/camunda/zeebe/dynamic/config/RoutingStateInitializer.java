/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.List;
import java.util.Optional;

/**
 * Initializes the routing state of the cluster configuration if the partition scaling feature is
 * enabled. All members will initialize the same routing state (as long as their statically
 * configured partition counts match).
 */
public class RoutingStateInitializer implements ClusterConfigurationModifier {

  private final boolean enablePartitionScaling;

  public RoutingStateInitializer(final boolean enablePartitionScaling) {
    this.enablePartitionScaling = enablePartitionScaling;
  }

  @Override
  public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
    if (configuration.routingState().isPresent() || !enablePartitionScaling) {
      return CompletableActorFuture.completed(configuration);
    }
    final var coordinator = MemberId.from("0");
    final List<ClusterConfigurationChangeOperation> operations =
        List.of(new UpdateRoutingState(coordinator, Optional.empty()));
    var pendingChanges = configuration.pendingChanges();
    if (pendingChanges.isEmpty()) {
      pendingChanges = Optional.of(ClusterChangePlan.init(1L, operations));
    }
    final var withRoutingState =
        new ClusterConfiguration(
            configuration.version(),
            configuration.members(),
            configuration.lastChange(),
            pendingChanges,
            Optional.empty());
    return CompletableActorFuture.completed(withRoutingState);
  }
}
