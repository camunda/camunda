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
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2LongHashMap.KeyIterator;

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
 * immediately; {@link #persistFrozen()} writes the frozen delta inside the transaction the task
 * supplies; {@link #completeFrozen(boolean)} drops it on success or merges it back on failure.
 * {@link #checkpoint()} composes the three synchronously for callers without an asynchronous
 * commit. The persisted delta is a <em>true</em> delta: each changed cell's bytes once, and a
 * delete only for an evicted cell some completed cut actually wrote — a cell born and evicted
 * between two cuts never had a durable row and leaves no delete.
 *
 * <p><b>Stream-time clock.</b> One operator multiplexes deltas from many upstream sources whose
 * event times are not mutually monotonic, so the clock that closes windows — the late-delta guard
 * in {@link #merge} and the checkpoint finalization watermark — is the MIN over each live source's
 * own max event time ({@link #merge(Windowed, Object, long, int)} attributes every delta to a
 * source): no window closes while any live source might still ship deltas for it. A source that has
 * shipped nothing for the idle timeout ({@link #sourceIdleness}) is excluded from the min until it
 * speaks again, so a silent source cannot stall the clock forever; if it re-enters behind the
 * clock, its late deltas drop into the {@link LateDropListener}. When <em>every</em> source is idle
 * the clock simply holds — no data is flowing, and finalization resumes with the next delta. The
 * clock is clamped monotonically non-decreasing: the raw min moves backwards when a source joins
 * behind it, and a regressing watermark would break downstream consumers ({@link
 * FinalizationListener#onWatermark}). Callers without source identity use the delegating overloads
 * (one implicit source), which makes the clock the plain max of the hints — today's single-source
 * behavior, unchanged.
 *
 * <p><b>Recovery.</b> A restart loses the per-source structure, so the clock restarts unknown and
 * waits for sources to speak: recovered cells alone never close windows (adopting the max recovered
 * window end immediately would let a source that recovered ahead finalize a slower source's
 * still-in-flight windows before it re-registers). The late-delta <em>guard</em>, however, is armed
 * immediately: it is floored at the max recovered window end, so while the clock is still forming a
 * straggler for a window finalized and evicted before the restart cannot resurrect it (folding it
 * would overwrite the finalized serving row with a partial value). To keep recovered-but-never-
 * spoken state from stalling finalization forever (an upstream that is drained and gone), the max
 * recovered window end is adopted as the clock once no source has spoken for a full idle timeout
 * after the first clock evaluation — the same escape hatch as an idle source.
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class SegmentMergingAggregation<K, ACC> {

  /** How long a source may stay silent before it stops gating the stream-time clock. */
  public static final long DEFAULT_IDLE_TIMEOUT_MS = 60_000L;

  /** The source every source-less {@link #merge} overload attributes its deltas to. */
  private static final int DEFAULT_SOURCE = 0;

  private final AggregateFunction<?, ACC, ?> aggregate;
  private final Windows windows;
  private final ResultSink<Windowed<K>, ACC> sink;
  private final GroupedCellStore<K, ACC> cells;
  private final RecordValue<ACC> accValue;
  private final TransactionRunner tx;
  private final Predicate<ACC> drained;

  /**
   * Observes window finalization — the one moment a cell's value becomes event-time-final. {@code
   * onFinal} fires per finalized cell in ascending window-end order (so per key, in window order),
   * with the value about to be evicted; {@code onWatermark} fires after each finalization pass with
   * the watermark that drove it. A consumer deriving cumulative views (e.g. periodic snapshots)
   * folds finalized values and uses the watermark to release boundaries no further window can
   * precede. The listener must not retain {@code value} beyond the callback if it is a mutable
   * accumulator — fold it, don't store it.
   */
  public interface FinalizationListener<K, ACC> {
    void onFinal(Windowed<K> cell, ACC value);

    default void onWatermark(final long watermark) {}
  }

  /**
   * Observes a delta the closed-window guard dropped: its window ended more than the grace before
   * the stream-time clock and was finalized/evicted, so folding it would resurrect the cell. Every
   * drop is data missing from a finalized value. The clock is the min over the live sources' own
   * max event times, so a drop means the source lagged <em>itself</em> past the grace, re-entered
   * behind the clock after an idle timeout, or replayed a straggler — never merely that a sibling
   * source ran ahead.
   */
  @FunctionalInterface
  public interface LateDropListener<K> {
    void onLateDrop(Windowed<K> cell, long eventTimeHint, long clock);
  }

  private FinalizationListener<K, ACC> finalizationListener = (cell, value) -> {};
  private LateDropListener<K> lateDropListener = (cell, eventTimeHint, maxEventTime) -> {};

  // Heap working set: one running accumulator per open cell, indexed by window end for due-window
  // finalization and tracked for flush/checkpoint deltas.
  private final WindowedCellState<K, ACC> open = new WindowedCellState<>();
  // A changed cell's serialized total, produced once at flush and reused by the checkpoint (a
  // commit flushes right before it checkpoints); invalidated when the cell changes again. The
  // cache only bridges flush -> checkpoint, so freeze() steals the whole map (installing a fresh
  // one) rather than shadowing every open cell's accumulator with a second serialized copy.
  private Map<Windowed<K>, byte[]> serializedSinceFlush = new HashMap<>();

  // Stream-time clock (see the class javadoc): each source's own max event-time hint and the wall
  // clock it last shipped a delta; the published clock is the min over the non-idle maxes, clamped
  // monotonically non-decreasing. All hot-path state is primitive — the fold stays garbage-free.
  private final Long2LongHashMap sourceMaxEventTime = new Long2LongHashMap(Long.MIN_VALUE);
  private final Long2LongHashMap sourceLastSeenMs = new Long2LongHashMap(Long.MIN_VALUE);
  private long clock = Long.MIN_VALUE;
  // The max recovered window end — the pre-restart clock's lower bound, adopted only by the
  // recovery escape hatch (no source spoke for a full idle timeout after the first evaluation).
  private long recoveredEventTime = Long.MIN_VALUE;
  private long firstClockEvaluationMs = Long.MIN_VALUE;
  private LongSupplier nowMs = System::currentTimeMillis;
  private long idleTimeoutMs = DEFAULT_IDLE_TIMEOUT_MS;
  // The cells whose durable row exists right now — written by a completed cut (or recovered) and
  // not yet deleted by one. It gates the cut's deletes: an evicted cell with no durable row (born
  // and evicted between two cuts, or written only by a failed cut) needs no delete. Mutated only
  // on complete/recover, never on freeze — a cut counts as written only once it durably completed.
  private final Set<Windowed<K>> durablyWritten = new HashSet<>();

  // The outstanding frozen checkpoint delta (null when none): the changed/evicted cell sets stolen
  // from the working set, the at-freeze serialized bytes of every frozen changed cell, and the
  // durable rows the cut deletes (the frozen evicted cells a completed cut had written). Owned by
  // the freeze/complete pair on the owner thread; persistFrozen only reads it.
  private CheckpointDelta<K> frozenCells;
  private Map<Windowed<K>, byte[]> frozenSerialized;
  private Set<Windowed<K>> frozenDeletes;

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

  /** Wires the finalization observer (before processing starts); at most one. */
  public void onFinalization(final FinalizationListener<K, ACC> listener) {
    finalizationListener = listener;
  }

  /** Wires the late-drop observer (before processing starts); at most one. */
  public void onLateDrop(final LateDropListener<K> listener) {
    lateDropListener = listener;
  }

  /**
   * Wires the wall clock and the source idle timeout (before processing starts). {@code nowMs}
   * feeds only the idleness bookkeeping — event time never derives from it.
   */
  public void sourceIdleness(final LongSupplier nowMs, final long idleTimeoutMs) {
    this.nowMs = nowMs;
    this.idleTimeoutMs = idleTimeoutMs;
  }

  /** Folds one (deduped) segment delta into {@code cell}'s running total and marks it changed. */
  public void merge(final Windowed<K> cell, final ACC delta) {
    merge(cell, delta, windowEnd(cell));
  }

  /**
   * Folds one (deduped) segment delta into {@code cell}'s running total and marks it changed,
   * attributing the delta to one implicit source — for callers whose input is single-source, where
   * the min-of-sources clock collapses to the plain max of the hints.
   */
  public void merge(final Windowed<K> cell, final ACC delta, final long eventTimeHint) {
    merge(cell, delta, eventTimeHint, DEFAULT_SOURCE);
  }

  /**
   * Folds one (deduped) segment delta into {@code cell}'s running total and marks it changed,
   * attributing it to {@code sourceId} for the min-of-sources stream-time clock (see the class
   * javadoc).
   *
   * <p>{@code eventTimeHint} is an upper bound on the event times the delta covers, driving the
   * source's slot of this operator's stream-time clock. For a finest-granularity cell that is its
   * own window end (the two-arg overload). A caller rolling finer deltas up into a <em>coarser</em>
   * window must pass the finer window's end instead: using the coarse window's end would leap
   * stream time a whole coarse window ahead on the tier's first delta, eroding every sibling cell's
   * grace by up to one window size and finalizing them early — late finer deltas would then be
   * dropped and the coarse tier would undercount relative to the finest.
   */
  public void merge(
      final Windowed<K> cell, final ACC delta, final long eventTimeHint, final int sourceId) {
    // Drop deltas for a window that already closed and was evicted: folding one would resurrect the
    // cell and the idempotent sink would overwrite its finalized value. Never silent — the
    // listener is how a lagging source's losses become visible. The dropped delta still registers
    // its source as live (and records its progress): a lagging source must keep gating the clock
    // so its still-open later windows are not closed early too. The guard is floored at the max
    // recovered window end: while the post-restart clock is still forming, a straggler for a
    // window finalized and evicted BEFORE the restart must not fold into a fresh accumulator and
    // overwrite the finalized row with a partial value — the guard the pre-restart clock provided.
    final long guardFloor = Math.max(clock, recoveredEventTime);
    if (guardFloor != Long.MIN_VALUE && windowEnd(cell) + windows.graceMs() <= guardFloor) {
      lateDropListener.onLateDrop(cell, eventTimeHint, guardFloor);
      observeSource(sourceId, eventTimeHint);
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
    observeSource(sourceId, eventTimeHint);
    advanceClock();
  }

  /** Records a delta from {@code sourceId}: its event-time progress and that it is alive now. */
  private void observeSource(final int sourceId, final long eventTimeHint) {
    if (eventTimeHint > sourceMaxEventTime.get(sourceId)) {
      sourceMaxEventTime.put(sourceId, eventTimeHint);
    }
    sourceLastSeenMs.put(sourceId, nowMs.getAsLong());
  }

  /**
   * Re-evaluates the stream-time clock: the min over the non-idle sources' max event times, clamped
   * monotonically non-decreasing (a source joining behind the clock, or idleness changing the min's
   * membership, must never regress the published watermark). With every known source idle the clock
   * holds. The recovery escape hatch lives here too: recovered-but-never-spoken state adopts the
   * max recovered window end once no source has spoken for a full idle timeout, so a
   * drained-and-gone upstream cannot stall finalization forever.
   */
  private void advanceClock() {
    final long now = nowMs.getAsLong();
    if (firstClockEvaluationMs == Long.MIN_VALUE) {
      firstClockEvaluationMs = now;
    }
    if (sourceMaxEventTime.isEmpty()) {
      if (recoveredEventTime != Long.MIN_VALUE && now - firstClockEvaluationMs >= idleTimeoutMs) {
        clock = Math.max(clock, recoveredEventTime);
      }
      return;
    }
    long min = Long.MAX_VALUE;
    boolean anyLive = false;
    final KeyIterator sources = sourceMaxEventTime.keySet().iterator();
    while (sources.hasNext()) {
      final long sourceId = sources.nextValue();
      if (now - sourceLastSeenMs.get(sourceId) >= idleTimeoutMs) {
        continue; // idle: excluded from the min until it speaks again
      }
      anyLive = true;
      min = Math.min(min, sourceMaxEventTime.get(sourceId));
    }
    if (anyLive) {
      clock = Math.max(clock, min);
    }
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
   * An inline cut on the owner thread: {@link #freeze()} the delta, persist it inside one
   * transaction, {@link #completeFrozen(boolean) complete}. Callers that overlap the persist with
   * processing drive the three steps themselves instead — freeze and complete on the owner thread,
   * {@link #persistFrozen()} inside the transaction the task supplies.
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
   * slot, installing fresh empty trackers so folding resumes immediately. The cut's deletes are
   * captured here too: only the frozen evicted cells whose durable row exists (written by a
   * completed cut and not yet deleted) — an evicted cell that was never durably written needs no
   * delete. The frozen delta is immutable data: later folds touch only the live accumulators and
   * trackers, never the frozen bytes, so no copy-on-write of live accumulators is needed.
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
    final Set<Windowed<K>> deletes = new HashSet<>();
    for (final Windowed<K> cell : delta.evicted()) {
      if (durablyWritten.contains(cell)) {
        deletes.add(cell);
      }
    }
    frozenCells = delta;
    frozenSerialized = serialized;
    frozenDeletes = deletes;
  }

  /**
   * IO thread, inside the caller's commit transaction: persists the frozen delta to the durable
   * cells — the at-freeze bytes for every frozen changed cell, a delete for every frozen evicted
   * cell a completed cut had written (an eviction with no durable row persists nothing). Touches
   * only the frozen slot and the cell store (which the owner thread itself only uses on this path
   * and at recovery), never the live working state — the owner keeps folding concurrently.
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
    for (final Windowed<K> cell : frozenDeletes) {
      cells.delete(cell);
    }
  }

  /**
   * Owner thread, once the transaction's outcome is known. Success: the frozen delta is durable —
   * drop it, remembering the rows the cut wrote and forgetting the ones it deleted (the
   * was-ever-persisted tracking moves only here, so a cut that froze but never durably completed
   * marks nothing as written). Failure: merge it back so the next freeze re-includes it — frozen
   * changed cells are re-marked changed and their bytes re-cached, frozen evicted cells re-marked
   * for deletion. The live state always wins; the frozen delta only fills gaps: a cell re-changed
   * since the freeze keeps its newer total and its pending (or already re-cached) re-serialization,
   * a cell evicted since the freeze stays evicted, and a cell re-created since the freeze is not
   * re-deleted.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void completeFrozen(final boolean success) {
    if (frozenCells == null) {
      throw new IllegalStateException("expected a frozen checkpoint delta to complete, but none");
    }
    if (success) {
      durablyWritten.addAll(frozenCells.changed());
      durablyWritten.removeAll(frozenDeletes);
    } else {
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
    frozenDeletes = null;
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
    // Idleness is re-evaluated here, not only on merges: a silent source stops gating the clock at
    // the next checkpoint even when no delta arrives to trigger the re-evaluation.
    advanceClock();
    if (clock == Long.MIN_VALUE) {
      return;
    }
    final long watermark = clock - windows.graceMs();
    // Only cells whose window has ended are candidates — a closed window (past the watermark)
    // finalizes unconditionally, an ended-but-in-grace one only once its accumulator has drained.
    open.evictDue(
        clock,
        (windowEnd, cell, value) -> {
          if (windowEnd > watermark && !drained.test(value)) {
            return false;
          }
          emitFinal(cell, value);
          finalizationListener.onFinal(cell, value);
          return true;
        });
    finalizationListener.onWatermark(watermark);
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
          durablyWritten.add(cell);
          // Not adopted as the clock: recovered cells only bound where the pre-restart clock could
          // have been. The escape hatch in advanceClock() adopts it if no source ever speaks.
          recoveredEventTime = Math.max(recoveredEventTime, windowEnd(cell));
        });
  }

  private long windowEnd(final Windowed<K> cell) {
    return cell.windowStart() + windows.sizeMs();
  }
}
