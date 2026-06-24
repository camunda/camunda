/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup.ASYNC_PROCESSING;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import java.time.Duration;

class ExtendedProcessingScheduleServiceImpl implements ProcessingScheduleService {
  private final AsyncScheduleServiceContext context;
  private final SimpleProcessingScheduleService processorActorService;
  private final boolean alwaysAsync;

  public ExtendedProcessingScheduleServiceImpl(
      final AsyncScheduleServiceContext asyncScheduleServiceContext,
      final SimpleProcessingScheduleService processorActorService,
      final boolean alwaysAsync) {
    context = asyncScheduleServiceContext;
    this.processorActorService = processorActorService;
    this.alwaysAsync = alwaysAsync;
  }

  @Override
  public void runAtFixedRateAsync(final Duration delay, final Task task) {
    runAtFixedRateAsync(delay, task, ASYNC_PROCESSING);
  }

  @Override
  public ScheduledTask runDelayedAsync(final Duration delay, final Task task) {
    return runDelayedAsync(delay, task, ASYNC_PROCESSING);
  }

  @Override
  public ScheduledTask runAtAsync(final long timestamp, final Task task) {
    return runAtAsync(timestamp, task, ASYNC_PROCESSING);
  }

  @Override
  public void runAtFixedRateAsync(
      final Duration delay, final Task task, final AsyncTaskGroup taskGroup) {
    final var actor = context.geAsyncActor(taskGroup);
    final var actorService = actor.getScheduleService();
    actor.run(
        () -> {
          // we must run in different actor in order to schedule task
          actorService.runAtFixedRate(delay, task);
        });
  }

  @Override
  public ScheduledTask runDelayedAsync(
      final Duration delay, final Task task, final AsyncTaskGroup taskGroup) {
    final var actor = context.geAsyncActor(taskGroup);
    final var actorService = actor.getScheduleService();

    final var futureScheduledTask = actor.<ScheduledTask>createFuture();
    actor.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = actorService.runDelayed(delay, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask, taskGroup);
  }

  @Override
  public ScheduledTask runAtAsync(
      final long timestamp, final Task task, final AsyncTaskGroup taskGroup) {
    final var actor = context.geAsyncActor(taskGroup);
    final var actorService = actor.getScheduleService();
    final var futureScheduledTask = actor.<ScheduledTask>createFuture();
    actor.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = actorService.runAt(timestamp, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask, taskGroup);
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
    if (alwaysAsync) {
      final var actor = context.geAsyncActor(ASYNC_PROCESSING);
      final var actorService = actor.getScheduleService();
      final var futureScheduledTask = actor.<ScheduledTask>createFuture();
      actor.run(
          () -> {
            // we must run in different actor in order to schedule task
            final var scheduledTask = actorService.runDelayed(delay, task);
            futureScheduledTask.complete(scheduledTask);
          });
      return new AsyncScheduledTask(futureScheduledTask, ASYNC_PROCESSING);
    } else {
      return processorActorService.runDelayed(delay, task);
    }
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Task task) {
    if (alwaysAsync) {
      return runDelayedAsync(delay, task);
    } else {
      return processorActorService.runDelayed(delay, task);
    }
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Task task) {
    if (alwaysAsync) {
      return runAtAsync(timestamp, task);
    } else {
      return processorActorService.runAt(timestamp, task);
    }
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Runnable task) {
    if (alwaysAsync) {
      final var actor = context.geAsyncActor(ASYNC_PROCESSING);
      final var actorService = actor.getScheduleService();
      final var futureScheduledTask = actor.<ScheduledTask>createFuture();
      actor.run(
          () -> {
            // we must run in different actor in order to schedule task
            final var scheduledTask = actorService.runAt(timestamp, task);
            futureScheduledTask.complete(scheduledTask);
          });
      return new AsyncScheduledTask(futureScheduledTask, ASYNC_PROCESSING);
    } else {
      return processorActorService.runAt(timestamp, task);
    }
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Task task) {
    if (alwaysAsync) {
      runAtFixedRateAsync(delay, task);
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
    private final AsyncTaskGroup taskGroup;

    public AsyncScheduledTask(
        final ActorFuture<ScheduledTask> futureScheduledTask, final AsyncTaskGroup taskGroup) {
      this.futureScheduledTask = futureScheduledTask;
      this.taskGroup = taskGroup;
    }

    /**
     * Cancels the task after it's scheduled. Depending on the delay, the task may execute before
     * cancellation takes effect.
     */
    @Override
    public void cancel() {
      final var actor = context.geAsyncActor(taskGroup);
      if (actor == null) {
        return;
      }
      actor.run(
          () ->
              actor.runOnCompletion(
                  futureScheduledTask,
                  (scheduledTask, throwable) -> {
                    if (scheduledTask != null) {
                      scheduledTask.cancel();
                    }
                  }));
    }
  }
}
