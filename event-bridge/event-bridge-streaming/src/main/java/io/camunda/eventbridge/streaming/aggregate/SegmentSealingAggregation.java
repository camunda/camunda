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
import java.util.HashSet;
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
 * <p><b>Durability.</b> Under the consistent-cut model (Model F) the runtime commits the <em>full
 * processed offset</em> together with all operator state in one transaction, so the open segment's
 * partial buffer must be checkpointed too — otherwise committing the full offset would lose it.
 * Constructed {@linkplain #SegmentSealingAggregation(int, AggregateFunction, KeySelector,
 * SourceCoordinate, ToLongFunction, Windows, Segments, SegmentSink, KeyValueStore, RecordValue,
 * RecordValue, TransactionRunner) with a state store}, {@link #checkpoint()} persists the open
 * {@code (window, key) -> accumulator} buffer plus the {@code (openSegment, sourcePartition)} meta,
 * and the buffer is restored on construction — so replay resumes from the committed offset onto the
 * matching open segment (no reconcile). Constructed {@linkplain #SegmentSealingAggregation(
 * AggregateFunction, KeySelector, SourceCoordinate, ToLongFunction, Windows, Segments, SegmentSink)
 * without a store}, it keeps no durable aggregation state — the buffer is ephemeral and rebuilt by
 * replay, so the owner must commit offsets only up to {@link #safeOffset()} (Model R).
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
  private final TransactionRunner tx;
  private final Set<Windowed<K>> durablyWritten = new HashSet<>();
  // Reused meta-value scratch: {@code openSegment(long) ++ sourcePartition(int)}, big-endian.
  private final byte[] metaValue = new byte[Long.BYTES + Integer.BYTES];
  private final UnsafeBuffer metaBuffer = new UnsafeBuffer(metaValue);

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

  @Override
  public void checkpoint() {
    if (cells == null) {
      // Model R: no durable aggregation state; the open buffer is ephemeral and rebuilt by replay.
      return;
    }
    // Model F: persist the open buffer + meta so the full committed offset lands on matching state.
    tx.runInTransaction(
        () -> {
          for (final Windowed<K> cell : durablyWritten) {
            if (!open.contains(cell)) {
              cells.delete(cell);
            }
          }
          durablyWritten.clear();
          open.forEachOpen(
              (cell, acc) -> {
                cells.put(cell, acc);
                durablyWritten.add(cell);
              });
          writeMeta();
        });
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

  /** The group's meta row: {@code openSegment ++ sourcePartition}, under the bare-group key. */
  private void writeMeta() {
    metaBuffer.putLong(0, openSegment, ByteOrder.BIG_ENDIAN);
    metaBuffer.putInt(Long.BYTES, sourcePartition, ByteOrder.BIG_ENDIAN);
    cells.putMeta(metaValue);
  }
}
