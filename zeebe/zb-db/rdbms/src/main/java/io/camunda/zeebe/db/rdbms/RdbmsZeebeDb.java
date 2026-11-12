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
import io.camunda.zeebe.db.RawBatchWriter;
import io.camunda.zeebe.db.RawWrite;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The state of one partition, kept in the state tables under its partition id. The tables are
 * shared by all partitions, which never see each other's rows.
 *
 * <p>It writes with the term it was opened with, until a newer leader claims the state. Opened
 * without a term, it only reads. It takes the write-behind cache's batches with their positions.
 */
final class RdbmsZeebeDb<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyNames>, RawBatchWriter {

  private final RdbmsStorage storage;
  private final StateMarker marker;
  private final OptionalLong term;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final MeterRegistry meterRegistry;
  private final Set<RdbmsTransaction> transactions = ConcurrentHashMap.newKeySet();
  private final RdbmsTransaction batchTransaction;

  /**
   * @param term the term to write with, or empty to only read
   */
  RdbmsZeebeDb(
      final RdbmsStorage storage,
      final StateMarker marker,
      final OptionalLong term,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final MeterRegistry meterRegistry) {
    this.storage = storage;
    this.marker = marker;
    this.term = term;
    this.consistencyChecksSettings = consistencyChecksSettings;
    this.meterRegistry = meterRegistry;
    batchTransaction = new RdbmsTransaction(storage, marker.partitionId(), marker.stateId(), term);
    transactions.add(batchTransaction);
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

  /**
   * Writes the marker that names this state. It reads nothing from the state: behind the cache, the
   * cache first waits until the state holds everything the snapshot stands for.
   */
  @Override
  public void createSnapshot(final File snapshotDir) {
    marker.writeTo(snapshotDir.toPath());
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return Optional.empty();
  }

  @Override
  public TransactionContext createContext() {
    final var transaction =
        new RdbmsTransaction(storage, marker.partitionId(), marker.stateId(), term);
    transactions.add(transaction);
    return new RdbmsTransactionContext(transaction);
  }

  /** Writes on a transaction of its own, apart from the transactions of the contexts. */
  @Override
  public void write(final List<RawWrite> writes, final long position) {
    batchTransaction.writeBatch(writes, position);
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
