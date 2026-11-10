/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Optional;

/**
 * A {@link ZeebeDb} that keeps committed state in a write-behind cache and writes it to the backend
 * it wraps in the background. It only uses the backend through the {@link ZeebeDb} API, so it works
 * with any backend.
 */
final class WriteBehindZeebeDb<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyNames> {

  private final ZeebeDb<ColumnFamilyNames> backend;
  private final WriteBehindStore<ColumnFamilyNames> store;
  private final ConsistencyChecksSettings consistencyChecksSettings;

  WriteBehindZeebeDb(
      final ZeebeDb<ColumnFamilyNames> backend,
      final WriteBehindConfiguration configuration,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this.backend = backend;
    this.consistencyChecksSettings = consistencyChecksSettings;
    store = new WriteBehindStore<>(backend, configuration);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyNames columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return new CachedColumnFamily<>(
        store,
        cachedContext(context),
        columnFamily,
        keyInstance,
        valueInstance,
        consistencyChecksSettings);
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    throw new UnsupportedOperationException(
        "Expected to take a snapshot in "
            + snapshotDir
            + ", but the write-behind cache can't drain its pending writes before a snapshot yet");
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return backend.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return new CachedTransactionContext<>(store, backend);
  }

  @Override
  public boolean isEmpty(final ColumnFamilyNames column, final TransactionContext context) {
    throw new UnsupportedOperationException(
        "Expected to check whether "
            + column
            + " is empty, but the write-behind cache doesn't support scans yet");
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return backend.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    backend.exportMetrics();
  }

  /** Closes the cache first, so that its writer doesn't touch the backend once it is closed. */
  @Override
  public void close() throws Exception {
    try {
      store.close();
    } finally {
      backend.close();
    }
  }

  @SuppressWarnings("unchecked") // every CachedTransactionContext is created by createContext
  private CachedTransactionContext<ColumnFamilyNames> cachedContext(
      final TransactionContext context) {
    if (context instanceof final CachedTransactionContext<?> cached) {
      return (CachedTransactionContext<ColumnFamilyNames>) cached;
    }
    throw new IllegalArgumentException(
        "Expected a transaction context of the write-behind cache, but got " + context);
  }
}
