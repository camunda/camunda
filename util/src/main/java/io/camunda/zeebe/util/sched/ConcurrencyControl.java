/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.BiConsumer;

/**
 * Control interface to schedule tasks or follow-up tasks such that different tasks scheduled via
 * the same {@code ConcurrencyControl} object are never executed concurrently
 */
public interface ConcurrencyControl {

  /**
   * Schedules a callback to be invoked after the future has completed
   *
   * @param future the future whose completion is awaited
   * @param callback the callback to call after the future has completed
   * @param <T> result type of the future
   */
  <T> void runOnCompletion(final ActorFuture<T> future, final BiConsumer<T, Throwable> callback);

  /**
   * Schedules an action to be invoked
   *
   * @param action action to be invoked
   */
  void run(final Runnable action);

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
}
