/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.micrometer.core.instrument.Metrics;
import java.util.concurrent.atomic.AtomicInteger;

public final class BannedInstanceMetrics {
  private static final io.micrometer.core.instrument.MeterRegistry METER_REGISTRY =
      Metrics.globalRegistry;

  private final AtomicInteger BANNED_INSTANCES_COUNTER = new AtomicInteger(0);

  public BannedInstanceMetrics(final int partitionId) {
    final String partitionIdLabel = String.valueOf(partitionId);

    io.micrometer.core.instrument.Gauge.builder(
            "zeebe_banned_instances_total", BANNED_INSTANCES_COUNTER, AtomicInteger::get)
        .description("Number of banned instances.")
        .tags("partition", partitionIdLabel)
        .register(METER_REGISTRY);
  }

  public void countBannedInstance() {
    BANNED_INSTANCES_COUNTER.incrementAndGet();
  }

  public void setBannedInstanceCounter(final int counter) {
    BANNED_INSTANCES_COUNTER.set(counter);
  }
}
