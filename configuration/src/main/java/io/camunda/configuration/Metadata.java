/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import java.time.Duration;

public class Metadata {

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
    return syncDelay;
  }

  public void setSyncDelay(final Duration syncDelay) {
    this.syncDelay = syncDelay;
  }

  public Duration getSyncRequestTimeout() {
    return syncRequestTimeout;
  }

  public void setSyncRequestTimeout(final Duration syncRequestTimeout) {
    this.syncRequestTimeout = syncRequestTimeout;
  }

  public int getGossipFanout() {
    return gossipFanout;
  }

  public void setGossipFanout(final Integer gossipFanout) {
    this.gossipFanout = gossipFanout;
  }
}
