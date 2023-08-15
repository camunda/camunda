/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.gossip;

import io.camunda.zeebe.topology.state.ClusterTopology;

final class ClusterTopologyGossipState {
  // TODO: This should also tracks the BrokerInfo which is currently in SWIM member.properties
  private ClusterTopology clusterTopology;

  ClusterTopology getClusterTopology() {
    return clusterTopology;
  }

  void setClusterTopology(final ClusterTopology clusterTopology) {
    this.clusterTopology = clusterTopology;
  }
}
