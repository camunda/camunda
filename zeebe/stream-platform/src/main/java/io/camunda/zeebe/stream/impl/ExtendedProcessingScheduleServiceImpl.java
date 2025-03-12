/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.metrics.ScheduledTaskMetrics;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.InstantSource;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ExtendedProcessingScheduleServiceImpl implements ProcessingScheduleService {
  private final ActorSchedulingService actorSchedulingService;
  private final ActorControl streamProcessorActorControl;
  private final ProcessingScheduleServiceImpl processorActorService;
  private final ProcessingScheduleServiceImpl asyncActorService;

  private final AsyncProcessingScheduleServiceActor asyncActor;
  private final ConcurrencyControl asyncConcurrencyControl;
  private final boolean alwaysAsync;

  private final List<AsyncProcessingScheduleServiceActor> asyncActors;
  private final List<ProcessingScheduleServiceImpl> asyncActorServices;

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
    this.alwaysAsync = alwaysAsync;

    asyncActors = new LinkedList<>();
    asyncActorServices = new LinkedList<>();

    processorActorService =
        new ProcessingScheduleServiceImpl(
            streamProcessorPhaseSupplier, // this is volatile
            abortCondition,
            writerSupplier,
            commandCache,
            clock,
            interval,
            metrics);

    asyncActorService =
        new ProcessingScheduleServiceImpl(
            streamProcessorPhaseSupplier, // this is volatile
            abortCondition,
            writerSupplier,
            commandCache,
            clock,
            interval,
            metrics);
    asyncActor = new AsyncProcessingScheduleServiceActor(asyncActorService, partitionId);
    asyncConcurrencyControl = asyncActor.getActorControl();

    asyncActors.add(asyncActor);
    asyncActorServices.add(asyncActorService);
  }

  @VisibleForTesting
  public ExtendedProcessingScheduleServiceImpl(
      final ProcessingScheduleServiceImpl sync,
      final ProcessingScheduleServiceImpl async,
      final ConcurrencyControl asyncConcurrencyControl,
      final boolean alwaysAsync) {
    this.alwaysAsync = alwaysAsync;
    actorSchedulingService = null;
    streamProcessorActorControl = null;
    asyncActor = null;
    asyncActors = null;
    asyncActorServices = null;

    this.asyncConcurrencyControl = asyncConcurrencyControl;
    processorActorService = sync;
    asyncActorService = async;
  }

  @Override
  public void runAtFixedRateAsync(final Duration delay, final Task task) {
    asyncConcurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          asyncActorService.runAtFixedRate(delay, task);
        });
  }

  @Override
  public ScheduledTask runDelayedAsync(final Duration delay, final Task task) {
    final var futureScheduledTask = asyncConcurrencyControl.<ScheduledTask>createFuture();
    asyncConcurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = asyncActorService.runDelayed(delay, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask);
  }

  @Override
  public ScheduledTask runAtAsync(final long timestamp, final Task task) {
    final var futureScheduledTask = asyncConcurrencyControl.<ScheduledTask>createFuture();
    asyncConcurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          final var scheduledTask = asyncActorService.runAt(timestamp, task);
          futureScheduledTask.complete(scheduledTask);
        });
    return new AsyncScheduledTask(futureScheduledTask);
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
    final Step[] array = asyncActors.stream().map(a -> (Step) a::closeAsync).toArray(Step[]::new);
    return chainSteps(0, array);
  }

  @Override
  public void closeSchedulers() {
    processorActorService.close();
    asyncActorServices.forEach(ProcessingScheduleServiceImpl::close);
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
      runAtFixedRateAsync(delay, task);
    } else {
      processorActorService.runAtFixedRate(delay, task);
    }
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
