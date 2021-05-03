/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import io.zeebe.util.Loggers;
import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A thread group is a group of threads which process the same kind of tasks (ie. blocking I/O vs.
 * CPU bound).
 */
public abstract class ActorThreadGroup {
  protected final String groupName;
  protected final ActorThread[] threads;
  protected final MultiLevelWorkstealingGroup tasks;
  protected final int numOfThreads;

  public ActorThreadGroup(
      final String groupName,
      final int numOfThreads,
      final int numOfQueuesPerThread,
      final ActorSchedulerBuilder builder) {
    this.groupName = groupName;
    this.numOfThreads = numOfThreads;

    tasks = new MultiLevelWorkstealingGroup(numOfThreads, numOfQueuesPerThread);

    threads = new ActorThread[numOfThreads];

    for (int t = 0; t < numOfThreads; t++) {
      final String threadName = String.format("%s-%d", groupName, t);
      final TaskScheduler taskScheduler = createTaskScheduler(tasks, builder);

      final ActorThread thread =
          builder
              .getActorThreadFactory()
              .newThread(
                  threadName,
                  t,
                  this,
                  taskScheduler,
                  builder.getActorClock(),
                  builder.getActorTimerQueue());

      threads[t] = thread;
    }
  }

  protected abstract TaskScheduler createTaskScheduler(
      MultiLevelWorkstealingGroup tasks, ActorSchedulerBuilder builder);

  public void submit(final ActorTask actorTask) {
    final int level = getLevel(actorTask);

    final ActorThread current = ActorThread.current();
    if (current != null && current.getActorThreadGroup() == this) {
      tasks.submit(actorTask, level, current.getRunnerId());
    } else {
      final int threadId = ThreadLocalRandom.current().nextInt(numOfThreads);
      tasks.submit(actorTask, level, threadId);
      threads[threadId].hintWorkAvailable();
    }
  }

  protected abstract int getLevel(ActorTask actorTask);

  public String getGroupName() {
    return groupName;
  }

  public int getNumOfThreads() {
    return numOfThreads;
  }

  public void start() {
    for (final ActorThread actorThread : threads) {
      actorThread.start();
    }
  }

  public CompletableFuture<Void> closeAsync() {
    Loggers.ACTOR_LOGGER.debug("Closing actor thread ground '{}'", groupName);

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
        terminationFutures[i] = CompletableFuture.completedFuture(null);
      }
    }

    return CompletableFuture.allOf(terminationFutures)
        .thenAccept(
            ok ->
                Loggers.ACTOR_LOGGER.debug(
                    "Closing actor thread ground '{}': closed successfully", groupName));
  }
}
