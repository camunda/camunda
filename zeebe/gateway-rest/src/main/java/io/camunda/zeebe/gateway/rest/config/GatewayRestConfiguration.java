/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

public class GatewayRestConfiguration {

  private final ProcessCacheConfiguration processCache = new ProcessCacheConfiguration();
  private final ApiExecutorConfiguration apiExecutor = new ApiExecutorConfiguration();

  public ProcessCacheConfiguration getProcessCache() {
    return processCache;
  }

  public ApiExecutorConfiguration getApiExecutor() {
    return apiExecutor;
  }

  public static class ProcessCacheConfiguration {
    private static final int DEFAULT_CACHE_SIZE = 100;

    /**
     * Process cache max size. Default value: {@link ProcessCacheConfiguration#DEFAULT_CACHE_SIZE}.
     */
    private int maxSize = DEFAULT_CACHE_SIZE;

    /** Process cache expiration milliseconds. Default value: {@code null}. */
    private Long expirationIdleMillis = null;

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(final int maxSize) {
      this.maxSize = maxSize;
    }

    public Long getExpirationIdleMillis() {
      return expirationIdleMillis;
    }

    public void setExpirationIdleMillis(final Long expirationIdleMillis) {
      this.expirationIdleMillis = expirationIdleMillis;
    }
  }

  /**
   * Configuration for the REST API executor thread pool.
   *
   * <p>The executor is sized proportionally to the number of available CPU cores using multipliers
   * instead of absolute thread counts, so it scales sensibly across different deployment sizes
   * without manual retuning.
   *
   * <p>Effective sizes (evaluated at startup):
   *
   * <pre>
   *   corePoolSize = availableProcessors * corePoolSizeMultiplier
   *   maxPoolSize  = availableProcessors * maxPoolSizeMultiplier
   * </pre>
   *
   * <p>Threads above the core size are created on demand and reclaimed after being idle for {@code
   * keepAliveSeconds}. A {@code SynchronousQueue} + direct handoff + a {@code CallerRunsPolicy} (or
   * similar strategy, depending on the executor implementation) is typically used to:
   *
   * <ul>
   *   <li>Avoid unbounded internal task buffering
   *   <li>Apply natural back-pressure when saturated (caller executes work)
   * </ul>
   *
   * <p><b>Constraints:</b>
   *
   * <ul>
   *   <li>{@code corePoolSizeMultiplier} should be &gt;= 0 (0 means no core threads, only on-demand
   *       threads).
   *   <li>{@code maxPoolSizeMultiplier} should be &gt; 0
   * </ul>
   */
  public static class ApiExecutorConfiguration {

    private static final int DEFAULT_CORE_POOL_SIZE_MULTIPLIER = 1;
    private static final int DEFAULT_MAX_POOL_SIZE_MULTIPLIER = 2;
    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 60L;

    /**
     * Multiplier applied to the number of available processors to compute the executor's core pool
     * size (minimum number of threads kept alive).
     *
     * <p>Effective value: {@code corePoolSize = availableProcessors * corePoolSizeMultiplier}.
     *
     * <p>Use a higher value if you have steady, continuous traffic and want to minimize cold-start
     * latency; keep it low to allow the pool to scale down when idle.
     *
     * <p>Default value: {@link #DEFAULT_CORE_POOL_SIZE_MULTIPLIER}.
     */
    private int corePoolSizeMultiplier = DEFAULT_CORE_POOL_SIZE_MULTIPLIER;

    /**
     * Multiplier applied to the number of available processors to compute the executor's maximum
     * pool size (hard cap on threads).
     *
     * <p>Effective value: {@code maxPoolSize = availableProcessors * maxPoolSizeMultiplier}.
     *
     * <p>Must be >= {@code corePoolSizeMultiplier}. Increase cautiously; high values can cause
     * oversubscription for CPU-bound workloads.
     *
     * <p>Default value: {@link #DEFAULT_MAX_POOL_SIZE_MULTIPLIER}.
     */
    private int maxPoolSizeMultiplier = DEFAULT_MAX_POOL_SIZE_MULTIPLIER;

    /**
     * Time in seconds that threads above the core size may remain idle before being terminated.
     * Lower values reclaim resources faster after bursts; higher values reduce thread
     * creation/destruction churn if bursts are frequent.
     *
     * <p>Default value: {@link #DEFAULT_KEEP_ALIVE_SECONDS}.
     */
    private long keepAliveSeconds = DEFAULT_KEEP_ALIVE_SECONDS;

    public int getCorePoolSizeMultiplier() {
      return corePoolSizeMultiplier;
    }

    public void setCorePoolSizeMultiplier(final int corePoolSizeMultiplier) {
      if (corePoolSizeMultiplier < 0) {
        throw new IllegalArgumentException(
            "corePoolSizeMultiplier must be >= 0 (was " + corePoolSizeMultiplier + ")");
      }
      this.corePoolSizeMultiplier = corePoolSizeMultiplier;
    }

    public int getMaxPoolSizeMultiplier() {
      return maxPoolSizeMultiplier;
    }

    public void setMaxPoolSizeMultiplier(final int maxPoolSizeMultiplier) {
      if (maxPoolSizeMultiplier <= 0) {
        throw new IllegalArgumentException(
            "maxPoolSizeMultiplier must be > 0 (was " + maxPoolSizeMultiplier + ")");
      }
      this.maxPoolSizeMultiplier = maxPoolSizeMultiplier;
    }

    public long getKeepAliveSeconds() {
      return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(final long keepAliveSeconds) {
      if (keepAliveSeconds <= 0) {
        throw new IllegalArgumentException(
            "keepAliveSeconds must be > 0 (was " + keepAliveSeconds + ")");
      }
      this.keepAliveSeconds = keepAliveSeconds;
    }
  }
}
