/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import io.camunda.analytics.streaming.shuffle.Partial;
import io.camunda.analytics.streaming.state.api.KeyValueStore;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.analytics.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Stage-2 reducer of the staged pipeline: it consumes {@link Partial}s (pre-aggregated per
 * source partition by the Stage-1 combiner) for a single {@code aggId} and merges them into the
 * global windowed aggregate, converging an idempotent {@link ResultSink} (so reads hit one cell —
 * O(1), no fan-in merge at read time).
 *
 * <p>Exactly-once without a transactional producer, via <b>per-writer slots</b>: each cell keeps
 * one accumulator per writer (source partition); a partial <em>overwrites</em> its writer's slot,
 * and the served value is the merge across a cell's slots. Because the merge is turned into an
 * idempotent overwrite, a Stage-1 re-emit or a Stage-2 replay just re-writes the same slot — no
 * source-position dedup, boundary-independent. The facts-topic offset (Stage 2's own input
 * position) is committed by the driver in the same checkpoint transaction.
 *
 * <p>Storage mirrors {@code DurableMaterializedRollup}: slots live on the heap (write-back cache)
 * and are checkpointed to a shared RocksDB store keyed by {@code aggId ++ windowStart ++ writer ++
 * key}; a rollup reads only its own slots via {@code prefixScan(aggId)}. Closed windows are merged
 * to a final value, emitted, and evicted (retention).
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class MergingRollup<K, ACC> {

  private final int aggId;
  private final AggregateFunction<?, ACC, ?> aggregate;
  private final TumblingWindows windows;
  private final long allowedLatenessMs;
  private final ResultSink<Windowed<K>, ACC> sink;
  private final KeyValueStore<DbBytes, DbBytes> slotStore;
  private final Codec<K> keyCodec;
  private final Codec<ACC> accCodec;
  private final TransactionRunner tx;
  private final Predicate<ACC> drained;

  private final DbBytes slotKey = new DbBytes();
  private final DbBytes slotValue = new DbBytes();
  private final DbBytes aggPrefix = new DbBytes();

  // Heap working set: per cell, one accumulator per writer (source partition).
  private final Map<Windowed<K>, Map<Integer, ACC>> slots = new HashMap<>();
  private final Set<Windowed<K>> changedSinceFlush = new HashSet<>();
  private final Set<SlotRef<K>> changedSinceCheckpoint = new HashSet<>();
  private final Set<SlotRef<K>> evictedSinceCheckpoint = new HashSet<>();
  private long maxEventTime = Long.MIN_VALUE;

  private record SlotRef<K>(Windowed<K> cell, int writer) {}

  public MergingRollup(
      final int aggId,
      final AggregateFunction<?, ACC, ?> aggregate,
      final TumblingWindows windows,
      final long allowedLatenessMs,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> slotStore,
      final Codec<K> keyCodec,
      final Codec<ACC> accCodec,
      final TransactionRunner tx) {
    this(
        aggId,
        aggregate,
        windows,
        allowedLatenessMs,
        sink,
        slotStore,
        keyCodec,
        accCodec,
        tx,
        acc -> false);
  }

  public MergingRollup(
      final int aggId,
      final AggregateFunction<?, ACC, ?> aggregate,
      final TumblingWindows windows,
      final long allowedLatenessMs,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> slotStore,
      final Codec<K> keyCodec,
      final Codec<ACC> accCodec,
      final TransactionRunner tx,
      final Predicate<ACC> drained) {
    this.aggId = aggId;
    this.aggregate = aggregate;
    this.windows = windows;
    this.allowedLatenessMs = allowedLatenessMs;
    this.sink = sink;
    this.slotStore = slotStore;
    this.keyCodec = keyCodec;
    this.accCodec = accCodec;
    this.tx = tx;
    this.drained = drained;
    recover();
  }

  /** Overwrites the writer's slot for the partial's cell (idempotent) and marks it changed. */
  public void accept(final Partial partial) {
    if (partial.aggId() != aggId) {
      return; // not ours (defensive; the driver dispatches by aggId)
    }
    final long windowStart = partial.windowStart();
    // Drop partials whose window has already closed and been evicted — folding them would resurrect
    // an evicted cell and the idempotent sink would overwrite the finalized row with a partial.
    if (maxEventTime != Long.MIN_VALUE
        && windowStart + windows.sizeMs() + allowedLatenessMs <= maxEventTime) {
      return;
    }
    final Windowed<K> cell = new Windowed<>(keyCodec.decode(partial.key()), windowStart);
    final ACC acc = accCodec.decode(partial.acc());
    slots.computeIfAbsent(cell, c -> new HashMap<>()).put(partial.writer(), acc);
    changedSinceFlush.add(cell);
    final SlotRef<K> ref = new SlotRef<>(cell, partial.writer());
    changedSinceCheckpoint.add(ref);
    evictedSinceCheckpoint.remove(ref);
    maxEventTime = Math.max(maxEventTime, windowStart + windows.sizeMs());
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
    final long watermark = maxEventTime - allowedLatenessMs;
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
    aggPrefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(aggId).array());
    slotStore.prefixScan(
        aggPrefix,
        (key, value) -> {
          final SlotRef<K> ref = decodeSlotKey(key.getBytes());
          slots
              .computeIfAbsent(ref.cell(), c -> new HashMap<>())
              .put(ref.writer(), accCodec.decode(value.getBytes()));
          maxEventTime = Math.max(maxEventTime, ref.cell().windowStart() + windows.sizeMs());
        });
  }

  private void writeSlot(final SlotRef<K> ref, final ACC acc) {
    slotKey.wrapBytes(encodeSlotKey(ref));
    slotValue.wrapBytes(accCodec.encode(acc));
    slotStore.put(slotKey, slotValue);
  }

  private void deleteSlot(final SlotRef<K> ref) {
    slotKey.wrapBytes(encodeSlotKey(ref));
    slotStore.delete(slotKey);
  }

  /** Slot key: {@code aggId ++ windowStart ++ writer ++ codec(key)}. */
  private byte[] encodeSlotKey(final SlotRef<K> ref) {
    final byte[] keyBytes = keyCodec.encode(ref.cell().key());
    return ByteBuffer.allocate(Integer.BYTES + Long.BYTES + Integer.BYTES + keyBytes.length)
        .putInt(aggId)
        .putLong(ref.cell().windowStart())
        .putInt(ref.writer())
        .put(keyBytes)
        .array();
  }

  private SlotRef<K> decodeSlotKey(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.getInt(); // aggId — already scoped by the prefix scan
    final long windowStart = buffer.getLong();
    final int writer = buffer.getInt();
    final byte[] keyBytes = new byte[buffer.remaining()];
    buffer.get(keyBytes);
    return new SlotRef<>(new Windowed<>(keyCodec.decode(keyBytes), windowStart), writer);
  }
}
