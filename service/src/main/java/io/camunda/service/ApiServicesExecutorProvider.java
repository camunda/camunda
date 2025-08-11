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
import java.util.concurrent.atomic.AtomicInteger;

public final class ApiServicesExecutorProvider {

  public static final String API_SERVICE_THREAD_NAME = "api-service-thread-";
  private final ExecutorService executor;

  public ApiServicesExecutorProvider(
      final int corePoolSize, final int threadCountMultiplier, final long keepAliveSeconds) {
    executor =
        Objects.requireNonNull(
            create(corePoolSize, threadCountMultiplier, keepAliveSeconds),
            "REST API Executor Service must not be null");
  }

  public ApiServicesExecutorProvider(final ExecutorService executor) {
    this.executor = Objects.requireNonNull(executor, "REST API Executor Service must not be null");
  }

  public static ApiServicesExecutorProvider of(final ExecutorService executor) {
    return new ApiServicesExecutorProvider(executor);
  }

  public ExecutorService getExecutor() {
    return executor;
  }

  /**
   * Create a customizable dynamic ThreadPoolExecutor.
   *
   * @param corePoolSize min threads to keep alive
   * @param threadCountMultiplier multiplier for the number of threads based on available processors
   * @param keepAliveSeconds how long to keep idle threads above core alive
   */
  private static ExecutorService create(
      final int corePoolSize, final int threadCountMultiplier, final long keepAliveSeconds) {
    final int maxPoolSize = Runtime.getRuntime().availableProcessors() * threadCountMultiplier;
    final ThreadFactory threadFactory =
        new ThreadFactory() {
          private final AtomicInteger counter = new AtomicInteger(0);

          @Override
          public Thread newThread(final Runnable r) {
            final Thread t = new Thread(r, API_SERVICE_THREAD_NAME + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
          }
        };

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
