/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.testing;

import io.camunda.zeebe.scheduler.ActorTask;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.agrona.LangUtil;

/**
 * Implementation of {@code ActorFuture} for use in tests. The main goal is to use this in tests
 * without starting the actor scheduler.
 *
 * <p>The implementation is limited - only the methods currently needed in tests are implemented,
 * all other methods throw "Not yet implemented" exceptions
 *
 * @param <V>
 */
public final class TestActorFuture<V> implements ActorFuture<V> {

  private final CountDownLatch countDownLatch = new CountDownLatch(1);
  private final List<BiConsumer<V, Throwable>> onCompleteCallbacks = new ArrayList<>();
  private Either<Throwable, V> result;

  @Override
  public void complete(final V value) {
    result = Either.right(value);
    countDownLatch.countDown();
    triggerOnCompleteListeners();
  }

  @Override
  public void completeExceptionally(final String failure, final Throwable throwable) {
    completeExceptionally(throwable);
  }

  @Override
  public void completeExceptionally(final Throwable throwable) {
    result = Either.left(throwable);
    countDownLatch.countDown();
    triggerOnCompleteListeners();
  }

  @Override
  public V join() {
    try {
      return get();
    } catch (final Throwable t) {
      LangUtil.rethrowUnchecked(t);
      // will never be executed
      return null;
    }
  }

  @Override
  public V join(final long timeout, final TimeUnit timeUnit) {
    try {
      return get(timeout, timeUnit);
    } catch (final Throwable t) {
      LangUtil.rethrowUnchecked(t);
      // will never be executed
      return null;
    }
  }

  @Override
  public void block(final ActorTask onCompletion) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public void onComplete(final BiConsumer<V, Throwable> consumer) {
    onCompleteCallbacks.add(consumer);

    if (isDone()) {
      triggerOnCompleteListener(consumer);
    }
  }

  @Override
  public void onComplete(final BiConsumer<V, Throwable> consumer, final Executor executor) {
    onComplete((res, error) -> executor.execute(() -> consumer.accept(res, error)));
  }

  @Override
  public boolean isCompletedExceptionally() {
    return result != null && result.isLeft();
  }

  @Override
  public Throwable getException() {
    return result != null && result.isLeft() ? result.getLeft() : null;
  }

  @Override
  public <U> ActorFuture<U> andThen(final Supplier<ActorFuture<U>> next, final Executor executor) {
    return andThen(ignored -> next.get(), executor);
  }

  @Override
  public <U> ActorFuture<U> andThen(
      final Function<V, ActorFuture<U>> next, final Executor executor) {
    return andThen(
        (v, err) -> {
          if (err != null) {
            final var future = new TestActorFuture<U>();
            future.completeExceptionally(err);
            return future;
          } else {
            return next.apply(v);
          }
        },
        executor);
  }

  @Override
  public <U> ActorFuture<U> andThen(
      final BiFunction<V, Throwable, ActorFuture<U>> next, final Executor executor) {
    final ActorFuture<U> nextFuture = new CompletableActorFuture<>();
    onComplete(
        (thisResult, thisError) -> {
          final var future = next.apply(thisResult, thisError);
          future.onComplete(nextFuture, executor);
        },
        executor);
    return nextFuture;
  }

  @Override
  public <U> ActorFuture<U> thenApply(final Function<V, U> next, final Executor executor) {
    final ActorFuture<U> nextFuture = new TestActorFuture<>();
    onComplete(
        (value, error) -> {
          if (error != null) {
            nextFuture.completeExceptionally(error);
            return;
          }

          try {
            nextFuture.complete(next.apply(value));
          } catch (final Exception e) {
            nextFuture.completeExceptionally(new CompletionException(e));
          }
        },
        executor);
    return nextFuture;
  }

  private void triggerOnCompleteListeners() {
    onCompleteCallbacks.forEach(this::triggerOnCompleteListener);
  }

  private void triggerOnCompleteListener(final BiConsumer<V, Throwable> consumer) {
    result.ifRightOrLeft(
        value -> consumer.accept(value, null), error -> consumer.accept(null, error));
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public boolean isCancelled() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public boolean isDone() {
    return result != null;
  }

  @Override
  public V get() throws InterruptedException, ExecutionException {
    countDownLatch.await();

    if (result.isRight()) {
      return result.get();
    } else {
      throw new ExecutionException(result.getLeft());
    }
  }

  @Override
  public V get(final long timeout, final TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (!countDownLatch.await(timeout, unit)) {
      throw new TimeoutException("Timeout waiting for future to complete");
    }

    if (result.isRight()) {
      return result.get();
    } else {
      throw new ExecutionException(result.getLeft());
    }
  }

  public static <U> ActorFuture<U> completedFuture(final U value) {
    final var result = new TestActorFuture<U>();
    result.complete(value);
    return result;
  }

  public static <U> ActorFuture<U> failedFuture(final Throwable error) {
    final var result = new TestActorFuture<U>();
    result.completeExceptionally(error);
    return result;
  }
}
