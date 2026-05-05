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
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Optional;

/**
 * Layered ZeebeDb with an active in-memory cache in front of the persistent RocksDB-backed state.
 */
public final class LayeredZeebeDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  private final ZeebeDb<ColumnFamilyType> activeDb;
  private final ZeebeDb<ColumnFamilyType> persistentDb;

  public LayeredZeebeDb(
      final ZeebeDb<ColumnFamilyType> activeDb, final ZeebeDb<ColumnFamilyType> persistentDb) {
    this.activeDb = activeDb;
    this.persistentDb = persistentDb;
  }

  ZeebeDb<ColumnFamilyType> persistentDb() {
    return persistentDb;
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    final var layeredContext =
        context instanceof final LayeredTransactionContext existingContext
            ? existingContext
            : new LayeredTransactionContext(activeDb.createContext(), context);

    return layeredContext.getOrCreateColumnFamily(
        columnFamily,
        keyInstance,
        valueInstance,
        () -> {
          final var activeColumnFamily =
              activeDb.createColumnFamily(
                  columnFamily,
                  layeredContext.activeContext(),
                  keyInstance,
                  newValueInstance(valueInstance));
          final var persistentColumnFamily =
              persistentDb.createColumnFamily(
                  columnFamily,
                  layeredContext.persistentContext(),
                  keyInstance,
                  newValueInstance(valueInstance));

          return new LayeredColumnFamily<>(
              layeredContext, activeColumnFamily, persistentColumnFamily, valueInstance);
        });
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    persistentDb.createSnapshot(snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return persistentDb.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return new LayeredTransactionContext(activeDb.createContext(), persistentDb.createContext());
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    return createColumnFamily(
            column,
            context,
            io.camunda.zeebe.db.impl.rocksdb.DbNullKey.INSTANCE,
            io.camunda.zeebe.db.impl.DbNil.INSTANCE)
        .isEmpty();
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return persistentDb.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    persistentDb.exportMetrics();
  }

  @Override
  public void close() throws Exception {
    try {
      activeDb.close();
    } finally {
      persistentDb.close();
    }
  }

  @SuppressWarnings("unchecked")
  private <ValueType extends DbValue> ValueType newValueInstance(final ValueType valueInstance) {
    return (ValueType) valueInstance.newInstance();
  }
}
