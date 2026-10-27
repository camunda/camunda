/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import io.camunda.zeebe.protocol.Protocol;
import org.agrona.collections.IntHashSet;

public final class PartitionUtil {
  private PartitionUtil() {}

  /**
   * Returns a set of all partitions from {@link Protocol#START_PARTITION_ID} to {@code
   * numPartitions}.
   */
  public static IntHashSet allPartitions(final int numPartitions) {
    requireValidPartitionId(numPartitions);
    final var partitions = new IntHashSet(numPartitions);
    for (int i = Protocol.START_PARTITION_ID;
        i < Protocol.START_PARTITION_ID + numPartitions;
        i++) {
      partitions.add(i);
    }
    return partitions;
  }

  /**
   * Throws if the partition is outside the range of {@link Protocol#START_PARTITION_ID} to {@link
   * Protocol#MAXIMUM_PARTITIONS}
   */
  public static void requireValidPartitionId(final int partitionId) {
    if (partitionId < Protocol.START_PARTITION_ID) {
      throw new IllegalArgumentException(
          "Partition id " + partitionId + " must be >= " + Protocol.START_PARTITION_ID);
    }
    if (partitionId > Protocol.MAXIMUM_PARTITIONS) {
      throw new IllegalArgumentException(
          "Partition id " + partitionId + " must be <= " + Protocol.MAXIMUM_PARTITIONS);
    }
  }
}
