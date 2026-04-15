/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Use to repeatedly call an async task until a condition is met.
// NB avoiding using closures for this as with all the callbacks it can
// easily lead to memory leaks if not used carefully. By using an explicit class
// we can ensure that the state is properly cleaned up after completion.
final class AsyncRepeatUntil<T> {
  private final CompletableFuture<Void> finalFuture = new CompletableFuture<>();
  private final Supplier<CompletableFuture<T>> asyncTask;
  private final Predicate<T> until;

  private AsyncRepeatUntil(
      final Supplier<CompletableFuture<T>> asyncTask, final Predicate<T> until) {
    this.asyncTask = asyncTask;
    this.until = until;
  }

  private void next() {
    try {
      asyncTask.get().thenAccept(this::completeOrNext).exceptionally(this::completeExceptionally);
    } catch (final Throwable err) {
      completeExceptionally(err);
    }
  }

  private void completeOrNext(final T result) {
    try {
      if (until.test(result)) {
        finalFuture.complete(null);
      } else {
        next();
      }
    } catch (final Throwable err) {
      completeExceptionally(err);
    }
  }

  private Void completeExceptionally(final Throwable err) {
    finalFuture.completeExceptionally(err);
    return null;
  }

  static <T> CompletableFuture<Void> repeatUntil(
      final Supplier<CompletableFuture<T>> asyncTask, final Predicate<T> until) {
    final var repeat = new AsyncRepeatUntil<>(asyncTask, until);

    repeat.next();

    return repeat.finalFuture;
  }
}
