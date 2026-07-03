/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * Routes a fan-out of mixed values to a typed downstream aggregation: accepts the common supertype,
 * passes only values of the given subtype on, and ignores the rest. This is how one fold that emits
 * several value types feeds several aggregations — each aggregation is wrapped to take its own
 * type, and the projector's values fan out to all of them. Lifecycle calls (flush, stream-time,
 * close) pass through.
 *
 * @param <F> the fan-out (supertype) the projector emits
 * @param <S> the subtype this aggregation consumes
 */
public final class TypeRoutingAggregation<F, S extends F> implements Aggregation<F> {

  private final Class<S> type;
  private final Aggregation<S> downstream;

  public TypeRoutingAggregation(final Class<S> type, final Aggregation<S> downstream) {
    this.type = type;
    this.downstream = downstream;
  }

  @Override
  public void accept(final F value) {
    if (type.isInstance(value)) {
      downstream.accept(type.cast(value));
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
