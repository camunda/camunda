/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.routing;

import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;

/**
 * Utility class that holds the current routing information. To be uses everywhere the number of
 * partitions or message correlation strategy is needed.
 *
 * <p>The information always reflects the current persisted routing info from {@link
 * DbRoutingState}.
 */
public interface RoutingInfo {
  /** Returns the current set of partitions. */
  Set<Integer> partitions();

  /** Returns the current partition id for the given correlation key. */
  int partitionForCorrelationKey(final DirectBuffer correlationKey);

  /**
   * Creates a {@link RoutingInfo} instance for static partitions. This is used when the partitions
   * are fixed and known at startup. Only relevant for testing.
   */
  static RoutingInfo forStaticPartitions(final int partitionCount) {
    final var partitions =
        IntStream.rangeClosed(Protocol.START_PARTITION_ID, partitionCount)
            .boxed()
            .collect(Collectors.toSet());
    return new StaticRoutingInfo(partitions, partitionCount);
  }

  static RoutingInfo dynamic(final RoutingState routingState, final RoutingInfo fallback) {
    return new DynamicRoutingInfo(routingState, fallback);
  }

  class StaticRoutingInfo implements RoutingInfo {
    private final Set<Integer> otherPartitions;
    private final int partitionCount;

    public StaticRoutingInfo(final Set<Integer> otherPartitions, final int partitionCount) {
      this.otherPartitions = otherPartitions;
      this.partitionCount = partitionCount;
    }

    @Override
    public Set<Integer> partitions() {
      return otherPartitions;
    }

    @Override
    public int partitionForCorrelationKey(final DirectBuffer correlationKey) {
      return SubscriptionUtil.getSubscriptionPartitionId(correlationKey, partitionCount);
    }
  }

  /**
   * Naive implementation that always looks up the routing information from the {@link
   * RoutingState}. Later on, we might want to cache this information.
   */
  class DynamicRoutingInfo implements RoutingInfo {
    private final RoutingState routingState;
    private final RoutingInfo fallback;

    public DynamicRoutingInfo(final RoutingState routingState, final RoutingInfo fallback) {
      this.routingState = routingState;
      this.fallback = fallback;
    }

    @Override
    public Set<Integer> partitions() {
      if (!routingState.isInitialized()) {
        return fallback.partitions();
      }
      return routingState.currentPartitions();
    }

    @Override
    public int partitionForCorrelationKey(final DirectBuffer correlationKey) {
      if (!routingState.isInitialized()) {
        return fallback.partitionForCorrelationKey(correlationKey);
      }

      switch (routingState.messageCorrelation()) {
        case HashMod(final var partitionCount) -> {
          return SubscriptionUtil.getSubscriptionPartitionId(correlationKey, partitionCount);
        }
      }
    }
  }
}
