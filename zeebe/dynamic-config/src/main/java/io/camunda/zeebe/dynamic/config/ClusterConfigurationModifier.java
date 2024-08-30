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
public interface ClusterConfigurationModifier {

  /**
   * Modifies the given configuration and returns the modified configuration.
   *
   * @param configuration current configuration
   * @return modified configuration
   */
  ActorFuture<ClusterConfiguration> modify(ClusterConfiguration configuration);
}
