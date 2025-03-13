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
import io.camunda.zeebe.stream.api.scheduling.BackgroundTaskScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import java.time.Duration;

public class BackgroundTaskScheduleServiceImpl implements BackgroundTaskScheduleService {

  private final SimpleProcessingScheduleService backgroundTaskActorService;
  private final ConcurrencyControl backgroundTaskConcurrencyControl;

  public BackgroundTaskScheduleServiceImpl(
      final SimpleProcessingScheduleService backgroundTaskActorService,
      final ConcurrencyControl backgroundTaskConcurrencyControl) {
    this.backgroundTaskActorService = backgroundTaskActorService;
    this.backgroundTaskConcurrencyControl = backgroundTaskConcurrencyControl;
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
    final var futureScheduledTask = backgroundTaskConcurrencyControl.<ScheduledTask>createFuture();
    backgroundTaskConcurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = backgroundTaskActorService.runDelayed(delay, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask);
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Task task) {
    final var futureScheduledTask = backgroundTaskConcurrencyControl.<ScheduledTask>createFuture();
    backgroundTaskConcurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = backgroundTaskActorService.runDelayed(delay, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask);
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Task task) {
    final var futureScheduledTask = backgroundTaskConcurrencyControl.<ScheduledTask>createFuture();
    backgroundTaskConcurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = backgroundTaskActorService.runAt(timestamp, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask);
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Runnable task) {
    final var futureScheduledTask = backgroundTaskConcurrencyControl.<ScheduledTask>createFuture();
    backgroundTaskConcurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = backgroundTaskActorService.runAt(timestamp, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask);
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Task task) {
    backgroundTaskConcurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          backgroundTaskActorService.runAtFixedRate(delay, task);
        });
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
      backgroundTaskConcurrencyControl.run(
          () ->
              backgroundTaskConcurrencyControl.runOnCompletion(
                  futureScheduledTask,
                  (scheduledTask, throwable) -> {
                    if (scheduledTask != null) {
                      scheduledTask.cancel();
                    }
                  }));
    }
  }
}
