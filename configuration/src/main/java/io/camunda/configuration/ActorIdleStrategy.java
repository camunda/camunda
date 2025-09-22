/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import java.time.Duration;

public class ActorIdleStrategy {
  private long maxSpins = ActorSchedulerBuilder.DEFAULT_MAX_SPINS;
  private long maxYields = ActorSchedulerBuilder.DEFAULT_MAX_YIELDS;
  private Duration minParkPeriod =
      Duration.ofNanos(ActorSchedulerBuilder.DEFAULT_MIN_PARK_PERIOD_NS);
  private Duration maxParkPeriod =
      Duration.ofNanos(ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS);

  public long getMaxSpins() {
    return maxSpins;
  }

  public void setMaxSpins(final long maxSpins) {
    this.maxSpins = maxSpins;
  }

  public long getMaxYields() {
    return maxYields;
  }

  public void setMaxYields(final long maxYields) {
    this.maxYields = maxYields;
  }

  public Duration getMinParkPeriod() {
    return minParkPeriod;
  }

  public void setMinParkPeriod(final Duration minParkPeriod) {
    this.minParkPeriod = minParkPeriod;
  }

  public Duration getMaxParkPeriod() {
    return maxParkPeriod;
  }

  public void setMaxParkPeriod(final Duration maxParkPeriod) {
    this.maxParkPeriod = maxParkPeriod;
  }
}
