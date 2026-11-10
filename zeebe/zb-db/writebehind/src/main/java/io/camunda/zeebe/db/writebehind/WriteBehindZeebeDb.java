/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.RawAccessibleDb;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Optional;

/**
 * {@link ZeebeDb} that commits to memory and writes to the wrapped DB in the background, one whole
 * transaction or more at a time. The column families are the wrapped DB's, unchanged; only the
 * transaction underneath them differs.
 */
public final class WriteBehindZeebeDb<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  private final RawAccessibleDb<ColumnFamilyType> delegate;
  private final WriteBehindStore store;

  WriteBehindZeebeDb(
      final RawAccessibleDb<ColumnFamilyType> delegate, final WriteBehindConfiguration config) {
    this.delegate = delegate;
    store = new WriteBehindStore(delegate.rawAccess(), config);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return delegate.createColumnFamily(columnFamily, context, keyInstance, valueInstance);
  }

  /**
   * Snapshots the wrapped DB once it holds everything committed before this call, so that the
   * snapshot holds at least the state that the caller has observed.
   */
  @Override
  public void createSnapshot(final File snapshotDir) {
    store.awaitFlushed();
    delegate.createSnapshot(snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return delegate.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return new WriteBehindTransactionContext(new WriteBehindTransaction(store));
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    return delegate.isEmpty(column, context);
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return delegate.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    delegate.exportMetrics();
  }

  /** Stops the writer before closing the wrapped DB, so that it never writes to a closed DB. */
  @Override
  public void close() throws Exception {
    try {
      store.close();
    } finally {
      delegate.close();
    }
  }
}
