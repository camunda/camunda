/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/** Metrics for coordinated leadership transfer (rebalancing). */
public class RebalanceMetrics extends RaftMetrics {

  private final Timer partitionPauseDuration;

  public RebalanceMetrics(final String partitionName, final MeterRegistry meterRegistry) {
    super(partitionName);
    partitionPauseDuration =
        Timer.builder(RebalanceMetricsDoc.PARTITION_PAUSE_DURATION.getName())
            .description(RebalanceMetricsDoc.PARTITION_PAUSE_DURATION.getDescription())
            .serviceLevelObjectives(RebalanceMetricsDoc.PARTITION_PAUSE_DURATION.getTimerSLOs())
            .tag(PartitionKeyNames.PARTITION.asString(), partition)
            .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), partitionGroupName)
            .register(meterRegistry);
  }

  public void observePauseDuration(final Duration duration) {
    partitionPauseDuration.record(duration);
  }
}
