/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.analytics.dimension.FactRow;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import java.util.Objects;
import java.util.function.Function;

/**
 * A reusable kind of meter (count, sum, execution-time, percentile, distinct, top-k, histogram, …)
 * identified by a stable {@code id}. It knows how to build, for a given {@link Meter} declaration,
 * the mergeable {@link AggregateFunction} bound to that meter's measure/params and the matching
 * accumulator {@link RecordValue} codec.
 *
 * <p>Meter types are organised by <em>merge semantics</em> (additive / mergeable-sketch), not by
 * observability-instrument names: a level-gauge is a {@code sum} of signed deltas, so there is no
 * separate gauge kind. Built as data via two factories rather than a class per kind, so the {@link
 * MeterCatalog} registers instances directly.
 *
 * @param <ACC> the accumulator type
 * @param <OUT> the read-facing result type
 */
public final class MeterType<ACC, OUT> {

  private final String id;
  private final Function<Meter, AggregateFunction<FactRow, ACC, OUT>> aggregateFactory;
  private final Function<Meter, RecordValue<ACC>> codecFactory;

  public MeterType(
      final String id,
      final Function<Meter, AggregateFunction<FactRow, ACC, OUT>> aggregateFactory,
      final Function<Meter, RecordValue<ACC>> codecFactory) {
    this.id = Objects.requireNonNull(id, "id");
    this.aggregateFactory = Objects.requireNonNull(aggregateFactory, "aggregateFactory");
    this.codecFactory = Objects.requireNonNull(codecFactory, "codecFactory");
  }

  public String id() {
    return id;
  }

  /** Resolves a {@link Meter} of this type into the aggregate + codec the pipeline consumes. */
  public BoundMeter<ACC, OUT> bind(final Meter meter) {
    return new BoundMeter<>(meter, aggregateFactory.apply(meter), codecFactory.apply(meter));
  }
}
