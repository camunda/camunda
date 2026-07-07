/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import io.camunda.zeebe.db.impl.DbBytes;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Merges immutable segment deltas into <b>one running accumulator per cell</b> and converges an
 * idempotent {@link ResultSink}, so a read hits a single cell. It is the reduce side of the
 * segment-delta shuffle: each delta is the from-empty accumulator of one sealed segment for a cell,
 * and this operator folds it into the cell's total via {@link AggregateFunction#mergeInto} (the
 * total is owned by this operator, so the in-place fold is safe).
 *
 * <p>Unlike per-writer slots, it keeps just one accumulator per cell — so exactly-once rests on
 * merging each delta at most once. That dedup is the caller's responsibility, done once per batch
 * via {@link SegmentDedup} (a re-emitted batch is dropped before it reaches {@link #merge}); the
 * merge itself is a plain, non-idempotent fold. The running totals are checkpointed to a shared
 * store through a {@link GroupedCellStore} (keyed {@code group ++ windowStart ++ codec(key)}; the
 * {@code group} lets several operators share one store and scan only their own cells). Closed
 * windows are finalized, emitted, and evicted. Single-writer, like every operator.
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class SegmentMergingAggregation<K, ACC> {

  private final AggregateFunction<?, ACC, ?> aggregate;
  private final Windows windows;
  private final ResultSink<Windowed<K>, ACC> sink;
  private final GroupedCellStore<K, ACC> cells;
  private final RecordValue<ACC> accValue;
  private final TransactionRunner tx;
  private final Predicate<ACC> drained;

  // Heap working set: one running accumulator per open cell, indexed by window end for due-window
  // finalization and tracked for flush/checkpoint deltas.
  private final WindowedCellState<K, ACC> open = new WindowedCellState<>();
  // A changed cell's serialized total, produced once at flush and reused by the checkpoint (a
  // commit flushes right before it checkpoints); invalidated when the cell changes again.
  private final Map<Windowed<K>, byte[]> serializedSinceFlush = new HashMap<>();
  private long maxEventTime = Long.MIN_VALUE;

  public SegmentMergingAggregation(
      final int group,
      final AggregateFunction<?, ACC, ?> aggregate,
      final Windows windows,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final TransactionRunner tx) {
    this(group, aggregate, windows, sink, cellStore, keyValue, accValue, tx, acc -> false);
  }

  public SegmentMergingAggregation(
      final int group,
      final AggregateFunction<?, ACC, ?> aggregate,
      final Windows windows,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final TransactionRunner tx,
      final Predicate<ACC> drained) {
    this.aggregate = aggregate;
    this.windows = windows;
    this.sink = sink;
    cells = new GroupedCellStore<>(group, cellStore, keyValue, accValue);
    this.accValue = accValue;
    this.tx = tx;
    this.drained = drained;
    recover();
  }

  /** Folds one (deduped) segment delta into {@code cell}'s running total and marks it changed. */
  public void merge(final Windowed<K> cell, final ACC delta) {
    // Drop deltas for a window that already closed and was evicted: folding one would resurrect the
    // cell and the idempotent sink would overwrite its finalized value.
    if (maxEventTime != Long.MIN_VALUE && windowEnd(cell) + windows.graceMs() <= maxEventTime) {
      return;
    }
    // The running total is always an accumulator this operator owns: the first delta is folded
    // into a fresh accumulator rather than stored, so a delta may be a transient read-only view
    // (RecordValue#fromBytesForMerge) and the in-place mergeInto never mutates a caller's object.
    final ACC current = open.get(cell);
    open.put(
        cell,
        current == null
            ? aggregate.mergeInto(aggregate.createAccumulator(), delta)
            : aggregate.mergeInto(current, delta));
    if (current == null) {
      // First touch — the cell's window end never changes afterwards.
      open.index(cell, windowEnd(cell));
    }
    open.markChanged(cell);
    serializedSinceFlush.remove(cell); // the cached serialized form (if any) is stale now
    maxEventTime = Math.max(maxEventTime, windowEnd(cell));
  }

  /** Wall-clock tick: converge the serving view for the cells changed since the last flush. */
  public void flush() {
    open.forEachChangedSinceFlush(
        (cell, total) -> {
          // Serialize once and hand the bytes to the sink; the checkpoint reuses them for the
          // durable write instead of serializing the same unchanged total a second time.
          final byte[] serialized = accValue.toBytes(total);
          serializedSinceFlush.put(cell, serialized);
          sink.upsert(cell, total, serialized);
        });
    open.clearChangedSinceFlush();
  }

  /**
   * Commit-interval tick: finalize closed windows, then persist changed/evicted cells in one txn.
   */
  public void checkpoint() {
    finalizeClosedWindows();
    tx.runInTransaction(
        () -> {
          open.forEachChangedSinceCheckpoint(
              (cell, total) -> {
                if (total != null) {
                  writeCell(cell, total);
                }
              });
          open.forEachEvictedSinceCheckpoint(this::deleteCell);
        });
    open.clearCheckpointDelta();
    // The cache only bridges one commit's flush -> checkpoint; drop it rather than shadowing every
    // open cell's accumulator with a second serialized copy.
    serializedSinceFlush.clear();
  }

  /**
   * Graceful shutdown: converge the serving view, but do <b>not</b> checkpoint. The serving upserts
   * are idempotent by key, so publishing ahead of the offset cut is safe — the restarted replay
   * re-converges them. The durable cells are not: a close between process and commit would persist
   * folds the committed offset (and the dedup admission watermark, which is also only persisted at
   * the commit cut) does not cover, so the replayed batches would be re-admitted and double-folded
   * onto the close-persisted totals. Durable cells therefore move only in {@link #checkpoint()},
   * inside the owner's commit cut; uncommitted folds are simply lost here and rebuilt by replay.
   */
  public void close() {
    flush();
  }

  private void finalizeClosedWindows() {
    if (maxEventTime == Long.MIN_VALUE) {
      return;
    }
    final long watermark = maxEventTime - windows.graceMs();
    // Only cells whose window has ended are candidates — a closed window (past the watermark)
    // finalizes unconditionally, an ended-but-in-grace one only once its accumulator has drained.
    open.evictDue(
        maxEventTime,
        (windowEnd, cell, value) -> {
          if (windowEnd > watermark && !drained.test(value)) {
            return false;
          }
          emitFinal(cell, value);
          return true;
        });
  }

  /** Emits the cell's final value to the serving view; the state then evicts the cell. */
  private void emitFinal(final Windowed<K> cell, final ACC value) {
    final byte[] serialized = serializedSinceFlush.remove(cell);
    if (serialized != null) {
      sink.upsert(cell, value, serialized); // final value, already serialized at the flush
    } else {
      sink.upsert(cell, value); // final value
    }
  }

  private void recover() {
    cells.scanCells(
        (cell, total) -> {
          open.put(cell, total);
          open.index(cell, windowEnd(cell));
          maxEventTime = Math.max(maxEventTime, windowEnd(cell));
        });
  }

  private long windowEnd(final Windowed<K> cell) {
    return cell.windowStart() + windows.sizeMs();
  }

  private void writeCell(final Windowed<K> cell, final ACC total) {
    // A commit flushes right before it checkpoints, so an unchanged-since-flush cell reuses the
    // bytes the flush already produced; the fallback covers a checkpoint without a prior flush.
    final byte[] serialized = serializedSinceFlush.get(cell);
    if (serialized != null) {
      cells.putSerialized(cell, serialized);
    } else {
      cells.put(cell, total);
    }
  }

  private void deleteCell(final Windowed<K> cell) {
    cells.delete(cell);
  }
}
