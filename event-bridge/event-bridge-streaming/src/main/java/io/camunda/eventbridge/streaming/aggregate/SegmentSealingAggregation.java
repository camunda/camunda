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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A combiner that pre-aggregates a source partition's stream into <em>immutable segment deltas</em>
 * instead of durable cumulative cells. It folds the records of the current open {@link Segments
 * segment} into an in-memory {@code (key, window) -> accumulator} buffer; when a record crosses
 * into a later segment it <em>seals</em> the open one — emitting each cell's accumulator (folded
 * from empty over just that segment) to the {@link SegmentSink} and clearing the buffer.
 *
 * <p>Because a sealed delta is a pure function of its segment's positions (and the base state
 * feeding the fold), re-folding after a crash reproduces the identical delta; downstream merges
 * each once, deduped by the {@code (sourcePartition, segment)} coordinate.
 *
 * <p><b>Durability.</b> Under the consistent-cut model (Model F) the owning task commits the
 * <em>full processed offset</em> together with all operator state in one transaction, so the open
 * segment's partial buffer must be checkpointed too — otherwise committing the full offset would
 * lose it. Constructed {@linkplain #SegmentSealingAggregation(int, AggregateFunction, KeySelector,
 * SourceCoordinate, ToLongFunction, Windows, Segments, SegmentSink, KeyValueStore, RecordValue,
 * RecordValue, TransactionRunner) with a state store}, {@link #checkpoint()} persists the open
 * {@code (window, key) -> accumulator} buffer plus the {@code (openSegment, sourcePartition)} meta,
 * and the buffer is restored on construction — so replay resumes from the committed offset onto the
 * matching open segment (no reconcile). Constructed {@linkplain #SegmentSealingAggregation(
 * AggregateFunction, KeySelector, SourceCoordinate, ToLongFunction, Windows, Segments, SegmentSink)
 * without a store}, it keeps no durable aggregation state — the buffer is ephemeral and rebuilt by
 * replay, so the owner must commit offsets only up to {@link #safeOffset()} (Model R).
 *
 * <p>The Model-F checkpoint is split so the durable write can run off the owner thread: {@link
 * #freeze()} serializes the open buffer into an immutable snapshot and folding resumes immediately;
 * {@link #persistFrozen()} writes the snapshot inside the transaction the task supplies; {@link
 * #completeFrozen(boolean)} settles the outcome. {@link #checkpoint()} composes the three
 * synchronously for callers without an asynchronous commit. All three are no-ops under Model R.
 *
 * @param <IN> the value type folded
 * @param <K> the base grouping key type
 * @param <ACC> the accumulator type
 */
public final class SegmentSealingAggregation<IN, K, ACC> implements Aggregation<IN> {

  /** No safe offset yet (nothing sealed); matches {@code Task.NO_OFFSET}. */
  public static final long NO_OFFSET = -1L;

  private static final long NO_SEGMENT = -1L;

  private final AggregateFunction<? super IN, ACC, ?> aggregate;
  private final KeySelector<? super IN, K> keySelector;
  private final SourceCoordinate<? super IN> coordinate;
  private final ToLongFunction<? super IN> eventTime;
  private final Windows windows;
  private final Segments segments;
  private final SegmentSink<K, ACC> sink;

  // Durable open-segment checkpoint (Model F). Null when the aggregation keeps no durable state.
  private final GroupedCellStore<K, ACC> cells;
  private final RecordValue<ACC> accCodec;
  private final TransactionRunner tx;
  private final Set<Windowed<K>> durablyWritten = new HashSet<>();

  // The outstanding frozen open-segment snapshot (null when none): every open cell serialized at
  // freeze time, the durable rows that must go (sealed away since the last successful cut), and
  // the segment meta. Owned by the freeze/complete pair on the owner thread; persistFrozen only
  // reads it.
  private FrozenSnapshot<K> frozen;

  private final WindowedCellState<K, ACC> open = new WindowedCellState<>();
  private long openSegment = NO_SEGMENT;
  private int sourcePartition = -1;

  /** Model R: no durable state; the open segment replays from {@link #safeOffset()}. */
  public SegmentSealingAggregation(
      final AggregateFunction<? super IN, ACC, ?> aggregate,
      final KeySelector<? super IN, K> keySelector,
      final SourceCoordinate<? super IN> coordinate,
      final ToLongFunction<? super IN> eventTime,
      final Windows windows,
      final Segments segments,
      final SegmentSink<K, ACC> sink) {
    this(
        aggregate,
        keySelector,
        coordinate,
        eventTime,
        windows,
        segments,
        sink,
        0,
        null,
        null,
        null,
        null);
  }

  /**
   * Model F: checkpoints the open segment to {@code openStore} (keyed by {@code group}, so several
   * aggregations may share one store) so committing the full offset never loses the open partial.
   */
  public SegmentSealingAggregation(
      final int group,
      final AggregateFunction<? super IN, ACC, ?> aggregate,
      final KeySelector<? super IN, K> keySelector,
      final SourceCoordinate<? super IN> coordinate,
      final ToLongFunction<? super IN> eventTime,
      final Windows windows,
      final Segments segments,
      final SegmentSink<K, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> openStore,
      final RecordValue<K> keyCodec,
      final RecordValue<ACC> accCodec,
      final TransactionRunner tx) {
    this(
        aggregate,
        keySelector,
        coordinate,
        eventTime,
        windows,
        segments,
        sink,
        group,
        openStore,
        keyCodec,
        accCodec,
        tx);
    recover();
  }

  private SegmentSealingAggregation(
      final AggregateFunction<? super IN, ACC, ?> aggregate,
      final KeySelector<? super IN, K> keySelector,
      final SourceCoordinate<? super IN> coordinate,
      final ToLongFunction<? super IN> eventTime,
      final Windows windows,
      final Segments segments,
      final SegmentSink<K, ACC> sink,
      final int group,
      final KeyValueStore<DbBytes, DbBytes> openStore,
      final RecordValue<K> keyCodec,
      final RecordValue<ACC> accCodec,
      final TransactionRunner tx) {
    this.aggregate = aggregate;
    this.keySelector = keySelector;
    this.coordinate = coordinate;
    this.eventTime = eventTime;
    this.windows = windows;
    this.segments = segments;
    this.sink = sink;
    cells = openStore == null ? null : new GroupedCellStore<>(group, openStore, keyCodec, accCodec);
    this.accCodec = accCodec;
    this.tx = tx;
  }

  @Override
  public void accept(final IN value) {
    final long segment = segments.index(coordinate.position(value));
    if (openSegment == NO_SEGMENT) {
      openSegment = segment;
      sourcePartition = coordinate.partition(value);
    } else if (segment > openSegment) {
      seal();
      openSegment = segment;
    }
    if (sourcePartition < 0) {
      // Restored mid-segment (open buffer recovered, first record not yet seen): the coordinate is
      // authoritative for the source partition.
      sourcePartition = coordinate.partition(value);
    }
    // Probe with a possibly-reusable view key; an owned copy is made only when a new cell is
    // inserted. On the hit path the map keeps its existing (owned) key — the probe is only
    // compared, never stored.
    final K probe = keySelector.probeKey(value);
    final Windowed<K> cell =
        new Windowed<>(probe, windows.windowStart(eventTime.applyAsLong(value)));
    final ACC current = open.get(cell);
    if (current == null) {
      open.put(
          new Windowed<>(keySelector.ownKey(probe), cell.windowStart()),
          aggregate.add(value, aggregate.createAccumulator()));
    } else {
      open.put(cell, aggregate.add(value, current));
    }
  }

  /**
   * The highest source position it is safe to commit under Model R: the last position before the
   * open segment, so a crash replays the open (unsealed) segment. {@link #NO_OFFSET} while the
   * first segment is still open (nothing sealed yet). Unused under Model F, which commits the full
   * offset and checkpoints the open segment.
   */
  public long safeOffset() {
    return openSegment == NO_SEGMENT ? NO_OFFSET : segments.startPosition(openSegment) - 1;
  }

  /**
   * Seals the open segment if the source has advanced fully past it — the {@code watermark} (the
   * highest processed source position) lies in a later segment, so no more records can land in the
   * open one. Without this, a stream that folds a record and then goes quiet (e.g. a rare incident
   * cell) would never cross a segment boundary, so that segment's cells would never seal into the
   * shuffle and never reach the serving store. Called by the owner as it commits, it makes those
   * sparse cells visible with bounded lag.
   *
   * <p>The caller feeds this its committed source offset. That is sound as long as the caller
   * dedups <em>producer</em> duplicates before the fold (each upstream event folds exactly once, by
   * its stable origin identity): the source topic is immutable, so its offsets are then a
   * deterministic identity for topic content — a crash-replay over the same offsets re-folds the
   * same values, re-seals a byte-identical delta for the same segment, and downstream drops the
   * re-emit by {@code (segment, chunk)}. The watermark is therefore a pure <em>liveness</em> signal
   * ("the source has consumed past this segment"), not an identity mechanism.
   */
  public void sealCompletedUpTo(final long watermark) {
    if (openSegment != NO_SEGMENT && segments.index(watermark) > openSegment) {
      seal();
      openSegment = NO_SEGMENT; // reopened by the next accepted record
    }
  }

  private void seal() {
    if (open.isEmpty()) {
      return;
    }
    open.forEachOpen((cell, acc) -> sink.emit(cell, sourcePartition, openSegment, acc));
    sink.flush();
    open.clear();
  }

  @Override
  public void flush() {
    // Freshness only: deliver already-sealed deltas. The open segment is not sealed here — a
    // partial segment is not deterministic.
    sink.flush();
  }

  /**
   * An inline cut on the owner thread: {@link #freeze()} the open buffer, persist it inside one
   * transaction, {@link #completeFrozen(boolean) complete}. Callers that overlap the persist with
   * processing drive the three steps themselves instead — freeze and complete on the owner thread,
   * {@link #persistFrozen()} inside the transaction the task supplies.
   */
  @Override
  public void checkpoint() {
    if (cells == null) {
      // Model R: no durable aggregation state; the open buffer is ephemeral and rebuilt by replay.
      return;
    }
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
   * Owner thread: snapshots the open buffer as immutable bytes. The open cells are not serialized
   * until they are persisted, so the freeze serializes them here — O(open buffer) codec work but no
   * store writes — rather than copy-on-write the live accumulators. Also captures the durable rows
   * to delete (written by the last successful cut but sealed away since) and the segment meta, so a
   * seal or fold after the freeze cannot leak into the frozen cut. No-op under Model R.
   *
   * @throws IllegalStateException if a frozen snapshot is already outstanding
   */
  public void freeze() {
    if (cells == null) {
      return;
    }
    if (frozen != null) {
      throw new IllegalStateException(
          "expected no outstanding frozen open-segment snapshot, but freeze() was called again"
              + " before completeFrozen()");
    }
    final Map<Windowed<K>, byte[]> snapshot = new HashMap<>();
    open.forEachOpen((cell, acc) -> snapshot.put(cell, accCodec.toBytes(acc)));
    final Set<Windowed<K>> stale = new HashSet<>();
    for (final Windowed<K> cell : durablyWritten) {
      if (!snapshot.containsKey(cell)) {
        stale.add(cell);
      }
    }
    frozen = new FrozenSnapshot<>(snapshot, stale, encodeMeta());
  }

  /**
   * IO thread, inside the caller's commit transaction: deletes the stale rows, then persists every
   * frozen cell's at-freeze bytes and the frozen meta. Touches only the frozen snapshot and the
   * cell store (which the owner thread itself only uses on this path and at recovery), never the
   * live buffer — the owner keeps folding, even sealing, concurrently. No-op under Model R.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void persistFrozen() {
    if (cells == null) {
      return;
    }
    if (frozen == null) {
      throw new IllegalStateException(
          "expected a frozen open-segment snapshot to persist," + " but none");
    }
    for (final Windowed<K> cell : frozen.staleCells()) {
      cells.delete(cell);
    }
    frozen.cells().forEach(cells::putSerialized);
    cells.putMeta(frozen.meta());
  }

  /**
   * Owner thread, once the transaction's outcome is known. Success: the frozen snapshot <em>is</em>
   * the durable state now — remember its rows so the next freeze can compute the stale set.
   * Failure: discard it; the snapshot is a full image (not an incremental delta), so the next
   * freeze re-captures everything against the unchanged durable rows and nothing is lost. No-op
   * under Model R.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void completeFrozen(final boolean success) {
    if (cells == null) {
      return;
    }
    if (frozen == null) {
      throw new IllegalStateException(
          "expected a frozen open-segment snapshot to complete," + " but none");
    }
    if (success) {
      durablyWritten.clear();
      durablyWritten.addAll(frozen.cells().keySet());
    }
    frozen = null;
  }

  @Override
  public void close() {
    // Do not seal the open segment; under Model F it is checkpointed, under Model R it replays.
    sink.flush();
  }

  private void recover() {
    cells.scan(
        (cell, acc) -> {
          open.put(cell, acc);
          durablyWritten.add(cell);
        },
        meta -> {
          final ByteBuffer buffer = ByteBuffer.wrap(meta);
          openSegment = buffer.getLong();
          sourcePartition = buffer.getInt();
        });
  }

  /** The group's meta row value: {@code openSegment ++ sourcePartition}, big-endian. */
  private byte[] encodeMeta() {
    final byte[] meta = new byte[Long.BYTES + Integer.BYTES];
    final UnsafeBuffer buffer = new UnsafeBuffer(meta);
    buffer.putLong(0, openSegment, ByteOrder.BIG_ENDIAN);
    buffer.putInt(Long.BYTES, sourcePartition, ByteOrder.BIG_ENDIAN);
    return meta;
  }

  /** One freeze's immutable snapshot of the open segment: cell bytes, rows to delete, meta. */
  private record FrozenSnapshot<K>(
      Map<Windowed<K>, byte[]> cells, Set<Windowed<K>> staleCells, byte[] meta) {}
}
