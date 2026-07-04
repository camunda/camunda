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

  /** Commit-interval tick: make the working state durable (state + offsets) in one cut. */
  default void checkpoint() {}

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
