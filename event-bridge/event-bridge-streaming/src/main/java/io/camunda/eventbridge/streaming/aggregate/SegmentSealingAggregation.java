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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.ToLongFunction;

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
  private final int group;
  private final KeyValueStore<DbBytes, DbBytes> openStore;
  private final RecordValue<K> keyCodec;
  private final RecordValue<ACC> accCodec;
  private final TransactionRunner tx;
  private final Set<Windowed<K>> durablyWritten = new HashSet<>();
  private final DbBytes storeKey = new DbBytes();
  private final DbBytes storeValue = new DbBytes();
  private final DbBytes groupPrefix = new DbBytes();

  private final Map<Windowed<K>, ACC> open = new HashMap<>();
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
    this.group = group;
    this.openStore = openStore;
    this.keyCodec = keyCodec;
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
    final Windowed<K> cell =
        new Windowed<>(
            keySelector.getKey(value), windows.windowStart(eventTime.applyAsLong(value)));
    final ACC current = open.get(cell);
    open.put(cell, aggregate.add(value, current == null ? aggregate.createAccumulator() : current));
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
    for (final Entry<Windowed<K>, ACC> cell : open.entrySet()) {
      sink.emit(cell.getKey(), sourcePartition, openSegment, cell.getValue());
    }
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
    if (openStore == null) {
      // Model R: no durable aggregation state; the open buffer is ephemeral and rebuilt by replay.
      return;
    }
    // Model F: persist the open buffer + meta so the full committed offset lands on matching state.
    tx.runInTransaction(
        () -> {
          for (final Windowed<K> cell : durablyWritten) {
            if (!open.containsKey(cell)) {
              openStore.delete(cellKey(cell));
            }
          }
          durablyWritten.clear();
          for (final Entry<Windowed<K>, ACC> cell : open.entrySet()) {
            storeValue.wrapBytes(accCodec.toBytes(cell.getValue()));
            openStore.put(cellKey(cell.getKey()), storeValue);
            durablyWritten.add(cell.getKey());
          }
          writeMeta();
        });
  }

  @Override
  public void close() {
    // Do not seal the open segment; under Model F it is checkpointed, under Model R it replays.
    sink.flush();
  }

  private void recover() {
    groupPrefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(group).array());
    openStore.prefixScan(
        groupPrefix,
        (key, value) -> {
          final byte[] keyBytes = key.getBytes();
          if (keyBytes.length == Integer.BYTES) {
            final ByteBuffer meta = ByteBuffer.wrap(value.getBytes());
            openSegment = meta.getLong();
            sourcePartition = meta.getInt();
          } else {
            final Windowed<K> cell = decodeCellKey(keyBytes);
            open.put(cell, accCodec.fromBytes(value.getBytes()));
            durablyWritten.add(cell);
          }
        });
  }

  private void writeMeta() {
    // Meta key is the bare group (4 bytes) — shorter than any cell key (>= group + windowStart), so
    // it never collides with a cell in the shared, group-prefixed store.
    storeKey.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(group).array());
    storeValue.wrapBytes(
        ByteBuffer.allocate(Long.BYTES + Integer.BYTES)
            .putLong(openSegment)
            .putInt(sourcePartition)
            .array());
    openStore.put(storeKey, storeValue);
  }

  /** Cell key: {@code group ++ windowStart ++ codec(key)}. */
  private DbBytes cellKey(final Windowed<K> cell) {
    final byte[] keyBytes = keyCodec.toBytes(cell.key());
    storeKey.wrapBytes(
        ByteBuffer.allocate(Integer.BYTES + Long.BYTES + keyBytes.length)
            .putInt(group)
            .putLong(cell.windowStart())
            .put(keyBytes)
            .array());
    return storeKey;
  }

  private Windowed<K> decodeCellKey(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.getInt(); // group — already scoped by the prefix scan
    final long windowStart = buffer.getLong();
    final byte[] keyBytes = new byte[buffer.remaining()];
    buffer.get(keyBytes);
    return new Windowed<>(keyCodec.fromBytes(keyBytes), windowStart);
  }
}
