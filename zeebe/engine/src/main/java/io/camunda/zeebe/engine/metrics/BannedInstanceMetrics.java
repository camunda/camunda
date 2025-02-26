/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.EngineMetricsDoc.BANNED_INSTANCES;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

public final class BannedInstanceMetrics {

  private final StatefulGauge bannedInstanceCounter;

  public BannedInstanceMetrics(final MeterRegistry meterRegistry) {
    bannedInstanceCounter =
        StatefulGauge.builder(BANNED_INSTANCES.getName())
            .description(BANNED_INSTANCES.getDescription())
            .register(meterRegistry);
  }

  public void countBannedInstance() {
    bannedInstanceCounter.increment();
  }

  /**
   * Since this is setting an absolute value, be very careful about calling this from outside the
   * stream processor actor. You could get incorrect values due to race conditions. If you need to
   * update it from outside, use increment/decrement only.
   */
  public void setBannedInstanceCounter(final int counter) {
    bannedInstanceCounter.set(counter);
  }
}
