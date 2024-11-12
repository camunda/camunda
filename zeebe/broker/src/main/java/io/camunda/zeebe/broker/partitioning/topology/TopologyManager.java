/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.util.health.HealthStatus;

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

  void onHealthChanged(int partitionId, HealthStatus status);

  void removePartition(int partitionId);
}
