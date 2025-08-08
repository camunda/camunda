/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED;

import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import java.time.Duration;
import java.util.Set;

public class Metadata {
  private static final String PREFIX = "camunda.cluster.metadata";

  private static final Set<String> LEGACY_SYNC_DELAY_PROPERTIES =
      Set.of("zeebe.broker.cluster.configManager.gossip.syncDelay");
  private static final Set<String> LEGACY_SYNC_REQUEST_TIMEOUT_PROPERTIES =
      Set.of("zeebe.broker.cluster.configManager.gossip.syncRequestTimeout");
  private static final Set<String> LEGACY_GOSSIP_FANOUT_PROPERTIES =
      Set.of("zeebe.broker.cluster.configManager.gossip.gossipFanout");

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
        PREFIX + ".sync-delay", syncDelay, Duration.class, SUPPORTED, LEGACY_SYNC_DELAY_PROPERTIES);
  }

  public void setSyncDelay(final Duration syncDelay) {
    this.syncDelay = syncDelay;
  }

  public Duration getSyncRequestTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".sync-request-timeout",
        syncRequestTimeout,
        Duration.class,
        SUPPORTED,
        LEGACY_SYNC_REQUEST_TIMEOUT_PROPERTIES);
  }

  public void setSyncRequestTimeout(final Duration syncRequestTimeout) {
    this.syncRequestTimeout = syncRequestTimeout;
  }

  public int getGossipFanout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gossip-fanout",
        gossipFanout,
        Integer.class,
        SUPPORTED,
        LEGACY_GOSSIP_FANOUT_PROPERTIES);
  }

  public void setGossipFanout(final Integer gossipFanout) {
    this.gossipFanout = gossipFanout;
  }
}
