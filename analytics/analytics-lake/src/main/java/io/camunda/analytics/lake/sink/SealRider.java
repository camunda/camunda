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
}
