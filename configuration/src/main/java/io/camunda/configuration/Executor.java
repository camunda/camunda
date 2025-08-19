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
  private static final Set<String> LEGACY_CORE_POOL_SIZE_MULTIPLIER_PROPERTIES =
      Set.of("camunda.rest.apiExecutor.corePoolSizeMultiplier");
  private static final Set<String> LEGACY_MAX_POOL_SIZE_MULTIPLIER_PROPERTIES =
      Set.of("camunda.rest.apiExecutor.maxPoolSizeMultiplier");
  private static final Set<String> LEGACY_KEEP_ALIVE_SECONDS_PROPERTIES =
      Set.of("camunda.rest.apiExecutor.keepAliveSeconds");

  /**
   * Multiplier applied to the number of available processors to compute the executor's core pool
   * size (minimum number of threads kept alive).
   *
   * <p>Effective value: {@code corePoolSize = availableProcessors * corePoolSizeMultiplier}.
   *
   * <p>Use a higher value if you have steady, continuous traffic and want to minimize cold-start
   * latency; keep it low to allow the pool to scale down when idle.
   *
   * <p>Default value: 1 (as defined in {@code
   * ApiExecutorConfiguration#DEFAULT_CORE_POOL_SIZE_MULTIPLIER})
   */
  private int corePoolSizeMultiplier = 1;

  /**
   * Multiplier applied to the number of available processors to compute the executor's maximum pool
   * size (hard cap on threads).
   *
   * <p>Effective value: {@code maxPoolSize = availableProcessors * maxPoolSizeMultiplier}.
   *
   * <p>Must be >= {@code corePoolSizeMultiplier}. Increase cautiously; high values can cause
   * oversubscription for CPU-bound workloads.
   *
   * <p>Default value: 2 (as defined in {@code
   * ApiExecutorConfiguration#DEFAULT_MAX_POOL_SIZE_MULTIPLIER})
   */
  private int maxPoolSizeMultiplier = 2;

  /**
   * Time in seconds that threads above the core size may remain idle before being terminated. Lower
   * values reclaim resources faster after bursts; higher values reduce thread creation/destruction
   * churn if bursts are frequent.
   *
   * <p>Default value: 60 (as defined in {@code
   * ApiExecutorConfiguration#DEFAULT_KEEP_ALIVE_SECONDS})
   */
  private Duration keepAlive = Duration.ofSeconds(60);

  public int getCorePoolSizeMultiplier() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".core-pool-size-multiplier",
        corePoolSizeMultiplier,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CORE_POOL_SIZE_MULTIPLIER_PROPERTIES);
  }

  public void setCorePoolSizeMultiplier(final int corePoolSizeMultiplier) {
    this.corePoolSizeMultiplier = corePoolSizeMultiplier;
  }

  public int getMaxPoolSizeMultiplier() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-pool-size-multiplier",
        maxPoolSizeMultiplier,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_POOL_SIZE_MULTIPLIER_PROPERTIES);
  }

  public void setMaxPoolSizeMultiplier(final int maxPoolSizeMultiplier) {
    this.maxPoolSizeMultiplier = maxPoolSizeMultiplier;
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
