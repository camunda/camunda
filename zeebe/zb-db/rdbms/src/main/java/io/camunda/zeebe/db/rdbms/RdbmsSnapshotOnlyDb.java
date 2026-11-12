/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.ColumnFamily;
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
 * A snapshot opened only to copy it, as the recovery does into the runtime directory. Copying the
 * marker is all it does: the state stays in the database, and the DB opened on the copy attaches to
 * it.
 */
final class RdbmsSnapshotOnlyDb<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyNames> {

  private final StateMarker marker;

  RdbmsSnapshotOnlyDb(final StateMarker marker) {
    this.marker = marker;
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyNames columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    throw unsupported("createColumnFamily");
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    marker.writeTo(snapshotDir.toPath());
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
  public boolean isEmpty(final ColumnFamilyNames column, final TransactionContext context) {
    throw unsupported("isEmpty");
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    throw unsupported("getMeterRegistry");
  }

  @Override
  public void close() {}

  private static UnsupportedOperationException unsupported(final String operation) {
    return new UnsupportedOperationException(
        "Failed to execute 'ZeebeDb#%s'; a snapshot-only DB can only take snapshots"
            .formatted(operation));
  }
}
