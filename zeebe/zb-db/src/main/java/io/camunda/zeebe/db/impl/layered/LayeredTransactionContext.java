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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class LayeredTransactionContext implements TransactionContext {

  private final TransactionContext activeContext;
  private final TransactionContext persistentContext;
  private final LayeredTransaction transaction;
  private final Map<Enum<?>, BoundColumnFamily> columnFamilies = new HashMap<>();

  LayeredTransactionContext(
      final TransactionContext activeContext, final TransactionContext persistentContext) {
    this.activeContext = activeContext;
    this.persistentContext = persistentContext;
    transaction = new LayeredTransaction(activeContext, persistentContext);
  }

  TransactionContext activeContext() {
    return activeContext;
  }

  TransactionContext persistentContext() {
    return persistentContext;
  }

  @SuppressWarnings("unchecked")
  synchronized <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> getOrCreateColumnFamily(
          final Enum<?> columnFamily,
          final KeyType keyInstance,
          final ValueType valueInstance,
          final Supplier<ColumnFamily<KeyType, ValueType>> factory) {
    final var keyType = keyInstance.getClass();
    final var valueType = valueInstance.getClass();
    final var existing = columnFamilies.get(columnFamily);

    if (existing != null) {
      if (!existing.keyType.equals(keyType) || !existing.valueType.equals(valueType)) {
        throw new IllegalStateException(
            ("Column family %s was already created for key type %s and value type %s, "
                    + "but was requested again with key type %s and value type %s")
                .formatted(
                    columnFamily,
                    existing.keyType.getName(),
                    existing.valueType.getName(),
                    keyType.getName(),
                    valueType.getName()));
      }

      return (ColumnFamily<KeyType, ValueType>) existing.columnFamily;
    }

    final var created = factory.get();
    columnFamilies.put(columnFamily, new BoundColumnFamily(created, keyType, valueType));
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

  private record BoundColumnFamily(
      ColumnFamily<?, ?> columnFamily, Class<?> keyType, Class<?> valueType) {}

  static final class LayeredTransaction implements ZeebeDbTransaction {

    private final TransactionContext activeContext;
    private final TransactionContext persistentContext;

    private @Nullable ZeebeDbTransaction activeTransaction;
    private @Nullable ZeebeDbTransaction persistentTransaction;
    private boolean inTransaction;

    LayeredTransaction(
        final TransactionContext activeContext, final TransactionContext persistentContext) {
      this.activeContext = activeContext;
      this.persistentContext = persistentContext;
    }

    boolean isInCurrentTransaction() {
      return inTransaction;
    }

    void reset() {
      activeTransaction = activeContext.getCurrentTransaction();
      persistentTransaction = persistentContext.getCurrentTransaction();
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
      } finally {
        inTransaction = false;
        clearTransactions();
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
