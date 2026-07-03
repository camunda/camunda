/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
 * each once, deduped by the {@code (sourcePartition, segment)} coordinate. This keeps <b>no durable
 * aggregation state</b> here — the buffer is ephemeral and rebuilt by replay — so recovery rests on
 * the source log alone. The trade-off is latency: a delta is emitted only when its segment seals,
 * so the open segment lingers until a later record arrives (segment fill time, tuned by the
 * stride). The open segment is deliberately <em>not</em> sealed on {@link #flush()}/{@link
 * #close()} — a partial segment is not deterministic — so the owner must commit offsets only up to
 * {@link #safeOffset()}.
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

  private final Map<Windowed<K>, ACC> open = new HashMap<>();
  private long openSegment = NO_SEGMENT;
  private int sourcePartition = -1;

  public SegmentSealingAggregation(
      final AggregateFunction<? super IN, ACC, ?> aggregate,
      final KeySelector<? super IN, K> keySelector,
      final SourceCoordinate<? super IN> coordinate,
      final ToLongFunction<? super IN> eventTime,
      final Windows windows,
      final Segments segments,
      final SegmentSink<K, ACC> sink) {
    this.aggregate = aggregate;
    this.keySelector = keySelector;
    this.coordinate = coordinate;
    this.eventTime = eventTime;
    this.windows = windows;
    this.segments = segments;
    this.sink = sink;
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
    final Windowed<K> cell =
        new Windowed<>(
            keySelector.getKey(value), windows.windowStart(eventTime.applyAsLong(value)));
    final ACC current = open.get(cell);
    open.put(cell, aggregate.add(value, current == null ? aggregate.createAccumulator() : current));
  }

  /**
   * The highest source position it is safe to commit: the last position before the open segment, so
   * a crash replays the open (unsealed) segment. {@link #NO_OFFSET} while the first segment is
   * still open (nothing sealed yet).
   */
  public long safeOffset() {
    return openSegment == NO_SEGMENT ? NO_OFFSET : segments.startPosition(openSegment) - 1;
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
    // No durable aggregation state: the open segment's buffer is ephemeral and rebuilt by replay.
  }

  @Override
  public void close() {
    // Do not seal the open segment; it replays from safeOffset on restart.
    sink.flush();
  }
}
