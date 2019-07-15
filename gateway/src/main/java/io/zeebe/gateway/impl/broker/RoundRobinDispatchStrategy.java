/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker;

import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinDispatchStrategy implements RequestDispatchStrategy {

  protected final BrokerTopologyManagerImpl topologyManager;
  protected final AtomicInteger partitions = new AtomicInteger(0);

  public RoundRobinDispatchStrategy(final BrokerTopologyManagerImpl topologyManager) {
    this.topologyManager = topologyManager;
  }

  @Override
  public int determinePartition() {
    final BrokerClusterState topology = topologyManager.getTopology();

    if (topology != null) {
      final int offset = partitions.getAndIncrement();
      return topology.getPartition(offset);
    } else {
      return BrokerClusterState.PARTITION_ID_NULL;
    }
  }
}
