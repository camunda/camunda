/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;

public class UpdateRoutingStateTransformer implements ConfigurationChangeRequest {

  private final Optional<RoutingState> routingState;

  public UpdateRoutingStateTransformer(final Optional<RoutingState> routingState) {
    this.routingState = routingState;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    final var coordinatorSupplier =
        ClusterConfigurationCoordinatorSupplier.of(() -> clusterConfiguration);
    final var coordinator = coordinatorSupplier.getDefaultCoordinator();
    return Either.right(List.of(new UpdateRoutingState(coordinator, routingState)));
  }
}
