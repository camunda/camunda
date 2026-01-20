/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import java.util.Iterator;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;

public final class PartitionIdIterator implements Iterator<Integer> {

  private final OfInt iterator;
  private int currentPartitionId;
  private final String partitionGroup;

  public PartitionIdIterator(
      final String partitionGroup,
      final int startPartitionId,
      final int partitionsCount,
      final BrokerTopologyManager topologyManager) {
    this.partitionGroup = partitionGroup;
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
    return topology != null
        && topology.getLeaderForPartition(new PartitionId(partitionGroup, p))
            != BrokerClusterState.NODE_ID_NULL;
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
