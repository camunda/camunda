/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.future;

import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.ActorTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
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
  public boolean isCompletedExceptionally() {
    return result != null && result.isLeft();
  }

  @Override
  public Throwable getException() {
    return result != null && result.isLeft() ? result.getLeft() : null;
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
      throw new CompletionException(result.getLeft());
    }
  }

  @Override
  public V get(final long timeout, final TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    countDownLatch.await(timeout, unit);
    return get();
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
