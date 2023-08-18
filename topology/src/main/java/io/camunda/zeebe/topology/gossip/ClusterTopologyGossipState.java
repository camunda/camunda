/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.gossip;

import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.Objects;

public final class ClusterTopologyGossipState {
  // TODO: This should also tracks the BrokerInfo which is currently in SWIM member.properties
  private ClusterTopology clusterTopology;

  public ClusterTopology getClusterTopology() {
    return clusterTopology;
  }

  public void setClusterTopology(final ClusterTopology clusterTopology) {
    this.clusterTopology = clusterTopology;
  }

  @Override
  public int hashCode() {
    return clusterTopology != null ? clusterTopology.hashCode() : 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ClusterTopologyGossipState that = (ClusterTopologyGossipState) o;

    return Objects.equals(clusterTopology, that.clusterTopology);
  }

  @Override
  public String toString() {
    return "ClusterTopologyGossipState{" + "clusterTopology=" + clusterTopology + '}';
  }
}
