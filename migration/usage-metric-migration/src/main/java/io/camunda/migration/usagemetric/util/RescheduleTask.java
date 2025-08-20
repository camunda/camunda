/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric.util;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RescheduleTask<T> implements Runnable {

  private final ScheduledExecutorService scheduler;
  private final Supplier<T> fnCallback;
  private final Supplier<T> fnTimeout;
  private final CompletableFuture<T> future;
  private final Instant timeout;

  public RescheduleTask(
      final ScheduledExecutorService scheduler,
      final Supplier<T> fnCallback,
      final Supplier<T> fnTimeout,
      final long maxWaitSeconds,
      final CompletableFuture<T> future) {
    this.scheduler = scheduler;
    this.fnCallback = fnCallback;
    this.fnTimeout = fnTimeout;
    this.future = future;
    timeout = Instant.now().plusSeconds(maxWaitSeconds);
  }

  @Override
  public void run() {
    try {
      final T result = fnCallback.get();

      if (result != null) {
        future.complete(result);
      } else if (Instant.now().isAfter(timeout)) {
        future.complete(fnTimeout.get());
      } else {
        scheduler.schedule(this, 5, TimeUnit.SECONDS);
      }
    } catch (final Exception e) {
      future.completeExceptionally(e);
    }
  }
}
