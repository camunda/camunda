/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.FineGrainedColumnFamilyMetrics;
import io.camunda.zeebe.db.impl.NoopColumnFamilyMetrics;
import io.camunda.zeebe.db.impl.rocksdb.DbNullKey;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;

/**
 * A pure in-memory implementation of {@link ZeebeDb}. All data is stored in a {@link
 * ConcurrentSkipListMap} using unsigned lexicographic ordering on serialized keys — the same
 * ordering that RocksDB uses by default.
 *
 * <p>Keys are serialized to {@code byte[]} (needed for correct lexicographic ordering). Values are
 * stored as Java objects via {@link DbValue#copy()} — no serialization on the write path.
 * Serialization of values is deferred until snapshot time or flush-to-RocksDB.
 *
 * <h3>Transaction isolation</h3>
 *
 * Every {@link TransactionContext} returned by {@link #createContext()} maintains a private write
 * buffer. Writes are accumulated in this buffer during a transaction and only become visible to
 * other contexts when the transaction commits. This gives read-committed isolation: concurrent
 * readers (e.g. async scheduled tasks with their own {@code TransactionContext}) never observe
 * partial/uncommitted state.
 */
public class InMemoryZeebeDb<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyType> {

  /**
   * The committed store. Keys are {@code [8-byte CF prefix] + [serialized DbKey]} bytes. Values are
   * {@link DbValue} copies — actual Java objects, not serialized bytes.
   */
  final ConcurrentSkipListMap<byte[], DbValue> committedData =
      new ConcurrentSkipListMap<>(Arrays::compareUnsigned);

  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final AccessMetricsConfiguration accessMetricsConfiguration;
  private final MeterRegistry meterRegistry;
  private volatile boolean closed;

  public InMemoryZeebeDb() {
    this(
        new ConsistencyChecksSettings(true, true),
        new AccessMetricsConfiguration(Kind.NONE, 1),
        new SimpleMeterRegistry());
  }

  public InMemoryZeebeDb(
      final ConsistencyChecksSettings consistencyChecksSettings,
      final AccessMetricsConfiguration accessMetricsConfiguration,
      final MeterRegistry meterRegistry) {
    this.consistencyChecksSettings = consistencyChecksSettings;
    this.accessMetricsConfiguration = accessMetricsConfiguration;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    final var metrics =
        switch (accessMetricsConfiguration.kind()) {
          case NONE -> new NoopColumnFamilyMetrics();
          case FINE -> new FineGrainedColumnFamilyMetrics(columnFamily, meterRegistry);
        };
    return new InMemoryColumnFamily<>(
        this,
        consistencyChecksSettings,
        columnFamily,
        context,
        keyInstance,
        valueInstance,
        metrics);
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    if (!snapshotDir.exists() && !snapshotDir.mkdirs()) {
      throw new IllegalStateException("Failed to create snapshot directory: " + snapshotDir);
    }
    // Snapshot is the one place we DO serialize — values are written to disk.
    InMemoryDbSnapshotSupport.writeSnapshot(committedData, snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return Optional.empty();
  }

  @Override
  public TransactionContext createContext() {
    return new InMemoryTransactionContext(this);
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    return createColumnFamily(column, context, DbNullKey.INSTANCE, DbNil.INSTANCE).isEmpty();
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @Override
  public void close() {
    closed = true;
    committedData.clear();
  }

  boolean isClosed() {
    return closed;
  }

  public void forEachCommittedEntry(final BiConsumer<byte[], DbValue> consumer) {
    committedData.forEach(consumer);
  }
}
