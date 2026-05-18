/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

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
  public ActorFuture<@Nullable Void> submitCpuBound(final ActorTask task) {
    return submitTask(task, cpuBoundThreads);
  }

  public ActorFuture<@Nullable Void> submitIoBoundTask(final ActorTask task) {
    return submitTask(task, ioBoundThreads);
  }

  private ActorFuture<@Nullable Void> submitTask(
      final ActorTask task, final ActorThreadGroup threadGroup) {
    if (task.getLifecyclePhase() != ActorLifecyclePhase.CLOSED) {
      throw new IllegalStateException("ActorTask was already submitted!");
    }
    final ActorFuture<@Nullable Void> startingFuture = task.onTaskScheduled(threadGroup);

    threadGroup.submit(task);
    return startingFuture;
  }

  public void start() {
    cpuBoundThreads.start();
    ioBoundThreads.start();
  }

  public CompletableFuture<@Nullable Void> closeAsync() {
    return CompletableFuture.allOf(ioBoundThreads.closeAsync(), cpuBoundThreads.closeAsync());
  }

  public ActorThreadGroup getCpuBoundThreads() {
    return cpuBoundThreads;
  }

  public ActorThreadGroup getIoBoundThreads() {
    return ioBoundThreads;
  }
}
