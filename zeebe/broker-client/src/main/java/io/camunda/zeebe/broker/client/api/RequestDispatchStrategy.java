/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;

/** Implementations must be thread-safe. */
public interface RequestDispatchStrategy {

  /**
   * @return {@link BrokerClusterState#PARTITION_ID_NULL} if no partition can be determined
   */
  int determinePartition(final String partitionGroup, final BrokerTopologyManager topologyManager);

  default int determinePartition(final BrokerTopologyManager topologyManager) {
    return determinePartition("raft-partition", topologyManager);
  }

  /**
   * Returns a dispatch strategy which will perform a stateful round robin between the partitions.
   */
  static RequestDispatchStrategy roundRobin() {
    return new RoundRobinDispatchStrategy();
  }
}
