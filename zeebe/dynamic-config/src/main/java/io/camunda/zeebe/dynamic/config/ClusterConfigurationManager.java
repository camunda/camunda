/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.UnaryOperator;

public interface ClusterConfigurationManager {

  ActorFuture<ClusterConfiguration> getClusterConfiguration();

  ActorFuture<ClusterConfiguration> updateClusterConfiguration(
      UnaryOperator<ClusterConfiguration> updatedConfiguration);

  /**
   * Whether this manager operates on the new multi-partition-group model. When {@code false}, the
   * {@code *MultiConfiguration} methods below are not supported.
   */
  default boolean isUsingNewConfig() {
    return false;
  }

  /** Returns the full multi-partition-group configuration. Only valid when the new model is on. */
  default ActorFuture<CurrentClusterConfiguration> getMultiConfiguration() {
    throw new UnsupportedOperationException(
        "getMultiConfiguration is only supported with the new configuration model");
  }

  /**
   * Applies {@code updater} to the multi-partition-group configuration, persists and gossips it,
   * and triggers local operation application. Only valid when the new model is on.
   */
  default ActorFuture<CurrentClusterConfiguration> updateMultiConfiguration(
      final UnaryOperator<CurrentClusterConfiguration> updater) {
    throw new UnsupportedOperationException(
        "updateMultiConfiguration is only supported with the new configuration model");
  }

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
        ClusterConfiguration newConfiguration, ClusterConfiguration oldConfiguration);
  }
}
