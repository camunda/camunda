/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.util.concurrent.ConcurrentSkipListMap;
import org.jspecify.annotations.Nullable;

/**
 * Transaction of one context. Buffers its writes as private copies of the values and hands them to
 * the store on commit.
 *
 * <p>The buffer is a skip list rather than a tree map because iteration over it must tolerate
 * writes from the visitor (delete while iterating).
 */
final class WriteBehindTransaction implements ZeebeDbTransaction {

  private static final Version DELETED = new Version(null, 0);

  private final WriteBehindStore store;
  private final ConcurrentSkipListMap<RawKey, Version> writes = new ConcurrentSkipListMap<>();
  private boolean inCurrentTransaction;

  WriteBehindTransaction(final WriteBehindStore store) {
    this.store = store;
  }

  /** Buffers a write; the transaction takes ownership of {@code value}. */
  void put(final RawKey key, final DbValue value) {
    writes.put(key, new Version(value, 0));
  }

  void delete(final RawKey key) {
    writes.put(key, DELETED);
  }

  /**
   * The value that this transaction sees, or null if there is none. Shared: callers must copy it.
   *
   * @param prototype decides the class that a value read from the storage is decoded into
   */
  @Nullable DbValue get(final RawKey key, final DbValue prototype) {
    final var own = writes.get(key);
    if (own != null) {
      return own.value();
    }
    return store.get(key, prototype);
  }

  boolean exists(final RawKey key) {
    final var own = writes.get(key);
    if (own != null) {
      return !own.isTombstone();
    }
    return store.exists(key);
  }

  /** A cursor over every key that starts with the prefix, including this transaction's writes. */
  MergingCursor newCursor(final byte[] prefix) {
    return new MergingCursor(writes, store, prefix);
  }

  /** Call before a scan of a column family: loads its key index if it has one that isn't loaded. */
  void prepareScan(final long columnFamily) {
    store.loadKeyIndexIfNeeded(columnFamily);
  }

  void resetTransaction() {
    writes.clear();
    inCurrentTransaction = true;
  }

  boolean isInCurrentTransaction() {
    return inCurrentTransaction;
  }

  @Override
  public void run(final TransactionOperation operations) throws Exception {
    operations.run();
  }

  @Override
  public void commit() {
    commitInternal();
  }

  @Override
  public void rollback() {
    rollbackInternal();
  }

  void commitInternal() {
    inCurrentTransaction = false;
    if (writes.isEmpty()) {
      store.ensureUsable();
      return;
    }

    final var keys = new RawKey[writes.size()];
    final @Nullable DbValue[] values = new DbValue[keys.length];
    int i = 0;
    for (final var entry : writes.entrySet()) {
      keys[i] = entry.getKey();
      values[i] = entry.getValue().value();
      i++;
    }
    store.commit(keys, values);
    // only after success: the stream processor retries a failed commit with the same writes
    writes.clear();
  }

  void rollbackInternal() {
    inCurrentTransaction = false;
    writes.clear();
  }
}
