/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beans;

import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import java.time.Duration;
import org.springframework.lang.Nullable;

/**
 * Configuration properties for the backoff idle strategy used by the actor scheduler.
 *
 * @param maxSpins the number of times the strategy will spin without work before transitioning to
 *     the yield state. If null, uses the default value from {@link
 *     ActorSchedulerBuilder#DEFAULT_MAX_SPINS}
 * @param maxYields the number of times the strategy will yield without work before transitioning to
 *     the park state. If null, uses the default value from {@link
 *     ActorSchedulerBuilder#DEFAULT_MAX_YIELDS}
 * @param minParkPeriod the minimum duration the strategy will park a thread. If null, uses the
 *     default value from {@link ActorSchedulerBuilder#DEFAULT_MIN_PARK_PERIOD_NS}
 * @param maxParkPeriod the maximum duration the strategy will park a thread. If null, uses the
 *     default value from {@link ActorSchedulerBuilder#DEFAULT_MAX_PARK_PERIOD_NS}
 */
public record IdleStrategyBasedProperties(
    @Nullable Long maxSpins,
    @Nullable Long maxYields,
    @Nullable Duration minParkPeriod,
    @Nullable Duration maxParkPeriod) {

  @Override
  public Long maxSpins() {
    return maxSpins == null ? ActorSchedulerBuilder.DEFAULT_MAX_SPINS : maxSpins;
  }

  @Override
  public Long maxYields() {
    return maxYields == null ? ActorSchedulerBuilder.DEFAULT_MAX_YIELDS : maxYields;
  }

  public long minParkPeriodNs() {
    return minParkPeriod == null
        ? ActorSchedulerBuilder.DEFAULT_MIN_PARK_PERIOD_NS
        : minParkPeriod.toNanos();
  }

  public long maxParkPeriodNs() {
    return maxParkPeriod == null
        ? ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS
        : maxParkPeriod.toNanos();
  }
}
