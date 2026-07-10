/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.serving.spi.DatasetWriter;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.SegmentMergingAggregation.FinalizationListener;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Materialises a cube's periodic snapshots (ADR 0010, the Kimball periodic-snapshot pattern): it
 * observes the <em>finest</em> tier's window finalizations — the one moment a window's value is
 * event-time-final — folds them into a durable cumulative accumulator per key, and emits the
 * absolute values as {@code _snapshots} rows whenever the fold provably covers a boundary of the
 * {@code everyMs} event-time grid.
 *
 * <p><b>When is a boundary provably covered?</b> Finest windows finalize in ascending window-end
 * order (per key, in window order), so after folding a window ending at {@code E} the pending
 * boundary is {@code ceil(E / everyMs) * everyMs} — but windows between {@code E} and that boundary
 * may still follow. The pending sample is released by either proof of completeness: a <em>later
 * window</em> of the same key finalizes (nothing earlier can follow), or the <em>watermark</em>
 * passes the boundary (no window ending at-or-before it remains open). The emitted value is fixed
 * either way — the fold of exactly the windows ending at-or-before the boundary — so replay
 * re-derives identical rows; only the emission moment varies. Emission is sparse: boundaries in a
 * key's silent stretches produce no rows (reads carry the last snapshot forward).
 *
 * <p><b>Durability.</b> The per-key state {@code (pendingBoundary, cumulative accumulator)} lives
 * in the shared cell store under this sampler's {@code cellGroup}, persisted inside the owner
 * task's commit cut via the freeze/persist/complete split — so a crash replays from a consistent
 * fold, and the idempotent keyed snapshot rows are simply re-written. Emitted rows flow through the
 * task's staged serving writer and inherit its frozen-cut and write-fence guarantees.
 */
public final class SnapshotSampler implements FinalizationListener<DimensionKey, Object[]> {

  private static final long NO_BOUNDARY = Long.MIN_VALUE;

  private final CompiledDataset dataset;
  private final DatasetWriter writer;
  private final KeyValueStore<DbBytes, DbBytes> stateStore;
  private final CompositeAggregateFunction aggregate;
  private final RecordValue<Object[]> accCodec;
  private final RecordValue<DimensionKey> keyCodec;
  private final long everyMs;
  private final long finestWindowMs;

  private final Map<DimensionKey, KeyState> states = new HashMap<>();
  // Keys whose state changed since the last successful cut; frozen at the barrier, re-marked on a
  // failed cut (the dedup-watermark pattern).
  private final Set<DimensionKey> dirty = new HashSet<>();
  private Map<byte[], byte[]> frozenState;
  private Set<DimensionKey> frozenDirty;

  private final DbBytes stateKey = new DbBytes();
  private final DbBytes stateValue = new DbBytes();

  public SnapshotSampler(
      final CompiledDataset dataset,
      final DatasetWriter writer,
      final KeyValueStore<DbBytes, DbBytes> stateStore,
      final RecordValue<DimensionKey> keyCodec,
      final RecordValue<Object[]> accCodec,
      final RecordValue<DimensionKey> scanKeyCodec,
      final RecordValue<Object[]> scanAccCodec) {
    this.dataset = dataset;
    this.writer = writer;
    this.stateStore = stateStore;
    this.keyCodec = keyCodec;
    this.accCodec = accCodec;
    aggregate = new CompositeAggregateFunction(dataset.meterBounds());
    everyMs = dataset.snapshots().everyMs();
    finestWindowMs = dataset.finestTier().windowMs();
    recover(scanKeyCodec, scanAccCodec);
  }

  @Override
  public void onFinal(final Windowed<DimensionKey> cell, final Object[] value) {
    final KeyState state = states.computeIfAbsent(cell.key(), key -> new KeyState());
    final long windowEnd = cell.windowStart() + finestWindowMs;
    // A later window proves nothing can precede the pending boundary anymore — release it with
    // the accumulator as it stood BEFORE this fold (this window ends after the boundary).
    if (state.pendingBoundary != NO_BOUNDARY && windowEnd > state.pendingBoundary) {
      emit(cell.key(), state);
    }
    // Fold the finalized window; never adopt the merger's (about-to-be-evicted) accumulator.
    state.accumulator =
        state.accumulator == null
            ? aggregate.mergeInto(aggregate.createAccumulator(), value)
            : aggregate.mergeInto(state.accumulator, value);
    state.pendingBoundary = ceilToGrid(windowEnd);
    dirty.add(cell.key());
  }

  @Override
  public void onWatermark(final long watermark) {
    // The watermark proves no window ending at-or-before it remains open — release every pending
    // boundary it passed (covers keys that went silent and never see a "later window").
    for (final Map.Entry<DimensionKey, KeyState> entry : states.entrySet()) {
      final KeyState state = entry.getValue();
      if (state.pendingBoundary != NO_BOUNDARY && state.pendingBoundary <= watermark) {
        emit(entry.getKey(), state);
        dirty.add(entry.getKey());
      }
    }
  }

  private void emit(final DimensionKey key, final KeyState state) {
    writer.upsertSnapshotRow(
        dataset, key, state.pendingBoundary, accCodec.toBytes(state.accumulator));
    state.pendingBoundary = NO_BOUNDARY;
  }

  private long ceilToGrid(final long windowEnd) {
    return ((windowEnd + everyMs - 1) / everyMs) * everyMs;
  }

  // --- durability: the freeze/persist/complete split of the owner task's commit cut -------------

  /** Owner thread, at the barrier: encodes the dirty entries as the outstanding frozen batch. */
  public void freeze() {
    if (frozenState != null) {
      throw new IllegalStateException("expected no outstanding frozen snapshot state");
    }
    final Map<byte[], byte[]> batch = new HashMap<>();
    for (final DimensionKey key : dirty) {
      final KeyState state = states.get(key);
      batch.put(encodeKey(key), encodeState(state));
    }
    frozenState = batch;
    frozenDirty = Set.copyOf(dirty);
    dirty.clear();
  }

  /** Inside the cut's transaction: writes the frozen batch to the durable state rows. */
  public void persistFrozen() {
    if (frozenState == null) {
      throw new IllegalStateException("expected a frozen snapshot state to persist");
    }
    for (final Map.Entry<byte[], byte[]> entry : frozenState.entrySet()) {
      stateKey.wrapBytes(entry.getKey());
      stateValue.wrapBytes(entry.getValue());
      stateStore.put(stateKey, stateValue);
    }
  }

  /** Owner thread, once the cut's outcome is known; a failed cut re-marks the frozen keys. */
  public void completeFrozen(final boolean success) {
    if (frozenState == null) {
      throw new IllegalStateException("expected a frozen snapshot state to complete");
    }
    if (!success) {
      dirty.addAll(frozenDirty);
    }
    frozenState = null;
    frozenDirty = null;
  }

  private void recover(
      final RecordValue<DimensionKey> scanKeyCodec, final RecordValue<Object[]> scanAccCodec) {
    final DbBytes prefix = new DbBytes();
    prefix.wrapBytes(
        ByteBuffer.allocate(Integer.BYTES).putInt(dataset.snapshots().cellGroup()).array());
    stateStore.prefixScan(
        prefix,
        (key, value) -> {
          final byte[] keyBytes = key.getBytes();
          final byte[] keyOnly = new byte[keyBytes.length - Integer.BYTES];
          System.arraycopy(keyBytes, Integer.BYTES, keyOnly, 0, keyOnly.length);
          final ByteBuffer buffer = ByteBuffer.wrap(value.getBytes());
          final long pendingBoundary = buffer.getLong();
          final byte[] accBytes = new byte[buffer.remaining()];
          buffer.get(accBytes);
          final KeyState state = new KeyState();
          state.pendingBoundary = pendingBoundary;
          state.accumulator = scanAccCodec.fromBytes(accBytes);
          states.put(scanKeyCodec.fromBytes(keyOnly), state);
        });
  }

  private byte[] encodeKey(final DimensionKey key) {
    final byte[] keyBytes = keyCodec.toBytes(key);
    return ByteBuffer.allocate(Integer.BYTES + keyBytes.length)
        .putInt(dataset.snapshots().cellGroup())
        .put(keyBytes)
        .array();
  }

  private byte[] encodeState(final KeyState state) {
    final byte[] accBytes = accCodec.toBytes(state.accumulator);
    return ByteBuffer.allocate(Long.BYTES + accBytes.length)
        .putLong(state.pendingBoundary)
        .put(accBytes)
        .array();
  }

  /** One key's cumulative fold and its not-yet-released sample boundary. */
  private static final class KeyState {
    private Object[] accumulator;
    private long pendingBoundary = NO_BOUNDARY;
  }
}
