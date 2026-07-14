/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import java.util.function.ToLongFunction;
import org.agrona.collections.MutableLong;

/**
 * A mergeable running sum of a signed long extracted from each value. With a {@code +1 / -1}
 * extractor and a single (all-time) window it is a gauge — e.g. in-flight instances = sum of {@code
 * +1} on start and {@code -1} on completion. {@code merge} is addition, so it pre-aggregates and
 * combines across partitions.
 *
 * <p>The accumulator is a <em>mutable</em> {@link MutableLong} rather than a boxed {@code Long}:
 * {@link #add} and {@link #mergeInto} fold in place and return the accumulator they were given, so
 * the per-record hot path allocates nothing (sum values routinely exceed the small-box cache, so a
 * boxed accumulator would allocate on every fold). {@link #merge} stays pure per the {@link
 * AggregateFunction} contract; the checkpoint wire format is unchanged (one long, see {@link
 * MutableLongRecordValue}).
 *
 * @param <F> the value type
 */
public final class SumAggregateFunction<F> implements AggregateFunction<F, MutableLong, Long> {

  private final ToLongFunction<F> value;

  public SumAggregateFunction(final ToLongFunction<F> value) {
    this.value = value;
  }

  @Override
  public MutableLong createAccumulator() {
    return new MutableLong();
  }

  @Override
  public MutableLong add(final F item, final MutableLong acc) {
    acc.value += value.applyAsLong(item);
    return acc;
  }

  @Override
  public MutableLong merge(final MutableLong a, final MutableLong b) {
    return new MutableLong(a.value + b.value);
  }

  @Override
  public MutableLong mergeInto(final MutableLong target, final MutableLong delta) {
    target.value += delta.value;
    return target;
  }

  @Override
  public Long getResult(final MutableLong acc) {
    return acc.value;
  }
}
