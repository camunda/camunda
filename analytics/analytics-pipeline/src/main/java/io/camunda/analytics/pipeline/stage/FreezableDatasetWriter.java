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
import io.camunda.eventbridge.streaming.sink.FrozenOutbox;
import java.util.List;

/**
 * An actor-side staging buffer over the backend {@link DatasetWriter}, so a stage task's frozen
 * commit cut can flush exactly the serving rows its barrier covers while the actor keeps folding.
 * Upserts land in a {@link FrozenOutbox} of heap ops (the arguments are immutable: materialized
 * strings, boxed numbers and freshly serialized accumulators), never in the backend's live batch;
 * the backend is only touched at a drain, so it is never shared between the actor and the IO
 * thread.
 *
 * <p>The outbox owns the cut lifecycle — {@link #freeze()} detaches the staged rows (a failed cut's
 * retained rows first — older-before-newer keeps last-write-wins per key), {@link #publishFrozen()}
 * replays that batch onto the backend and flushes it durably on the IO thread, {@link
 * #completeFrozen(boolean)} drops or retains it — re-applying is safe because every serving write
 * is an idempotent full-value upsert. {@link #flush()} remains the synchronous batch boundary (the
 * final stop commit and the fallback path): it drains retained and staged rows and flushes the
 * backend in place.
 *
 * <p><b>Threading:</b> everything except {@link #publishFrozen()} runs on the task's actor thread;
 * {@link #publishFrozen()} runs on the IO thread and touches only the frozen batch and the backend.
 * The task's single-flight cut plus the runtime's executor handoff provide the happens-before
 * edges; there is no internal locking.
 */
final class FreezableDatasetWriter implements DatasetWriter {

  private final DatasetWriter delegate;
  private final FrozenOutbox<Op> outbox = new FrozenOutbox<>();

  FreezableDatasetWriter(final DatasetWriter delegate) {
    this.delegate = delegate;
  }

  @Override
  public void upsertCell(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long windowStart,
      final long windowSize,
      final byte[] compositeAccumulator) {
    outbox.stage(new CellUpsert(dataset, key, windowStart, windowSize, compositeAccumulator));
  }

  @Override
  public void upsertRow(final CompiledTable table, final String rowKey, final List<Object> values) {
    outbox.stage(new RowUpsert(table, rowKey, values));
  }

  /** Synchronous batch boundary: drains every retained and staged row and flushes durably. */
  @Override
  public void flush() {
    if (outbox.hasFrozen()) {
      throw new IllegalStateException(
          "cannot flush synchronously: a frozen serving batch is outstanding");
    }
    outbox.drainPending(op -> op.applyTo(delegate));
    delegate.flush();
  }

  /**
   * Owner thread, at the commit barrier: detaches the staged rows (behind any retained rows of a
   * failed cut) into the outstanding frozen batch. Rows upserted afterwards belong to the next cut.
   *
   * @throws IllegalStateException if a frozen batch is already outstanding
   */
  void freeze() {
    outbox.freeze();
  }

  /**
   * IO thread, outside the state transaction: applies the frozen batch to the backend and flushes
   * it durably (produce-before-commit). Touches only the frozen batch and the backend — never the
   * staging buffer the actor thread keeps filling. Idempotent under replay: every write is a
   * full-value upsert by key.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  void publishFrozen() {
    if (outbox.drainFrozen(op -> op.applyTo(delegate)) > 0) {
      delegate.flush();
    }
  }

  /**
   * Owner thread, once the cut's outcome is known. Success: the rows are durable — drop the batch.
   * Failure: retain it so the next cut (or the next synchronous {@link #flush()}) re-applies it
   * first; whether or not the failed flush actually landed, the re-apply overwrites idempotently.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  void completeFrozen(final boolean success) {
    outbox.completeFrozen(success);
  }

  @Override
  public void close() {
    delegate.close();
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
      byte[] compositeAccumulator)
      implements Op {

    @Override
    public void applyTo(final DatasetWriter writer) {
      writer.upsertCell(dataset, key, windowStart, windowSize, compositeAccumulator);
    }
  }

  private record RowUpsert(CompiledTable table, String rowKey, List<Object> values) implements Op {

    @Override
    public void applyTo(final DatasetWriter writer) {
      writer.upsertRow(table, rowKey, values);
    }
  }
}
