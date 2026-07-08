/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.serving.spi.DatasetWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * An actor-side staging buffer over the backend {@link DatasetWriter}, so a stage task's frozen
 * commit cut can flush exactly the serving rows its barrier covers while the actor keeps folding.
 * Upserts land in a heap buffer (the arguments are immutable: materialized strings, boxed numbers
 * and freshly serialized accumulators), never in the backend's live batch; the backend is only
 * touched at a drain, so it is never shared between the actor and the IO thread.
 *
 * <p>{@link #freeze()} steals the staged rows into the outstanding frozen batch (any failed cut's
 * retained rows first — older-before-newer keeps last-write-wins per key); {@link #publishFrozen()}
 * applies that batch to the backend and flushes it durably on the IO thread; {@link
 * #completeFrozen(boolean)} drops the batch on success or retains it for the next cut on failure —
 * re-applying is safe because every serving write is an idempotent full-value upsert. {@link
 * #flush()} remains the synchronous batch boundary (the final stop commit and the fallback path):
 * it drains retained and staged rows and flushes the backend in place.
 *
 * <p><b>Threading:</b> everything except {@link #publishFrozen()} runs on the task's actor thread;
 * {@link #publishFrozen()} runs on the IO thread and touches only the frozen batch and the backend.
 * The task's single-flight cut plus the runtime's executor handoff provide the happens-before
 * edges; there is no internal locking.
 */
final class FreezableDatasetWriter implements DatasetWriter {

  private final DatasetWriter delegate;

  /** Rows staged since the last drain or freeze; actor thread only. */
  private List<Op> staged = new ArrayList<>();

  /** A failed cut's rows, re-applied ahead of newer rows at the next drain or freeze. */
  private final List<Op> retry = new ArrayList<>();

  /** The outstanding frozen batch (null when none); owned by the IO thread until completed. */
  private List<Op> frozen;

  FreezableDatasetWriter(final DatasetWriter delegate) {
    this.delegate = delegate;
  }

  @Override
  public void upsertCell(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long windowStart,
      final long windowSize,
      final String meterName,
      final byte[] accumulator) {
    staged.add(new CellUpsert(dataset, key, windowStart, windowSize, meterName, accumulator));
  }

  @Override
  public void upsertRow(final CompiledTable table, final String rowKey, final List<Object> values) {
    staged.add(new RowUpsert(table, rowKey, values));
  }

  /** Synchronous batch boundary: drains every retained and staged row and flushes durably. */
  @Override
  public void flush() {
    if (frozen != null) {
      throw new IllegalStateException(
          "cannot flush synchronously: a frozen serving batch is outstanding");
    }
    for (final Op op : retry) {
      op.applyTo(delegate);
    }
    retry.clear();
    drainStaged();
    delegate.flush();
  }

  /**
   * Owner thread, at the commit barrier: steals the staged rows (behind any retained rows of a
   * failed cut) into the outstanding frozen batch. Rows upserted afterwards belong to the next cut.
   *
   * @throws IllegalStateException if a frozen batch is already outstanding
   */
  void freeze() {
    if (frozen != null) {
      throw new IllegalStateException(
          "expected no outstanding frozen serving batch, but freeze() was called again before"
              + " completeFrozen()");
    }
    final List<Op> batch = new ArrayList<>(retry.size() + staged.size());
    batch.addAll(retry);
    retry.clear();
    batch.addAll(staged);
    staged = new ArrayList<>();
    frozen = batch;
  }

  /**
   * IO thread, outside the state transaction: applies the frozen batch to the backend and flushes
   * it durably (produce-before-commit). Touches only the frozen batch and the backend — never the
   * staging buffer the owner thread keeps filling. Idempotent under replay: every write is a
   * full-value upsert by key.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  void publishFrozen() {
    if (frozen == null) {
      throw new IllegalStateException("expected a frozen serving batch to publish, but none");
    }
    if (frozen.isEmpty()) {
      return;
    }
    for (final Op op : frozen) {
      op.applyTo(delegate);
    }
    delegate.flush();
  }

  /**
   * Owner thread, once the cut's outcome is known. Success: the rows are durable — drop the batch.
   * Failure: retain it so the next cut (or the next synchronous {@link #flush()}) re-applies it
   * first; whether or not the failed flush actually landed, the re-apply overwrites idempotently.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  void completeFrozen(final boolean success) {
    if (frozen == null) {
      throw new IllegalStateException("expected a frozen serving batch to complete, but none");
    }
    if (!success) {
      retry.addAll(frozen);
    }
    frozen = null;
  }

  @Override
  public void close() {
    delegate.close();
  }

  private void drainStaged() {
    for (final Op op : staged) {
      op.applyTo(delegate);
    }
    staged.clear();
  }

  /** One buffered serving write, replayed against the backend at drain time. */
  private sealed interface Op permits CellUpsert, RowUpsert {
    void applyTo(DatasetWriter writer);
  }

  private record CellUpsert(
      CompiledDataset dataset,
      DimensionKey key,
      long windowStart,
      long windowSize,
      String meterName,
      byte[] accumulator)
      implements Op {

    @Override
    public void applyTo(final DatasetWriter writer) {
      writer.upsertCell(dataset, key, windowStart, windowSize, meterName, accumulator);
    }
  }

  private record RowUpsert(CompiledTable table, String rowKey, List<Object> values) implements Op {

    @Override
    public void applyTo(final DatasetWriter writer) {
      writer.upsertRow(table, rowKey, values);
    }
  }
}
