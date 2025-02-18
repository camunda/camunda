/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import io.camunda.exporter.tasks.util.ReschedulingTaskLogger;
import io.camunda.zeebe.util.ExponentialBackoff;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public final class ReschedulingTask implements RunnableTask {
  private final BackgroundTask task;
  private final int minimumWorkCount;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private final ReschedulingTaskLogger periodicLogger;
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

    periodicLogger = new ReschedulingTaskLogger(logger);
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
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);

    logError(error);

    return errorDelayMs;
  }

  private void logError(final Throwable error) {
    periodicLogger.logError(
        "Error occurred while performing a background task {}; error message {}; operation will be retried",
        error,
        task.getCaption(),
        error.getCause().getMessage());
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
