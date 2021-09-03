/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.future;

import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;

import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Aggregates a number of {@code ActorFuture} objects into a single one. The aggregated future is
 * completed when all individual futures have completed. If all futures complete with a value, the
 * aggregated future returns the ordered list of said values. If one or more complete exceptionally,
 * the aggregated future will complete exceptionally. The exception will have the exceptions of the
 * individual futures aas suppressed exceptions. If exceptions occur, this does not interrupt the
 * individual futures.
 *
 * <pre>
 * var aggregated = of(future1, future2).stream().collect(new ActorFutureCollector<>(concurrencyControl));
 * </pre>
 *
 * @param <V> type of the value of each future
 */
public final class ActorFutureCollector<V>
    implements Collector<ActorFuture<V>, List<ActorFuture<V>>, ActorFuture<List<V>>> {

  private final ConcurrencyControl concurrencyControl;

  public ActorFutureCollector(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = Objects.requireNonNull(concurrencyControl);
  }

  @Override
  public Supplier<List<ActorFuture<V>>> supplier() {
    return ArrayList::new;
  }

  @Override
  public BiConsumer<List<ActorFuture<V>>, ActorFuture<V>> accumulator() {
    return List::add;
  }

  @Override
  public BinaryOperator<List<ActorFuture<V>>> combiner() {
    return (listA, listB) -> {
      listA.addAll(listB);
      return listA;
    };
  }

  @Override
  public Function<List<ActorFuture<V>>, ActorFuture<List<V>>> finisher() {
    return futures -> new CompletionWaiter<>(concurrencyControl, futures).get();
  }

  @Override
  public Set<Characteristics> characteristics() {
    return emptySet();
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
      aggregated = concurrencyControl.createFuture();

      if (pendingFutures.isEmpty()) {
        aggregated.complete(Collections.emptyList());
      } else {
        for (int index = 0; index < pendingFutures.size(); index++) {
          final var pendingFuture = pendingFutures.get(index);

          final var currentIndex = index;
          concurrencyControl.runOnCompletion(
              pendingFuture,
              (result, error) -> handleCompletion(pendingFuture, currentIndex, result, error));
        }
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
