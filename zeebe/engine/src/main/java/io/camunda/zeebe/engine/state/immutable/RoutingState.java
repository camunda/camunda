/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import java.util.Map;
import java.util.Optional;
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
   * @return the event key of the SCALING_UP event that scaled to the specified partition count.
   *     Returns 0 for the initial partition count, or -1 if the system has never scaled to the
   *     specified number of partitions
   */
  long bootstrappedAt(int partitionCount);

  static RoutingState of(
      final Map<Integer, Long> activePartitions,
      final Set<Integer> desiredPartitions,
      final MessageCorrelation messageCorrelation) {
    return new RoutingState() {
      @Override
      public Set<Integer> currentPartitions() {
        return activePartitions.keySet();
      }

      @Override
      public Set<Integer> desiredPartitions() {
        return desiredPartitions;
      }

      @Override
      public MessageCorrelation messageCorrelation() {
        return messageCorrelation;
      }

      @Override
      public boolean isInitialized() {
        return true;
      }

      @Override
      public long bootstrappedAt(final int partitionCount) {
        return Optional.of(activePartitions.get(partitionCount)).orElse(-1L);
      }
    };
  }

  sealed interface MessageCorrelation {
    record HashMod(int partitionCount) implements MessageCorrelation {
      public HashMod {
        if (partitionCount <= 0) {
          throw new IllegalArgumentException("Partition count must be positive");
        }
      }
    }
  }
}
