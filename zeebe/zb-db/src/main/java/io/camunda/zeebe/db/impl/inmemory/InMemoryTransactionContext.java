/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.util.Arrays;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * In-memory transaction context that buffers all writes until commit.
 *
 * <p>Values are stored as Java object copies ({@link DbValue#copyTo(DbValue)}) — no serialization.
 * Keys are {@code byte[]} for ordering.
 *
 * <p>Deletes are tracked as tombstones: a key present in {@code pendingDeletes} means "this key was
 * deleted in this transaction" and reads must return {@code null} for it, even if the committed
 * store still has a value.
 */
final class InMemoryTransactionContext implements TransactionContext {

  private final InMemoryZeebeDb<?> db;
  private final InMemoryTransaction transaction;

  InMemoryTransactionContext(final InMemoryZeebeDb<?> db) {
    this.db = db;
    transaction = new InMemoryTransaction(db);
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    try {
      if (transaction.isInCurrentTransaction()) {
        operations.run();
      } else {
        runInNewTransaction(operations);
      }
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception ex) {
      throw new RuntimeException(
          "Unexpected error occurred during in-memory db transaction operation.", ex);
    }
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    if (!transaction.isInCurrentTransaction()) {
      transaction.reset();
    }
    return transaction;
  }

  private void runInNewTransaction(final TransactionOperation operations) throws Exception {
    try {
      transaction.reset();
      operations.run();
      transaction.commitInternal();
    } catch (final Exception e) {
      transaction.rollbackInternal();
      throw e;
    }
  }

  /**
   * The in-memory transaction. Buffers all writes/deletes and applies them atomically to the
   * committed store on commit. Values are Java object copies — no serialization until RocksDB
   * flush.
   */
  static final class InMemoryTransaction implements ZeebeDbTransaction {

    private final InMemoryZeebeDb<?> db;

    /** Pending writes: byte[] key → DbValue copy. Sorted for merge-iteration. */
    private final NavigableMap<byte[], DbValue> pendingWrites =
        new TreeMap<>(Arrays::compareUnsigned);

    /** Keys deleted in this transaction. Takes precedence over both pendingWrites and committed. */
    private final Set<byte[]> pendingDeletes = new TreeSet<>(Arrays::compareUnsigned);

    private boolean inTransaction;

    InMemoryTransaction(final InMemoryZeebeDb<?> db) {
      this.db = db;
    }

    boolean isInCurrentTransaction() {
      return inTransaction;
    }

    void reset() {
      pendingWrites.clear();
      pendingDeletes.clear();
      inTransaction = true;
    }

    // ---- Operations called by InMemoryColumnFamily ----

    void put(final byte[] key, final DbValue value) {
      pendingDeletes.remove(key);
      // Reuse existing pending instance if present, otherwise allocate once
      final DbValue stored = pendingWrites.computeIfAbsent(key, ignored -> value.newInstance());
      value.copyTo(stored);
      pendingWrites.put(key, stored);
    }

    /**
     * Returns the value for the given key, considering pending writes and deletes.
     *
     * @return the value, or {@code null} if not found or deleted
     */
    @Nullable DbValue get(final byte[] key) {
      if (isDeleted(key)) {
        return null;
      }
      final DbValue pending = pendingWrites.get(key);
      if (pending != null) {
        return pending;
      }
      return db.committedData.get(key);
    }

    void delete(final byte[] key) {
      pendingWrites.remove(key);
      pendingDeletes.add(key);
    }

    boolean isDeleted(final byte[] key) {
      for (final byte[] deleted : pendingDeletes) {
        if (Arrays.equals(deleted, key)) {
          return true;
        }
      }
      return false;
    }

    NavigableMap<byte[], DbValue> getPendingWrites() {
      return pendingWrites;
    }

    Set<byte[]> getPendingDeletes() {
      return pendingDeletes;
    }

    // ---- ZeebeDbTransaction interface ----

    @Override
    public void run(final TransactionOperation operations) throws Exception {
      operations.run();
    }

    @Override
    public void commit() throws Exception {
      commitInternal();
    }

    @Override
    public void rollback() throws Exception {
      rollbackInternal();
    }

    void commitInternal() {
      for (final byte[] key : pendingDeletes) {
        db.committedData.remove(key);
      }
      db.committedData.putAll(pendingWrites);
      inTransaction = false;
      pendingWrites.clear();
      pendingDeletes.clear();
    }

    void rollbackInternal() {
      inTransaction = false;
      pendingWrites.clear();
      pendingDeletes.clear();
    }
  }
}
