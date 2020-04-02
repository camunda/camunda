/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.concurrent;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link CompletableFuture} that ensures callbacks are called in FIFO order.
 *
 * <p>The default {@link CompletableFuture} does not guarantee the ordering of callbacks, and indeed
 * appears to execute them in LIFO order.
 */
public class OrderedFuture<T> extends CompletableFuture<T> {

  private static final ThreadContext NULL_CONTEXT = new NullThreadContext();
  private final Queue<CompletableFuture<T>> orderedFutures = new LinkedList<>();
  private volatile boolean complete;
  private volatile T result;
  private volatile Throwable error;

  public OrderedFuture() {
    super.whenComplete(this::complete);
  }

  /**
   * Wraps the given future in a new blockable future.
   *
   * @param future the future to wrap
   * @param <T> the future value type
   * @return a new blockable future
   */
  public static <T> CompletableFuture<T> wrap(final CompletableFuture<T> future) {
    final CompletableFuture<T> newFuture = new OrderedFuture<>();
    future.whenComplete(
        (result, error) -> {
          if (error == null) {
            newFuture.complete(result);
          } else {
            newFuture.completeExceptionally(error);
          }
        });
    return newFuture;
  }

  private ThreadContext getThreadContext() {
    final ThreadContext context = ThreadContext.currentContext();
    return context != null ? context : NULL_CONTEXT;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    final ThreadContext context = getThreadContext();
    context.block();
    try {
      return super.get();
    } finally {
      context.unblock();
    }
  }

  @Override
  public T get(final long timeout, final TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    final ThreadContext context = getThreadContext();
    context.block();
    try {
      return super.get(timeout, unit);
    } finally {
      context.unblock();
    }
  }

  @Override
  public synchronized T join() {
    final ThreadContext context = getThreadContext();
    context.block();
    try {
      return super.join();
    } finally {
      context.unblock();
    }
  }

  @Override
  public <U> CompletableFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
    return wrap(orderedFuture().thenApply(fn));
  }

  @Override
  public <U> CompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
    return wrap(orderedFuture().thenApplyAsync(fn));
  }

  @Override
  public <U> CompletableFuture<U> thenApplyAsync(
      final Function<? super T, ? extends U> fn, final Executor executor) {
    return wrap(orderedFuture().thenApplyAsync(fn, executor));
  }

  @Override
  public CompletableFuture<Void> thenAccept(final Consumer<? super T> action) {
    return wrap(orderedFuture().thenAccept(action));
  }

  @Override
  public CompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action) {
    return wrap(orderedFuture().thenAcceptAsync(action));
  }

  @Override
  public CompletableFuture<Void> thenAcceptAsync(
      final Consumer<? super T> action, final Executor executor) {
    return wrap(orderedFuture().thenAcceptAsync(action, executor));
  }

  @Override
  public CompletableFuture<Void> thenRun(final Runnable action) {
    return wrap(orderedFuture().thenRun(action));
  }

  @Override
  public CompletableFuture<Void> thenRunAsync(final Runnable action) {
    return wrap(orderedFuture().thenRunAsync(action));
  }

  @Override
  public CompletableFuture<Void> thenRunAsync(final Runnable action, final Executor executor) {
    return wrap(orderedFuture().thenRunAsync(action, executor));
  }

  @Override
  public <U, V> CompletableFuture<V> thenCombine(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn) {
    return wrap(orderedFuture().thenCombine(other, fn));
  }

  @Override
  public <U, V> CompletableFuture<V> thenCombineAsync(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn) {
    return wrap(orderedFuture().thenCombineAsync(other, fn));
  }

  @Override
  public <U, V> CompletableFuture<V> thenCombineAsync(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn,
      final Executor executor) {
    return wrap(orderedFuture().thenCombineAsync(other, fn, executor));
  }

  @Override
  public <U> CompletableFuture<Void> thenAcceptBoth(
      final CompletionStage<? extends U> other, final BiConsumer<? super T, ? super U> action) {
    return wrap(orderedFuture().thenAcceptBoth(other, action));
  }

  @Override
  public <U> CompletableFuture<Void> thenAcceptBothAsync(
      final CompletionStage<? extends U> other, final BiConsumer<? super T, ? super U> action) {
    return wrap(orderedFuture().thenAcceptBothAsync(other, action));
  }

  @Override
  public <U> CompletableFuture<Void> thenAcceptBothAsync(
      final CompletionStage<? extends U> other,
      final BiConsumer<? super T, ? super U> action,
      final Executor executor) {
    return wrap(orderedFuture().thenAcceptBothAsync(other, action, executor));
  }

  @Override
  public CompletableFuture<Void> runAfterBoth(
      final CompletionStage<?> other, final Runnable action) {
    return wrap(orderedFuture().runAfterBoth(other, action));
  }

  @Override
  public CompletableFuture<Void> runAfterBothAsync(
      final CompletionStage<?> other, final Runnable action) {
    return wrap(orderedFuture().runAfterBothAsync(other, action));
  }

  @Override
  public CompletableFuture<Void> runAfterBothAsync(
      final CompletionStage<?> other, final Runnable action, final Executor executor) {
    return wrap(orderedFuture().runAfterBothAsync(other, action, executor));
  }

  @Override
  public <U> CompletableFuture<U> applyToEither(
      final CompletionStage<? extends T> other, final Function<? super T, U> fn) {
    return wrap(orderedFuture().applyToEither(other, fn));
  }

  @Override
  public <U> CompletableFuture<U> applyToEitherAsync(
      final CompletionStage<? extends T> other, final Function<? super T, U> fn) {
    return wrap(orderedFuture().applyToEitherAsync(other, fn));
  }

  @Override
  public <U> CompletableFuture<U> applyToEitherAsync(
      final CompletionStage<? extends T> other,
      final Function<? super T, U> fn,
      final Executor executor) {
    return wrap(orderedFuture().applyToEitherAsync(other, fn, executor));
  }

  @Override
  public CompletableFuture<Void> acceptEither(
      final CompletionStage<? extends T> other, final Consumer<? super T> action) {
    return wrap(orderedFuture().acceptEither(other, action));
  }

  @Override
  public CompletableFuture<Void> acceptEitherAsync(
      final CompletionStage<? extends T> other, final Consumer<? super T> action) {
    return wrap(orderedFuture().acceptEitherAsync(other, action));
  }

  @Override
  public CompletableFuture<Void> acceptEitherAsync(
      final CompletionStage<? extends T> other,
      final Consumer<? super T> action,
      final Executor executor) {
    return wrap(orderedFuture().acceptEitherAsync(other, action, executor));
  }

  @Override
  public CompletableFuture<Void> runAfterEither(
      final CompletionStage<?> other, final Runnable action) {
    return wrap(orderedFuture().runAfterEither(other, action));
  }

  @Override
  public CompletableFuture<Void> runAfterEitherAsync(
      final CompletionStage<?> other, final Runnable action) {
    return wrap(orderedFuture().runAfterEitherAsync(other, action));
  }

  @Override
  public CompletableFuture<Void> runAfterEitherAsync(
      final CompletionStage<?> other, final Runnable action, final Executor executor) {
    return wrap(orderedFuture().runAfterEitherAsync(other, action, executor));
  }

  @Override
  public <U> CompletableFuture<U> thenCompose(
      final Function<? super T, ? extends CompletionStage<U>> fn) {
    return wrap(orderedFuture().thenCompose(fn));
  }

  @Override
  public <U> CompletableFuture<U> thenComposeAsync(
      final Function<? super T, ? extends CompletionStage<U>> fn) {
    return wrap(orderedFuture().thenComposeAsync(fn));
  }

  @Override
  public <U> CompletableFuture<U> thenComposeAsync(
      final Function<? super T, ? extends CompletionStage<U>> fn, final Executor executor) {
    return wrap(orderedFuture().thenComposeAsync(fn, executor));
  }

  @Override
  public CompletableFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
    return wrap(orderedFuture().whenComplete(action));
  }

  @Override
  public CompletableFuture<T> whenCompleteAsync(
      final BiConsumer<? super T, ? super Throwable> action) {
    return wrap(orderedFuture().whenCompleteAsync(action));
  }

  @Override
  public CompletableFuture<T> whenCompleteAsync(
      final BiConsumer<? super T, ? super Throwable> action, final Executor executor) {
    return wrap(orderedFuture().whenCompleteAsync(action, executor));
  }

  @Override
  public <U> CompletableFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
    return wrap(orderedFuture().handle(fn));
  }

  @Override
  public <U> CompletableFuture<U> handleAsync(
      final BiFunction<? super T, Throwable, ? extends U> fn) {
    return wrap(orderedFuture().handleAsync(fn));
  }

  @Override
  public <U> CompletableFuture<U> handleAsync(
      final BiFunction<? super T, Throwable, ? extends U> fn, final Executor executor) {
    return wrap(orderedFuture().handleAsync(fn, executor));
  }

  @Override
  public CompletableFuture<T> toCompletableFuture() {
    return this;
  }

  @Override
  public CompletableFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
    return wrap(orderedFuture().exceptionally(fn));
  }

  /** Adds a new ordered future. */
  private CompletableFuture<T> orderedFuture() {
    if (!complete) {
      synchronized (orderedFutures) {
        if (!complete) {
          final CompletableFuture<T> future = new CompletableFuture<>();
          orderedFutures.add(future);
          return future;
        }
      }
    }

    // Completed
    if (error == null) {
      return CompletableFuture.completedFuture(result);
    } else {
      return Futures.exceptionalFuture(error);
    }
  }

  /** Completes futures in FIFO order. */
  private void complete(final T result, final Throwable error) {
    synchronized (orderedFutures) {
      this.result = result;
      this.error = error;
      this.complete = true;
      if (error == null) {
        for (final CompletableFuture<T> future : orderedFutures) {
          future.complete(result);
        }
      } else {
        for (final CompletableFuture<T> future : orderedFutures) {
          future.completeExceptionally(error);
        }
      }
      orderedFutures.clear();
    }
  }
}
