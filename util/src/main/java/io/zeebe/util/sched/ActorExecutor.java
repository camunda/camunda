/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;
import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.CompletableFuture;

/**
 * Used to submit {@link ActorTask ActorTasks} and Blocking Actions to the scheduler's internal
 * runners and queues.
 */
public final class ActorExecutor {
  private final ActorThreadGroup cpuBoundThreads;
  private final ActorThreadGroup ioBoundThreads;

  public ActorExecutor(final ActorSchedulerBuilder builder) {
    ioBoundThreads = builder.getIoBoundActorThreads();
    cpuBoundThreads = builder.getCpuBoundActorThreads();
  }

  /**
   * Initially submit a non-blocking actor to be managed by this scheduler.
   *
   * @param task the task to submit
   */
  public ActorFuture<Void> submitCpuBound(final ActorTask task) {
    return submitTask(task, cpuBoundThreads);
  }

  public ActorFuture<Void> submitIoBoundTask(final ActorTask task) {
    return submitTask(task, ioBoundThreads);
  }

  private ActorFuture<Void> submitTask(final ActorTask task, final ActorThreadGroup threadGroup) {
    if (task.getLifecyclePhase() != ActorLifecyclePhase.CLOSED) {
      throw new IllegalStateException("ActorTask was already submitted!");
    }
    final ActorFuture<Void> startingFuture = task.onTaskScheduled(this, threadGroup);

    threadGroup.submit(task);
    return startingFuture;
  }

  public void start() {
    cpuBoundThreads.start();
    ioBoundThreads.start();
  }

  public CompletableFuture<Void> closeAsync() {
    return CompletableFuture.allOf(ioBoundThreads.closeAsync(), cpuBoundThreads.closeAsync());
  }

  public ActorThreadGroup getCpuBoundThreads() {
    return cpuBoundThreads;
  }

  public ActorThreadGroup getIoBoundThreads() {
    return ioBoundThreads;
  }
}
