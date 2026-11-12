/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The state of one partition, kept in the state table under its own state id. The table is shared
 * by all partitions, which never see each other's rows.
 */
final class RdbmsZeebeDb<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyNames> {

  private final RdbmsStorage storage;
  private final long stateId;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final MeterRegistry meterRegistry;
  private final Set<RdbmsTransaction> transactions = ConcurrentHashMap.newKeySet();

  RdbmsZeebeDb(
      final RdbmsStorage storage,
      final long stateId,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final MeterRegistry meterRegistry) {
    this.storage = storage;
    this.stateId = stateId;
    this.consistencyChecksSettings = consistencyChecksSettings;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyNames columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return new RdbmsColumnFamily<>(
        rdbmsContext(context), columnFamily, keyInstance, valueInstance, consistencyChecksSettings);
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    throw new UnsupportedOperationException(
        "Expected to take a snapshot of the partition state, but the relational storage can't yet");
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return Optional.empty();
  }

  @Override
  public TransactionContext createContext() {
    final var transaction = new RdbmsTransaction(storage, stateId);
    transactions.add(transaction);
    return new RdbmsTransactionContext(transaction);
  }

  @Override
  public boolean isEmpty(final ColumnFamilyNames column, final TransactionContext context) {
    return createColumnFamily(column, context, new DbBytes(), new DbBytes()).isEmpty();
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  /** Rolls back the transactions still open, which returns their connections. */
  @Override
  public void close() {
    try {
      transactions.forEach(RdbmsTransaction::rollbackInternal);
      transactions.clear();
    } finally {
      MicrometerUtil.close(meterRegistry);
    }
  }

  private RdbmsTransactionContext rdbmsContext(final TransactionContext context) {
    if (context instanceof final RdbmsTransactionContext rdbmsContext) {
      return rdbmsContext;
    }
    throw new IllegalArgumentException(
        "Expected a transaction context of the relational storage, but got " + context);
  }
}
