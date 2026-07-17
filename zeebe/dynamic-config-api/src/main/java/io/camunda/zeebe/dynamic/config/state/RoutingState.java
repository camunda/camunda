/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableSortedSet;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.protocol.Protocol;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
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
    long version, RequestHandling requestHandling, MessageCorrelation messageCorrelation) {
  public RoutingState {
    Objects.requireNonNull(requestHandling);
    Objects.requireNonNull(messageCorrelation);

    if (version < 0) {
      throw new IllegalArgumentException("Version must be positive");
    }
  }

  public RoutingState withRequestHandling(final UnaryOperator<RequestHandling> update) {
    return new RoutingState(version + 1, update.apply(requestHandling), messageCorrelation);
  }

  public RoutingState withVersion(final long version) {
    return new RoutingState(version, requestHandling, messageCorrelation);
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
        1, new AllPartitions(partitionCount), new MessageCorrelation.HashMod(partitionCount));
  }

  private static void validatePartitionCount(final int partitionCount) {
    if (partitionCount < 1) {
      throw new IllegalArgumentException("Partition count must be greater than or equal to 1");
    }
  }

  private static void validatePartitionId(final int partitionId) {
    if (partitionId < Protocol.START_PARTITION_ID) {
      throw new IllegalArgumentException(
          "Partition %d must be greater than %d"
              .formatted(partitionId, Protocol.START_PARTITION_ID));
    }
    if (partitionId > Protocol.MAXIMUM_PARTITIONS) {
      throw new IllegalArgumentException(
          "Partition %d must be less than %d".formatted(partitionId, Protocol.MAXIMUM_PARTITIONS));
    }
  }

  private static Set<Integer> allPartitions(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount).boxed().collect(Collectors.toSet());
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

  public sealed interface RequestHandling {
    Set<Integer> activePartitions();

    record AllPartitions(int partitionCount) implements RequestHandling {
      public AllPartitions {
        validatePartitionId(partitionCount);
      }

      @Override
      public Set<Integer> activePartitions() {
        return allPartitions(partitionCount);
      }
    }

    /**
     * @param basePartitionCount number of partitions (1..=N) that are already active.
     * @param additionalActivePartitions set of additional partitions that are also active.
     * @param inactivePartitions set of partitions that are explicitly not active.
     */
    record ActivePartitions(
        int basePartitionCount,
        SortedSet<Integer> additionalActivePartitions,
        SortedSet<Integer> inactivePartitions)
        implements RequestHandling {
      public ActivePartitions(
          final int basePartitionCount,
          final Set<Integer> additionalActivePartitions,
          final Set<Integer> inactivePartitions) {
        this(
            basePartitionCount,
            ImmutableSortedSet.copyOf(additionalActivePartitions),
            ImmutableSortedSet.copyOf(inactivePartitions));
      }

      public ActivePartitions {
        Objects.requireNonNull(additionalActivePartitions);
        Objects.requireNonNull(inactivePartitions);
        validatePartitionCount(basePartitionCount);

        for (final int activePartition : additionalActivePartitions) {
          validatePartitionId(activePartition);
          if (inactivePartitions.contains(activePartition)) {
            throw new IllegalArgumentException(
                "Partition %d cannot be active and inactive at the same time."
                    .formatted(activePartition));
          }
        }

        for (final int inactivePartition : inactivePartitions) {
          validatePartitionId(inactivePartition);
        }
      }

      @Override
      public Set<Integer> activePartitions() {
        final var all = new TreeSet<Integer>();
        all.addAll(allPartitions(basePartitionCount));
        all.addAll(additionalActivePartitions);
        all.removeAll(inactivePartitions);
        return all;
      }

      public RequestHandling activatePartitions(final Set<Integer> partitions) {
        final var updatedInactivePartitions = new HashSet<>(inactivePartitions());
        updatedInactivePartitions.removeAll(partitions);
        final var updatedAdditionalActivePartitions = new HashSet<>(additionalActivePartitions);
        updatedAdditionalActivePartitions.addAll(partitions);
        if (updatedInactivePartitions.isEmpty()) {
          return new AllPartitions(basePartitionCount + updatedAdditionalActivePartitions.size());
        } else {
          return new ActivePartitions(
              basePartitionCount, updatedAdditionalActivePartitions, updatedInactivePartitions);
        }
      }
    }
  }
}
