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
 * The common stage: a {@link Projector} folds each record and its derived facts fan out to one or
 * more {@link Aggregation}s. Registering several rollups is how one fact feeds several
 * aggregations; using several projection stages (or other stages) is how a record yields different
 * facts.
 *
 * @param <R> the source record type
 * @param <F> the derived fact type
 */
public final class ProjectionStage<R, F> implements Stage<R> {

  private final Projector<R, F> projector;
  private final List<Aggregation<F>> rollups;
  private final Collector<F> collector;

  public ProjectionStage(final Projector<R, F> projector, final List<Aggregation<F>> rollups) {
    this.projector = projector;
    this.rollups = List.copyOf(rollups);
    this.collector = fact -> this.rollups.forEach(rollup -> rollup.accept(fact));
  }

  /** A stage from a projector and its rollups. */
  @SafeVarargs
  public static <R, F> ProjectionStage<R, F> of(
      final Projector<R, F> projector, final Aggregation<F>... rollups) {
    return new ProjectionStage<>(projector, List.of(rollups));
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
    rollups.forEach(Aggregation::flush);
  }

  @Override
  public void checkpoint() {
    // Persist the projector's fold state first, then the rollups — all in the runtime's one
    // checkpoint transaction, so this stage's entire durable state advances as a single cut.
    projector.checkpoint();
    rollups.forEach(Aggregation::checkpoint);
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    rollups.forEach(rollup -> rollup.advanceStreamTime(streamTimeMs));
  }

  @Override
  public void punctuateWallClock(final long wallClockMs) {
    rollups.forEach(rollup -> rollup.punctuateWallClock(wallClockMs));
  }

  @Override
  public boolean needsCheckpoint() {
    if (projector.needsCheckpoint()) {
      return true;
    }
    for (final Aggregation<F> rollup : rollups) {
      if (rollup.needsCheckpoint()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() {
    projector.close();
    rollups.forEach(Aggregation::close);
  }
}
