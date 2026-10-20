/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.gossip;

import java.time.Duration;

public record ClusterConfigurationGossiperConfig(
    boolean enableSync,
    Duration syncDelay,
    Duration syncRequestTimeout,
    int gossipFanout,
    Duration bootstrapTimeout) {

  public static final Duration DEFAULT_BOOTSTRAP_TIMEOUT = Duration.ofSeconds(30);

  public static final ClusterConfigurationGossiperConfig DEFAULT =
      new ClusterConfigurationGossiperConfig(
          true, Duration.ofSeconds(10), Duration.ofSeconds(2), 2, DEFAULT_BOOTSTRAP_TIMEOUT);

  public ClusterConfigurationGossiperConfig(
      final boolean enableSync,
      final Duration syncDelay,
      final Duration syncRequestTimeout,
      final int gossipFanout) {
    this(enableSync, syncDelay, syncRequestTimeout, gossipFanout, DEFAULT_BOOTSTRAP_TIMEOUT);
  }
}
