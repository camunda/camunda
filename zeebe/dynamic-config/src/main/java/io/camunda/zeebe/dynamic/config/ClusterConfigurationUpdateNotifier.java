/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;

public interface ClusterConfigurationUpdateNotifier {

  /**
   * Register configuration update listener which is notified when cluster configuration is changed.
   * The listener is immediately invoked if the current configuration is not null.
   *
   * @param listener which is notified
   */
  void addUpdateListener(ClusterConfigurationUpdateListener listener);

  /**
   * Removed the registered listener
   *
   * @param listener to be removed
   */
  void removeUpdateListener(ClusterConfigurationUpdateListener listener);

  @FunctionalInterface
  interface ClusterConfigurationUpdateListener {
    void onClusterConfigurationUpdated(ClusterConfiguration clusterConfiguration);
  }
}
