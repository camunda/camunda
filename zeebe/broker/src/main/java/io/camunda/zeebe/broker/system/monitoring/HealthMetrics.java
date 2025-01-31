/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;

public final class HealthMetrics {
  private final AtomicInteger health = new AtomicInteger();

  public HealthMetrics(final MeterRegistry registry, final int partitionId) {
    final var meterDoc = HealthMetricsDoc.HEALTH;
    Gauge.builder(meterDoc.getName(), health, AtomicInteger::intValue)
        .description(meterDoc.getDescription())
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .register(registry);
  }

  public void setHealthy() {
    health.set(1);
  }

  public void setUnhealthy() {
    health.set(0);
  }

  public void setDead() {
    health.set(-1);
  }
}
