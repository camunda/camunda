/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;

import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import java.util.Iterator;
import java.util.PrimitiveIterator.OfInt;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;

public final class PartitionIdIterator implements Iterator<Integer> {

  private final OfInt iterator;
  private int currentPartitionId;

  public PartitionIdIterator(
      final int startPartitionId,
      final int partitionsCount,
      final BrokerTopologyManager topologyManager,
      final String partitionGroup) {
    this(startPartitionId, partitionsCount, topologyManager, partitionGroup, (group, p) -> false);
  }

  /**
   * @param hasLeaderOutsideTopology tells whether a partition of a group has a leader that the
   *     topology does not show, but that can take requests, for example through the mailbox. Such a
   *     partition is not skipped. Must not block.
   */
  public PartitionIdIterator(
      final int startPartitionId,
      final int partitionsCount,
      final BrokerTopologyManager topologyManager,
      final String partitionGroup,
      final BiPredicate<String, Integer> hasLeaderOutsideTopology) {
    iterator =
        IntStream.range(0, partitionsCount)
            .map(
                index ->
                    (index + startPartitionId - START_PARTITION_ID) % partitionsCount
                        + START_PARTITION_ID)
            .filter(
                p ->
                    hasLeader(topologyManager, partitionGroup, p)
                        || hasLeaderOutsideTopology.test(partitionGroup, p))
            .iterator();
  }

  private boolean hasLeader(
      final BrokerTopologyManager topologyManager, final String partitionGroup, final int p) {
    final var topology = topologyManager.getTopology(partitionGroup);
    return topology != null && topology.getLeaderForPartition(p) != null;
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
