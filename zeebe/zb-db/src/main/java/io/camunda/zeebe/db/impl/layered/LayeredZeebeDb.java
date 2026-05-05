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
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.rocksdb.DbNullKey;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Layered ZeebeDb with an active in-memory cache in front of the persistent RocksDB-backed state.
 */
public final class LayeredZeebeDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  private final ZeebeDb<ColumnFamilyType> activeDb;
  private final ZeebeDb<ColumnFamilyType> persistentDb;
  private final NavigableSet<byte[]> committedTombstones = new TreeSet<>(Arrays::compareUnsigned);
  private final Map<RegisteredColumnFamilyKey, RegisteredColumnFamily> registeredColumnFamilies =
      new HashMap<>();

  public LayeredZeebeDb(
      final ZeebeDb<ColumnFamilyType> activeDb, final ZeebeDb<ColumnFamilyType> persistentDb) {
    this.activeDb = activeDb;
    this.persistentDb = persistentDb;
  }

  ZeebeDb<ColumnFamilyType> activeDb() {
    return activeDb;
  }

  ZeebeDb<ColumnFamilyType> persistentDb() {
    return persistentDb;
  }

  synchronized boolean isCommittedTombstoned(final byte[] rawKey) {
    return committedTombstones.contains(rawKey);
  }

  synchronized NavigableSet<byte[]> snapshotCommittedTombstones() {
    final NavigableSet<byte[]> snapshot = new TreeSet<>(Arrays::compareUnsigned);
    committedTombstones.forEach(key -> snapshot.add(Arrays.copyOf(key, key.length)));
    return snapshot;
  }

  synchronized void applyCommittedTombstoneChanges(
      final NavigableSet<byte[]> additions, final NavigableSet<byte[]> removals) {
    removals.forEach(committedTombstones::remove);
    additions.forEach(key -> committedTombstones.add(Arrays.copyOf(key, key.length)));
  }

  synchronized void clearCommittedTombstones() {
    committedTombstones.clear();
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
            : new LayeredTransactionContext(this, activeDb.createContext(), context);

    final ColumnFamily<KeyType, ValueType> columnFamilyInstance =
        layeredContext.getOrCreateColumnFamily(
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
                  this,
                  layeredContext,
                  columnFamily,
                  activeColumnFamily,
                  persistentColumnFamily,
                  keyInstance,
                  valueInstance);
            });

    registerColumnFamily(columnFamily, keyInstance, valueInstance, columnFamilyInstance);
    return columnFamilyInstance;
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    LayeredSnapshotFlusher.flush(this);
    persistentDb.createSnapshot(snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return persistentDb.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return new LayeredTransactionContext(
        this, activeDb.createContext(), persistentDb.createContext());
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    return createColumnFamily(column, context, DbNullKey.INSTANCE, DbNil.INSTANCE).isEmpty();
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

  synchronized Iterable<RegisteredColumnFamily> registeredColumnFamilies() {
    return registeredColumnFamilies.values().stream().toList();
  }

  @SuppressWarnings("unchecked")
  private <ValueType extends DbValue> ValueType newValueInstance(final ValueType valueInstance) {
    return (ValueType) valueInstance.newInstance();
  }

  private <KeyType extends DbKey, ValueType extends DbValue> void registerColumnFamily(
      final ColumnFamilyType columnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final ColumnFamily<KeyType, ValueType> columnFamilyInstance) {
    synchronized (this) {
      registeredColumnFamilies.putIfAbsent(
          new RegisteredColumnFamilyKey(
              columnFamily.getDeclaringClass(),
              columnFamily.name(),
              keyInstance.getClass(),
              valueInstance.getClass()),
          new RegisteredColumnFamily(
              columnFamily,
              keyInstance.getClass(),
              valueInstance.getClass(),
              (LayeredColumnFamily<?, ?>) columnFamilyInstance));
    }
  }

  record RegisteredColumnFamilyKey(
      Class<?> columnFamilyEnumType,
      String columnFamilyName,
      Class<?> keyType,
      Class<?> valueType) {}

  record RegisteredColumnFamily(
      Enum<?> columnFamily, Class<?> keyType, Class<?> valueType, LayeredColumnFamily<?, ?> layer) {
    @SuppressWarnings("unchecked")
    <KeyType extends DbKey, ValueType extends DbValue>
        LayeredColumnFamily<KeyType, ValueType> typed() {
      return (LayeredColumnFamily<KeyType, ValueType>) layer;
    }

    boolean matches(final byte[] rawKey) {
      return typed().matchesColumnFamily(rawKey);
    }
  }
}
