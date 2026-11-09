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
  private final FairTaskScheduler fair;
  private final TimerService timerService;
  protected final int numOfThreads;
  private final String schedulerName;

  public ActorThreadGroup(
      final String groupName,
      final int numOfThreads,
      final ActorSchedulerBuilder builder,
      final String schedulerName) {
    this.groupName = groupName;
    this.numOfThreads = numOfThreads;
    this.schedulerName = schedulerName;

    if (builder.isSimpleScheduling()) {
      fair = new FairTaskScheduler(numOfThreads, CpuThreadGroup.simpleSizing(builder)[1]);
      tasks = null;
    } else {
      fair = null;
      tasks = new WorkStealingGroup(numOfThreads);
    }
    timerService = builder.getTimerService();

    threads = new ActorThread[numOfThreads];

    for (int t = 0; t < numOfThreads; t++) {
      final String threadName = String.format("%s-%d", groupName, t);
      final ActorThread thread =
          builder
              .getActorThreadFactory()
              .newThread(
                  threadName,
                  t,
                  this,
                  fair != null ? fair : tasks,
                  builder.getActorClock(),
                  builder.getActorTimerQueue(),
                  builder.getActorMetrics(),
                  fair != null ? fair.newIdleStrategy() : builder.getIdleStrategySupplier().get());

      threads[t] = thread;
    }
  }

  public void submit(final ActorTask actorTask) {
    if (fair != null) {
      actorTask.schedulingState.set(ActorTask.TaskSchedulingState.QUEUED);
      final ActorThread current = ActorThread.current();
      if (current != null
          && current.getActorThreadGroup() == this
          && current.getCurrentTask() != actorTask) {
        // woken by a job running on this worker: run it next, here
        fair.pushRunNext(current.getRunnerId(), actorTask);
      } else {
        // from outside the group, or a task re-submitting itself after its turn
        fair.enqueue(actorTask);
      }
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

  public void start() {
    for (final ActorThread actorThread : threads) {
      actorThread.start();
    }
    if (fair != null) {
      fair.start(threads);
    }
  }

  boolean isSimpleScheduling() {
    return fair != null;
  }

  TimerService getTimerService() {
    return timerService;
  }

  public String getSchedulerName() {
    return schedulerName;
  }

  public CompletableFuture<Void> closeAsync() {
    Loggers.ACTOR_LOGGER.debug("Closing actor thread ground '{}'", groupName);
    if (fair != null) {
      fair.stop();
    }

    final CompletableFuture<Void>[] terminationFutures = new CompletableFuture[numOfThreads];

    for (int i = 0; i < numOfThreads; i++) {
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
