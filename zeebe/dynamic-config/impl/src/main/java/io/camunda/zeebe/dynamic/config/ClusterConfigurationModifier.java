/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Collection;
import java.util.Set;

/**
 * After the configuration is initialized, we can use a {@link ClusterConfigurationModifier} to
 * update the initialized configuration. This process do not go through the usual process of adding
 * a {@link io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation} to change the
 * configuration. Instead, overwrites the configuration immediately after it is initialized. Hence,
 * this should be used carefully.
 *
 * <p>Ideally, a modifier should only update the configuration of the local member to avoid any
 * concurrent conflicting changes from other members.
 */
public interface ClusterConfigurationModifier<T extends InitializableClusterConfiguration> {

  default ExecutionFilter filter() {
    return new ExecutionFilter(false, null);
  }

  /**
   * Modifies the given configuration and returns the modified configuration.
   *
   * @param configuration current configuration
   * @return modified configuration
   */
  ActorFuture<T> modify(T configuration);

  record ExecutionFilter(boolean coordinatorOnly, MemberId localMemberId) {
    public boolean canRunInitializer(final Collection<MemberId> clusterMembers) {
      if (!coordinatorOnly) {
        return true;
      }
      final var coordinator =
          ClusterConfigurationCoordinatorSupplier.ofMembers(Set.copyOf(clusterMembers))
              .getDefaultCoordinator();
      return coordinator.equals(localMemberId);
    }

    public boolean canRunInitializer(final InitializableClusterConfiguration configuration) {
      return canRunInitializer(configuration.getMembers());
    }
  }

  abstract class CoordinatorOnly<T extends InitializableClusterConfiguration>
      implements ClusterConfigurationModifier<T> {

    private final ExecutionFilter filter;

    CoordinatorOnly(final MemberId localMemberId) {
      filter = new ExecutionFilter(true, localMemberId);
    }

    @Override
    public ExecutionFilter filter() {
      return filter;
    }
  }
}
