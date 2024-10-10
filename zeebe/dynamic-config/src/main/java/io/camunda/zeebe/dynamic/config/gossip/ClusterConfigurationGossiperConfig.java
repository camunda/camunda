/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.gossip;

import java.time.Duration;
import java.util.Optional;

public record ClusterConfigurationGossiperConfig(
    Boolean enableSync, Duration syncDelay, Duration syncRequestTimeout, Integer gossipFanout) {

  public static final boolean ENABLE_SYNC = true;
  public static final Duration DEFAULT_SYNC_DELAY = Duration.ofSeconds(10);
  public static final Duration DEFAULT_SYNC_REQUEST_TIMEOUT = Duration.ofSeconds(2);
  public static final int DEFAULT_GOSSIP_FANOUT = 2;

  public static final ClusterConfigurationGossiperConfig DEFAULT =
      new ClusterConfigurationGossiperConfig(
          ENABLE_SYNC, DEFAULT_SYNC_DELAY, DEFAULT_SYNC_REQUEST_TIMEOUT, DEFAULT_GOSSIP_FANOUT);

  public ClusterConfigurationGossiperConfig(
      final Boolean enableSync,
      final Duration syncDelay,
      final Duration syncRequestTimeout,
      final Integer gossipFanout) {
    this.enableSync = enableSync;
    this.syncDelay = Optional.ofNullable(syncDelay).orElse(DEFAULT_SYNC_DELAY);
    this.syncRequestTimeout =
        Optional.ofNullable(syncRequestTimeout).orElse(DEFAULT_SYNC_REQUEST_TIMEOUT);
    this.gossipFanout = Optional.ofNullable(gossipFanout).orElse(DEFAULT_GOSSIP_FANOUT);
  }
}
