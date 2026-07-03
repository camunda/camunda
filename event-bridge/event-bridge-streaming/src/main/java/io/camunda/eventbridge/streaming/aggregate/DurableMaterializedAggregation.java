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
import io.camunda.zeebe.db.impl.DbLong;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * A windowed aggregation whose open-window accumulators live on the heap (the authoritative working
 * set, a write-back cache) and are periodically checkpointed to a RocksDB-backed state store — a
 * write-back record-cache model. It replaces a write-through design (get+merge+put every cell every
 * batch, plus a full column-family scan to finalize) whose per-batch cost was the throughput
 * ceiling.
 *
 * <p>Three clocks drive it, decoupled on purpose:
 *
 * <ul>
 *   <li>{@link #accept} folds each value into the heap cell and advances the event-time watermark —
 *       no I/O.
 *   <li>{@link #flush()} (per batch) converges the serving {@link ResultSink} for the cells changed
 *       since the last flush — dashboard freshness, but nothing is made durable.
 *   <li>{@link #checkpoint()} (per commit interval) finalizes closed windows, then writes the cells
 *       changed (and deletes the ones evicted) since the last checkpoint together with the consumed
 *       source positions in <em>one</em> transaction. Because it runs far less often than a batch,
 *       many updates to the same cell coalesce into a single durable write.
 * </ul>
 *
 * <p>Correctness under at-least-once delivery is unchanged from the write-through design: a
 * per-partition applied-position high-watermark drops replayed values ({@link #consumedPositions()}
 * is persisted alongside the cells, so state and offset share one atomic cut), each changed cell is
 * upserted as its full value by a deterministic key (idempotent re-emit), and finalization emits a
 * final value and evicts the cell once its window closes. A crash between checkpoints rolls the
 * heap-derived durable state and the offset back to the last checkpoint together; on restart the
 * cells are reloaded into the heap and the source resumes from the checkpointed position.
 *
 * <p>The heap holds one accumulator per open window (bounded by cardinality × open windows), the
 * same footprint the reference engines keep in their state cache; finalized windows are evicted, so
 * on-disk and in-heap state track the open windows (retention).
 *
 * <p>Many aggregations share the <em>same</em> cell and offset stores (one RocksDB per stage):
 * every durable key is prefixed with a stable {@code aggregationId}, so a aggregation reads/evicts
 * only its own cells (via {@code prefixScan(aggregationId)}) and a new metric or user-created
 * dataset is a new {@code aggregationId}, not a new column family.
 *
 * @param <F> the value type
 * @param <K> the (pre-window) grouping key type
 * @param <ACC> the accumulator type
 */
public final class DurableMaterializedAggregation<F, K, ACC> implements Aggregation<F> {

  /** Reserved offset-store partition slot (no real partition uses it) holding the watermark. */
  private static final int WATERMARK_SLOT = -1;

  private final int aggregationId;
  private final AggregateFunction<F, ACC, ?> aggregate;
  private final KeySelector<F, K> keySelector;
  private final ToLongFunction<F> eventTime;
  private final SourceCoordinate<F> coordinate;
  private final Windows windows;
  private final ResultSink<Windowed<K>, ACC> sink;

  private final KeyValueStore<DbBytes, DbBytes> cellStore;
  private final KeyValueStore<DbBytes, DbLong> offsetStore;
  private final RecordValue<K> keyValue;
  private final RecordValue<ACC> accValue;
  private final TransactionRunner tx;

  // Optional early-finalization predicate: a window is finalized as soon as its accumulator reports
  // it is "drained" (all its instances have reached a terminal state) once its own event-time
  // window
  // has passed — so a start cohort that receives a late completion/incident signal stays open until
  // it is genuinely complete, rather than being evicted on a time guess and then resurrected. The
  // time-based lateness remains as a backstop for cohorts that never drain (e.g. stuck instances).
  private final Predicate<ACC> drained;

  // Flyweights this aggregation writes through (reads return the store's own flyweights).
  private final DbBytes cellKey = new DbBytes();
  private final DbBytes cellValue = new DbBytes();
  private final DbBytes offsetKey = new DbBytes();
  private final DbBytes aggregationPrefix = new DbBytes();
  private final DbLong longValue = new DbLong();

  // Heap: the authoritative open-window accumulators (the record cache), plus the change/eviction
  // sets that make flush and checkpoint touch only what moved, and the recovered offset metadata.
  private final Map<Windowed<K>, ACC> cells = new HashMap<>();
  private final Set<Windowed<K>> changedSinceFlush = new HashSet<>();
  private final Set<Windowed<K>> changedSinceCheckpoint = new HashSet<>();
  private final Set<Windowed<K>> evictedSinceCheckpoint = new HashSet<>();
  private final Map<Integer, Long> appliedPosition = new HashMap<>();
  private long maxEventTime = Long.MIN_VALUE;

  public DurableMaterializedAggregation(
      final int aggregationId,
      final AggregateFunction<F, ACC, ?> aggregate,
      final KeySelector<F, K> keySelector,
      final ToLongFunction<F> eventTime,
      final SourceCoordinate<F> coordinate,
      final Windows windows,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final KeyValueStore<DbBytes, DbLong> offsetStore,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final TransactionRunner tx) {
    this(
        aggregationId,
        aggregate,
        keySelector,
        eventTime,
        coordinate,
        windows,
        sink,
        cellStore,
        offsetStore,
        keyValue,
        accValue,
        tx,
        acc -> false); // no early drain — pure time-based retention
  }

  public DurableMaterializedAggregation(
      final int aggregationId,
      final AggregateFunction<F, ACC, ?> aggregate,
      final KeySelector<F, K> keySelector,
      final ToLongFunction<F> eventTime,
      final SourceCoordinate<F> coordinate,
      final Windows windows,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> cellStore,
      final KeyValueStore<DbBytes, DbLong> offsetStore,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final TransactionRunner tx,
      final Predicate<ACC> drained) {
    this.aggregationId = aggregationId;
    this.aggregate = aggregate;
    this.keySelector = keySelector;
    this.eventTime = eventTime;
    this.coordinate = coordinate;
    this.windows = windows;
    this.sink = sink;
    this.cellStore = cellStore;
    this.offsetStore = offsetStore;
    this.keyValue = keyValue;
    this.accValue = accValue;
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
  public void accept(final F value) {
    final int partition = coordinate.partition(value);
    final long position = coordinate.position(value);
    final Long applied = appliedPosition.get(partition);
    if (applied != null && position <= applied) {
      return; // already folded (durable watermark) — replay/redelivery
    }
    appliedPosition.put(partition, position);

    final long timestamp = eventTime.applyAsLong(value);
    final long windowStart = windows.windowStart(timestamp);
    // Drop values whose window has already closed (its cell has been, or is about to be, finalized
    // and evicted). Folding them would resurrect an evicted cell from an empty accumulator, and the
    // idempotent full-value sink would then overwrite the finalized row with that partial —
    // silently
    // wiping counts (e.g. a late completion resetting a start cohort's "started" to 0). The dedup
    // position is still advanced so the source offset progresses.
    if (maxEventTime != Long.MIN_VALUE
        && windowStart + windows.sizeMs() + windows.graceMs() <= maxEventTime) {
      return;
    }
    final Windowed<K> key = new Windowed<>(keySelector.getKey(value), windowStart);
    cells.merge(key, aggregate.add(value, aggregate.createAccumulator()), aggregate::merge);
    changedSinceFlush.add(key);
    changedSinceCheckpoint.add(key);
    evictedSinceCheckpoint.remove(key); // a re-touched cell is live again, not to be deleted
    maxEventTime = Math.max(maxEventTime, timestamp);
  }

  /** Wall-clock tick: converge the serving view for the cells changed since the last flush. */
  @Override
  public void flush() {
    for (final Windowed<K> key : changedSinceFlush) {
      sink.upsert(key, cells.get(key));
    }
    changedSinceFlush.clear();
  }

  /**
   * Commit-interval tick: finalize closed windows, then persist the cells changed and evicted since
   * the last checkpoint together with the consumed positions in one transaction — one atomic cut of
   * {state, offset} so a restart resumes without replay or divergence.
   */
  @Override
  public void checkpoint() {
    finalizeClosedWindows();
    tx.runInTransaction(
        () -> {
          for (final Windowed<K> key : changedSinceCheckpoint) {
            writeDurableCell(key, cells.get(key));
          }
          for (final Windowed<K> key : evictedSinceCheckpoint) {
            deleteDurableCell(key);
          }
          persistPositions();
        });
    changedSinceCheckpoint.clear();
    evictedSinceCheckpoint.clear();
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    maxEventTime = Math.max(maxEventTime, streamTimeMs);
    finalizeClosedWindows();
  }

  @Override
  public void close() {
    flush();
    checkpoint();
  }

  /**
   * Loads this aggregation's applied positions, watermark, and durable cells into the heap,
   * scanning only its own {@code aggregationId} prefix within the shared cell/offset stores.
   */
  private void recover() {
    aggregationPrefix.wrapBytes(aggregationIdPrefix());
    offsetStore.prefixScan(
        aggregationPrefix,
        (key, position) -> {
          final int partition = partitionOf(key.getBytes());
          if (partition == WATERMARK_SLOT) {
            maxEventTime = position.getValue();
          } else {
            appliedPosition.put(partition, position.getValue());
          }
        });
    cellStore.prefixScan(
        aggregationPrefix,
        (key, value) -> cells.put(decodeKey(key.getBytes()), accValue.fromBytes(value.getBytes())));
  }

  private void writeDurableCell(final Windowed<K> windowed, final ACC acc) {
    cellKey.wrapBytes(encodeKey(windowed));
    cellValue.wrapBytes(accValue.toBytes(acc));
    cellStore.put(cellKey, cellValue);
  }

  private void deleteDurableCell(final Windowed<K> windowed) {
    cellKey.wrapBytes(encodeKey(windowed));
    cellStore.delete(cellKey);
  }

  private void persistPositions() {
    appliedPosition.forEach(
        (partition, position) -> {
          offsetKey.wrapBytes(encodeOffsetKey(partition));
          longValue.wrapLong(position);
          offsetStore.put(offsetKey, longValue);
        });
    offsetKey.wrapBytes(encodeOffsetKey(WATERMARK_SLOT));
    longValue.wrapLong(maxEventTime);
    offsetStore.put(offsetKey, longValue);
  }

  /**
   * Emit a final value for and evict from the heap every open cell whose window has closed; the
   * eviction is applied to the durable store at the next checkpoint.
   */
  private void finalizeClosedWindows() {
    if (maxEventTime == Long.MIN_VALUE) {
      return;
    }
    final long watermark = maxEventTime - windows.graceMs();
    final Iterator<Map.Entry<Windowed<K>, ACC>> it = cells.entrySet().iterator();
    while (it.hasNext()) {
      final Map.Entry<Windowed<K>, ACC> entry = it.next();
      final Windowed<K> key = entry.getKey();
      final long windowEnd = key.windowStart() + windows.sizeMs();
      final ACC acc = entry.getValue();
      // Evict when the cohort has drained (complete, and its activation window has passed so no
      // more
      // instances can join it), or — as a backstop for windows that never drain — once the
      // time-based lateness has elapsed.
      if ((windowEnd <= maxEventTime && drained.test(acc)) || windowEnd <= watermark) {
        sink.upsert(key, acc); // final value
        it.remove(); // evict from the heap working set
        changedSinceFlush.remove(key);
        changedSinceCheckpoint.remove(key);
        evictedSinceCheckpoint.add(key); // delete the durable cell at the next checkpoint
      }
    }
  }

  /**
   * The 4-byte big-endian {@code aggregationId} prefix every durable key of this aggregation
   * carries.
   */
  private byte[] aggregationIdPrefix() {
    return ByteBuffer.allocate(Integer.BYTES).putInt(aggregationId).array();
  }

  /** Cell key: {@code aggregationId ++ windowStart ++ codec(groupingKey)}. */
  private byte[] encodeKey(final Windowed<K> windowed) {
    final byte[] keyBytes = keyValue.toBytes(windowed.key());
    return ByteBuffer.allocate(Integer.BYTES + Long.BYTES + keyBytes.length)
        .putInt(aggregationId)
        .putLong(windowed.windowStart())
        .put(keyBytes)
        .array();
  }

  private Windowed<K> decodeKey(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.getInt(); // aggregationId prefix — already scoped by the prefix scan
    final long windowStart = buffer.getLong();
    final byte[] keyBytes = new byte[buffer.remaining()];
    buffer.get(keyBytes);
    return new Windowed<>(keyValue.fromBytes(keyBytes), windowStart);
  }

  /**
   * Offset key: {@code aggregationId ++ partitionId} (the watermark uses {@link #WATERMARK_SLOT}).
   */
  private byte[] encodeOffsetKey(final int partition) {
    return ByteBuffer.allocate(Integer.BYTES + Integer.BYTES)
        .putInt(aggregationId)
        .putInt(partition)
        .array();
  }

  private int partitionOf(final byte[] offsetKey) {
    return ByteBuffer.wrap(offsetKey).getInt(Integer.BYTES); // second int, after the aggregationId
  }
}
