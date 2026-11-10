/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.util.concurrent.ConcurrentSkipListMap;
import org.jspecify.annotations.Nullable;

/**
 * A transaction of the write-behind cache. It buffers its writes until it commits, then publishes
 * them to the store as one transaction.
 *
 * <p>The buffer is a skip list, so iterating over it stays possible while the transaction keeps
 * writing to it. It is cleared only once the store accepted the commit, so a retried {@link
 * #commit()} publishes the same writes.
 */
final class CachedTransaction implements ZeebeDbTransaction {

  private final WriteBehindStore<?> store;
  private final ConcurrentSkipListMap<CacheKey, Op> writes = new ConcurrentSkipListMap<>();
  private boolean inCurrentTransaction;

  CachedTransaction(final WriteBehindStore<?> store) {
    this.store = store;
  }

  /** Returns this transaction's own write of {@code key}, or {@code null} if it has none. */
  @Nullable Op get(final CacheKey key) {
    return writes.get(key);
  }

  void put(final CacheKey key, final Op op) {
    writes.put(key, op);
  }

  /**
   * Returns this transaction's next written key after {@code from} in the scan's direction, within
   * the column family of {@code from}, or {@code null} if there is none. It reads the live buffer,
   * so writes made during a scan show up in it.
   */
  @Nullable CacheKey nextKey(final CacheKey from, final boolean inclusive, final boolean reverse) {
    final CacheKey next;
    if (reverse) {
      next = inclusive ? writes.floorKey(from) : writes.lowerKey(from);
    } else {
      next = inclusive ? writes.ceilingKey(from) : writes.higherKey(from);
    }
    return next != null && next.columnFamily().getValue() == from.columnFamily().getValue()
        ? next
        : null;
  }

  boolean isInCurrentTransaction() {
    return inCurrentTransaction;
  }

  void resetTransaction() {
    writes.clear();
    inCurrentTransaction = true;
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
    store.commit(writes);
    writes.clear();
  }

  void rollbackInternal() {
    inCurrentTransaction = false;
    writes.clear();
  }
}
