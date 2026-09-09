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
  private static final Set<String> LEGACY_KEEP_QUEUE_CAPACITY_PROPERTIES =
      Set.of("camunda.rest.apiExecutor.queueCapacity");

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

  /**
   * Capacity of the executor's task queue. A small bounded queue (e.g. 64) is recommended to handle
   * short bursts while still allowing the pool to grow.
   *
   * <p>Default value: 64 (as defined in ApiExecutorConfiguration#DEFAULT_QUEUE_CAPACITY)
   */
  private int queueCapacity = 64;

  /**
   * Additional core threads to add per physical tenant beyond the first, on top of the vCPU-derived
   * {@link #corePoolSizeMultiplier} term. Each physical tenant is an independent traffic source, so
   * this term is additive rather than folded into the vCPU multiplier.
   *
   * <p>Effective value: {@code corePoolSize = min(corePoolSizeCeiling, availableProcessors *
   * corePoolSizeMultiplier + max(0, physicalTenantCount - 1) * corePoolSizePerTenant)}.
   *
   * <p>Default value: 1
   */
  private int corePoolSizePerTenant = 1;

  /**
   * Additional max threads to add per physical tenant beyond the first, on top of the vCPU-derived
   * {@link #maxPoolSizeMultiplier} term. See {@link #corePoolSizePerTenant}.
   *
   * <p>Default value: 2
   */
  private int maxPoolSizePerTenant = 2;

  /**
   * Additional queue capacity to add per physical tenant beyond the first. See {@link
   * #corePoolSizePerTenant}.
   *
   * <p>Default value: 16
   */
  private int queueCapacityPerTenant = 16;

  /**
   * Flat ceiling on {@code corePoolSize} regardless of vCPU count or physical-tenant count. Not
   * vCPU-relative, since these threads are I/O-bound (parked waiting on RDBMS/ES) and cheap to keep
   * idle.
   *
   * <p>Default value: 256
   */
  private int corePoolSizeCeiling = 256;

  /**
   * Flat ceiling on {@code maxPoolSize}. See {@link #corePoolSizeCeiling}.
   *
   * <p>Default value: 512
   */
  private int maxPoolSizeCeiling = 512;

  /**
   * Flat ceiling on {@code queueCapacity}. See {@link #corePoolSizeCeiling}.
   *
   * <p>Default value: 4096
   */
  private int queueCapacityCeiling = 4096;

  public int getCorePoolSizeMultiplier() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
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
        UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
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

  public int getQueueCapacity() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".queue-capacity",
        queueCapacity,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_KEEP_QUEUE_CAPACITY_PROPERTIES);
  }

  public void setQueueCapacity(final int queueCapacity) {
    this.queueCapacity = queueCapacity;
  }

  public int getCorePoolSizePerTenant() {
    return corePoolSizePerTenant;
  }

  public void setCorePoolSizePerTenant(final int corePoolSizePerTenant) {
    this.corePoolSizePerTenant = corePoolSizePerTenant;
  }

  public int getMaxPoolSizePerTenant() {
    return maxPoolSizePerTenant;
  }

  public void setMaxPoolSizePerTenant(final int maxPoolSizePerTenant) {
    this.maxPoolSizePerTenant = maxPoolSizePerTenant;
  }

  public int getQueueCapacityPerTenant() {
    return queueCapacityPerTenant;
  }

  public void setQueueCapacityPerTenant(final int queueCapacityPerTenant) {
    this.queueCapacityPerTenant = queueCapacityPerTenant;
  }

  public int getCorePoolSizeCeiling() {
    return corePoolSizeCeiling;
  }

  public void setCorePoolSizeCeiling(final int corePoolSizeCeiling) {
    this.corePoolSizeCeiling = corePoolSizeCeiling;
  }

  public int getMaxPoolSizeCeiling() {
    return maxPoolSizeCeiling;
  }

  public void setMaxPoolSizeCeiling(final int maxPoolSizeCeiling) {
    this.maxPoolSizeCeiling = maxPoolSizeCeiling;
  }

  public int getQueueCapacityCeiling() {
    return queueCapacityCeiling;
  }

  public void setQueueCapacityCeiling(final int queueCapacityCeiling) {
    this.queueCapacityCeiling = queueCapacityCeiling;
  }
}
