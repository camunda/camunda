/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import io.camunda.analytics.dataset.CompiledDataset;
import java.util.List;
import java.util.function.Consumer;

/**
 * The backend-neutral <b>read</b> seam of the serving store (mirroring OC's {@code
 * DocumentBasedSearchClient}): executes a {@link DatasetFetch} and returns the matching raw {@link
 * Cell}s (for a cube), or a {@link TableFetch} and returns the matching {@link TableRow}s (for a
 * table). Each backend transforms the neutral fetch into its native query (SQL for RDBMS, the
 * aggregation/search DSL for ES/OS); the {@link DatasetQueryExecutor} does the cube merge/finalize
 * on top, while a table is served as fetched — so this seam stays a thin filter-and-fetch.
 */
public interface DatasetQueryClient extends AutoCloseable {

  /** Fetches the raw cells of a cube at one tier (see {@link DatasetFetch}). */
  List<Cell> fetch(DatasetFetch fetch);

  /**
   * The snapshot BASELINE (ADR 0010): per key, the newest snapshot at-or-before {@code atMs} — the
   * "opening balance" a series read carries forward from. One point per key that has ever
   * snapshotted, however far back its newest row lies; O(keys), never O(history).
   */
  default List<SnapshotPoint> snapshotBaseline(final CompiledDataset dataset, final long atMs) {
    throw new UnsupportedOperationException(
        "periodic snapshots are not supported by this backend yet");
  }

  /**
   * The snapshot RANGE (ADR 0010): every snapshot point with {@code fromMs < sample_time <= toMs},
   * ordered by key then time — the sparse change points a series read applies on top of the
   * baseline.
   */
  default List<SnapshotPoint> snapshotRange(
      final CompiledDataset dataset, final long fromMs, final long toMs) {
    throw new UnsupportedOperationException(
        "periodic snapshots are not supported by this backend yet");
  }

  /**
   * Streams the raw cells of a cube at one tier to {@code sink}, one page at a time — the executor
   * folds each into its accumulator as it arrives, so neither side materializes the full result (no
   * fixed cap, memory bounded to a page). A backend that can stream (RDBMS forward cursor, ES/OS
   * {@code search_after}) overrides this; the default just replays a {@link #fetch}, so a backend
   * is correct before it is optimized.
   */
  default void streamCells(final DatasetFetch fetch, final Consumer<Cell> sink) {
    fetch(fetch).forEach(sink);
  }

  /**
   * Executes a pushed-down / direct read (see {@link AggregatedFetch}): the store does the
   * reduction ({@code GROUP BY} + {@code SUM}/{@code MIN}/{@code MAX}, or none for {@link
   * ReadStrategy#DIRECT}) and returns finalized {@link AggregatedRow}s — the executor unions these
   * with the streamed sketch rows. Used only for meters with a {@code PushdownSpec}; sketches
   * always stream.
   */
  List<AggregatedRow> fetchAggregated(AggregatedFetch fetch);

  /** Fetches the raw rows of a table (see {@link TableFetch}). */
  List<TableRow> fetchRows(TableFetch fetch);

  @Override
  void close();
}
