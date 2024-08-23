/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Holds information about the state of partitions that is necessary to decide where to route new
 * requests.
 *
 * <p>The version is incremented by the coordinator, every time the routing state changes. This is
 * used to resolve conflicts when the members receive gossip updates out of order.
 */
public record RoutingState(
    long version, Set<Integer> activePartitions, MessageCorrelation messageCorrelation) {
  public RoutingState {
    Objects.requireNonNull(activePartitions);
    Objects.requireNonNull(messageCorrelation);

    if (version < 0) {
      throw new IllegalArgumentException("Version must be positive");
    }

    for (final var partition : activePartitions) {
      if (partition <= 0) {
        throw new IllegalArgumentException("Partition id must be positive");
      }
    }
  }

  public RoutingState merge(final RoutingState other) {
    if (equals(other)) {
      return this;
    }

    if (version > other.version) {
      return this;
    } else if (version < other.version) {
      return other;
    } else {
      throw new IllegalStateException(
          "Cannot merge two different routing states with the same version");
    }
  }

  /**
   * Returns the initial routing info for the given partition count when all partitions participate
   * in message correlation.
   */
  public static RoutingState initializeWithPartitionCount(final int partitionCount) {
    return new RoutingState(
        1,
        IntStream.rangeClosed(1, partitionCount).boxed().collect(Collectors.toSet()),
        new MessageCorrelation.HashMod(partitionCount));
  }

  /** Strategy used to correlate messages via their correlation key to partitions. */
  public sealed interface MessageCorrelation {
    record HashMod(int partitionCount) implements MessageCorrelation {
      public HashMod {
        if (partitionCount <= 0) {
          throw new IllegalArgumentException("Partition count must be positive");
        }
      }
    }
  }
}
