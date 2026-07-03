/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.eventbridge.streaming.fold.Projector;

/**
 * A {@link Aggregation} that expands each value before it reaches a downstream aggregation: it runs
 * the value through a {@link Projector} (the enricher) and forwards the result on. This is how one
 * already derived value feeds another dataset with additional data — the enricher typically adds
 * fields from a state-store lookup (a stream-table join), so the downstream dataset is built
 * without re-deriving or re-fetching the base value.
 *
 * <p>Place it alongside the base aggregation in a {@link ProjectionStage}: the projector emits the
 * base value once, and it fans out to both the base aggregation and this one, which enriches and
 * forwards to {@code downstream}.
 *
 * @param <A> the incoming value type
 * @param <B> the enriched value type the downstream aggregation consumes
 */
public final class TransformingAggregation<A, B> implements Aggregation<A> {

  private final Projector<A, B> enricher;
  private final Aggregation<B> downstream;
  private final Collector<B> toDownstream;

  public TransformingAggregation(final Projector<A, B> enricher, final Aggregation<B> downstream) {
    this.enricher = enricher;
    this.downstream = downstream;
    this.toDownstream = downstream::accept;
  }

  @Override
  public void accept(final A value) {
    enricher.apply(value, toDownstream);
  }

  @Override
  public void flush() {
    downstream.flush();
  }

  @Override
  public void checkpoint() {
    downstream.checkpoint();
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    downstream.advanceStreamTime(streamTimeMs);
  }

  @Override
  public void close() {
    downstream.close();
  }
}
