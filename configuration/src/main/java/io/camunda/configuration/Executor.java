/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

public class Executor {
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

  public int getCorePoolSizeMultiplier() {
    return corePoolSizeMultiplier;
  }

  public void setCorePoolSizeMultiplier(final int corePoolSizeMultiplier) {
    this.corePoolSizeMultiplier = corePoolSizeMultiplier;
  }

  public int getMaxPoolSizeMultiplier() {
    return maxPoolSizeMultiplier;
  }

  public void setMaxPoolSizeMultiplier(final int maxPoolSizeMultiplier) {
    this.maxPoolSizeMultiplier = maxPoolSizeMultiplier;
  }

  public Duration getKeepAlive() {
    return keepAlive;
  }

  public void setKeepAlive(final Duration keepAlive) {
    this.keepAlive = keepAlive;
  }

  public int getQueueCapacity() {
    return queueCapacity;
  }

  public void setQueueCapacity(final int queueCapacity) {
    this.queueCapacity = queueCapacity;
  }
}
