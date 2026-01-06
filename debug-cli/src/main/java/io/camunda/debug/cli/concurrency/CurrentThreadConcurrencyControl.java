/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.concurrency;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CurrentThreadConcurrencyControl implements ConcurrencyControl {

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> future, final BiConsumer<T, Throwable> callback) {
    future.onComplete(callback);
  }

  @Override
  public <T> void runOnCompletion(
      final Collection<ActorFuture<T>> actorFutures, final Consumer<Throwable> callback) {
    actorFutures.stream()
        .collect(new ActorFutureCollector<>(this))
        .onComplete(
            (v, err) -> {
              if (err != null) {
                callback.accept(err);
              }
            });
  }

  @Override
  public void run(final Runnable action) {
    action.run();
  }

  @Override
  public <T> ActorFuture<T> call(final Callable<T> callable) {
    try {
      return CompletableActorFuture.completed(callable.call());
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  @Override
  public ScheduledTimer schedule(final Duration delay, final Runnable runnable) {
    throw new UnsupportedOperationException("schedule is not supported");
  }
}
