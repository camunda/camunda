/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.StatefulMeterRegistry;
import java.util.concurrent.atomic.AtomicLong;

public final class BannedInstanceMetrics {

  private final AtomicLong bannedInstanceCounter;

  public BannedInstanceMetrics(final StatefulMeterRegistry meterRegistry) {
    bannedInstanceCounter = meterRegistry.newLongGauge(EngineMetricsDoc.BANNED_INSTANCES).state();
  }

  public void countBannedInstance() {
    bannedInstanceCounter.incrementAndGet();
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
