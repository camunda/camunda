/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

import java.util.Set;

public interface RoutingState {
  Set<Integer> currentPartitions();

  Set<Integer> desiredPartitions();

  MessageCorrelation messageCorrelation();

  boolean isInitialized();

  /**
   * Retrieves the event key of the SCALING_UP event for a specific partition count.
   *
   * @param partitionCount the target number of partitions for the scaling operation
   * @return the command position of the SCALING_UP event that scaled to the specified partition
   *     count. Returns 0 for the initial partition count, or -1 if the system has never scaled to
   *     the specified number of partitions
   */
  long scalingStartedAt(int partitionCount);

  sealed interface MessageCorrelation {
    int partitionCount();

    record HashMod(int partitionCount) implements MessageCorrelation {
      public HashMod {
        if (partitionCount <= 0) {
          throw new IllegalArgumentException(
              "Partition count must be positive, was " + partitionCount);
        }
      }
    }
  }
}
