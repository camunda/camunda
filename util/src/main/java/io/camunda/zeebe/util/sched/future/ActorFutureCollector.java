/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.future;

import static java.util.Arrays.stream;

import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ActorFutureCollector<V>
    implements Consumer<ActorFuture<V>>, Supplier<ActorFuture<List<V>>> {

  private final ConcurrencyControl concurrencyControl;
  private final List<ActorFuture<V>> futures = new ArrayList<>();

  ActorFutureCollector(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = concurrencyControl;
  }

  ActorFutureCollector(final ActorFutureCollector<V> a, final ActorFutureCollector<V> b) {
    concurrencyControl = a.concurrencyControl;
    a.futures.forEach(this);
    b.futures.forEach(this);
  }

  @Override
  public void accept(final ActorFuture<V> actorFuture) {
    futures.add(Objects.requireNonNull(actorFuture));
  }

  @Override
  public ActorFuture<List<V>> get() {
    return new CompletionWaiter<>(concurrencyControl, futures).get();
  }

  private static final class CompletionWaiter<V> implements Supplier<ActorFuture<List<V>>> {
    private final ConcurrencyControl concurrencyControl;
    private final List<ActorFuture<V>> pendingFutures;
    private final Either<Throwable, V>[] results;

    private ActorFuture<List<V>> aggregated;

    private CompletionWaiter(
        final ConcurrencyControl concurrencyControl, final List<ActorFuture<V>> pendingFutures) {
      this.concurrencyControl = concurrencyControl;
      this.pendingFutures = new ArrayList<>(pendingFutures);
      results = new Either[(pendingFutures.size())];
    }

    @Override
    public ActorFuture<List<V>> get() {
      if (pendingFutures.isEmpty()) {
        return CompletableActorFuture.completed(Collections.emptyList());
      }

      aggregated = concurrencyControl.createFuture();

      for (int index = 0; index < pendingFutures.size(); index++) {
        final var pendingFuture = pendingFutures.get(index);

        final var currentIndex = index;
        concurrencyControl.runOnCompletion(
            pendingFuture,
            (result, error) -> handleCompletion(pendingFuture, currentIndex, result, error));
      }

      return aggregated;
    }

    private void handleCompletion(
        final ActorFuture<V> pendingFuture,
        final int currentIndex,
        final V result,
        final Throwable error) {
      pendingFutures.remove(pendingFuture);

      results[currentIndex] = error == null ? Either.right(result) : Either.left(error);

      if (pendingFutures.isEmpty()) {
        completeAggregatedFuture();
      }
    }

    private void completeAggregatedFuture() {
      final var aggregatedResult = stream(results).collect(Either.collector());

      if (aggregatedResult.isRight()) {
        aggregated.complete(aggregatedResult.get());
      } else {
        final var exception =
            new Exception("Errors occurred, see suppressed exceptions for details");

        aggregatedResult.getLeft().forEach(exception::addSuppressed);
        aggregated.completeExceptionally(exception);
      }
    }
  }
}
