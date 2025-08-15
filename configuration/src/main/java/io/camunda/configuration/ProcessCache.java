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

public class ProcessCache {
  private static final String PREFIX = "camunda.api.rest.process-cache";
  private static final Set<String> LEGACY_MAX_SIZE_PROPERTIES =
      Set.of("camunda.rest.processCache.maxSize");
  private static final Set<String> LEGACY_EXPIRATION_IDLE_PROPERTIES =
      Set.of("camunda.rest.processCache.expirationIdleMillis");

  /** Process cache max size */
  private int maxSize = 100;

  /** Process cache expiration milliseconds */
  private Duration expirationIdle = Duration.ofMillis(0);

  public int getMaxSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-size",
        maxSize,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_SIZE_PROPERTIES);
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }

  public Duration getExpirationIdle() {
    final Long currentExpirationIdle =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            PREFIX + ".expiration-idle",
            expirationIdle.toMillis(),
            Long.class,
            BackwardsCompatibilityMode.SUPPORTED,
            LEGACY_EXPIRATION_IDLE_PROPERTIES);

    return Duration.ofMillis(currentExpirationIdle);
  }

  public void setExpirationIdle(final Duration expirationIdle) {
    this.expirationIdle = expirationIdle;
  }
}
