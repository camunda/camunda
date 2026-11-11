/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.RawAccessibleDb;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbNullKey;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Optional;

/**
 * {@link ZeebeDb} that commits to memory and writes to the wrapped DB in the background, one whole
 * transaction or more at a time. Its column families keep values as objects; the wrapped DB is used
 * only as storage, through its raw access.
 */
public final class WriteBehindZeebeDb<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyType> {

  private final RawAccessibleDb<ColumnFamilyType> delegate;
  private final ConsistencyChecksSettings consistencyChecks;
  private final WriteBehindStore<RawKey, DbValue> store;

  WriteBehindZeebeDb(
      final RawAccessibleDb<ColumnFamilyType> delegate,
      final WriteBehindConfiguration config,
      final ConsistencyChecksSettings consistencyChecks) {
    this.delegate = delegate;
    this.consistencyChecks = consistencyChecks;
    store = new WriteBehindStore<>(new RawBackend(delegate.rawAccess()), config);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return new WriteBehindColumnFamily<>(
        columnFamily, ownContext(context), consistencyChecks, keyInstance, valueInstance);
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
    return createColumnFamily(column, context, DbNullKey.INSTANCE, DbNil.INSTANCE).isEmpty();
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return delegate.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    delegate.exportMetrics();
    store.exportMetrics(delegate.getMeterRegistry());
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

  private static WriteBehindTransactionContext ownContext(final TransactionContext context) {
    if (context instanceof final WriteBehindTransactionContext own) {
      return own;
    }
    throw new IllegalArgumentException(
        "Expected a transaction context of this DB, but got a %s"
            .formatted(context.getClass().getName()));
  }
}
