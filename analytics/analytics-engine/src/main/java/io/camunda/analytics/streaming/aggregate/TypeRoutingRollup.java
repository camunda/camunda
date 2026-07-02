/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * Routes a fan-out of mixed facts to a typed downstream rollup: accepts the common supertype,
 * passes only facts of the given subtype on, and ignores the rest. This is how one fold that emits
 * several fact types feeds several aggregations — each rollup is wrapped to take its own type, and
 * the projector's facts fan out to all of them. Lifecycle calls (flush, stream-time, close) pass
 * through.
 *
 * @param <F> the fan-out (supertype) the projector emits
 * @param <S> the subtype this rollup consumes
 */
public final class TypeRoutingRollup<F, S extends F> implements Rollup<F> {

  private final Class<S> type;
  private final Rollup<S> downstream;

  public TypeRoutingRollup(final Class<S> type, final Rollup<S> downstream) {
    this.type = type;
    this.downstream = downstream;
  }

  @Override
  public void accept(final F fact) {
    if (type.isInstance(fact)) {
      downstream.accept(type.cast(fact));
    }
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
