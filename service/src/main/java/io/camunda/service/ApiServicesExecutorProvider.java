/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import java.util.Objects;
import java.util.concurrent.*;

public final class ApiServicesExecutorProvider {

  private static final String API_SERVICE_THREAD_NAME = "api-service-thread-";
  private final ExecutorService executor;

  public ApiServicesExecutorProvider(
      final int corePoolSizeMultiplier,
      final int maxPoolSizeMultiplier,
      final long keepAliveSeconds) {
    executor =
        Objects.requireNonNull(
            create(corePoolSizeMultiplier, maxPoolSizeMultiplier, keepAliveSeconds),
            "REST API Executor Service must not be null");
  }

  public ApiServicesExecutorProvider(final ExecutorService executor) {
    this.executor = Objects.requireNonNull(executor, "REST API Executor Service must not be null");
  }

  public ExecutorService getExecutor() {
    return executor;
  }

  /**
   * Create a customizable dynamic ThreadPoolExecutor.
   *
   * @param corePoolSizeMultiplier multiplier for the number of core threads based on available
   *     processors
   * @param maxPoolSizeMultiplier multiplier for the maximum number of threads based on available
   *     processors
   * @param keepAliveSeconds how long to keep idle threads above core alive
   */
  private static ExecutorService create(
      final int corePoolSizeMultiplier,
      final int maxPoolSizeMultiplier,
      final long keepAliveSeconds) {
    final int availableProcessors = Runtime.getRuntime().availableProcessors();
    final int corePoolSize = availableProcessors * corePoolSizeMultiplier;
    final int maxPoolSize = availableProcessors * maxPoolSizeMultiplier;
    final ThreadFactory threadFactory =
        Thread.ofPlatform().name(API_SERVICE_THREAD_NAME, 0).daemon(true).factory();

    final ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            keepAliveSeconds,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy());
    executor.allowCoreThreadTimeOut(true); // needed if corePoolSize is greater than 0
    return executor;
  }
}
