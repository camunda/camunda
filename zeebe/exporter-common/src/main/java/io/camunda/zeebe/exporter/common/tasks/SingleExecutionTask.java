/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tasks;

import io.camunda.zeebe.exporter.common.tasks.util.ReschedulingTaskLogger;
import io.camunda.zeebe.util.CheckedRunnable;
import io.camunda.zeebe.util.ExponentialBackoff;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/**
 * A background task that retries on failures but stops after the first successful execution.
 *
 * <p>Unlike {@link ReschedulingTask}, the result count of the {@link BackgroundTask} is ignored.
 */
public final class SingleExecutionTask implements RunnableTask {
  private final BackgroundTask task;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private final ReschedulingTaskLogger periodicLogger;
  private final ExponentialBackoff errorStrategy;
  private final AtomicBoolean taskClosed = new AtomicBoolean(false);

  private long errorDelayMs;
  private volatile boolean closed = false;
  private volatile boolean scheduled;

  public SingleExecutionTask(
      final String name,
      final Runnable runnable,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this(name, (CheckedRunnable) runnable::run, executor, logger);
  }

  public SingleExecutionTask(
      final String name,
      final CheckedRunnable runnable,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this(toBackgroundTask(name, runnable), 1_000L, executor, logger);
  }

  public SingleExecutionTask(
      final BackgroundTask task,
      final long delayBetweenRunsMs,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this.task = task;
    this.executor = executor;
    this.logger = logger;

    periodicLogger = new ReschedulingTaskLogger(logger, true);
    errorStrategy = new ExponentialBackoff(10_000, delayBetweenRunsMs, 1.2, 0);
  }

  @Override
  public void run() {
    scheduled = true;

    CompletionStage<Integer> result;
    try {
      result = task.execute();
    } catch (final Exception e) {
      result = CompletableFuture.failedFuture(e);
    }

    if (result == null) {
      logger.warn(
          "Expected to perform a background task, but no result returned for job {}; retrying",
          task);
      onError(new IllegalStateException("Background task returned null completion stage"));
      reschedule();
      return;
    }

    result
        .thenRunAsync(this::onSuccess, executor)
        .exceptionallyAsync(this::onError, executor)
        .thenRunAsync(this::reschedule, executor);
  }

  @Override
  public void close() {
    closed = true;
    if (!scheduled) {
      closeTask();
    }
  }

  private void onSuccess() {
    errorDelayMs = 0;
    closed = true;
    closeTask();
  }

  private Void onError(final Throwable error) {
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);

    final var errorMessage =
        error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
    periodicLogger.logError(
        "Error occurred while performing a background task {}; error message {}; operation will be retried",
        error,
        task.getCaption(),
        errorMessage);

    return null;
  }

  private void reschedule() {
    if (!closed) {
      logger.trace("Rescheduling task {} in {}ms", task, errorDelayMs);
      executor.schedule(this, errorDelayMs, TimeUnit.MILLISECONDS);
    } else {
      logger.info("Task {} was closed, not rescheduling.", task.getClass().getSimpleName());
      closeTask();
    }
  }

  private void closeTask() {
    if (taskClosed.compareAndSet(false, true)) {
      task.close();
    }
  }

  private static BackgroundTask toBackgroundTask(
      final String name, final CheckedRunnable runnable) {
    return new BackgroundTask() {
      @Override
      public CompletionStage<Integer> execute() {
        try {
          runnable.run();
          return CompletableFuture.completedFuture(0);
        } catch (final Exception e) {
          return CompletableFuture.failedFuture(e);
        }
      }

      @Override
      public String getCaption() {
        return name;
      }
    };
  }
}
