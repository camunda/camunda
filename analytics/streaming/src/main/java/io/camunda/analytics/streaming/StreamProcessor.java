/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a source through a list of {@link Stage}s and owns the lifecycle: {@link #init()} once
 * after persistent state is restored, {@link #process(Object)} per record interleaved with the two
 * punctuation clocks — {@link #flush()} on a wall-clock tick (bounds latency, drains idle buffers)
 * and {@link #advanceStreamTime(long)} on event-time progress (window finalization/retention) — and
 * {@link #close()} once on shutdown.
 *
 * <p>The runtime is agnostic to what the stages do; the application {@link #add(Stage) adds}
 * whatever stages it needs. {@link #register(Projector, List)} is sugar for the common
 * fold-then-aggregate {@link ProjectionStage}.
 *
 * <p>The runtime drives the restore: it reopens persistent state and replays records through {@link
 * #process} from the last flushed offset before going live; ephemeral buffers rebuild during
 * replay.
 *
 * <p>Single-writer: not thread-safe; one processor per source partition.
 *
 * @param <R> the source record type
 */
public final class StreamProcessor<R> implements AutoCloseable {

  private final List<Stage<R>> stages = new ArrayList<>();

  /** Adds any stage — the agnostic entry point the application wires. */
  public StreamProcessor<R> add(final Stage<R> stage) {
    stages.add(stage);
    return this;
  }

  /** Sugar: a fold-then-aggregate stage from a projector and its rollups. */
  public <F> StreamProcessor<R> register(
      final Projector<R, F> projector, final List<Rollup<F>> rollups) {
    return add(new ProjectionStage<>(projector, rollups));
  }

  /** Sugar: a fold-then-aggregate stage with a single rollup. */
  public <F> StreamProcessor<R> register(final Projector<R, F> projector, final Rollup<F> rollup) {
    return register(projector, List.of(rollup));
  }

  public void init() {
    stages.forEach(Stage::init);
  }

  public void process(final R record) {
    for (final Stage<R> stage : stages) {
      stage.process(record);
    }
  }

  /** Wall-clock tick: flush every stage. */
  public void flush() {
    stages.forEach(Stage::flush);
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
