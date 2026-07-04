/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

/**
 * The mutable-ish accumulator for the windowed aggregate.
 *
 * <p>{@code count + avg(durationMs)} is not directly expressible with a single DSL reducer, so we
 * carry both the {@code count} and the running {@code sumDurationMs} and compute the mean at the
 * very end (in {@code mapValues}). Keeping the sum (not the average) in the accumulator is what makes
 * the aggregate <b>associative</b> and therefore correct across the shuffle: Kafka Streams may fold
 * this accumulator over records arriving in any order, and summing is order-independent whereas
 * averaging incrementally is not.
 *
 * @param count number of facts folded so far
 * @param sumDurationMs sum of their durations
 */
public record DurationAggregate(long count, long sumDurationMs) {

  public static DurationAggregate empty() {
    return new DurationAggregate(0L, 0L);
  }

  public DurationAggregate add(final CompletionFact fact) {
    return new DurationAggregate(count + 1, sumDurationMs + fact.durationMs());
  }

  public double average() {
    return count == 0 ? 0.0 : (double) sumDurationMs / count;
  }
}
