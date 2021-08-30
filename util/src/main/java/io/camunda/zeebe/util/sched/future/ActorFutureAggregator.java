/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.future;

import io.camunda.zeebe.util.sched.ConcurrencyControl;
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
 * Aggregates a number of {@code ActorFuture} objects into a single one.
 *
 * <pre>
 * var aggregated = of(future1, future2).stream().collect(new ActorFutureAggregator<>(concurrencyControl));
 * </pre>
 *
 * @param <V> type of the value of each future
 */
public final class ActorFutureAggregator<V>
    implements Collector<ActorFuture<V>, ActorFutureCollector<V>, ActorFuture<List<V>>> {

  private final ConcurrencyControl concurrencyControl;

  public ActorFutureAggregator(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = Objects.requireNonNull(concurrencyControl);
  }

  @Override
  public Supplier<ActorFutureCollector<V>> supplier() {
    return () -> new ActorFutureCollector<>(concurrencyControl);
  }

  @Override
  public BiConsumer<ActorFutureCollector<V>, ActorFuture<V>> accumulator() {
    return ActorFutureCollector::accept;
  }

  @Override
  public BinaryOperator<ActorFutureCollector<V>> combiner() {
    return ActorFutureCollector::new;
  }

  @Override
  public Function<ActorFutureCollector<V>, ActorFuture<List<V>>> finisher() {
    return ActorFutureCollector::get;
  }

  @Override
  public Set<Characteristics> characteristics() {
    return Collections.emptySet();
  }
}
