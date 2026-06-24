/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import static io.atomix.raft.metrics.MetaStoreMetricsDoc.LAST_FLUSHED_INDEX;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

public final class MetaStoreMetrics extends RaftMetrics {
  private final Timer lastFlushedIndexUpdate;
  private final Clock clock;

  public MetaStoreMetrics(final String partitionName, final MeterRegistry registry) {
    super(partitionName);
    Objects.requireNonNull(registry, "MeterRegistry cannot be null");
    lastFlushedIndexUpdate =
        Timer.builder(LAST_FLUSHED_INDEX.getName())
            .description(LAST_FLUSHED_INDEX.getDescription())
            .serviceLevelObjectives(LAST_FLUSHED_INDEX.getTimerSLOs())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);
    clock = registry.config().clock();
  }

  public CloseableSilently observeLastFlushedIndexUpdate() {
    return MicrometerUtil.timer(lastFlushedIndexUpdate, Timer.start(clock));
  }
}
