/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import io.camunda.eventbridge.streaming.aggregate.Aggregation;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.eventbridge.streaming.fold.Projector;
import java.util.List;

/**
 * The common stage: a {@link Projector} folds each record and its derived values fan out to one or
 * more {@link Aggregation}s. Registering several aggregations is how one value feeds several
 * aggregations; using several projection stages (or other stages) is how a record yields different
 * values.
 *
 * @param <R> the source record type
 * @param <F> the derived value type
 */
public final class ProjectionStage<R, F> implements Stage<R> {

  private final Projector<R, F> projector;
  private final List<Aggregation<F>> aggregations;
  private final Collector<F> collector;

  public ProjectionStage(final Projector<R, F> projector, final List<Aggregation<F>> aggregations) {
    this.projector = projector;
    this.aggregations = List.copyOf(aggregations);
    this.collector = value -> this.aggregations.forEach(aggregation -> aggregation.accept(value));
  }

  /** A stage from a projector and its aggregations. */
  @SafeVarargs
  public static <R, F> ProjectionStage<R, F> of(
      final Projector<R, F> projector, final Aggregation<F>... aggregations) {
    return new ProjectionStage<>(projector, List.of(aggregations));
  }

  @Override
  public void init() {
    projector.init();
  }

  @Override
  public void process(final R record) {
    projector.apply(record, collector);
  }

  @Override
  public void flush() {
    aggregations.forEach(Aggregation::flush);
  }

  @Override
  public void checkpoint() {
    // Persist the projector's fold state first, then the aggregations — all in the runtime's one
    // checkpoint transaction, so this stage's entire durable state advances as a single cut.
    projector.checkpoint();
    aggregations.forEach(Aggregation::checkpoint);
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    aggregations.forEach(aggregation -> aggregation.advanceStreamTime(streamTimeMs));
  }

  @Override
  public void punctuateWallClock(final long wallClockMs) {
    aggregations.forEach(aggregation -> aggregation.punctuateWallClock(wallClockMs));
  }

  @Override
  public boolean needsCheckpoint() {
    if (projector.needsCheckpoint()) {
      return true;
    }
    for (final Aggregation<F> aggregation : aggregations) {
      if (aggregation.needsCheckpoint()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() {
    projector.close();
    aggregations.forEach(Aggregation::close);
  }
}
