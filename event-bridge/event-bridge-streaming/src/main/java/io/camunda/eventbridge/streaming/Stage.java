/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * A unit of work the {@link StreamProcessor} drives over the source: it processes each record and
 * shares the runtime lifecycle (init, the two punctuation clocks, close). The runtime knows nothing
 * about what a stage <em>does</em> — fold-and-aggregate, enrich, route, sample — so the application
 * composes whatever stages it needs and registers them. {@link
 * io.camunda.eventbridge.streaming.processor.ProcessorTopology} — a graph of operators — is the
 * general implementation.
 *
 * @param <R> the source record type
 */
public interface Stage<R> {

  /** Processes one source record. */
  void process(R record);

  /** Called once after persistent state is restored, before any {@link #process}. */
  default void init() {}

  /** Wall-clock tick: flush buffered work so latency stays bounded. */
  default void flush() {}

  /**
   * Commit-interval tick: make the working state durable. Runs inside the processor's shard
   * transaction, so it lands in the same atomic cut as the consumed offset.
   */
  default void checkpoint() {}

  /**
   * Whether this stage supports splitting its checkpoint into {@link #freezeCheckpoint()}, {@link
   * #persistCheckpoint()} and {@link #completeCheckpoint(boolean)} so the runtime can persist in
   * the background while the stage keeps processing. When any registered stage returns {@code
   * false}, the {@link StreamProcessor} falls back to the synchronous {@link #checkpoint()} for all
   * of them. Default {@code false}.
   */
  default boolean supportsFrozenCheckpoint() {
    return false;
  }

  /**
   * Freezes the stage's checkpoint delta as immutable data detached from the live working state.
   * Runs on the processing thread at the commit barrier; must be cheap (steal-and-replace, at most
   * O(delta) serialization, no store writes). At most one frozen delta is outstanding.
   */
  default void freezeCheckpoint() {}

  /**
   * Persists the frozen delta to the durable store. Runs on an IO thread inside the processor's
   * shard transaction — must not open its own transaction and must not touch any live (non-frozen)
   * state, which the processing thread keeps mutating concurrently.
   */
  default void persistCheckpoint() {}

  /**
   * Completes the frozen delta on the processing thread: on success retire it (it is durable now),
   * on failure merge it back underneath the live delta (newer changes win) so the next freeze
   * retries it as part of a larger cut.
   */
  default void completeCheckpoint(final boolean success) {}

  /** Event-time progress: finalize closed windows, prune state. */
  default void advanceStreamTime(final long streamTimeMs) {}

  /**
   * Wall-clock punctuation tick: run time-driven work (e.g. finalize windows whose grace has
   * elapsed in real time) even for an idle partition with no event-time progress. Default: no-op.
   */
  default void punctuateWallClock(final long wallClockMs) {}

  /**
   * Whether this stage holds buffered writes that should be checkpointed before the regular
   * interval — e.g. a bounded cache is full. Bubbles up to {@link Task#needsCheckpoint()}. Default
   * {@code false}.
   */
  default boolean needsCheckpoint() {
    return false;
  }

  /** Called once on shutdown — final flush + release. */
  default void close() {}
}
