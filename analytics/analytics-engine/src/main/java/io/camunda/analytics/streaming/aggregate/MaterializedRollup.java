/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.analytics.streaming.window.Windowed;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Holds the authoritative windowed aggregate locally and writes <em>full current values</em> to an
 * idempotent {@link ResultSink} — the sink-agnostic, exactly-once shape: local state is the source
 * of truth, the sink is a materialized view that converges.
 *
 * <p>Three mechanisms make it correct under at-least-once delivery:
 *
 * <ul>
 *   <li><b>Source-coordinate dedup</b> — a per-partition applied-position high-watermark drops any
 *       fact at or below it, so replay never folds a fact twice into the local aggregate.
 *   <li><b>Idempotent emit</b> — each changed cell is upserted as its whole value by a
 *       deterministic key, so a re-emit overwrites rather than double-counts.
 *   <li><b>Finalization</b> — once the event-time watermark passes {@code windowEnd + lateness} a
 *       cell is emitted a final time and <b>evicted</b>, bounding state (this is the retention).
 * </ul>
 *
 * <p>This increment keeps the aggregate on the heap; swapping it for a RocksDB-backed store (with
 * the watermark persisted alongside) makes it durable across restarts.
 *
 * @param <F> the fact type
 * @param <K> the (pre-window) grouping key type
 * @param <ACC> the accumulator type
 */
public final class MaterializedRollup<F, K, ACC> implements Rollup<F> {

  private final AggregateFunction<F, ACC, ?> aggregate;
  private final KeySelector<F, K> keySelector;
  private final ToLongFunction<F> eventTime;
  private final SourceCoordinate<F> coordinate;
  private final TumblingWindows windows;
  private final long allowedLatenessMs;
  private final ResultSink<Windowed<K>, ACC> sink;

  private final Map<Windowed<K>, ACC> cells = new HashMap<>();
  private final Set<Windowed<K>> dirty = new HashSet<>();
  private final Map<Integer, Long> appliedPosition = new HashMap<>();
  private long maxEventTime = Long.MIN_VALUE;

  public MaterializedRollup(
      final AggregateFunction<F, ACC, ?> aggregate,
      final KeySelector<F, K> keySelector,
      final ToLongFunction<F> eventTime,
      final SourceCoordinate<F> coordinate,
      final TumblingWindows windows,
      final long allowedLatenessMs,
      final ResultSink<Windowed<K>, ACC> sink) {
    this.aggregate = aggregate;
    this.keySelector = keySelector;
    this.eventTime = eventTime;
    this.coordinate = coordinate;
    this.windows = windows;
    this.allowedLatenessMs = allowedLatenessMs;
    this.sink = sink;
  }

  @Override
  public void accept(final F fact) {
    // dedup by source coordinate — drop anything already folded (replay / redelivery)
    final int partition = coordinate.partition(fact);
    final long position = coordinate.position(fact);
    final Long applied = appliedPosition.get(partition);
    if (applied != null && position <= applied) {
      return;
    }
    appliedPosition.put(partition, position);

    final long timestamp = eventTime.applyAsLong(fact);
    final Windowed<K> key =
        new Windowed<>(keySelector.getKey(fact), windows.windowStart(timestamp));
    cells.merge(key, aggregate.add(fact, aggregate.createAccumulator()), aggregate::merge);
    dirty.add(key);
    maxEventTime = Math.max(maxEventTime, timestamp);
  }

  /** Wall-clock tick: write the changed cells (full value) and finalize any closed windows. */
  @Override
  public void flush() {
    for (final Windowed<K> key : dirty) {
      sink.upsert(key, cells.get(key));
    }
    dirty.clear();
    finalizeClosedWindows();
  }

  /** Event-time progress can close windows even when no new facts touched them. */
  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    maxEventTime = Math.max(maxEventTime, streamTimeMs);
    finalizeClosedWindows();
  }

  @Override
  public void close() {
    flush();
  }

  /** Number of windows currently held in local state (drops as windows finalize and evict). */
  public int openWindowCount() {
    return cells.size();
  }

  /** Emit a final value for and evict every window whose end + lateness is below the watermark. */
  private void finalizeClosedWindows() {
    if (maxEventTime == Long.MIN_VALUE) {
      return;
    }
    final long watermark = maxEventTime - allowedLatenessMs;
    final Iterator<Map.Entry<Windowed<K>, ACC>> it = cells.entrySet().iterator();
    while (it.hasNext()) {
      final Map.Entry<Windowed<K>, ACC> entry = it.next();
      final long windowEnd = entry.getKey().windowStart() + windows.sizeMs();
      if (windowEnd <= watermark) {
        sink.upsert(entry.getKey(), entry.getValue()); // final value
        it.remove(); // evict — bounds state (retention)
        dirty.remove(entry.getKey());
      }
    }
  }
}
