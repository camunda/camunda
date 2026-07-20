/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.gossip;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.util.Objects;

public final class ClusterConfigurationGossipState {
  // TODO: This should also tracks the BrokerInfo which is currently in SWIM member.properties
  private ClusterConfiguration clusterConfiguration;
  // Field 2 of the gossiped state: the new multi-partition-group configuration, populated
  // alongside clusterConfiguration by upgraded brokers (dual-write). Null on the legacy path.
  private CurrentClusterConfiguration currentClusterConfiguration;

  public ClusterConfiguration getClusterConfiguration() {
    return clusterConfiguration;
  }

  public void setClusterConfiguration(final ClusterConfiguration clusterConfiguration) {
    this.clusterConfiguration = clusterConfiguration;
  }

  public CurrentClusterConfiguration getCurrentClusterConfiguration() {
    return currentClusterConfiguration;
  }

  public void setCurrentClusterConfiguration(
      final CurrentClusterConfiguration currentClusterConfiguration) {
    this.currentClusterConfiguration = currentClusterConfiguration;
  }

  @Override
  public int hashCode() {
    return Objects.hash(clusterConfiguration, currentClusterConfiguration);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ClusterConfigurationGossipState that = (ClusterConfigurationGossipState) o;

    return Objects.equals(clusterConfiguration, that.clusterConfiguration)
        && Objects.equals(currentClusterConfiguration, that.currentClusterConfiguration);
  }

  @Override
  public String toString() {
    return "ClusterTopologyGossipState{"
        + "clusterTopology="
        + clusterConfiguration
        + ", currentClusterConfiguration="
        + currentClusterConfiguration
        + '}';
  }
}
