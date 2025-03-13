/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.stream.api.scheduling.AsyncSchedulePool.ASYNC_PROCESSING;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.scheduling.AsyncSchedulePool;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import java.time.Duration;

public class ExtendedProcessingScheduleServiceImpl implements ProcessingScheduleService {
  private final AsyncScheduleServiceContext context;
  private final ProcessingScheduleServiceImpl processorActorService;
  private final boolean alwaysAsync;

  public ExtendedProcessingScheduleServiceImpl(
      final AsyncScheduleServiceContext asyncScheduleServiceContext,
      final ProcessingScheduleServiceImpl processorActorService,
      final boolean alwaysAsync) {
    context = asyncScheduleServiceContext;
    this.processorActorService = processorActorService;
    this.alwaysAsync = alwaysAsync;
  }

  @Override
  public void runAtFixedRateAsync(final Duration delay, final Task task) {
    runAtFixedRateOnPool(delay, task, ASYNC_PROCESSING);
  }

  @Override
  public ScheduledTask runDelayedAsync(final Duration delay, final Task task) {
    return runDelayedOnPool(delay, task, ASYNC_PROCESSING);
  }

  @Override
  public ScheduledTask runAtAsync(final long timestamp, final Task task) {
    return runAtOnPool(timestamp, task, ASYNC_PROCESSING);
  }

  @Override
  public void runAtFixedRateOnPool(
      final Duration delay, final Task task, final AsyncSchedulePool pool) {
    final var actor = context.getOrCreateAsyncActor(pool);
    final var actorService = context.getAsyncActorService(pool);
    actor.run(
        () -> {
          // we must run in different actor in order to schedule task
          actorService.runAtFixedRate(delay, task);
        });
  }

  @Override
  public ScheduledTask runDelayedOnPool(
      final Duration delay, final Task task, final AsyncSchedulePool pool) {
    final var actor = context.getOrCreateAsyncActor(pool);
    final var actorService = context.getAsyncActorService(pool);

    final var futureScheduledTask = actor.<ScheduledTask>createFuture();
    actor.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = actorService.runDelayed(delay, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask, pool);
  }

  @Override
  public ScheduledTask runAtOnPool(
      final long timestamp, final Task task, final AsyncSchedulePool pool) {
    final var actor = context.getOrCreateAsyncActor(pool);
    final var actorService = context.getAsyncActorService(pool);
    final var futureScheduledTask = actor.<ScheduledTask>createFuture();
    actor.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = actorService.runAt(timestamp, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask, pool);
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
    if (alwaysAsync) {
      final var actor = context.getOrCreateAsyncActor(ASYNC_PROCESSING);
      final var actorService = context.getAsyncActorService(ASYNC_PROCESSING);
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
      final var actor = context.getOrCreateAsyncActor(ASYNC_PROCESSING);
      final var actorService = context.getAsyncActorService(ASYNC_PROCESSING);
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
    private final AsyncSchedulePool pool;

    public AsyncScheduledTask(
        final ActorFuture<ScheduledTask> futureScheduledTask, final AsyncSchedulePool pool) {
      this.futureScheduledTask = futureScheduledTask;
      this.pool = pool;
    }

    /**
     * Cancels the task after it's scheduled. Depending on the delay, the task may execute before
     * cancellation takes effect.
     */
    @Override
    public void cancel() {
      final var actor = context.geAsyncActor(pool, false);
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
