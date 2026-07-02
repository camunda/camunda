/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming;

/**
 * A unit of work the {@link StreamProcessor} drives over the source: it processes each record and
 * shares the runtime lifecycle (init, the two punctuation clocks, close). The runtime knows nothing
 * about what a stage <em>does</em> — fold-and-aggregate, enrich, route, sample — so the application
 * composes whatever stages it needs and registers them. {@link ProjectionStage} is the common
 * fold-then-aggregate stage, but it is just one implementation.
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

  /** Called once on shutdown — final flush + release. */
  default void close() {}
}
