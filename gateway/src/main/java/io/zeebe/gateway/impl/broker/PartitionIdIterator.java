/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.broker;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;

import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import java.util.Iterator;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;

public final class PartitionIdIterator implements Iterator<Integer> {

  private final OfInt iterator;
  private int currentPartitionId;

  public PartitionIdIterator(
      final int startPartitionId,
      final int partitionsCount,
      final BrokerTopologyManager topologyManager) {
    iterator =
        IntStream.range(0, partitionsCount)
            .map(
                index ->
                    (index + startPartitionId - START_PARTITION_ID) % partitionsCount
                        + START_PARTITION_ID)
            .filter(p -> hasLeader(topologyManager, p))
            .iterator();
  }

  private boolean hasLeader(final BrokerTopologyManager topologyManager, final int p) {
    final var topology = topologyManager.getTopology();
    return topology != null && topology.getLeaderForPartition(p) != BrokerClusterState.NODE_ID_NULL;
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public Integer next() {
    currentPartitionId = iterator.next();
    return currentPartitionId;
  }

  public int getCurrentPartitionId() {
    return currentPartitionId;
  }
}
