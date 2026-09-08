/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.search.schema.config.SchemaManagerConfiguration.SchemaManagerRetryConfiguration;
import java.time.Duration;
import java.util.Set;

public class SchemaManagerRetry {

  private static final String LEGACY_PREFIX = "camunda.database.schema-manager.retry";

  private final String prefix;

  private int maxRetries = SchemaManagerRetryConfiguration.DEFAULT_MAX_RETRIES;
  private Duration minRetryDelay = SchemaManagerRetryConfiguration.DEFAULT_MIN_RETRY_DELAY;
  private Duration maxRetryDelay = SchemaManagerRetryConfiguration.DEFAULT_MAX_RETRY_DELAY;

  public SchemaManagerRetry(final String databaseName) {
    prefix = "camunda.data.secondary-storage.%s.retry".formatted(databaseName);
  }

  public int getMaxRetries() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix + ".max-retries",
        maxRetries,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_PREFIX + ".maxRetries"));
  }

  public void setMaxRetries(final int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Duration getMinRetryDelay() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix + ".min-retry-delay",
        minRetryDelay,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_PREFIX + ".minRetryDelay"));
  }

  public void setMinRetryDelay(final Duration minRetryDelay) {
    this.minRetryDelay = minRetryDelay;
  }

  public Duration getMaxRetryDelay() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix + ".max-retry-delay",
        maxRetryDelay,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_PREFIX + ".maxRetryDelay"));
  }

  public void setMaxRetryDelay(final Duration maxRetryDelay) {
    this.maxRetryDelay = maxRetryDelay;
  }
}
