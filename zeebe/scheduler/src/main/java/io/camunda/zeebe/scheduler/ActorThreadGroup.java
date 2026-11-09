/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static io.camunda.zeebe.util.Unit.unit;

import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.util.Loggers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A thread group is a group of threads which process the same kind of tasks (ie. blocking I/O vs.
 * CPU bound).
 */
public abstract class ActorThreadGroup {
  protected final String groupName;
  protected final ActorThread[] threads;
  protected final WorkStealingGroup tasks;
  protected final int numOfThreads;
  private final String schedulerName;
  private final boolean virtualThreads;
  private final ActorSchedulerBuilder builder;

  public ActorThreadGroup(
      final String groupName,
      final int numOfThreads,
      final ActorSchedulerBuilder builder,
      final String schedulerName) {
    this.groupName = groupName;
    this.numOfThreads = numOfThreads;
    this.schedulerName = schedulerName;
    this.builder = builder;
    // a custom thread factory (e.g. the controlled test scheduler) needs real ActorThreads
    virtualThreads =
        VirtualActorRunner.ENABLED
            && builder.getActorThreadFactory() instanceof ActorScheduler.DefaultActorThreadFactory;

    tasks = new WorkStealingGroup(numOfThreads);

    // SPIKE: in virtual-thread mode every actor runs on its own virtual thread; no platform threads
    threads = new ActorThread[virtualThreads ? 0 : numOfThreads];

    for (int t = 0; t < threads.length; t++) {
      final String threadName = String.format("%s-%d", groupName, t);
      final ActorThread thread =
          builder
              .getActorThreadFactory()
              .newThread(
                  threadName,
                  t,
                  this,
                  tasks,
                  builder.getActorClock(),
                  builder.getActorTimerQueue(),
                  builder.getActorMetrics(),
                  builder.getIdleStrategySupplier().get());

      threads[t] = thread;
    }
  }

  public void submit(final ActorTask actorTask) {
    if (virtualThreads) {
      submitVirtual(actorTask);
      return;
    }

    final ActorThread current = ActorThread.current();
    if (current != null && current.getActorThreadGroup() == this) {
      tasks.submit(actorTask, current.getRunnerId());
    } else {
      final int threadId = ThreadLocalRandom.current().nextInt(numOfThreads);
      tasks.submit(actorTask, threadId);
      threads[threadId].hintWorkAvailable();
    }
  }

  private void submitVirtual(final ActorTask actorTask) {
    actorTask.schedulingState.set(ActorTask.TaskSchedulingState.QUEUED);
    VirtualActorRunner runner = actorTask.virtualRunner;
    if (runner == null) {
      // only the initial submit from ActorExecutor gets here, on the scheduling thread
      runner =
          new VirtualActorRunner(
              actorTask, this, builder.getActorClock(), builder.getActorMetrics());
      actorTask.virtualRunner = runner;
      runner.wakeup();
      runner.start();
    } else {
      runner.wakeup();
    }
  }

  public void start() {
    for (final ActorThread actorThread : threads) {
      actorThread.start();
    }
  }

  public String getSchedulerName() {
    return schedulerName;
  }

  public CompletableFuture<Void> closeAsync() {
    Loggers.ACTOR_LOGGER.debug("Closing actor thread ground '{}'", groupName);

    final CompletableFuture<Void>[] terminationFutures = new CompletableFuture[threads.length];

    for (int i = 0; i < threads.length; i++) {
      final ActorThread thread = threads[i];
      try {
        terminationFutures[i] = thread.close();
      } catch (final IllegalStateException e) {
        Loggers.ACTOR_LOGGER.error(
            "Closing actor thread ground '{}'. Failed to close thread {}",
            groupName,
            thread.getRunnerId(),
            e);
        terminationFutures[i] = CompletableFuture.completedFuture(unit());
      }
    }

    return CompletableFuture.allOf(terminationFutures)
        .thenAccept(
            ok ->
                Loggers.ACTOR_LOGGER.debug(
                    "Closing actor thread ground '{}': closed successfully", groupName));
  }
}
