/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import io.camunda.analytics.streaming.state.api.KeyValueStore;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.analytics.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * The durable form of {@link MaterializedRollup}: the windowed aggregate lives in a RocksDB-backed
 * state store rather than on the heap, so total state is bounded by disk, not memory, and survives
 * a restart. The consumed source positions are persisted in the <em>same transaction</em> as the
 * changed cells, so state and offset never diverge — on restart the caller reads {@link
 * #consumedPositions()} and resumes the source from there (start-from-offset), which makes the
 * source-coordinate dedup belt-and-suspenders rather than load-bearing.
 *
 * <p>Flow per batch: facts fold into a small heap working set (bounded by the cells touched since
 * the last flush); {@link #flush()} merges that working set into the durable cells and advances the
 * persisted positions/watermark in one transaction, then upserts each changed cell's full value to
 * the idempotent {@link ResultSink}. Window finalization scans the durable cells and evicts the
 * closed ones, so the on-disk footprint is bounded by the open windows (retention).
 *
 * <p>Cells are keyed by {@code windowStart ++ codec(groupingKey)}; the accumulator is stored via
 * its codec. Offsets are keyed by source partition id; a reserved slot holds the event-time
 * watermark so finalization is correct across a restart.
 *
 * @param <F> the fact type
 * @param <K> the (pre-window) grouping key type
 * @param <ACC> the accumulator type
 */
public final class DurableMaterializedRollup<F, K, ACC> implements Rollup<F> {

  /** Reserved offset-store key (no real partition uses it) holding the event-time watermark. */
  private static final int WATERMARK_SLOT = -1;

  private final AggregateFunction<F, ACC, ?> aggregate;
  private final KeySelector<F, K> keySelector;
  private final ToLongFunction<F> eventTime;
  private final SourceCoordinate<F> coordinate;
  private final TumblingWindows windows;
  private final long allowedLatenessMs;
  private final ResultSink<Windowed<K>, ACC> sink;

  private final KeyValueStore<DbBytes, DbBytes> cellStore;
  private final KeyValueStore<DbInt, DbLong> offsetStore;
  private final Codec<K> keyCodec;
  private final Codec<ACC> accCodec;
  private final TransactionRunner tx;

  // Optional early-finalization predicate: a window is finalized as soon as its accumulator reports
  // it is "drained" (all its instances have reached a terminal state) once its own event-time
  // window
  // has passed — so a start cohort that receives a late completion/incident signal stays open until
  // it is genuinely complete, rather than being evicted on a time guess and then resurrected. The
  // time-based lateness remains as a backstop for cohorts that never drain (e.g. stuck instances).
  private final Predicate<ACC> drained;

  // Flyweights this rollup writes through (reads return the store's own flyweights).
  private final DbBytes cellKey = new DbBytes();
  private final DbBytes cellValue = new DbBytes();
  private final DbInt partitionKey = new DbInt();
  private final DbLong longValue = new DbLong();

  // Heap: the per-batch working set (bounded by cells touched per flush) plus small recovered
  // metadata. The authoritative full aggregate lives in the cell store.
  private final Map<Windowed<K>, ACC> pending = new HashMap<>();
  private final Map<Integer, Long> appliedPosition = new HashMap<>();
  private long maxEventTime = Long.MIN_VALUE;

  public DurableMaterializedRollup(
      final AggregateFunction<F, ACC, ?> aggregate,
      final KeySelector<F, K> keySelector,
      final ToLongFunction<F> eventTime,
      final SourceCoordinate<F> coordinate,
      final TumblingWindows windows,
      final long allowedLatenessMs,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final KeyValueStore<DbInt, DbLong> offsetStore,
      final Codec<K> keyCodec,
      final Codec<ACC> accCodec,
      final TransactionRunner tx) {
    this(
        aggregate,
        keySelector,
        eventTime,
        coordinate,
        windows,
        allowedLatenessMs,
        sink,
        cellStore,
        offsetStore,
        keyCodec,
        accCodec,
        tx,
        acc -> false); // no early drain — pure time-based retention
  }

  public DurableMaterializedRollup(
      final AggregateFunction<F, ACC, ?> aggregate,
      final KeySelector<F, K> keySelector,
      final ToLongFunction<F> eventTime,
      final SourceCoordinate<F> coordinate,
      final TumblingWindows windows,
      final long allowedLatenessMs,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final KeyValueStore<DbInt, DbLong> offsetStore,
      final Codec<K> keyCodec,
      final Codec<ACC> accCodec,
      final TransactionRunner tx,
      final Predicate<ACC> drained) {
    this.aggregate = aggregate;
    this.keySelector = keySelector;
    this.eventTime = eventTime;
    this.coordinate = coordinate;
    this.windows = windows;
    this.allowedLatenessMs = allowedLatenessMs;
    this.sink = sink;
    this.cellStore = cellStore;
    this.offsetStore = offsetStore;
    this.keyCodec = keyCodec;
    this.accCodec = accCodec;
    this.tx = tx;
    this.drained = drained;
    recover();
  }

  /** The persisted next-fetch position per source partition, for start-from-offset on restart. */
  public Map<Integer, Long> consumedPositions() {
    final Map<Integer, Long> next = new HashMap<>();
    appliedPosition.forEach((partition, applied) -> next.put(partition, applied + 1));
    return next;
  }

  @Override
  public void accept(final F fact) {
    final int partition = coordinate.partition(fact);
    final long position = coordinate.position(fact);
    final Long applied = appliedPosition.get(partition);
    if (applied != null && position <= applied) {
      return; // already folded (durable watermark) — replay/redelivery
    }
    appliedPosition.put(partition, position);

    final long timestamp = eventTime.applyAsLong(fact);
    final long windowStart = windows.windowStart(timestamp);
    // Drop facts whose window has already closed (its cell has been, or is about to be, finalized
    // and evicted). Folding them would resurrect an evicted cell from an empty accumulator, and the
    // idempotent full-value sink would then overwrite the finalized row with that partial —
    // silently
    // wiping counts (e.g. a late completion resetting a start cohort's "started" to 0). The dedup
    // position is still advanced so the source offset progresses.
    if (maxEventTime != Long.MIN_VALUE
        && windowStart + windows.sizeMs() + allowedLatenessMs <= maxEventTime) {
      return;
    }
    final Windowed<K> key = new Windowed<>(keySelector.getKey(fact), windowStart);
    pending.merge(key, aggregate.add(fact, aggregate.createAccumulator()), aggregate::merge);
    maxEventTime = Math.max(maxEventTime, timestamp);
  }

  @Override
  public void flush() {
    if (!pending.isEmpty()) {
      final Map<Windowed<K>, ACC> merged = new HashMap<>();
      tx.runInTransaction(
          () -> {
            for (final Map.Entry<Windowed<K>, ACC> entry : pending.entrySet()) {
              final ACC full = mergeIntoDurableCell(entry.getKey(), entry.getValue());
              merged.put(entry.getKey(), full);
            }
            persistPositions();
          });
      // After the durable commit, converge the serving view (idempotent full-value upsert).
      merged.forEach(sink::upsert);
      pending.clear();
    } else {
      tx.runInTransaction(this::persistPositions);
    }
    finalizeClosedWindows();
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    maxEventTime = Math.max(maxEventTime, streamTimeMs);
    finalizeClosedWindows();
  }

  @Override
  public void close() {
    flush();
  }

  /** Loads the applied positions and the event-time watermark from the durable offset store. */
  private void recover() {
    offsetStore.forEach(
        (partition, position) -> {
          if (partition.getValue() == WATERMARK_SLOT) {
            maxEventTime = position.getValue();
          } else {
            appliedPosition.put(partition.getValue(), position.getValue());
          }
        });
  }

  /**
   * Reads the existing cell, merges the batch partial into it, writes it back; returns the full.
   */
  private ACC mergeIntoDurableCell(final Windowed<K> windowed, final ACC partial) {
    cellKey.wrapBytes(encodeKey(windowed));
    final ACC base =
        cellStore.get(cellKey).map(stored -> accCodec.decode(stored.getBytes())).orElse(null);
    final ACC full = base == null ? partial : aggregate.merge(base, partial);
    cellKey.wrapBytes(encodeKey(windowed));
    cellValue.wrapBytes(accCodec.encode(full));
    cellStore.put(cellKey, cellValue);
    return full;
  }

  private void persistPositions() {
    appliedPosition.forEach(
        (partition, position) -> {
          partitionKey.wrapInt(partition);
          longValue.wrapLong(position);
          offsetStore.put(partitionKey, longValue);
        });
    partitionKey.wrapInt(WATERMARK_SLOT);
    longValue.wrapLong(maxEventTime);
    offsetStore.put(partitionKey, longValue);
  }

  /** Emit a final value for and evict every durable cell whose end + lateness is below the mark. */
  private void finalizeClosedWindows() {
    if (maxEventTime == Long.MIN_VALUE) {
      return;
    }
    final long watermark = maxEventTime - allowedLatenessMs;
    final Map<Windowed<K>, ACC> closed = new HashMap<>();
    cellStore.forEach(
        (key, value) -> {
          final Windowed<K> windowed = decodeKey(key.getBytes());
          final long windowEnd = windowed.windowStart() + windows.sizeMs();
          final ACC acc = accCodec.decode(value.getBytes());
          // Evict when the cohort has drained (complete, and its activation window has passed so no
          // more instances can join it), or — as a backstop for windows that never drain — once the
          // time-based lateness has elapsed.
          if ((windowEnd <= maxEventTime && drained.test(acc)) || windowEnd <= watermark) {
            closed.put(windowed, acc);
          }
        });
    for (final Map.Entry<Windowed<K>, ACC> entry : closed.entrySet()) {
      sink.upsert(entry.getKey(), entry.getValue()); // final value
      cellKey.wrapBytes(encodeKey(entry.getKey()));
      cellStore.delete(cellKey); // evict — bounds on-disk state (retention)
    }
  }

  private byte[] encodeKey(final Windowed<K> windowed) {
    final byte[] keyBytes = keyCodec.encode(windowed.key());
    return ByteBuffer.allocate(Long.BYTES + keyBytes.length)
        .putLong(windowed.windowStart())
        .put(keyBytes)
        .array();
  }

  private Windowed<K> decodeKey(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    final long windowStart = buffer.getLong();
    final byte[] keyBytes = new byte[buffer.remaining()];
    buffer.get(keyBytes);
    return new Windowed<>(keyCodec.decode(keyBytes), windowStart);
  }
}
