/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.sql.Connection;
import java.util.Optional;

/**
 * Experimental: a {@link ZeebeDb} whose contexts are {@link MixedTransactionContext}s, so state
 * classes can keep part of the state in a relational database while the rest stays in RocksDB.
 *
 * <p>Everything else delegates to the RocksDB database. Column families always get the RocksDB
 * context, since they cast its transaction to the RocksDB one.
 *
 * <p>Owns the JDBC connection, which all contexts share: only the stream processor actor may use
 * the JDBC side of a context. Closing this only closes the connection; the RocksDB database is
 * owned and closed by the partition.
 */
public final class MixedZeebeDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  private final ZeebeDb<ColumnFamilyType> rocksDb;
  private final Connection connection;

  public MixedZeebeDb(final ZeebeDb<ColumnFamilyType> rocksDb, final Connection connection) {
    this.rocksDb = rocksDb;
    this.connection = connection;
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return rocksDb.createColumnFamily(columnFamily, unwrap(context), keyInstance, valueInstance);
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    rocksDb.createSnapshot(snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return rocksDb.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return new MixedTransactionContext(
        rocksDb.createContext(), new JdbcTransactionContext(connection));
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    return rocksDb.isEmpty(column, unwrap(context));
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return rocksDb.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    rocksDb.exportMetrics();
  }

  @Override
  public void close() throws Exception {
    connection.close();
  }

  private static TransactionContext unwrap(final TransactionContext context) {
    return context instanceof final MixedTransactionContext mixed ? mixed.rocksDb() : context;
  }
}
