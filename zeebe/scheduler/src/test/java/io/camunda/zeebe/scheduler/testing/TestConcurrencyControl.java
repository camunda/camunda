/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.testing;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Test implementation of {@code ConcurrencyControl}. The main goal is to use this in tests without
 * starting the actor scheduler.
 *
 * <p>The fact that this is used in tests without actor scheduler also means that its behavior
 * differs from a {@code ConcurrencyControl} implementations based on the actor scheduler. The
 * differences are as follows:
 *
 * <ul>
 *   <li>Callables, runaables passed to its methods are called immediately, synchronously on the
 *       current thread (as opposed to the actor scheduler which would schedule them to run deferred
 *       and asynchronous - from the point of view of the caller)
 *   <li>Works best in conjunction with {@code TestActorFuture} returned by this class
 * </ul>
 *
 * Due to these limitations this implementation is ideal for unit tests. <br>
 * However, precaution is deserved to not rely only on unit tests alone. Developers are advised to
 * accompany unit tests with integration/acceptance tests which do use the actor scheduler in order
 * to test the dynamic scheduling behavior.
 */
public class TestConcurrencyControl implements ConcurrencyControl {

  private final Object lock = new Object();

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> future, final BiConsumer<T, Throwable> callback) {
    future.onComplete(callback);
  }

  @Override
  public <T> void runOnCompletion(
      final Collection<ActorFuture<T>> actorFutures, final Consumer<Throwable> callback) {
    if (actorFutures.isEmpty()) {
      callback.accept(null);
      return;
    }

    final var error = new AtomicReference<Throwable>();
    final var futuresCompleted = new AtomicInteger(actorFutures.size());
    final var finalFuture = new TestActorFuture<>();
    for (final ActorFuture<T> f : actorFutures) {
      f.onComplete(
          (r, e) -> {
            if (e != null) {
              error.set(e);
            }
            if (futuresCompleted.decrementAndGet() == 0) {
              if (error.get() != null) {
                finalFuture.completeExceptionally(error.get());
              } else {
                finalFuture.complete(null);
              }
            }
          });
    }
    finalFuture.onComplete((ignore, throwable) -> callback.accept(throwable));
  }

  @Override
  public void run(final Runnable action) {
    synchronized (lock) {
      action.run();
    }
  }

  @Override
  public <T> ActorFuture<T> call(final Callable<T> callable) {
    final T call;
    try {
      call = callable.call();
    } catch (final Exception e) {
      return TestActorFuture.failedFuture(e);
    }
    return TestActorFuture.completedFuture(call);
  }

  @Override
  public ScheduledTimer schedule(final Duration delay, final Runnable runnable) {
    // Schedule immediately
    runnable.run();
    return () -> {};
  }

  @Override
  public <V> ActorFuture<V> createFuture() {
    return new TestActorFuture<>();
  }

  @Override
  public <V> ActorFuture<V> createCompletedFuture() {
    return completedFuture(null);
  }

  public <U> ActorFuture<U> completedFuture(final U value) {
    final ActorFuture<U> result = createFuture();
    result.complete(value);
    return result;
  }

  public <U> ActorFuture<U> failedFuture(final Throwable error) {
    final ActorFuture<U> result = createFuture();
    result.completeExceptionally(error);
    return result;
  }
}
