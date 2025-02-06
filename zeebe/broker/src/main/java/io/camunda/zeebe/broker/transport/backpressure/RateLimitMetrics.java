/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backpressure;

import static io.camunda.zeebe.broker.transport.backpressure.BackpressureMetricsDoc.CURRENT_INFLIGHT;
import static io.camunda.zeebe.broker.transport.backpressure.BackpressureMetricsDoc.CURRENT_LIMIT;

import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;

final class RateLimitMetrics {
  private final AtomicInteger currentInflight = new AtomicInteger();
  private final AtomicInteger currentLimit = new AtomicInteger();

  RateLimitMetrics(final MeterRegistry registry, final int partitionId) {
    final var partitionTagValue = String.valueOf(partitionId);

    Gauge.builder(CURRENT_INFLIGHT.getName(), currentInflight, AtomicInteger::intValue)
        .description(CURRENT_INFLIGHT.getDescription())
        .tag(PartitionKeyNames.PARTITION.asString(), partitionTagValue)
        .register(registry);
    Gauge.builder(CURRENT_LIMIT.getName(), currentLimit, AtomicInteger::intValue)
        .description(CURRENT_LIMIT.getDescription())
        .tag(PartitionKeyNames.PARTITION.asString(), partitionTagValue)
        .register(registry);
  }

  public void incInflight() {
    currentInflight.incrementAndGet();
  }

  public void decInflight() {
    currentInflight.decrementAndGet();
  }

  public void setNewLimit(final int newLimit) {
    currentLimit.set(newLimit);
  }

  public void setInflight(final int count) {
    currentInflight.set(count);
  }
}
