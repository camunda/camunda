/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Write {

  private static final String PREFIX = "camunda.processing.flow-control.write";

  private static final boolean DEFAULT_WRITE_ENABLED = false;
  private static final int DEFAULT_LIMIT = 0;
  private static final Duration DEFAULT_RAMP_UP_TIME = Duration.ZERO;

  private static final Set<String> LEGACY_WRITE_ENABLED_PROPERTIES =
      Set.of("zeebe.broker.flowControl.write.enabled");
  private static final Set<String> LEGACY_LIMIT_PROPERTIES =
      Set.of("zeebe.broker.flowControl.write.limit");
  private static final Set<String> LEGACY_RAMP_UP_TIME_PROPERTIES =
      Set.of("zeebe.broker.flowControl.write.rampUp");

  /** Enable rate limit */
  private boolean enabled = DEFAULT_WRITE_ENABLED;

  /** Sets the maximum number of records written per second */
  private int limit = DEFAULT_LIMIT;

  /** Sets the ramp up time, for example 10s */
  private Duration rampUp = DEFAULT_RAMP_UP_TIME;

  /** Configure throttling */
  @NestedConfigurationProperty private Throttle throttle = new Throttle();

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_WRITE_ENABLED_PROPERTIES);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".limit",
        limit,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_LIMIT_PROPERTIES);
  }

  public void setLimit(final int limit) {
    this.limit = limit;
  }

  public Duration getRampUp() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".ramp-up",
        rampUp,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_RAMP_UP_TIME_PROPERTIES);
  }

  public void setRampUp(final Duration rampUp) {
    this.rampUp = rampUp;
  }

  public Throttle getThrottle() {
    return throttle;
  }

  public void setThrottle(final Throttle throttle) {
    this.throttle = throttle;
  }
}
