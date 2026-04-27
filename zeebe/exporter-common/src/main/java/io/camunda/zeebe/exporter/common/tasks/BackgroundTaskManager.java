/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tasks;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Manages background tasks by submitting them to a {@link ScheduledThreadPoolExecutor} and handling
 * their lifecycle (start/close).
 *
 * <p>The {@link #start()} method is retry-safe: calling it multiple times after partial failures
 * will only submit previously unsubmitted tasks.
 *
 * <p>This class handles the generic lifecycle; exporters with additional resources to close (e.g.
 * search-engine repositories) should wrap this class and close those resources after calling {@link
 * #close()}.
 */
public final class BackgroundTaskManager implements CloseableSilently {
  private final int partitionId;
  private final Logger logger;
  private final ScheduledThreadPoolExecutor executor;
  private final List<RunnableTask> tasks;
  private final Duration closeTimeout;

  private int submittedTasks = 0;

  public BackgroundTaskManager(
      final int partitionId,
      final Logger logger,
      final ScheduledThreadPoolExecutor executor,
      final List<RunnableTask> tasks,
      final Duration closeTimeout) {
    this.partitionId = partitionId;
    this.logger = Objects.requireNonNull(logger, "must specify a logger");
    this.executor = Objects.requireNonNull(executor, "must specify an executor");
    this.tasks = Objects.requireNonNull(tasks, "must specify tasks");
    this.closeTimeout = Objects.requireNonNull(closeTimeout, "must specify a close timeout");
  }

  /**
   * Submits unsubmitted tasks to the executor. Safe to call multiple times: only tasks not yet
   * submitted are scheduled.
   */
  public void start() {
    final var unsubmittedTasks = tasks.size() - submittedTasks;
    if (unsubmittedTasks == 0) {
      return;
    }

    logger.debug(
        "Starting {} background tasks (with {} previously submitted tasks out of {} tasks)",
        unsubmittedTasks,
        submittedTasks,
        tasks.size());
    for (; submittedTasks < tasks.size(); submittedTasks++) {
      final var task = tasks.get(submittedTasks);
      executor.submit(task);
    }
  }

  /** Stops all tasks and shuts down the executor, waiting up to {@code closeTimeout}. */
  @Override
  public void close() {
    tasks.forEach(RunnableTask::close);
    executor.shutdown();
    try {
      executor.awaitTermination(closeTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
    if (!executor.isTerminated()) {
      executor.shutdownNow();
    }
  }

  public static ScheduledThreadPoolExecutor buildExecutor(
      final String executorName, final int partitionId, final Logger logger) {
    final var threadFactory =
        Thread.ofPlatform()
            .name(executorName + "-p" + partitionId + "-tasks-", 0)
            .uncaughtExceptionHandler(FatalErrorHandler.uncaughtExceptionHandler(logger))
            .factory();
    final var executor = new ScheduledThreadPoolExecutor(0, threadFactory);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    executor.allowCoreThreadTimeOut(true);
    executor.setKeepAliveTime(1, TimeUnit.MINUTES);

    return executor;
  }
}
