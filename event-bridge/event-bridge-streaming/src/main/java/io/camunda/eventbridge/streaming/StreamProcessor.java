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
 * (e.g. a {@link io.camunda.eventbridge.streaming.processor.ProcessorTopology} operator graph).
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

  /**
   * Freezes every stage's checkpoint delta into one cut, provided all stages support it — otherwise
   * {@code null}, and the runtime falls back to the synchronous {@link #checkpoint()}. The runtime
   * persists the cut inside its shared transaction (this processor defers durability), so {@link
   * CommitCut#persist()} just drains every stage's frozen delta.
   */
  @Override
  public CommitCut freezeCut(final long offset) {
    for (final Stage<R> stage : stages) {
      if (!stage.supportsFrozenCheckpoint()) {
        return null;
      }
    }
    flush(); // converge buffered output first — the freeze captures its serialized form
    stages.forEach(Stage::freezeCheckpoint);
    return new CommitCut() {
      @Override
      public void persist() {
        stages.forEach(Stage::persistCheckpoint);
      }

      @Override
      public void complete(final boolean success) {
        stages.forEach(stage -> stage.completeCheckpoint(success));
      }
    };
  }

  /** Event-time progress: advance finalization/retention on every stage. */
  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    stages.forEach(stage -> stage.advanceStreamTime(streamTimeMs));
  }

  /** Wall-clock tick: run time-driven work on every stage, even for idle partitions. */
  @Override
  public void punctuateWallClock(final long wallClockMs) {
    stages.forEach(stage -> stage.punctuateWallClock(wallClockMs));
  }

  @Override
  public boolean needsCheckpoint() {
    for (final Stage<R> stage : stages) {
      if (stage.needsCheckpoint()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() {
    stages.forEach(Stage::close);
  }
}
