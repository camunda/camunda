/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tasks;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * A task that executes a supplier returning the next delay duration and then reschedules itself
 * using that duration. On error, falls back to an error delay supplier.
 *
 * <p>This is a synchronous counterpart to {@link ReschedulingTask}: instead of wrapping an async
 * {@link BackgroundTask}, it wraps a synchronous {@link Supplier} that returns the delay until the
 * next run. This is suitable for tasks where the service call itself determines the rescheduling
 * interval.
 */
public final class SelfSchedulingTask implements RunnableTask {
  private final String name;
  private final Supplier<Duration> task;
  private final Supplier<Duration> errorDelay;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private volatile boolean closed = false;

  public SelfSchedulingTask(
      final String name,
      final Supplier<Duration> task,
      final Supplier<Duration> errorDelay,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this.name = name;
    this.task = task;
    this.errorDelay = errorDelay;
    this.executor = executor;
    this.logger = logger;
  }

  @Override
  public void run() {
    if (closed) {
      return;
    }
    Duration nextDelay;
    try {
      nextDelay = task.get();
    } catch (final Exception e) {
      final var fallback = errorDelay.get();
      logger.warn(
          "Failed to execute background task '{}', retrying in {}ms: {}",
          name,
          fallback.toMillis(),
          e.getMessage());
      nextDelay = fallback;
    }
    if (!closed) {
      executor.schedule(this, nextDelay.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void close() {
    closed = true;
  }
}
