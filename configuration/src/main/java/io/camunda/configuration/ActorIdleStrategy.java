/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import java.time.Duration;
import java.util.Set;

public class ActorIdleStrategy {
  private static final String PREFIX = "camunda.system.actor.idle";

  private static final Set<String> LEGACY_MAX_SPINS_PROPERTIES =
      Set.of("zeebe.actor.idle.maxSpins");
  private static final Set<String> LEGACY_MAX_YIELDS_PROPERTIES =
      Set.of("zeebe.actor.idle.maxYields");
  private static final Set<String> LEGACY_MIN_PARK_PERIOD_PROPERTIES =
      Set.of("zeebe.actor.idle.minParkPeriod");
  private static final Set<String> LEGACY_MAX_PARK_PERIOD_PROPERTIES =
      Set.of("zeebe.actor.idle.maxParkPeriod");

  private long maxSpins = ActorSchedulerBuilder.DEFAULT_MAX_SPINS;
  private long maxYields = ActorSchedulerBuilder.DEFAULT_MAX_YIELDS;
  private Duration minParkPeriod =
      Duration.ofNanos(ActorSchedulerBuilder.DEFAULT_MIN_PARK_PERIOD_NS);
  private Duration maxParkPeriod =
      Duration.ofNanos(ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS);

  public long getMaxSpins() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-spins",
        maxSpins,
        Long.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_SPINS_PROPERTIES);
  }

  public void setMaxSpins(final long maxSpins) {
    this.maxSpins = maxSpins;
  }

  public long getMaxYields() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-yields",
        maxYields,
        Long.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_YIELDS_PROPERTIES);
  }

  public void setMaxYields(final long maxYields) {
    this.maxYields = maxYields;
  }

  public Duration getMinParkPeriod() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".min-park-period",
        minParkPeriod,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MIN_PARK_PERIOD_PROPERTIES);
  }

  public void setMinParkPeriod(final Duration minParkPeriod) {
    this.minParkPeriod = minParkPeriod;
  }

  public Duration getMaxParkPeriod() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-park-period",
        maxParkPeriod,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_PARK_PERIOD_PROPERTIES);
  }

  public void setMaxParkPeriod(final Duration maxParkPeriod) {
    this.maxParkPeriod = maxParkPeriod;
  }
}
