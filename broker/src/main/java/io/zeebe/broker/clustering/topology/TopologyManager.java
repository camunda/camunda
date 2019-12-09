/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.topology;

/**
 * Maintains the cluster topology.
 *
 * <p>Three main interactions are possible:
 *
 * <ul>
 *   <li>observer: registering a listener and getting updated about node and partition events
 * </ul>
 */
public interface TopologyManager {
  void removeTopologyPartitionListener(TopologyPartitionListener listener);

  void addTopologyPartitionListener(TopologyPartitionListener listener);
}
