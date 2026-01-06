/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;

public class Distribution {
  private static final String PREFIX = "camunda.processing.engine.distribution";

  private static final Set<String> LEGACY_MAX_BACKOFF_DURATION_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.distribution.maxBackoffDuration");
  private static final Set<String> LEGACY_REDISTRIBUTION_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.distribution.redistributionInterval");

  /**
   * Allows configuring the maximum backoff duration for command redistribution retries. The retry
   * interval is doubled after each retry until it reaches this maximum duration.
   */
  private Duration maxBackoffDuration = DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION;

  /**
   * Allows configuring command redistribution retry interval. This is the initial interval used
   * when retrying command distributions that have not been acknowledged.
   */
  private Duration redistributionInterval = DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL;

  public Duration getMaxBackoffDuration() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-backoff-duration",
        maxBackoffDuration,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_BACKOFF_DURATION_PROPERTIES);
  }

  public void setMaxBackoffDuration(final Duration maxBackoffDuration) {
    this.maxBackoffDuration = maxBackoffDuration;
  }

  public Duration getRedistributionInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".redistribution-interval",
        redistributionInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_REDISTRIBUTION_INTERVAL_PROPERTIES);
  }

  public void setRedistributionInterval(final Duration redistributionInterval) {
    this.redistributionInterval = redistributionInterval;
  }
}
