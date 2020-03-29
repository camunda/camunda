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
 * A {@link CompletableFuture} that tracks whether the future or one of its descendants has been
 * blocked on a {@link CompletableFuture#get()} or {@link CompletableFuture#join()} call.
 */
public class AtomixFuture<T> extends CompletableFuture<T> {

  private static final ThreadContext NULL_CONTEXT = new NullThreadContext();

  /**
   * Wraps the given future in a new blockable future.
   *
   * @param future the future to wrap
   * @param <T> the future value type
   * @return a new blockable future
   */
  public static <T> AtomixFuture<T> wrap(final CompletableFuture<T> future) {
    final AtomixFuture<T> newFuture = new AtomixFuture<>();
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

  /**
   * Returns a new completed Atomix future.
   *
   * @param result the future result
   * @param <T> the future result type
   * @return the completed future
   */
  public static <T> CompletableFuture<T> completedFuture(final T result) {
    final CompletableFuture<T> future = new AtomixFuture<>();
    future.complete(result);
    return future;
  }

  /**
   * Returns a new exceptionally completed Atomix future.
   *
   * @param t the future exception
   * @param <T> the future result type
   * @return the completed future
   */
  public static <T> CompletableFuture<T> exceptionalFuture(final Throwable t) {
    final CompletableFuture<T> future = new AtomixFuture<>();
    future.completeExceptionally(t);
    return future;
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
    return wrap(super.thenApply(fn));
  }

  @Override
  public <U> CompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
    return wrap(super.thenApplyAsync(fn));
  }

  @Override
  public <U> CompletableFuture<U> thenApplyAsync(
      final Function<? super T, ? extends U> fn, final Executor executor) {
    return wrap(super.thenApplyAsync(fn, executor));
  }

  @Override
  public CompletableFuture<Void> thenAccept(final Consumer<? super T> action) {
    return wrap(super.thenAccept(action));
  }

  @Override
  public CompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action) {
    return wrap(super.thenAcceptAsync(action));
  }

  @Override
  public CompletableFuture<Void> thenAcceptAsync(
      final Consumer<? super T> action, final Executor executor) {
    return wrap(super.thenAcceptAsync(action, executor));
  }

  @Override
  public CompletableFuture<Void> thenRun(final Runnable action) {
    return wrap(super.thenRun(action));
  }

  @Override
  public CompletableFuture<Void> thenRunAsync(final Runnable action) {
    return wrap(super.thenRunAsync(action));
  }

  @Override
  public CompletableFuture<Void> thenRunAsync(final Runnable action, final Executor executor) {
    return wrap(super.thenRunAsync(action, executor));
  }

  @Override
  public <U, V> CompletableFuture<V> thenCombine(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn) {
    return wrap(super.thenCombine(other, fn));
  }

  @Override
  public <U, V> CompletableFuture<V> thenCombineAsync(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn) {
    return wrap(super.thenCombineAsync(other, fn));
  }

  @Override
  public <U, V> CompletableFuture<V> thenCombineAsync(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn,
      final Executor executor) {
    return wrap(super.thenCombineAsync(other, fn, executor));
  }

  @Override
  public <U> CompletableFuture<Void> thenAcceptBoth(
      final CompletionStage<? extends U> other, final BiConsumer<? super T, ? super U> action) {
    return wrap(super.thenAcceptBoth(other, action));
  }

  @Override
  public <U> CompletableFuture<Void> thenAcceptBothAsync(
      final CompletionStage<? extends U> other, final BiConsumer<? super T, ? super U> action) {
    return wrap(super.thenAcceptBothAsync(other, action));
  }

  @Override
  public <U> CompletableFuture<Void> thenAcceptBothAsync(
      final CompletionStage<? extends U> other,
      final BiConsumer<? super T, ? super U> action,
      final Executor executor) {
    return wrap(super.thenAcceptBothAsync(other, action, executor));
  }

  @Override
  public CompletableFuture<Void> runAfterBoth(
      final CompletionStage<?> other, final Runnable action) {
    return wrap(super.runAfterBoth(other, action));
  }

  @Override
  public CompletableFuture<Void> runAfterBothAsync(
      final CompletionStage<?> other, final Runnable action) {
    return wrap(super.runAfterBothAsync(other, action));
  }

  @Override
  public CompletableFuture<Void> runAfterBothAsync(
      final CompletionStage<?> other, final Runnable action, final Executor executor) {
    return wrap(super.runAfterBothAsync(other, action, executor));
  }

  @Override
  public <U> CompletableFuture<U> applyToEither(
      final CompletionStage<? extends T> other, final Function<? super T, U> fn) {
    return wrap(super.applyToEither(other, fn));
  }

  @Override
  public <U> CompletableFuture<U> applyToEitherAsync(
      final CompletionStage<? extends T> other, final Function<? super T, U> fn) {
    return wrap(super.applyToEitherAsync(other, fn));
  }

  @Override
  public <U> CompletableFuture<U> applyToEitherAsync(
      final CompletionStage<? extends T> other,
      final Function<? super T, U> fn,
      final Executor executor) {
    return wrap(super.applyToEitherAsync(other, fn, executor));
  }

  @Override
  public CompletableFuture<Void> acceptEither(
      final CompletionStage<? extends T> other, final Consumer<? super T> action) {
    return wrap(super.acceptEither(other, action));
  }

  @Override
  public CompletableFuture<Void> acceptEitherAsync(
      final CompletionStage<? extends T> other, final Consumer<? super T> action) {
    return wrap(super.acceptEitherAsync(other, action));
  }

  @Override
  public CompletableFuture<Void> acceptEitherAsync(
      final CompletionStage<? extends T> other,
      final Consumer<? super T> action,
      final Executor executor) {
    return wrap(super.acceptEitherAsync(other, action, executor));
  }

  @Override
  public CompletableFuture<Void> runAfterEither(
      final CompletionStage<?> other, final Runnable action) {
    return wrap(super.runAfterEither(other, action));
  }

  @Override
  public CompletableFuture<Void> runAfterEitherAsync(
      final CompletionStage<?> other, final Runnable action) {
    return wrap(super.runAfterEitherAsync(other, action));
  }

  @Override
  public CompletableFuture<Void> runAfterEitherAsync(
      final CompletionStage<?> other, final Runnable action, final Executor executor) {
    return wrap(super.runAfterEitherAsync(other, action, executor));
  }

  @Override
  public <U> CompletableFuture<U> thenCompose(
      final Function<? super T, ? extends CompletionStage<U>> fn) {
    return wrap(super.thenCompose(fn));
  }

  @Override
  public <U> CompletableFuture<U> thenComposeAsync(
      final Function<? super T, ? extends CompletionStage<U>> fn) {
    return wrap(super.thenComposeAsync(fn));
  }

  @Override
  public <U> CompletableFuture<U> thenComposeAsync(
      final Function<? super T, ? extends CompletionStage<U>> fn, final Executor executor) {
    return wrap(super.thenComposeAsync(fn, executor));
  }

  @Override
  public CompletableFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
    return wrap(super.whenComplete(action));
  }

  @Override
  public CompletableFuture<T> whenCompleteAsync(
      final BiConsumer<? super T, ? super Throwable> action) {
    return wrap(super.whenCompleteAsync(action));
  }

  @Override
  public CompletableFuture<T> whenCompleteAsync(
      final BiConsumer<? super T, ? super Throwable> action, final Executor executor) {
    return wrap(super.whenCompleteAsync(action, executor));
  }

  @Override
  public <U> CompletableFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
    return wrap(super.handle(fn));
  }

  @Override
  public <U> CompletableFuture<U> handleAsync(
      final BiFunction<? super T, Throwable, ? extends U> fn) {
    return wrap(super.handleAsync(fn));
  }

  @Override
  public <U> CompletableFuture<U> handleAsync(
      final BiFunction<? super T, Throwable, ? extends U> fn, final Executor executor) {
    return wrap(super.handleAsync(fn, executor));
  }
}
