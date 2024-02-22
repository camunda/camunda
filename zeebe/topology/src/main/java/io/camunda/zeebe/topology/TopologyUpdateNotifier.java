/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.camunda.zeebe.topology.state.ClusterTopology;

public interface TopologyUpdateNotifier {

  /**
   * Register topology update listener which is notified when Topology is changed. The listener is
   * immediately invoked if the current topology is not null.
   *
   * @param listener which is notified
   */
  void addUpdateListener(TopologyUpdateListener listener);

  /**
   * Removed the registered listener
   *
   * @param listener to be removed
   */
  void removeUpdateListener(TopologyUpdateListener listener);

  @FunctionalInterface
  interface TopologyUpdateListener {
    void onTopologyUpdated(ClusterTopology clusterTopology);
  }
}
