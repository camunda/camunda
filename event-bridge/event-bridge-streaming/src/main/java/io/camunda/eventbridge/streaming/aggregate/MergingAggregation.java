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
 * Merges per-writer contributions into one windowed aggregate and converges an idempotent {@link
 * ResultSink}, so a read hits a single cell (no fan-in at read time). It is the reduce side of a
 * shuffle: several upstream writers each pre-aggregate a slice of the input and emit their partial
 * for a cell; this operator keeps <b>one accumulator per writer</b> in a cell and serves the merge
 * across a cell's writers.
 *
 * <p>Exactly-once without a transactional producer: a writer's contribution <em>overwrites</em> its
 * own slot, so a re-emit or a replay just rewrites the same slot to the same value — the merge is
 * an idempotent overwrite, needing no position dedup and independent of batch boundaries.
 *
 * <p>Slots live on the heap and are checkpointed to a shared store keyed by {@code group ++
 * windowStart ++ writer ++ codec(key)}; a {@code group} lets several operators share one store and
 * still scan only their own slots via {@code prefixScan(group)}. Closed windows are merged to a
 * final value, emitted, and evicted (retention). Single-writer, like every operator.
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class MergingAggregation<K, ACC> {

  private final int group;
  private final AggregateFunction<?, ACC, ?> aggregate;
  private final Windows windows;
  private final ResultSink<Windowed<K>, ACC> sink;
  private final KeyValueStore<DbBytes, DbBytes> slotStore;
  private final RecordValue<K> keyValue;
  private final RecordValue<ACC> accValue;
  private final TransactionRunner tx;
  private final Predicate<ACC> drained;

  private final DbBytes slotKey = new DbBytes();
  private final DbBytes slotValue = new DbBytes();
  private final DbBytes groupPrefix = new DbBytes();

  // Heap working set: per cell, one accumulator per writer.
  private final Map<Windowed<K>, Map<Integer, ACC>> slots = new HashMap<>();
  private final Set<Windowed<K>> changedSinceFlush = new HashSet<>();
  private final Set<SlotRef<K>> changedSinceCheckpoint = new HashSet<>();
  private final Set<SlotRef<K>> evictedSinceCheckpoint = new HashSet<>();
  private long maxEventTime = Long.MIN_VALUE;

  private record SlotRef<K>(Windowed<K> cell, int writer) {}

  public MergingAggregation(
      final int group,
      final AggregateFunction<?, ACC, ?> aggregate,
      final Windows windows,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> slotStore,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final TransactionRunner tx) {
    this(group, aggregate, windows, sink, slotStore, keyValue, accValue, tx, acc -> false);
  }

  public MergingAggregation(
      final int group,
      final AggregateFunction<?, ACC, ?> aggregate,
      final Windows windows,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> slotStore,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final TransactionRunner tx,
      final Predicate<ACC> drained) {
    this.group = group;
    this.aggregate = aggregate;
    this.windows = windows;
    this.sink = sink;
    this.slotStore = slotStore;
    this.keyValue = keyValue;
    this.accValue = accValue;
    this.tx = tx;
    this.drained = drained;
    recover();
  }

  /** Overwrites {@code writer}'s slot for {@code cell} (idempotent) and marks it changed. */
  public void accept(final int writer, final Windowed<K> cell, final ACC acc) {
    // Drop contributions whose window has already closed and been evicted — folding them would
    // resurrect an evicted cell and the idempotent sink would overwrite the finalized value.
    if (maxEventTime != Long.MIN_VALUE
        && cell.windowStart() + windows.sizeMs() + windows.graceMs() <= maxEventTime) {
      return;
    }
    slots.computeIfAbsent(cell, c -> new HashMap<>()).put(writer, acc);
    changedSinceFlush.add(cell);
    final SlotRef<K> ref = new SlotRef<>(cell, writer);
    changedSinceCheckpoint.add(ref);
    evictedSinceCheckpoint.remove(ref);
    maxEventTime = Math.max(maxEventTime, cell.windowStart() + windows.sizeMs());
  }

  /** Wall-clock tick: converge the serving view for the cells changed since the last flush. */
  public void flush() {
    for (final Windowed<K> cell : changedSinceFlush) {
      sink.upsert(cell, merged(cell));
    }
    changedSinceFlush.clear();
  }

  /**
   * Commit-interval tick: finalize closed windows, then persist changed/evicted slots in one txn.
   */
  public void checkpoint() {
    finalizeClosedWindows();
    tx.runInTransaction(
        () -> {
          for (final SlotRef<K> ref : changedSinceCheckpoint) {
            final Map<Integer, ACC> cellSlots = slots.get(ref.cell());
            if (cellSlots != null && cellSlots.containsKey(ref.writer())) {
              writeSlot(ref, cellSlots.get(ref.writer()));
            }
          }
          for (final SlotRef<K> ref : evictedSinceCheckpoint) {
            deleteSlot(ref);
          }
        });
    changedSinceCheckpoint.clear();
    evictedSinceCheckpoint.clear();
  }

  public void close() {
    flush();
    checkpoint();
  }

  /** The global value of a cell: merge across its writers' slots. */
  private ACC merged(final Windowed<K> cell) {
    ACC result = null;
    for (final ACC writerAcc : slots.get(cell).values()) {
      result = result == null ? writerAcc : aggregate.merge(result, writerAcc);
    }
    return result;
  }

  private void finalizeClosedWindows() {
    if (maxEventTime == Long.MIN_VALUE) {
      return;
    }
    final long watermark = maxEventTime - windows.graceMs();
    final Iterator<Map.Entry<Windowed<K>, Map<Integer, ACC>>> it = slots.entrySet().iterator();
    while (it.hasNext()) {
      final Map.Entry<Windowed<K>, Map<Integer, ACC>> entry = it.next();
      final Windowed<K> cell = entry.getKey();
      final long windowEnd = cell.windowStart() + windows.sizeMs();
      final ACC value = merged(cell);
      if ((windowEnd <= maxEventTime && drained.test(value)) || windowEnd <= watermark) {
        sink.upsert(cell, value); // final value
        for (final Integer writer : entry.getValue().keySet()) {
          final SlotRef<K> ref = new SlotRef<>(cell, writer);
          changedSinceFlush.remove(cell);
          changedSinceCheckpoint.remove(ref);
          evictedSinceCheckpoint.add(ref); // delete the durable slot at the next checkpoint
        }
        it.remove(); // evict from the heap working set
      }
    }
  }

  private void recover() {
    groupPrefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(group).array());
    slotStore.prefixScan(
        groupPrefix,
        (key, value) -> {
          final SlotRef<K> ref = decodeSlotKey(key.getBytes());
          slots
              .computeIfAbsent(ref.cell(), c -> new HashMap<>())
              .put(ref.writer(), accValue.fromBytes(value.getBytes()));
          maxEventTime = Math.max(maxEventTime, ref.cell().windowStart() + windows.sizeMs());
        });
  }

  private void writeSlot(final SlotRef<K> ref, final ACC acc) {
    slotKey.wrapBytes(encodeSlotKey(ref));
    slotValue.wrapBytes(accValue.toBytes(acc));
    slotStore.put(slotKey, slotValue);
  }

  private void deleteSlot(final SlotRef<K> ref) {
    slotKey.wrapBytes(encodeSlotKey(ref));
    slotStore.delete(slotKey);
  }

  /** Slot key: {@code group ++ windowStart ++ writer ++ codec(key)}. */
  private byte[] encodeSlotKey(final SlotRef<K> ref) {
    final byte[] keyBytes = keyValue.toBytes(ref.cell().key());
    return ByteBuffer.allocate(Integer.BYTES + Long.BYTES + Integer.BYTES + keyBytes.length)
        .putInt(group)
        .putLong(ref.cell().windowStart())
        .putInt(ref.writer())
        .put(keyBytes)
        .array();
  }

  private SlotRef<K> decodeSlotKey(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.getInt(); // group — already scoped by the prefix scan
    final long windowStart = buffer.getLong();
    final int writer = buffer.getInt();
    final byte[] keyBytes = new byte[buffer.remaining()];
    buffer.get(keyBytes);
    return new SlotRef<>(new Windowed<>(keyValue.fromBytes(keyBytes), windowStart), writer);
  }
}
