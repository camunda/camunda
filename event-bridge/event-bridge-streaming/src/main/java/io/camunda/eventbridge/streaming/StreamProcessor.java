/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a record through a list of {@link Stage}s and owns the per-partition lifecycle: {@link
 * #init()} once after persistent state is restored, {@link #process(Object)} per record, {@link
 * #flush()} (emit) then {@link #checkpoint()} (durable) at the commit barrier, {@link
 * #advanceStreamTime(long)} on event-time progress (window finalization/retention), and {@link
 * #close()} once on shutdown.
 *
 * <p>It is the generic execution model of the streaming framework and implements {@link Task}, so a
 * {@link StreamRuntime} drives it directly. What the stages <em>do</em> (fold-then-aggregate,
 * merge, enrich) is the application's concern — it {@link #add(Stage) adds} the stages it needs
 * (e.g. the fold-then-aggregate {@code ProjectionStage} from the analytics engine).
 *
 * <p>Single-writer: not thread-safe; one processor per source partition.
 *
 * @param <R> the source record type
 */
public final class StreamProcessor<R> implements Task<R>, AutoCloseable {

  private final List<Stage<R>> stages = new ArrayList<>();

  /** Adds any stage — the agnostic entry point the application wires. */
  public StreamProcessor<R> add(final Stage<R> stage) {
    stages.add(stage);
    return this;
  }

  @Override
  public void init() {
    stages.forEach(Stage::init);
  }

  @Override
  public void process(final R record) {
    for (final Stage<R> stage : stages) {
      stage.process(record);
    }
  }

  /** Wall-clock tick: flush every stage. */
  @Override
  public void flush() {
    stages.forEach(Stage::flush);
  }

  /** Commit-interval tick: checkpoint every stage (make working state durable). */
  @Override
  public void checkpoint() {
    stages.forEach(Stage::checkpoint);
  }

  /** Event-time progress: advance finalization/retention on every stage. */
  public void advanceStreamTime(final long streamTimeMs) {
    stages.forEach(stage -> stage.advanceStreamTime(streamTimeMs));
  }

  @Override
  public void close() {
    stages.forEach(Stage::close);
  }
}
