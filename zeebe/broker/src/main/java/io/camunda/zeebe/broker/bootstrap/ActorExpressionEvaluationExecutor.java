/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Runs each task in a throwaway actor on the IO threads of the tenant scheduler, so that the CPU
 * time of an expression evaluation is charged to the CPU budget of the tenant and stops with it.
 *
 * <p>The IO threads are used because the caller blocks until the result is available. The caller
 * holds a CPU thread of the same scheduler, which could then not run the evaluation.
 *
 * <p>A running evaluation cannot be interrupted: cancelling its future only discards the result. An
 * evaluation that does not end keeps its IO thread.
 */
final class ActorExpressionEvaluationExecutor extends AbstractExecutorService {

  private final ActorSchedulingService scheduler;
  private volatile boolean shutdown;

  ActorExpressionEvaluationExecutor(final ActorSchedulingService scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public <T> Future<T> submit(final Callable<T> task) {
    final var future = new CompletableFuture<T>();
    execute(
        () -> {
          // a task that timed out before it started is not run
          if (!future.isDone()) {
            try {
              future.complete(task.call());
            } catch (final Throwable t) {
              future.completeExceptionally(t);
            }
          }
        });
    return future;
  }

  @Override
  public void execute(final Runnable command) {
    if (shutdown) {
      throw new RejectedExecutionException("Expression evaluation executor is shut down");
    }
    scheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  command.run();
                  actor.close();
                });
          }
        },
        SchedulingHints.ioBound());
  }

  @Override
  public void shutdown() {
    shutdown = true;
  }

  @Override
  public List<Runnable> shutdownNow() {
    shutdown = true;
    return List.of();
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public boolean isTerminated() {
    return shutdown;
  }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit) {
    return true;
  }
}
