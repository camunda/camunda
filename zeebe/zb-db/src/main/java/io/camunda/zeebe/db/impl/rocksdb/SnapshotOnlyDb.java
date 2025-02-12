/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.util.micrometer.StatefulMeterRegistry;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.agrona.CloseHelper;
import org.rocksdb.Checkpoint;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;

final class SnapshotOnlyDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {
  private static final Logger LOG = Loggers.DB_LOGGER;

  private final RocksDB db;
  private final List<AutoCloseable> managedResources;

  public SnapshotOnlyDb(final RocksDB db, final List<AutoCloseable> managedResources) {
    this.db = db;
    this.managedResources = managedResources;
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    throw unsupported("createColumnFamily");
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    try (final var checkpoint = Checkpoint.create(db)) {
      checkpoint.createCheckpoint(snapshotDir.getAbsolutePath());
    } catch (final RocksDBException e) {
      throw new ZeebeDbException(
          "Failed to take a RocksDB snapshot at '%s'".formatted(snapshotDir), e);
    }
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    throw unsupported("getProperty");
  }

  @Override
  public TransactionContext createContext() {
    throw unsupported("createContext");
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    throw unsupported("isEmpty");
  }

  @Override
  public StatefulMeterRegistry getMeterRegistry() {
    throw new UnsupportedOperationException(
        "No meter registry is available for a snapshot only DB, as no metrics are collected.");
  }

  @Override
  public void close() {
    Collections.reverse(managedResources);
    CloseHelper.closeAll(
        error ->
            LOG.error("Failed to close RockDB resource, which may lead to leaked resources", error),
        managedResources);
  }

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
      ZeebeDb<ColumnFamilyType> openDb(
          final Options options, final String path, final List<AutoCloseable> managedResources)
          throws RocksDBException {
    final RocksDB db = RocksDB.openReadOnly(options, path);
    managedResources.add(db);

    return new SnapshotOnlyDb<>(db, managedResources);
  }

  private UnsupportedOperationException unsupported(final String operation) {
    return new UnsupportedOperationException(
        "Failed to execute 'ZeebeDb#%s'; this operation is not supported on a snapshot-only DB"
            .formatted(operation));
  }
}
