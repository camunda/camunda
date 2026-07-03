/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * A mergeable aggregate: how facts fold into an accumulator and how partial accumulators combine.
 * The accumulator type {@code ACC} is the intermediate state (e.g. {@code count, total, min, max});
 * {@code OUT} is the read-facing result (e.g. with {@code avg} derived from {@code total/count}).
 *
 * <p>{@link #merge} must be <strong>commutative and associative</strong>. That is what makes
 * pre-aggregation correct: folding many facts into a local accumulator and merging the one partial
 * into the serving store yields the same result as merging each fact individually. Metrics with no
 * exact scalar {@code merge} — distinct-count, quantiles, top-k — still fit this contract when the
 * accumulator is a <em>sketch</em> (HLL, KLL, frequent items): the estimate is approximate, but the
 * sketch <em>merge</em> is exact, commutative and associative, so they pre-aggregate with bounded
 * state exactly as {@code count} and {@code sum} do (as the sketch-based distinct-count, quantile
 * and top-k implementations do).
 *
 * <p>The function itself is stateless; all state lives in the accumulator instances it produces.
 *
 * @param <IN> the input fact type
 * @param <ACC> the accumulator type
 * @param <OUT> the result type
 */
public interface AggregateFunction<IN, ACC, OUT> {

  /** A fresh, empty accumulator. */
  ACC createAccumulator();

  /** Folds one fact into {@code accumulator}, returning the updated accumulator. */
  ACC add(IN value, ACC accumulator);

  /** Combines two partial accumulators (commutative, associative). */
  ACC merge(ACC a, ACC b);

  /** Derives the read-facing result from an accumulator. */
  OUT getResult(ACC accumulator);
}
