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
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link Meter} resolved against its {@link MeterType}: the mergeable {@link AggregateFunction}
 * that folds facts (read through the {@link FactRow} seam) into its accumulator, paired with the
 * {@link RecordValue} codec that serializes that accumulator for the durable rollup and the
 * shuffle. This is what the pipeline stages consume — one bound meter per declared metric.
 *
 * <p>{@code pushdown} carries the meter type's {@link PushdownSpec} (null for a blob/app-merged
 * sketch), so the store can decide between native numeric columns and a blob without re-resolving
 * the type.
 *
 * <p>The codec is exposed as a {@code Supplier} rather than a single instance: a {@link
 * RecordValue} is a mutable flyweight whose write ({@code wrapValue}/serialize) and read ({@code
 * wrap}/{@code value}) halves must not interleave. One bound meter is shared across threads — the
 * pipeline writes accumulators while serving queries read them — so handing out a fresh flyweight
 * per {@link #accumulatorCodec()} call gives each caller its own, rather than corrupting a single
 * shared one.
 *
 * @param <ACC> the accumulator type
 * @param <OUT> the read-facing result type
 */
public record BoundMeter<ACC, OUT>(
    Meter meter,
    AggregateFunction<FactRow, ACC, OUT> aggregate,
    Supplier<RecordValue<ACC>> codecFactory,
    PushdownSpec<ACC, OUT> pushdownSpec) {

  /** A fresh accumulator codec flyweight — never share one across threads (it is mutable). */
  public RecordValue<ACC> accumulatorCodec() {
    return codecFactory.get();
  }

  /** This meter's pushdown capability, or empty when it is a blob (sketch / summary). */
  public Optional<PushdownSpec<ACC, OUT>> pushdown() {
    return Optional.ofNullable(pushdownSpec);
  }
}
