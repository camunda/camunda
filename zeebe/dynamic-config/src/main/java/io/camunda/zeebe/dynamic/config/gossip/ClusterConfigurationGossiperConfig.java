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
    Duration syncDelay,
    Duration syncRequestTimeout,
    Integer gossipFanout,
    Duration syncInitializerDelay) {

  public static final Duration DEFAULT_SYNC_DELAY = Duration.ofSeconds(10);
  public static final Duration DEFAULT_SYNC_INITIALIZER_DELAY = Duration.ofSeconds(5);
  public static final Duration DEFAULT_SYNC_REQUEST_TIMEOUT = Duration.ofSeconds(2);
  public static final int DEFAULT_GOSSIP_FANOUT = 2;

  public static final ClusterConfigurationGossiperConfig DEFAULT =
      new ClusterConfigurationGossiperConfig(
          DEFAULT_SYNC_DELAY,
          DEFAULT_SYNC_REQUEST_TIMEOUT,
          DEFAULT_GOSSIP_FANOUT,
          DEFAULT_SYNC_INITIALIZER_DELAY);

  public ClusterConfigurationGossiperConfig(
      final Duration syncDelay,
      final Duration syncRequestTimeout,
      final Integer gossipFanout,
      final Duration syncInitializerDelay) {
    this.syncDelay = Optional.ofNullable(syncDelay).orElse(DEFAULT_SYNC_DELAY);
    this.syncRequestTimeout =
        Optional.ofNullable(syncRequestTimeout).orElse(DEFAULT_SYNC_REQUEST_TIMEOUT);
    this.gossipFanout = Optional.ofNullable(gossipFanout).orElse(DEFAULT_GOSSIP_FANOUT);
    this.syncInitializerDelay =
        Optional.ofNullable(syncInitializerDelay).orElse(DEFAULT_SYNC_INITIALIZER_DELAY);
  }
}
