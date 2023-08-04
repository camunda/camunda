/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.clustering.topology;

final class PersistedClusterTopology {
  ClusterTopology clusterTopology;

  void initialize() {
    // TODO read from local persisted storage
    clusterTopology = null;
  }

  ClusterTopology getTopology() {
    return clusterTopology;
  }

  void update(final ClusterTopology clusterTopology) {
    this.clusterTopology = clusterTopology;
    // TODO write to file
  }
}
