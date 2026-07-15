/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dimension.DimensionKey;
import java.util.List;

/**
 * The backend seam of the serving store's write path: every write is an idempotent full-value
 * upsert keyed by identity <em>and fenced by a {@link WriteVersion}</em> — the backend applies it
 * only when the version is at-or-above the stored one, so a fenced zombie's stale overwrite is
 * rejected by the store itself and a deterministic replay (equal version) still re-writes.
 *
 * <p>This is deliberately a different interface from {@link DatasetWriter}: the sinks that
 * <em>produce</em> writes stage them version-unaware (the version is a property of the commit cut
 * that flushes them, not of the individual write), while every backend must enforce the fence —
 * splitting the seams makes forgetting it a compile error, not a silent gap.
 */
public interface VersionedDatasetWriter extends AutoCloseable {

  /**
   * Upserts one cube cell from its composite accumulator (see {@link DatasetWriter#upsertCell}),
   * applied only when {@code version} is at-or-above the cell's stored version.
   */
  void upsertCell(
      CompiledDataset dataset,
      DimensionKey key,
      long windowStart,
      long windowSize,
      byte[] compositeAccumulator,
      WriteVersion version);

  /**
   * Upserts one raw row of a projected dataset keyed by {@code rowKey}, applied only when {@code
   * version} is at-or-above the row's stored version.
   */
  void upsertRow(CompiledTable table, String rowKey, List<Object> values, WriteVersion version);

  /**
   * Deletes the row keyed by {@code rowKey} (see {@link DatasetWriter#deleteRow}); idempotent —
   * deleting an absent row is a no-op. Backends fence it where cheap ({@code version} at-or-above
   * the stored one), but with a shared caveat either way: a delete <em>forgets</em> the row's
   * stored version (RDBMS drops the row; a document store garbage-collects deleted-doc versions
   * after {@code gc_deletes}), so a zombie's stale upsert arriving after the delete can transiently
   * resurrect the row until the eviction replays. Within one owner the staged-cut protocol replays
   * deletes after their preceding upserts, so the net state converges.
   */
  void deleteRow(CompiledTable table, String rowKey, WriteVersion version);

  /**
   * Upserts one periodic-snapshot row (see {@link DatasetWriter#upsertSnapshotRow}), applied only
   * when {@code version} is at-or-above the row's stored version.
   */
  void upsertSnapshotRow(
      CompiledDataset dataset,
      DimensionKey key,
      long sampleTime,
      byte[] compositeAccumulator,
      WriteVersion version);

  /** Batch boundary: make buffered writes durable. */
  void flush();

  /**
   * Writes rejected by the version fence since this writer opened — the fence working, not an error
   * (zero outside rebalances/replays). Exposed here so the pipeline can meter it uniformly across
   * backends; a backend that cannot observe rejections reports 0.
   */
  default long fencedWrites() {
    return 0L;
  }

  @Override
  void close();
}
