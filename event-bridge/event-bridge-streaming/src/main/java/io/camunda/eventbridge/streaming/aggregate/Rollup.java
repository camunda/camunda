/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * Consumes derived facts and maintains a pre-aggregated, grouped result. One projector's facts can
 * fan out to several rollups, each grouping differently (e.g. by region and by definition) — that
 * is how multiple aggregations are built over the same fact.
 *
 * <p>Driven by two clocks (see {@code StreamProcessor}): {@link #flush()} on a wall-clock tick
 * keeps latency bounded and drains idle partials; {@link #advanceStreamTime(long)} on event-time
 * progress drives window finalization/retention.
 *
 * @param <F> the fact type consumed
 */
public interface Rollup<F> extends AutoCloseable {

  /** Folds one fact into its group cell (typically into an in-memory pre-aggregation buffer). */
  void accept(F fact);

  /**
   * Wall-clock tick: converge the serving view for the cells changed since the last flush. This is
   * the freshness clock — it keeps the sink up to date but does <em>not</em> make state durable
   * (see {@link #checkpoint()}).
   */
  void flush();

  /**
   * Commit-interval tick: make the in-memory working state durable — write the cells changed (and
   * evict the ones finalized) since the last checkpoint, together with the consumed offsets, in one
   * transaction. Decoupled from {@link #flush()} so many batches coalesce into one durable write (a
   * write-back record-cache model). Default: no-op for rollups that keep no durable state.
   */
  default void checkpoint() {}

  /** Event-time progress: finalize windows that have closed, prune state. Default: no-op. */
  default void advanceStreamTime(final long streamTimeMs) {}

  /**
   * Wall-clock punctuation tick: finalize/emit on real time even when no facts are arriving (an
   * idle grouping), for callers that want idle windows to close by wall clock rather than event
   * time. Default: no-op — the standard model finalizes on {@link #advanceStreamTime}.
   */
  default void punctuateWallClock(final long wallClockMs) {}

  /**
   * Whether this rollup holds buffered state that should be checkpointed before the regular
   * interval — e.g. a bounded cache is full. Default: no-op ({@code false}).
   */
  default boolean needsCheckpoint() {
    return false;
  }

  /** Final flush + release. */
  @Override
  void close();
}
