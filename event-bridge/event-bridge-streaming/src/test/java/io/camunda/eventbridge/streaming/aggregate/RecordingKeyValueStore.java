/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbBytes;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@link KeyValueStore} wrapper that records every {@link #put} and {@link #delete} — the
 * observable writes of an aggregation's {@code persistFrozen()} — so a test can assert what a cut
 * actually persisted (each write once, no re-put of an unchanged cell, no delete for a row that
 * never existed), not just the store's final contents.
 */
final class RecordingKeyValueStore implements KeyValueStore<DbBytes, DbBytes> {

  private final KeyValueStore<DbBytes, DbBytes> delegate;
  private final List<byte[]> puts = new ArrayList<>();
  private final List<byte[]> deletes = new ArrayList<>();

  RecordingKeyValueStore(final KeyValueStore<DbBytes, DbBytes> delegate) {
    this.delegate = delegate;
  }

  /** The {@code windowStart} of every recorded cell-row put, in write order (meta rows skipped). */
  List<Long> putWindowStarts() {
    return windowStarts(puts);
  }

  /** The {@code windowStart} of every recorded cell-row delete, in write order. */
  List<Long> deleteWindowStarts() {
    return windowStarts(deletes);
  }

  /** Every recorded cell-row put key's raw bytes, in write order (meta rows skipped). */
  List<byte[]> putCellKeys() {
    return cellKeys(puts);
  }

  /** Every recorded cell-row delete key's raw bytes, in write order. */
  List<byte[]> deleteCellKeys() {
    return cellKeys(deletes);
  }

  /** Forgets everything recorded so far, so a test can scope assertions to one cut. */
  void clearRecorded() {
    puts.clear();
    deletes.clear();
  }

  @Override
  public void put(final DbBytes key, final DbBytes value) {
    puts.add(key.getBytes().clone());
    delegate.put(key, value);
  }

  @Override
  public void delete(final DbBytes key) {
    deletes.add(key.getBytes().clone());
    delegate.delete(key);
  }

  @Override
  public Optional<DbBytes> get(final DbBytes key) {
    return delegate.get(key);
  }

  @Override
  public boolean exists(final DbBytes key) {
    return delegate.exists(key);
  }

  @Override
  public void prefixScan(final DbKey prefix, final BiConsumer<DbBytes, DbBytes> visitor) {
    delegate.prefixScan(prefix, visitor);
  }

  @Override
  public void prefixScanKeys(final DbKey prefix, final Consumer<DbBytes> visitor) {
    delegate.prefixScanKeys(prefix, visitor);
  }

  @Override
  public void forEach(final BiConsumer<DbBytes, DbBytes> visitor) {
    delegate.forEach(visitor);
  }

  /**
   * Decodes each recorded cell key's {@code windowStart} ({@code group ++ windowStart ++ key});
   * bare-group meta rows (shorter than any cell key) are skipped.
   */
  private static List<Long> windowStarts(final List<byte[]> keys) {
    final List<Long> starts = new ArrayList<>();
    for (final byte[] key : cellKeys(keys)) {
      starts.add(ByteBuffer.wrap(key).getLong(Integer.BYTES));
    }
    return starts;
  }

  private static List<byte[]> cellKeys(final List<byte[]> keys) {
    final List<byte[]> cells = new ArrayList<>();
    for (final byte[] key : keys) {
      if (key.length >= Integer.BYTES + Long.BYTES) {
        cells.add(key);
      }
    }
    return cells;
  }
}
