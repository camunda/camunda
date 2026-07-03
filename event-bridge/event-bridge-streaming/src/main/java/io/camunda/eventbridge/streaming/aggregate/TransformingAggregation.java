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
 * A {@link Aggregation} that expands each fact before it reaches a downstream rollup: it runs the
 * fact through a {@link Projector} (the enricher) and forwards the result on. This is how one
 * already derived fact feeds another dataset with additional data — the enricher typically adds
 * fields from a state-store lookup (a stream-table join), so the downstream dataset is built
 * without re-deriving or re-fetching the base fact.
 *
 * <p>Place it alongside the base rollup in a {@link ProjectionStage}: the projector emits the base
 * fact once, and it fans out to both the base rollup and this one, which enriches and forwards to
 * {@code downstream}.
 *
 * @param <A> the incoming fact type
 * @param <B> the enriched fact type the downstream rollup consumes
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
  public void accept(final A fact) {
    enricher.apply(fact, toDownstream);
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
