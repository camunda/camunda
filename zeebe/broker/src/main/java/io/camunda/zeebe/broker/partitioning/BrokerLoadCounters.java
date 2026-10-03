/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineAction;
import io.camunda.zeebe.rebalance.LoadCounters;
import io.camunda.zeebe.rebalance.LoadMeasure;
import io.camunda.zeebe.stream.impl.metrics.StreamProcessorAction;
import io.camunda.zeebe.util.EnumCounters;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds up the counters of every partition this broker has hosted since it started. What a partition
 * counted is kept once it is retired, so the totals never go down.
 */
public final class BrokerLoadCounters implements LoadCounters {

  private final List<PartitionLoadCounters> partitions = new ArrayList<>();
  private final long[] retired = new long[LoadMeasure.values().length];

  /** Counters for a new partition, included in the totals until they are retired. */
  public synchronized PartitionLoadCounters register() {
    final var counters =
        new PartitionLoadCounters(
            new EnumCounters<>(StreamProcessorAction.class),
            new EnumCounters<>(EngineAction.class));
    partitions.add(counters);
    return counters;
  }

  /**
   * Stops tracking counters that will no longer increase, keeping what they counted in the totals.
   */
  public synchronized void retire(final PartitionLoadCounters counters) {
    if (partitions.remove(counters)) {
      for (final var measure : LoadMeasure.values()) {
        retired[measure.ordinal()] += counters.total(measure);
      }
    }
  }

  @Override
  public synchronized long total(final LoadMeasure measure) {
    long total = retired[measure.ordinal()];
    for (final var partition : partitions) {
      total += partition.total(measure);
    }
    return total;
  }

  /** The counters one partition increments while it runs. */
  public record PartitionLoadCounters(
      EnumCounters<StreamProcessorAction> processing,
      EnumCounters<EngineAction> rootProcessInstances) {

    private long total(final LoadMeasure measure) {
      return switch (measure) {
        case ROOT_PROCESS_INSTANCES -> rootProcessInstances.get(EngineAction.ACTIVATED);
        case PROCESSED_COMMANDS -> processing.get(StreamProcessorAction.PROCESSED);
      };
    }
  }
}
