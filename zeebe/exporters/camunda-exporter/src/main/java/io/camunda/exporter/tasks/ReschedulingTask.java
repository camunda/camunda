/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import io.camunda.zeebe.util.ExponentialBackoff;
import io.camunda.zeebe.util.VisibleForTesting;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public final class ReschedulingTask implements RunnableTask {
  private static final Set<Class<? extends Exception>> BACKGROUND_SUPPRESSED_EXCEPTIONS =
      Set.of(SocketTimeoutException.class, ConnectException.class, SocketException.class);
  private final BackgroundTask task;
  private final int minimumWorkCount;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private final ExponentialBackoff idleStrategy;
  private final ExponentialBackoff errorStrategy;
  private long executionCounter;

  private long delayMs;
  private long errorDelayMs;
  private volatile boolean closed = false;

  public ReschedulingTask(
      final BackgroundTask task,
      final int minimumWorkCount,
      final long delayBetweenRunsMs,
      final long maxDelayBetweenRunsMs,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this.task = task;
    this.minimumWorkCount = minimumWorkCount;
    this.executor = executor;
    this.logger = logger;

    idleStrategy = new ExponentialBackoff(maxDelayBetweenRunsMs, delayBetweenRunsMs, 1.2, 0);
    errorStrategy = new ExponentialBackoff(10_000, delayBetweenRunsMs, 1.2, 0);
  }

  @Override
  public void run() {
    var result = task.execute();
    // while we could always expect this to return a non-null result, we don't necessarily want to
    // stop, and more importantly, we want to make it transparent that something went wrong
    if (result == null) {
      logger.warn(
          "Expected to perform a background task, but no result returned for job {}; rescheduling anyway",
          task);
      result = CompletableFuture.completedFuture(0);
    }

    result
        .thenApplyAsync(this::onWorkPerformed, executor)
        .exceptionallyAsync(this::onError, executor)
        .thenAcceptAsync(this::reschedule, executor)
        .thenAccept(unused -> executionCounter++);
  }

  @Override
  public void close() {
    closed = true;
  }

  @VisibleForTesting
  public long executionCount() {
    return executionCounter;
  }

  private long onWorkPerformed(final int count) {
    errorDelayMs = 0;

    // if we worked on less than the minimum expected work count, then there's probably even more
    // work to be done, so use the minimum delay between runs; otherwise, backoff from the last
    // known delay
    delayMs =
        count >= minimumWorkCount ? idleStrategy.applyAsLong(0) : idleStrategy.applyAsLong(delayMs);
    return delayMs;
  }

  private long onError(final Throwable error) {
    final String logMessage =
        "Error occurred while performing a background task; operation will be retried";
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);

    if (BACKGROUND_SUPPRESSED_EXCEPTIONS.contains(error.getCause().getClass())) {
      logger.warn("{}. `{}`", logMessage, error.getCause().getMessage());
    } else {
      logger.error(logMessage, error);
    }

    return errorDelayMs;
  }

  private void reschedule(final long delay) {
    if (!closed) {
      logger.trace("Rescheduling task {} in {}ms", task, delay);
      executor.schedule(this, delay, TimeUnit.MILLISECONDS);
    } else {
      logger.info("Task {} was closed, not rescheduling.", task.getClass().getSimpleName());
    }
  }
}
