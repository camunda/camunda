/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import java.util.Arrays;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * An immutable, ordered collection of two or more callbacks of a {@link CompletableActorFuture}.
 *
 * <p>A future holds no collection at all for no callback, and the callback itself for a single one,
 * as most futures have at most one. Only from the second callback on it holds one of these. Values
 * are never modified: adding a callback returns a new value, which the future publishes with a CAS
 * on the one field that references it. That CAS is what makes registering and notifying safe
 * against each other, so a value must never be changed after it was published.
 *
 * <p>Few callbacks are kept in an array, which is copied on every add and runs in registration
 * order. A future that gets many callbacks switches to a chain of nodes where adding is cheap
 * regardless of the count.
 */
abstract sealed class Callbacks<V extends @Nullable Object> {

  /** More callbacks than this are kept in a chain of nodes instead of an array. */
  private static final int MAX_IN_ARRAY = 6;

  /** Returns the callbacks of a future that registers a second callback. */
  @SuppressWarnings("unchecked")
  static <V extends @Nullable Object> Callbacks<V> of(
      final BiConsumer<V, @Nullable Throwable> first,
      final BiConsumer<V, @Nullable Throwable> second) {
    return new InArray<>(new BiConsumer[] {first, second});
  }

  /** Returns these callbacks followed by the given one, this value is not modified. */
  abstract Callbacks<V> add(BiConsumer<V, @Nullable Throwable> callback);

  /** Runs the callbacks in the order they were added. */
  abstract void run(@Nullable V value, @Nullable Throwable error);

  abstract int size();

  private static final class InArray<V extends @Nullable Object> extends Callbacks<V> {
    private final BiConsumer<V, @Nullable Throwable>[] callbacks;

    private InArray(final BiConsumer<V, @Nullable Throwable>[] callbacks) {
      this.callbacks = callbacks;
    }

    @Override
    Callbacks<V> add(final BiConsumer<V, @Nullable Throwable> callback) {
      if (callbacks.length < MAX_IN_ARRAY) {
        final var extended = Arrays.copyOf(callbacks, callbacks.length + 1);
        extended[callbacks.length] = callback;
        return new InArray<>(extended);
      }

      Chained<V> chain = null;
      for (final var existing : callbacks) {
        chain = new Chained<>(existing, chain);
      }
      return new Chained<>(callback, chain);
    }

    @Override
    void run(final @Nullable V value, final @Nullable Throwable error) {
      for (final var callback : callbacks) {
        callback.accept(value, error);
      }
    }

    @Override
    int size() {
      return callbacks.length;
    }
  }

  private static final class Chained<V extends @Nullable Object> extends Callbacks<V> {
    private final BiConsumer<V, @Nullable Throwable> callback;
    private final @Nullable Chained<V> previous;
    private final int size;

    private Chained(
        final BiConsumer<V, @Nullable Throwable> callback, final @Nullable Chained<V> previous) {
      this.callback = callback;
      this.previous = previous;
      size = previous == null ? 1 : previous.size + 1;
    }

    @Override
    Callbacks<V> add(final BiConsumer<V, @Nullable Throwable> callback) {
      return new Chained<>(callback, this);
    }

    @Override
    @SuppressWarnings("unchecked")
    void run(final @Nullable V value, final @Nullable Throwable error) {
      // the chain is in reverse order of registration, and must not be modified to reverse it
      final BiConsumer<V, @Nullable Throwable>[] inOrder = new BiConsumer[size];
      int index = size;
      for (var node = this; node != null; node = node.previous) {
        inOrder[--index] = node.callback;
      }

      for (final var callback : inOrder) {
        callback.accept(value, error);
      }
    }

    @Override
    int size() {
      return size;
    }
  }
}
