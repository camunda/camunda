/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.aggregate.WindowedCellState.CheckpointDelta;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import io.camunda.zeebe.db.impl.DbBytes;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
 * <p>Checkpointing is split so the durable write can run off the owner thread: {@link #freeze()}
 * captures the delta-to-persist as immutable, already-serialized bytes and processing resumes
 * immediately; {@link #persistFrozen()} writes the frozen delta inside the transaction the runtime
 * supplies; {@link #completeFrozen(boolean)} drops it on success or merges it back on failure.
 * {@link #checkpoint()} composes the three synchronously for callers without an asynchronous
 * commit.
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
  // commit flushes right before it checkpoints); invalidated when the cell changes again. The
  // cache only bridges flush -> checkpoint, so freeze() steals the whole map (installing a fresh
  // one) rather than shadowing every open cell's accumulator with a second serialized copy.
  private Map<Windowed<K>, byte[]> serializedSinceFlush = new HashMap<>();
  private long maxEventTime = Long.MIN_VALUE;

  // The outstanding frozen checkpoint delta (null when none): the changed/evicted cell sets stolen
  // from the working set plus the at-freeze serialized bytes of every frozen changed cell. Owned
  // by the freeze/complete pair on the owner thread; persistFrozen only reads it.
  private CheckpointDelta<K> frozenCells;
  private Map<Windowed<K>, byte[]> frozenSerialized;

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
   * Commit-interval tick as one synchronous cut: {@link #freeze()} the delta, persist it inside one
   * transaction, {@link #completeFrozen(boolean) complete}. Callers that overlap the persist with
   * processing drive the three steps themselves instead — freeze and complete on the owner thread,
   * {@link #persistFrozen()} inside the transaction the runtime supplies.
   */
  public void checkpoint() {
    freeze();
    try {
      tx.runInTransaction(this::persistFrozen);
    } catch (final RuntimeException e) {
      completeFrozen(false);
      throw e;
    }
    completeFrozen(true);
  }

  /**
   * Owner thread: finalizes closed windows, flushes, and detaches the checkpoint delta — the
   * changed/evicted cell sets plus the serialized bytes of every changed cell — into the frozen
   * slot, installing fresh empty trackers so folding resumes immediately. The frozen delta is
   * immutable data: later folds touch only the live accumulators and trackers, never the frozen
   * bytes, so no copy-on-write of live accumulators is needed.
   *
   * <p>Invariant (checked): after the flush, {@code serializedSinceFlush} holds current bytes for
   * every cell changed since the last checkpoint — a change invalidates the cached bytes and
   * re-marks the cell for exactly the flush that just ran, and an eviction removes the cell from
   * the changed set altogether.
   *
   * @throws IllegalStateException if a frozen delta is already outstanding
   */
  public void freeze() {
    if (frozenCells != null) {
      throw new IllegalStateException(
          "expected no outstanding frozen checkpoint delta, but freeze() was called again before"
              + " completeFrozen()");
    }
    finalizeClosedWindows();
    flush();
    final CheckpointDelta<K> delta = open.detachCheckpointDelta();
    final Map<Windowed<K>, byte[]> serialized = serializedSinceFlush;
    serializedSinceFlush = new HashMap<>();
    for (final Windowed<K> cell : delta.changed()) {
      if (!serialized.containsKey(cell)) {
        throw new IllegalStateException(
            "expected serialized bytes for every changed cell after the freeze flush, but cell "
                + cell
                + " has none");
      }
    }
    frozenCells = delta;
    frozenSerialized = serialized;
  }

  /**
   * IO thread, inside the caller's commit transaction: persists the frozen delta to the durable
   * cells — the at-freeze bytes for every frozen changed cell, a delete for every frozen evicted
   * cell. Touches only the frozen slot and the cell store (which the owner thread itself only uses
   * on this path and at recovery), never the live working state — the owner keeps folding
   * concurrently.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void persistFrozen() {
    if (frozenCells == null) {
      throw new IllegalStateException("expected a frozen checkpoint delta to persist, but none");
    }
    for (final Windowed<K> cell : frozenCells.changed()) {
      cells.putSerialized(cell, frozenSerialized.get(cell));
    }
    for (final Windowed<K> cell : frozenCells.evicted()) {
      cells.delete(cell);
    }
  }

  /**
   * Owner thread, once the transaction's outcome is known. Success: the frozen delta is durable —
   * drop it. Failure: merge it back so the next freeze re-includes it — frozen changed cells are
   * re-marked changed and their bytes re-cached, frozen evicted cells re-marked for deletion. The
   * live state always wins; the frozen delta only fills gaps: a cell re-changed since the freeze
   * keeps its newer total and its pending (or already re-cached) re-serialization, a cell evicted
   * since the freeze stays evicted, and a cell re-created since the freeze is not re-deleted.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void completeFrozen(final boolean success) {
    if (frozenCells == null) {
      throw new IllegalStateException("expected a frozen checkpoint delta to complete, but none");
    }
    if (!success) {
      open.mergeBackCheckpointDelta(frozenCells);
      for (final Entry<Windowed<K>, byte[]> frozen : frozenSerialized.entrySet()) {
        final Windowed<K> cell = frozen.getKey();
        // The frozen bytes fill a gap only while they are still current: not for a cell re-changed
        // (its re-serialization is pending or already re-cached), and not for one evicted since
        // the freeze (the merge-back left it out of the changed set; its delete supersedes any
        // write). Restoring stale bytes would serve or persist an outdated total.
        if (open.isChangedSinceFlush(cell)
            || serializedSinceFlush.containsKey(cell)
            || !open.isChangedSinceCheckpoint(cell)) {
          continue;
        }
        serializedSinceFlush.put(cell, frozen.getValue());
      }
    }
    frozenCells = null;
    frozenSerialized = null;
  }

  /**
   * Graceful shutdown: converge the serving view, but do <b>not</b> checkpoint. The serving upserts
   * are idempotent by key, so publishing ahead of the offset cut is safe — the restarted replay
   * re-converges them. The durable cells are not: a close between process and commit would persist
   * folds the committed offset (and the dedup admission watermark, which is also only persisted at
   * the commit cut) does not cover, so the replayed batches would be re-admitted and double-folded
   * onto the close-persisted totals. Durable cells therefore move only in the checkpoint's persist,
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
}
