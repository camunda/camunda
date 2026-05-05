/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class LayeredTransactionContext implements TransactionContext {

  private final LayeredZeebeDb<?> db;
  private final TransactionContext activeContext;
  private final TransactionContext persistentContext;
  private final LayeredTransaction transaction;
  private final Map<ColumnFamilyCacheKey, BoundColumnFamily> columnFamilies = new HashMap<>();

  LayeredTransactionContext(
      final LayeredZeebeDb<?> db,
      final TransactionContext activeContext,
      final TransactionContext persistentContext) {
    this.db = db;
    this.activeContext = activeContext;
    this.persistentContext = persistentContext;
    transaction = new LayeredTransaction(db, activeContext, persistentContext);
  }

  TransactionContext activeContext() {
    return activeContext;
  }

  TransactionContext persistentContext() {
    return persistentContext;
  }

  boolean isTombstoned(final byte[] rawKey) {
    return transaction.isTombstoned(rawKey);
  }

  NavigableSet<byte[]> visibleTombstones() {
    return transaction.visibleTombstones();
  }

  void addTombstone(final byte[] rawKey) {
    transaction.addTombstone(rawKey);
  }

  void clearTombstone(final byte[] rawKey) {
    transaction.clearTombstone(rawKey);
  }

  @SuppressWarnings("unchecked")
  synchronized <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> getOrCreateColumnFamily(
          final Enum<?> columnFamily,
          final KeyType keyInstance,
          final ValueType valueInstance,
          final Supplier<ColumnFamily<KeyType, ValueType>> factory) {
    final var cacheKey =
        new ColumnFamilyCacheKey(
            columnFamily.getDeclaringClass(),
            columnFamily.name(),
            keyInstance.getClass(),
            valueInstance.getClass());
    final var existing = columnFamilies.get(cacheKey);

    if (existing != null) {
      return (ColumnFamily<KeyType, ValueType>) existing.columnFamily;
    }

    final var created = factory.get();
    columnFamilies.put(
        cacheKey, new BoundColumnFamily(created, keyInstance.getClass(), valueInstance.getClass()));
    return created;
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
          "Unexpected error occurred during layered db transaction operation.", ex);
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

  private record ColumnFamilyCacheKey(
      Class<?> columnFamilyEnumType,
      String columnFamilyName,
      Class<?> keyType,
      Class<?> valueType) {}

  private record BoundColumnFamily(
      ColumnFamily<?, ?> columnFamily, Class<?> keyType, Class<?> valueType) {}

  static final class LayeredTransaction implements ZeebeDbTransaction {

    private final LayeredZeebeDb<?> db;
    private final TransactionContext activeContext;
    private final TransactionContext persistentContext;

    private final NavigableSet<byte[]> pendingTombstones = new TreeSet<>(Arrays::compareUnsigned);
    private final NavigableSet<byte[]> pendingTombstoneRemovals =
        new TreeSet<>(Arrays::compareUnsigned);

    private @Nullable ZeebeDbTransaction activeTransaction;
    private @Nullable ZeebeDbTransaction persistentTransaction;
    private boolean inTransaction;

    LayeredTransaction(
        final LayeredZeebeDb<?> db,
        final TransactionContext activeContext,
        final TransactionContext persistentContext) {
      this.db = db;
      this.activeContext = activeContext;
      this.persistentContext = persistentContext;
    }

    boolean isInCurrentTransaction() {
      return inTransaction;
    }

    boolean isTombstoned(final byte[] rawKey) {
      if (pendingTombstones.contains(rawKey)) {
        return true;
      }
      if (pendingTombstoneRemovals.contains(rawKey)) {
        return false;
      }
      return db.isCommittedTombstoned(rawKey);
    }

    NavigableSet<byte[]> visibleTombstones() {
      final NavigableSet<byte[]> visible = new TreeSet<>(Arrays::compareUnsigned);
      visible.addAll(db.snapshotCommittedTombstones());
      visible.removeAll(pendingTombstoneRemovals);
      visible.addAll(pendingTombstones);
      return visible;
    }

    void addTombstone(final byte[] rawKey) {
      final var copy = Arrays.copyOf(rawKey, rawKey.length);
      pendingTombstoneRemovals.remove(copy);
      pendingTombstones.add(copy);
    }

    void clearTombstone(final byte[] rawKey) {
      if (pendingTombstones.remove(rawKey)) {
        return;
      }

      if (db.isCommittedTombstoned(rawKey)) {
        pendingTombstoneRemovals.add(Arrays.copyOf(rawKey, rawKey.length));
      }
    }

    void reset() {
      activeTransaction = activeContext.getCurrentTransaction();
      persistentTransaction = persistentContext.getCurrentTransaction();
      pendingTombstones.clear();
      pendingTombstoneRemovals.clear();
      inTransaction = true;
    }

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

    void commitInternal() throws Exception {
      final var persistent = java.util.Objects.requireNonNull(persistentTransaction);
      final var active = java.util.Objects.requireNonNull(activeTransaction);

      try {
        persistent.commit();
        active.commit();
        db.applyCommittedTombstoneChanges(pendingTombstones, pendingTombstoneRemovals);
      } finally {
        inTransaction = false;
        clearTransactions();
        pendingTombstones.clear();
        pendingTombstoneRemovals.clear();
      }
    }

    void rollbackInternal() throws Exception {
      Exception failure = null;

      if (persistentTransaction != null) {
        try {
          persistentTransaction.rollback();
        } catch (final Exception e) {
          failure = e;
        }
      }

      if (activeTransaction != null) {
        try {
          activeTransaction.rollback();
        } catch (final Exception e) {
          if (failure == null) {
            failure = e;
          } else {
            failure.addSuppressed(e);
          }
        }
      }

      inTransaction = false;
      clearTransactions();
      pendingTombstones.clear();
      pendingTombstoneRemovals.clear();

      if (failure != null) {
        throw failure;
      }
    }

    private void clearTransactions() {
      activeTransaction = null;
      persistentTransaction = null;
    }
  }
}
