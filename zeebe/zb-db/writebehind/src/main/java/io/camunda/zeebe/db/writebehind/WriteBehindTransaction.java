/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbIterator;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeTransaction;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListMap;
import org.jspecify.annotations.Nullable;

/**
 * Transaction of one context. Buffers its writes on the heap and hands them to the store on commit.
 *
 * <p>The buffer is a skip list rather than a tree map because iteration over it must tolerate
 * writes from the visitor (delete while iterating).
 */
final class WriteBehindTransaction implements ZeebeTransaction {

  /** Marks a delete in the buffer. Compared by identity, so an empty value is not a delete. */
  static final byte[] DELETED = new byte[0];

  private final WriteBehindStore store;
  private final ConcurrentSkipListMap<RawKey, byte[]> writes = new ConcurrentSkipListMap<>();
  private boolean inCurrentTransaction;

  WriteBehindTransaction(final WriteBehindStore store) {
    this.store = store;
  }

  @Override
  public void put(
      final byte[] key, final int keyLength, final byte[] value, final int valueLength) {
    writes.put(RawKey.copyOf(key, keyLength), Arrays.copyOf(value, valueLength));
  }

  @Override
  public byte @Nullable [] get(final byte[] key, final int keyLength) {
    final var rawKey = RawKey.copyOf(key, keyLength);
    final var own = writes.get(rawKey);
    if (own != null) {
      return own == DELETED ? null : own.clone();
    }
    final var committed = store.get(rawKey);
    return committed == null ? null : committed.clone();
  }

  @Override
  public void delete(final byte[] key, final int keyLength) {
    writes.put(RawKey.copyOf(key, keyLength), DELETED);
  }

  @Override
  public DbIterator newIterator(final byte[] prefix, final int prefixLength) {
    return new MergingDbIterator(writes, store.openView(prefix, prefixLength));
  }

  @Override
  public void resetTransaction() {
    writes.clear();
    inCurrentTransaction = true;
  }

  @Override
  public boolean isInCurrentTransaction() {
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

  @Override
  public void commitInternal() {
    inCurrentTransaction = false;
    if (writes.isEmpty()) {
      store.ensureUsable();
      return;
    }

    final var keys = new RawKey[writes.size()];
    final byte[] @Nullable [] values = new byte[keys.length][];
    int i = 0;
    for (final var entry : writes.entrySet()) {
      keys[i] = entry.getKey();
      values[i] = entry.getValue() == DELETED ? null : entry.getValue();
      i++;
    }
    store.commit(keys, values);
    // only after success: the stream processor retries a failed commit with the same writes
    writes.clear();
  }

  @Override
  public void rollbackInternal() {
    inCurrentTransaction = false;
    writes.clear();
  }
}
