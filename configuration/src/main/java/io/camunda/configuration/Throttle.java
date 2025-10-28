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

public class Throttle {

  private static final String PREFIX = "camunda.processing.flow-control.write.throttle";

  private static final boolean DEFAULT_THROTTLING_ENABLED = false;
  private static final int DEFAULT_ACCEPTABLE_BACKLOG = 100_000;
  private static final int DEFAULT_MINIMUM_LIMIT = 100;
  private static final Duration DEFAULT_RESOLUTION = Duration.ofSeconds(15);

  private static final Set<String> LEGACY_THROTTLING_ENABLED_PROPERTIES =
      Set.of("zeebe.broker.flowControl.write.throttling.enabled");
  private static final Set<String> LEGACY_ACCEPTABLE_BACKLOG_PROPERTIES =
      Set.of("zeebe.broker.flowControl.write.throttling.acceptableBacklog");
  private static final Set<String> LEGACY_MINIMUM_LIMIT_PROPERTIES =
      Set.of("zeebe.broker.flowControl.write.throttling.minimumLimit");
  private static final Set<String> LEGACY_RESOLUTION_PROPERTIES =
      Set.of("zeebe.broker.flowControl.write.throttling.resolution");

  /** Enable throttling. If enabled, throttle the write rate based on exporting backlog */
  private boolean enabled = DEFAULT_THROTTLING_ENABLED;

  /**
   * When exporting is a bottleneck, the write rate is throttled to keep the backlog at this value.
   */
  private int acceptableBacklog = DEFAULT_ACCEPTABLE_BACKLOG;

  /** Even when exporting is fully blocked, always allow this many writes per second */
  private int minimumLimit = DEFAULT_MINIMUM_LIMIT;

  /** How often to adjust the throttling */
  private Duration resolution = DEFAULT_RESOLUTION;

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_THROTTLING_ENABLED_PROPERTIES);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getAcceptableBacklog() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".acceptable-backlog",
        acceptableBacklog,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ACCEPTABLE_BACKLOG_PROPERTIES);
  }

  public void setAcceptableBacklog(final int acceptableBacklog) {
    this.acceptableBacklog = acceptableBacklog;
  }

  public int getMinimumLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".minimum-limit",
        minimumLimit,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MINIMUM_LIMIT_PROPERTIES);
  }

  public void setMinimumLimit(final int minimumLimit) {
    this.minimumLimit = minimumLimit;
  }

  public Duration getResolution() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".resolution",
        resolution,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_RESOLUTION_PROPERTIES);
  }

  public void setResolution(final Duration resolution) {
    this.resolution = resolution;
  }
}
