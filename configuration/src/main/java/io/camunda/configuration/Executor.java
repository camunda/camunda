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

public class Executor {
  private static final String PREFIX = "camunda.api.rest.executor";
  private static final Set<String> LEGACY_CORE_POOL_SIZE_PROPERTIES =
      Set.of("camunda.rest.apiExecutor.corePoolSize");
  private static final Set<String> LEGACY_THREAD_COUNT_MULTIPLIER_PROPERTIES =
      Set.of("camunda.rest.apiExecutor.threadCountMultiplier");
  private static final Set<String> LEGACY_KEEP_ALIVE_SECONDS_PROPERTIES =
      Set.of("camunda.rest.apiExecutor.keepAliveSeconds");

  // TODO KPO add java doc
  private int corePoolSize = 1;
  private int threadCountMultiplier = 2;
  private Duration keepAlive = Duration.ofSeconds(60);

  public int getCorePoolSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".core-pool-size",
        corePoolSize,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CORE_POOL_SIZE_PROPERTIES);
  }

  public void setCorePoolSize(final int corePoolSize) {
    this.corePoolSize = corePoolSize;
  }

  public int getThreadCountMultiplier() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".thread-count-multiplier",
        threadCountMultiplier,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_THREAD_COUNT_MULTIPLIER_PROPERTIES);
  }

  public void setThreadCountMultiplier(final int threadCountMultiplier) {
    this.threadCountMultiplier = threadCountMultiplier;
  }

  public Duration getKeepAlive() {
    final Long currentKeepAliveSeconds =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            PREFIX + ".keep-alive",
            keepAlive.getSeconds(),
            Long.class,
            BackwardsCompatibilityMode.SUPPORTED,
            LEGACY_KEEP_ALIVE_SECONDS_PROPERTIES);
    return Duration.ofSeconds(currentKeepAliveSeconds);
  }

  public void setKeepAlive(final Duration keepAlive) {
    this.keepAlive = keepAlive;
  }
}
