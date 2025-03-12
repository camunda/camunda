/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.stream.api.scheduling.AsyncSchedulePool.ASYNC_PROCESSING;

import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.scheduling.AsyncSchedulePool;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.metrics.ScheduledTaskMetrics;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ExtendedProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final ActorSchedulingService actorSchedulingService;
  private final ActorControl streamProcessorActorControl;
  private final ProcessingScheduleServiceImpl processorActorService;

  private final StageableScheduledCommandCache commandCache;
  private final InstantSource clock;
  private final Duration interval;
  private final ScheduledTaskMetrics metrics;
  private final boolean alwaysAsync;
  private final int partitionId;

  private final HashMap<AsyncSchedulePool, AsyncProcessingScheduleServiceActor> asyncActors;
  private final HashMap<AsyncSchedulePool, ProcessingScheduleServiceImpl> asyncActorServices;
  private final Supplier<Phase> streamProcessorPhaseSupplier;
  private final BooleanSupplier abortCondition;
  private final Supplier<LogStreamWriter> writerSupplier;
  private final AsyncProcessingScheduleServiceActor asyncActor;

  public ExtendedProcessingScheduleServiceImpl(
      final ActorSchedulingService actorSchedulingService,
      final ActorControl streamProcessorActorControl,
      final Supplier<Phase> streamProcessorPhaseSupplier,
      final BooleanSupplier abortCondition,
      final Supplier<LogStreamWriter> writerSupplier,
      final StageableScheduledCommandCache commandCache,
      final InstantSource clock,
      final Duration interval,
      final ScheduledTaskMetrics metrics,
      final boolean alwaysAsync,
      final int partitionId) {
    this.actorSchedulingService = actorSchedulingService;
    this.streamProcessorActorControl = streamProcessorActorControl;
    this.streamProcessorPhaseSupplier = streamProcessorPhaseSupplier;
    this.abortCondition = abortCondition;
    this.writerSupplier = writerSupplier;
    this.commandCache = commandCache;
    this.clock = clock;
    this.interval = interval;
    this.metrics = metrics;
    this.alwaysAsync = alwaysAsync;
    this.partitionId = partitionId;

    asyncActors = new LinkedHashMap<>();
    asyncActorServices = new LinkedHashMap<>();

    processorActorService =
        new ProcessingScheduleServiceImpl(
            streamProcessorPhaseSupplier, // this is volatile
            abortCondition,
            writerSupplier,
            commandCache,
            clock,
            interval,
            metrics);
    asyncActor = createAsyncActor(ASYNC_PROCESSING);
  }

  @VisibleForTesting
  public ExtendedProcessingScheduleServiceImpl(
      final ProcessingScheduleServiceImpl sync,
      final ProcessingScheduleServiceImpl async,
      final AsyncProcessingScheduleServiceActor asyncConcurrencyControl,
      final boolean alwaysAsync) {
    this.alwaysAsync = alwaysAsync;
    actorSchedulingService = null;
    streamProcessorActorControl = null;
    streamProcessorPhaseSupplier = null;
    abortCondition = null;
    writerSupplier = null;
    commandCache = null;
    clock = null;
    interval = null;
    metrics = null;

    processorActorService = sync;
    partitionId = 0;
    asyncActor = null;

    asyncActors = new LinkedHashMap<>();
    asyncActors.put(ASYNC_PROCESSING, asyncConcurrencyControl);
    asyncActorServices = new LinkedHashMap<>();
    asyncActorServices.put(ASYNC_PROCESSING, async);
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
    final var actor = asyncActors.computeIfAbsent(pool, this::createAndSubmitAsyncActor);
    final var actorService = asyncActorServices.get(pool);
    actor.run(
        () -> {
          // we must run in different actor in order to schedule task
          actorService.runAtFixedRate(delay, task);
        });
  }

  @Override
  public ScheduledTask runDelayedOnPool(
      final Duration delay, final Task task, final AsyncSchedulePool pool) {
    final var actor = asyncActors.computeIfAbsent(pool, this::createAndSubmitAsyncActor);
    final var actorService = asyncActorServices.get(pool);

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
    final var actor = asyncActors.computeIfAbsent(pool, this::createAndSubmitAsyncActor);
    final var actorService = asyncActorServices.get(pool.getName());
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
  public ActorFuture<Void> open() {
    return chainSteps(
        0,
        new Step[] {
          () -> processorActorService.open(streamProcessorActorControl),
          () -> actorSchedulingService.submitActor(asyncActor)
        });
  }

  @Override
  public ActorFuture<Void> closeActorsAsync() {
    final Step[] array =
        asyncActors.values().stream().map(a -> (Step) a::closeAsync).toArray(Step[]::new);
    return chainSteps(0, array);
  }

  @Override
  public void closeSchedulers() {
    processorActorService.close();
    asyncActorServices.values().forEach(ProcessingScheduleServiceImpl::close);
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
    if (alwaysAsync) {
      final var actor =
          asyncActors.computeIfAbsent(ASYNC_PROCESSING, this::createAndSubmitAsyncActor);
      final var actorService = asyncActorServices.get(ASYNC_PROCESSING);
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
      final var actor =
          asyncActors.computeIfAbsent(ASYNC_PROCESSING, this::createAndSubmitAsyncActor);
      final var actorService = asyncActorServices.get(ASYNC_PROCESSING);
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

  private AsyncProcessingScheduleServiceActor createAndSubmitAsyncActor(
      final AsyncSchedulePool pool) {
    final var actor = createAsyncActor(pool);

    actorSchedulingService.submitActor(actor, pool.getSchedulingHints()).join();
    return actor;
  }

  private AsyncProcessingScheduleServiceActor createAsyncActor(final AsyncSchedulePool pool) {
    final var actorService =
        new ProcessingScheduleServiceImpl(
            streamProcessorPhaseSupplier, // this is volatile
            abortCondition,
            writerSupplier,
            commandCache,
            clock,
            interval,
            metrics);
    final var actor =
        new AsyncProcessingScheduleServiceActor(pool.getName(), actorService, partitionId);

    asyncActorServices.put(pool, actorService);
    asyncActors.put(pool, actor);
    return actor;
  }

  private ActorFuture<Void> chainSteps(final int index, final Step[] steps) {
    if (index >= steps.length) {
      return new CompletableActorFuture<>();
    }

    final Step step = steps[index];
    final ActorFuture<Void> future = step.run();

    final int nextIndex = index + 1;
    if (nextIndex < steps.length) {
      future.onComplete(
          (v, t) -> {
            if (t == null) {
              chainSteps(nextIndex, steps);
            } else {
              future.completeExceptionally(t);
            }
          });
    }
    return future;
  }

  private interface Step {
    ActorFuture<Void> run();
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
      final var actor = asyncActors.get(pool);
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
