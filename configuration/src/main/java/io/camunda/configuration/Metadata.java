/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import java.time.Duration;

public class Metadata {
  private static final String PREFIX = "camunda.cluster.metadata.";

  /**
   * The delay between two sync requests in the ClusterConfigurationManager. A sync request is sent
   * to another node to get the latest topology of the cluster.
   */
  private Duration syncDelay = ClusterConfigurationGossiperConfig.DEFAULT_SYNC_DELAY;

  /** The timeout for a sync request in the ClusterConfigurationManager. */
  private Duration syncRequestTimeout =
      ClusterConfigurationGossiperConfig.DEFAULT_SYNC_REQUEST_TIMEOUT;

  /** The number of nodes to which a cluster topology is gossiped. */
  private int gossipFanout = ClusterConfigurationGossiperConfig.DEFAULT_GOSSIP_FANOUT;

  public Duration getSyncDelay() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "sync-delay", syncDelay, Duration.class, BackwardsCompatibilityMode.SUPPORTED);
  }

  public void setSyncDelay(final Duration syncDelay) {
    this.syncDelay = syncDelay;
  }

  public Duration getSyncRequestTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "sync-request-timeout",
        syncRequestTimeout,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED);
  }

  public void setSyncRequestTimeout(final Duration syncRequestTimeout) {
    this.syncRequestTimeout = syncRequestTimeout;
  }

  public int getGossipFanout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "gossip-fanout",
        gossipFanout,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED);
  }

  public void setGossipFanout(final Integer gossipFanout) {
    this.gossipFanout = gossipFanout;
  }
}
