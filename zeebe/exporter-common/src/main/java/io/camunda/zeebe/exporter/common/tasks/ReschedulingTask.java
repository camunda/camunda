/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tasks;

import io.camunda.zeebe.exporter.common.tasks.util.ReschedulingTaskLogger;
import io.camunda.zeebe.util.ExponentialBackoff;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

public final class ReschedulingTask implements RunnableTask {
  static final long PAUSE_CHECK_DELAY_MS = 5_000L;
  private static final long ERROR_DELAY_MS = 10_000L;
  private final BackgroundTask task;
  private final int minimumWorkCount;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private final ReschedulingTaskLogger periodicLogger;
  private final ExponentialBackoff idleStrategy;
  private final ExponentialBackoff errorStrategy;
  private final AtomicLong executionCounter = new AtomicLong(0L);
  private final PauseStateSupplier pauseState;
  private final long pausedDelayMs;
  private long delayMs;
  private long errorDelayMs;
  private volatile boolean closed = false;
  private volatile boolean scheduled;
  private volatile boolean pauseLogged;

  public ReschedulingTask(
      final BackgroundTask task,
      final int minimumWorkCount,
      final long delayBetweenRunsMs,
      final long maxDelayBetweenRunsMs,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this(
        task,
        minimumWorkCount,
        delayBetweenRunsMs,
        maxDelayBetweenRunsMs,
        executor,
        logger,
        PauseStateSupplier.NOT_PAUSED);
  }

  public ReschedulingTask(
      final BackgroundTask task,
      final int minimumWorkCount,
      final long delayBetweenRunsMs,
      final long maxDelayBetweenRunsMs,
      final ScheduledExecutorService executor,
      final Logger logger,
      final PauseStateSupplier pauseState) {
    this.task = task;
    this.minimumWorkCount = minimumWorkCount;
    this.executor = executor;
    this.logger = logger;
    this.pauseState = pauseState;
    // some tasks have an interval of hours, a resume must not wait that long
    pausedDelayMs = Math.min(delayBetweenRunsMs, PAUSE_CHECK_DELAY_MS);

    periodicLogger = new ReschedulingTaskLogger(logger, true);
    idleStrategy = new ExponentialBackoff(maxDelayBetweenRunsMs, delayBetweenRunsMs, 1.2, 0);
    // some tasks have a really long delayBetweenRunsMs, so we need to tweak
    // those delays for the error backoff.
    errorStrategy =
        new ExponentialBackoff(
            Math.max(ERROR_DELAY_MS, delayBetweenRunsMs),
            Math.min(ERROR_DELAY_MS, delayBetweenRunsMs),
            1.2,
            0);
  }

  @Override
  public void run() {
    scheduled = true;
    CompletionStage<Integer> result;
    try {
      result =
          pauseState.paused()
              ? CompletableFuture.failedFuture(
                  new TaskPausedException("Exporting is paused, skipping " + task.getCaption()))
              : task.execute();
    } catch (final Exception e) {
      // in case the task throws an exception instead of returning a failed CompletionStage
      result = CompletableFuture.failedFuture(e);
    }
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
        .thenAccept(unused -> executionCounter.incrementAndGet())
        .exceptionally(
            error -> {
              final var cause = error.getCause() != null ? error.getCause() : error;
              if (closed && cause instanceof RejectedExecutionException) {
                logger.debug(
                    "Task {} was rejected because executor is shutting down; not retrying", task);
                return null;
              }
              logger.error(
                  "Unexpected error occurred while rescheduling task {} (bug in error handling code?);"
                      + " task will NOT be retried",
                  task,
                  error);
              return null;
            });
  }

  @Override
  public void close() {
    closed = true;
    if (!scheduled) {
      // if we never scheduled the task, we need to close it ourselves
      task.close();
    }
  }

  @VisibleForTesting
  public long executionCount() {
    return executionCounter.get();
  }

  private long onWorkPerformed(final int count) {
    logResumeIfPaused();
    errorDelayMs = 0;

    // if we worked on less than the minimum expected work count, then there's probably even more
    // work to be done, so use the minimum delay between runs; otherwise, backoff from the last
    // known delay
    delayMs =
        count >= minimumWorkCount ? idleStrategy.applyAsLong(0) : idleStrategy.applyAsLong(delayMs);
    return delayMs;
  }

  @VisibleForTesting
  public boolean isPaused() {
    return pauseState.paused();
  }

  private void logResumeIfPaused() {
    if (pauseLogged) {
      pauseLogged = false;
      logger.info("Task {} is no longer paused, running again", task.getCaption());
    }
  }

  private long onError(final Throwable error) {
    if (TaskPausedException.isCauseOf(error)) {
      if (!pauseLogged) {
        pauseLogged = true;
        logger.info(
            "Task {} is paused because exporting is paused, checking again every {}ms",
            task.getCaption(),
            pausedDelayMs);
      }
      logger.trace("Task {} is paused, checking again in {}ms", task, pausedDelayMs);
      return pausedDelayMs;
    }

    logResumeIfPaused();
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);

    logError(error);

    return errorDelayMs;
  }

  private void logError(final Throwable error) {
    final var cause = error.getCause();
    final var msg = cause != null ? cause.getMessage() : error.getMessage();
    periodicLogger.logError(
        "Error occurred while performing a background task {}; error message {}; operation will be retried",
        error,
        task.getCaption(),
        msg);
  }

  private void reschedule(final long delay) {
    if (!closed) {
      logger.trace("Rescheduling task {} in {}ms", task, delay);
      executor.schedule(this, delay, TimeUnit.MILLISECONDS);
    } else {
      logger.info("Task {} was closed, not rescheduling.", task.getClass().getSimpleName());
      task.close();
    }
  }
}
