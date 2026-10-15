/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.UnaryOperator;

public interface ClusterConfigurationManager {

  /** Returns the full multi-partition-group configuration. */
  ActorFuture<CurrentClusterConfiguration> getMultiConfiguration();

  /**
   * Applies {@code updater} to the multi-partition-group configuration, persists and gossips it,
   * and triggers local operation application.
   */
  ActorFuture<CurrentClusterConfiguration> updateMultiConfiguration(
      UnaryOperator<CurrentClusterConfiguration> updater);

  /**
   * Returns the configuration a coordinator bases a change on: the one in the {@link
   * ClusterConfigurationStore} merged into the local one, along with the next change id the store
   * must still have when the change is committed via {@link #commitCoordinatedConfiguration}.
   */
  ActorFuture<CoordinatedConfiguration> readCoordinatedConfiguration();

  /**
   * Writes {@code configuration} to the {@link ClusterConfigurationStore} if its next change id is
   * still {@code expectedNextId}, and only then merges it into the local configuration, which is
   * persisted and gossiped as by {@link #updateMultiConfiguration}.
   */
  ActorFuture<CurrentClusterConfiguration> commitCoordinatedConfiguration(
      long expectedNextId, CurrentClusterConfiguration configuration);

  /**
   * Applies {@code updater} to the {@link #readCoordinatedConfiguration() coordinated
   * configuration} and {@link #commitCoordinatedConfiguration commits} the result.
   */
  ActorFuture<CurrentClusterConfiguration> updateCoordinatedConfiguration(
      UnaryOperator<CurrentClusterConfiguration> updater);

  /**
   * @param configuration the stored configuration merged into the local one
   * @param expectedNextId the next change id of the stored configuration, or {@link
   *     ClusterConfigurationStore#ABSENT}
   */
  record CoordinatedConfiguration(CurrentClusterConfiguration configuration, long expectedNextId) {}

  /**
   * A listener that is invoked, when the local member state in the local configuration is older
   * compared to the received configuration. Normally, only a member can change its own state.
   * However, if the state changes without its knowledge it means there was a request that force
   * changed the configuration. In that case the listener can decide how to react - for example by
   * shutting down the node or restarting all partitions with the new configuration.
   */
  @FunctionalInterface
  interface InconsistentConfigurationListener {

    /**
     * Invoked when the local member state in the local configuration is old compared to the newer
     * received configuration. Before invoking this listener, the local configuration will be
     * updated to the newConfiguration.
     *
     * @param newConfiguration new configuration received
     * @param oldConfiguration the local configuration before receiving the new one
     */
    void onInconsistentConfiguration(
        CurrentClusterConfiguration newConfiguration, CurrentClusterConfiguration oldConfiguration);
  }
}
