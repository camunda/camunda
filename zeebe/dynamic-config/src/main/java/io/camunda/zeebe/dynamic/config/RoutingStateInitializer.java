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

/**
 * Initializes a missing {@link RoutingState} from the partitions already present in the cluster
 * configuration. Used on upgrades from versions that did not persist routing state. New clusters
 * already get a routing state from {@link
 * io.camunda.zeebe.dynamic.config.util.ConfigurationUtil#getClusterConfigFrom}.
 */
public class RoutingStateInitializer implements ClusterConfigurationModifier {

  @Override
  public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
    if (configuration.routingState().isPresent()) {
      return CompletableActorFuture.completed(configuration);
    }

    final int partitionCount = configuration.partitionCount();
    if (partitionCount < 1) {
      return CompletableActorFuture.completed(configuration);
    }

    final var routingState = RoutingState.initializeWithPartitionCount(partitionCount);
    return CompletableActorFuture.completed(configuration.setRoutingState(routingState));
  }
}
