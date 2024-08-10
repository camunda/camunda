/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Control interface to schedule tasks or follow-up tasks such that different tasks scheduled via
 * the same {@code ConcurrencyControl} object are never executed concurrently
 */
public interface ConcurrencyControl extends Executor {

  /**
   * Schedules a callback to be invoked after the future has completed
   *
   * @param future the future whose completion is awaited
   * @param callback the callback to call after the future has completed
   * @param <T> result type of the future
   */
  <T> void runOnCompletion(final ActorFuture<T> future, final BiConsumer<T, Throwable> callback);

  /**
   * Invoke the callback when the given futures are completed (successfully or exceptionally). This
   * call does not block the actor.
   *
   * <p>The callback is executed while the actor is in the following actor lifecycle phases: {@link
   * ActorLifecyclePhase#STARTED}
   *
   * @param futures the futures to wait on
   * @param callback The throwable is <code>null</code> when all futures are completed successfully.
   *     Otherwise, it holds the exception of the last completed future.
   */
  <T> void runOnCompletion(
      final Collection<ActorFuture<T>> futures, final Consumer<Throwable> callback);

  /**
   * Schedules an action to be invoked (must be called from an actor thread)
   *
   * @param action action to be invoked
   */
  void run(final Runnable action);

  /**
   * Schedules a callable to be executed
   *
   * @param callable callable to be executed
   * @return a future with the result
   * @param <T> type of the result
   */
  <T> ActorFuture<T> call(final Callable<T> callable);

  /** Schedule a task to be executed after a delay */
  ScheduledTimer schedule(final Duration delay, final Runnable runnable);

  /**
   * Create a new future object
   *
   * @param <V> value type of future
   * @return new future object
   */
  default <V> ActorFuture<V> createFuture() {
    return new CompletableActorFuture<>();
  }

  /**
   * Create a new completed future object
   *
   * @param <V> value type of future
   * @return new completed future object
   */
  default <V> ActorFuture<V> createCompletedFuture() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  default void execute(@NotNull final Runnable command) {
    run(command);
  }
}
