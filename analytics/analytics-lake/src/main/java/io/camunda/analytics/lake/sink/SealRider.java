/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

import java.util.List;
import java.util.Map;

/**
 * Seam for fold-at-flush riders: row-local gold measures (counts, sums, histogram-bin partials)
 * computed from the sorted run the flush thread already produced — the data's one guaranteed moment
 * in RAM. Instance-assembled measures (variants, KPIs) must NOT ride here; they belong to the
 * completion-triggered lane (see the maintenance-plane design).
 *
 * <p>Lifecycle, all on the flush thread: {@link #onSealed} is called once per sealed segment;
 * accumulation state carries <em>across</em> segments of one flush window (partial rows are
 * additive, so per-window emission is pure row economy — correctness would survive per-segment
 * emission too). {@link #onWindowClose} is called exactly once when the window is being closed into
 * a {@link Descriptor}: the rider drains its accumulators into partials files and returns them
 * keyed by derived-table name, to ride the same descriptor — and thus the same atomic commit — as
 * the raw files they were computed from. {@link #abortWindow} is the failure path: discard all
 * accumulated state and any open files, matching the window's own abort.
 */
public interface SealRider {

  /** Called once per sealed segment, on the flush thread, after sorting and before encoding. */
  void onSealed(SortedRun run);

  /**
   * Drains everything accumulated since the last window boundary into finished partials files,
   * resetting the rider for the next window.
   *
   * @return this window's partials files keyed by derived Iceberg table name; empty if the window
   *     touched nothing
   */
  default Map<String, List<DataFileResult>> onWindowClose() {
    return Map.of();
  }

  /** Discards accumulated state and aborts any open files (pipeline failure path). */
  default void abortWindow() {}

  /**
   * Poll-thread hook: fired at the exact moment the host {@link
   * io.camunda.analytics.lake.sink.pipeline.SinkPipeline} seals a file boundary (TIME_DUE /
   * SIZE_CAP / SHUTDOWN) — see {@code SinkPipeline#trySeal}/{@code SinkPipeline#close}, called
   * strictly <em>before</em> the ring's own seal so it is published (in program order, on the same
   * thread) no later than the {@code io.camunda.analytics.lake.sink.pipeline.SealSnapshot} the
   * boundary itself captures — matching that snapshot's own "enqueue before seal" ordering rule,
   * for the same reason: the flush thread must never be able to observe the sealed segment before
   * this hook's effects are visible.
   *
   * <p>A <em>poll-fed</em> rider (one that folds directly from records on the poll thread, rather
   * than from raw rows at flush time — see {@code io.camunda.analytics.lake.metrics.PollFedRider})
   * uses this to freeze its currently-active accumulator set and start a fresh, empty one, so the
   * frozen set's increments align exactly with this boundary's descriptor offset range: everything
   * folded before the boundary is in the frozen set, everything folded after is in the new active
   * one. Default no-op: a fold-at-flush rider (see {@code
   * io.camunda.analytics.lake.metrics.MetricsRider}) accumulates only from {@link #onSealed}, which
   * already only ever runs on real raw-row data at the right time, so it has nothing to freeze
   * here.
   */
  default void onPollBoundary() {}

  /**
   * Undoes the effect of the immediately preceding {@link #onPollBoundary()} call: fired when the
   * boundary attempt it was speculatively taken for turns out not to have happened after all (the
   * ring was full — see {@code SinkPipeline#trySeal}'s own rollback). Never called except
   * immediately after a matching {@link #onPollBoundary()}, with no other call to this rider (poll
   * thread only, never reentrant) in between — an implementation may rely on undoing exactly the
   * swap it just performed.
   */
  default void rollbackPollBoundary() {}

  /**
   * Whether this rider holds accumulated state that must still be drained even though the host
   * pipeline's own raw-row window is currently empty — see {@code FlushLoop#drainShutdown}, which
   * would otherwise skip finalizing an empty window (and thus skip calling {@link #onWindowClose}
   * on every rider) at shutdown. Only ever {@code true} for a poll-fed rider (see {@link
   * #onPollBoundary}'s own javadoc): its accumulators are not gated on raw row appends the way
   * {@link #onSealed}-driven state is, so it can hold real data the host window knows nothing
   * about. The default ({@code false}) matches every fold-at-flush rider, whose state is
   * necessarily empty exactly when the raw window is.
   */
  default boolean hasPendingPollFedData() {
    return false;
  }
}
