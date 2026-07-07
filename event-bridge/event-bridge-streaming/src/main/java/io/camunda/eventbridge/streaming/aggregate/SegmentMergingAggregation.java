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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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
 * store keyed by {@code group ++ windowStart ++ codec(key)}; a {@code group} lets several operators
 * share one store and scan only their own cells. Closed windows are finalized, emitted, and
 * evicted. Single-writer, like every operator.
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class SegmentMergingAggregation<K, ACC> {

  private final int group;
  private final AggregateFunction<?, ACC, ?> aggregate;
  private final Windows windows;
  private final ResultSink<Windowed<K>, ACC> sink;
  private final KeyValueStore<DbBytes, DbBytes> cellStore;
  private final RecordValue<K> keyValue;
  private final RecordValue<ACC> accValue;
  private final TransactionRunner tx;
  private final Predicate<ACC> drained;

  private final DbBytes cellKey = new DbBytes();
  private final DbBytes cellValue = new DbBytes();
  private final DbBytes groupPrefix = new DbBytes();

  // Heap working set: one running accumulator per cell.
  private final Map<Windowed<K>, ACC> cellTotal = new HashMap<>();
  private final Set<Windowed<K>> changedSinceFlush = new HashSet<>();
  private final Set<Windowed<K>> changedSinceCheckpoint = new HashSet<>();
  private final Set<Windowed<K>> evictedSinceCheckpoint = new HashSet<>();
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
    this.group = group;
    this.aggregate = aggregate;
    this.windows = windows;
    this.sink = sink;
    this.cellStore = cellStore;
    this.keyValue = keyValue;
    this.accValue = accValue;
    this.tx = tx;
    this.drained = drained;
    recover();
  }

  /** Folds one (deduped) segment delta into {@code cell}'s running total and marks it changed. */
  public void merge(final Windowed<K> cell, final ACC delta) {
    // Drop deltas for a window that already closed and was evicted: folding one would resurrect the
    // cell and the idempotent sink would overwrite its finalized value.
    if (maxEventTime != Long.MIN_VALUE
        && cell.windowStart() + windows.sizeMs() + windows.graceMs() <= maxEventTime) {
      return;
    }
    // The running total is always an accumulator this operator owns: the first delta is folded
    // into a fresh accumulator rather than stored, so a delta may be a transient read-only view
    // (RecordValue#fromBytesForMerge) and the in-place mergeInto never mutates a caller's object.
    final ACC current = cellTotal.get(cell);
    cellTotal.put(
        cell,
        current == null
            ? aggregate.mergeInto(aggregate.createAccumulator(), delta)
            : aggregate.mergeInto(current, delta));
    changedSinceFlush.add(cell);
    changedSinceCheckpoint.add(cell);
    evictedSinceCheckpoint.remove(cell);
    maxEventTime = Math.max(maxEventTime, cell.windowStart() + windows.sizeMs());
  }

  /** Wall-clock tick: converge the serving view for the cells changed since the last flush. */
  public void flush() {
    for (final Windowed<K> cell : changedSinceFlush) {
      sink.upsert(cell, cellTotal.get(cell));
    }
    changedSinceFlush.clear();
  }

  /**
   * Commit-interval tick: finalize closed windows, then persist changed/evicted cells in one txn.
   */
  public void checkpoint() {
    finalizeClosedWindows();
    tx.runInTransaction(
        () -> {
          for (final Windowed<K> cell : changedSinceCheckpoint) {
            final ACC total = cellTotal.get(cell);
            if (total != null) {
              writeCell(cell, total);
            }
          }
          for (final Windowed<K> cell : evictedSinceCheckpoint) {
            deleteCell(cell);
          }
        });
    changedSinceCheckpoint.clear();
    evictedSinceCheckpoint.clear();
  }

  public void close() {
    flush();
    checkpoint();
  }

  private void finalizeClosedWindows() {
    if (maxEventTime == Long.MIN_VALUE) {
      return;
    }
    final long watermark = maxEventTime - windows.graceMs();
    final Iterator<Map.Entry<Windowed<K>, ACC>> it = cellTotal.entrySet().iterator();
    while (it.hasNext()) {
      final Map.Entry<Windowed<K>, ACC> entry = it.next();
      final Windowed<K> cell = entry.getKey();
      final long windowEnd = cell.windowStart() + windows.sizeMs();
      final ACC value = entry.getValue();
      if ((windowEnd <= maxEventTime && drained.test(value)) || windowEnd <= watermark) {
        sink.upsert(cell, value); // final value
        changedSinceFlush.remove(cell);
        changedSinceCheckpoint.remove(cell);
        evictedSinceCheckpoint.add(cell); // delete the durable cell at the next checkpoint
        it.remove(); // evict from the heap working set
      }
    }
  }

  private void recover() {
    groupPrefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(group).array());
    cellStore.prefixScan(
        groupPrefix,
        (key, value) -> {
          final Windowed<K> cell = decodeCellKey(key.getBytes());
          cellTotal.put(cell, accValue.fromBytes(value.getBytes()));
          maxEventTime = Math.max(maxEventTime, cell.windowStart() + windows.sizeMs());
        });
  }

  private void writeCell(final Windowed<K> cell, final ACC total) {
    cellKey.wrapBytes(encodeCellKey(cell));
    cellValue.wrapBytes(accValue.toBytes(total));
    cellStore.put(cellKey, cellValue);
  }

  private void deleteCell(final Windowed<K> cell) {
    cellKey.wrapBytes(encodeCellKey(cell));
    cellStore.delete(cellKey);
  }

  /** Cell key: {@code group ++ windowStart ++ codec(key)}. */
  private byte[] encodeCellKey(final Windowed<K> cell) {
    final byte[] keyBytes = keyValue.toBytes(cell.key());
    return ByteBuffer.allocate(Integer.BYTES + Long.BYTES + keyBytes.length)
        .putInt(group)
        .putLong(cell.windowStart())
        .put(keyBytes)
        .array();
  }

  private Windowed<K> decodeCellKey(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.getInt(); // group — already scoped by the prefix scan
    final long windowStart = buffer.getLong();
    final byte[] keyBytes = new byte[buffer.remaining()];
    buffer.get(keyBytes);
    return new Windowed<>(keyValue.fromBytes(keyBytes), windowStart);
  }
}
