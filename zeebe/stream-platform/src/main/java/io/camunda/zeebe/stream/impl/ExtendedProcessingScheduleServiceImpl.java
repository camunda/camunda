/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import java.time.Duration;

public class ExtendedProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final SimpleProcessingScheduleService processorActorService;
  private final SimpleProcessingScheduleService asyncActorService;
  private final SimpleProcessingScheduleService backgroundTaskActorService;
  private final ConcurrencyControl asyncConcurrencyControl;
  private final ConcurrencyControl backgroundTaskConcurrencyControl;
  private final boolean alwaysAsync;

  public ExtendedProcessingScheduleServiceImpl(
      final SimpleProcessingScheduleService processorActorService,
      final SimpleProcessingScheduleService asyncActorService,
      final SimpleProcessingScheduleService backgroundTaskActorService,
      final ConcurrencyControl asyncConcurrencyControl,
      final ConcurrencyControl backgroundTaskConcurrencyControl,
      final boolean alwaysAsync) {
    this.processorActorService = processorActorService;
    this.asyncActorService = asyncActorService;
    this.backgroundTaskActorService = backgroundTaskActorService;
    this.asyncConcurrencyControl = asyncConcurrencyControl;
    this.backgroundTaskConcurrencyControl = backgroundTaskConcurrencyControl;
    this.alwaysAsync = alwaysAsync;
  }

  @Override
  public void runAtFixedRateAsync(final Duration delay, final Task task, final Pool pool) {
    if (pool == Pool.BACKGROUND) {
      backgroundTaskConcurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            backgroundTaskActorService.runAtFixedRate(delay, task);
          });
    } else {
      asyncConcurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            asyncActorService.runAtFixedRate(delay, task);
          });
    }
  }

  @Override
  public ScheduledTask runDelayedAsync(final Duration delay, final Task task, final Pool pool) {
    if (pool == Pool.BACKGROUND) {
      final var futureScheduledTask = backgroundTaskConcurrencyControl.<ScheduledTask>createFuture();
      backgroundTaskConcurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            final var scheduledTask = backgroundTaskActorService.runDelayed(delay, task);
            futureScheduledTask.complete(scheduledTask);
          });
      return new AsyncScheduledTask(futureScheduledTask);
    } else {
      final var futureScheduledTask = asyncConcurrencyControl.<ScheduledTask>createFuture();
      asyncConcurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            final var scheduledTask = asyncActorService.runDelayed(delay, task);
            futureScheduledTask.complete(scheduledTask);
          });
      return new AsyncScheduledTask(futureScheduledTask);
    }
  }

  @Override
  public ScheduledTask runAtAsync(final long timestamp, final Task task, final Pool pool) {
    if (pool == Pool.BACKGROUND) {
      final var futureScheduledTask = backgroundTaskConcurrencyControl.<ScheduledTask>createFuture();
      backgroundTaskConcurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            final var scheduledTask = backgroundTaskActorService.runAt(timestamp, task);
            futureScheduledTask.complete(scheduledTask);
          });
      return new AsyncScheduledTask(futureScheduledTask);
    } else {
      final var futureScheduledTask = asyncConcurrencyControl.<ScheduledTask>createFuture();
      asyncConcurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            final var scheduledTask = asyncActorService.runAt(timestamp, task);
            futureScheduledTask.complete(scheduledTask);
          });
      return new AsyncScheduledTask(futureScheduledTask);
    }
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
    if (alwaysAsync) {
      final var futureScheduledTask = asyncConcurrencyControl.<ScheduledTask>createFuture();
      asyncConcurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            final var scheduledTask = asyncActorService.runDelayed(delay, task);
            futureScheduledTask.complete(scheduledTask);
          });
      return new AsyncScheduledTask(futureScheduledTask);
    } else {
      return processorActorService.runDelayed(delay, task);
    }
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Task task) {
    if (alwaysAsync) {
      return runDelayedAsync(delay, task, Pool.REALTIME);
    } else {
      return processorActorService.runDelayed(delay, task);
    }
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Task task) {
    if (alwaysAsync) {
      return runAtAsync(timestamp, task, Pool.REALTIME);
    } else {
      return processorActorService.runAt(timestamp, task);
    }
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Runnable task) {
    if (alwaysAsync) {
      final var futureScheduledTask = asyncConcurrencyControl.<ScheduledTask>createFuture();
      asyncConcurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            final var scheduledTask = asyncActorService.runAt(timestamp, task);
            futureScheduledTask.complete(scheduledTask);
          });
      return new AsyncScheduledTask(futureScheduledTask);
    } else {
      return processorActorService.runAt(timestamp, task);
    }
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Task task) {
    if (alwaysAsync) {
      runAtFixedRateAsync(delay, task, Pool.REALTIME);
    } else {
      processorActorService.runAtFixedRate(delay, task);
    }
  }

  /**
   * Allows control over a task that is asynchronously scheduled. It uses a future that holds the
   * task once it's scheduled.
   */
  private final class AsyncScheduledTask implements ScheduledTask {

    private final ActorFuture<ScheduledTask> futureScheduledTask;

    public AsyncScheduledTask(final ActorFuture<ScheduledTask> futureScheduledTask) {
      this.futureScheduledTask = futureScheduledTask;
    }

    /**
     * Cancels the task after it's scheduled. Depending on the delay, the task may execute before
     * cancellation takes effect.
     */
    @Override
    public void cancel() {
      asyncConcurrencyControl.run(
          () ->
              asyncConcurrencyControl.runOnCompletion(
                  futureScheduledTask,
                  (scheduledTask, throwable) -> {
                    if (scheduledTask != null) {
                      scheduledTask.cancel();
                    }
                  }));
    }
  }
}
