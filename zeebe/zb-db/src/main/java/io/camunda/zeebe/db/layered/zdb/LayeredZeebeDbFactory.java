/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.db.layered.SnapshotSource;
import io.camunda.zeebe.protocol.EnumValue;
import java.io.File;
import java.util.Objects;

/**
 * Decorates a {@link ZeebeDbFactory} so every runtime database it creates is wrapped in a {@link
 * LayeredZeebeDb}. Snapshot-only databases are <em>not</em> wrapped — they exist purely to copy
 * checkpointed files and never see the layered write path.
 *
 * <p><b>Snapshot source:</b> when created via {@link #of(ZeebeDbFactory, LayeredZeebeDbConfig,
 * Class)} and the delegate produces a {@link ZeebeTransactionDb}, the wrapped databases read their
 * views through a pinning {@link SnapshotSource} over the same RocksDB instance ({@link
 * ZeebeTransactionDb#pinnedSnapshotSource(Class)}) — required as soon as asynchronous readers
 * consume {@link io.camunda.zeebe.db.layered.ReadOnlyView}s. Without a column family class (the
 * two-argument {@link #of(ZeebeDbFactory, LayeredZeebeDbConfig)}), or when the delegate produces
 * some other database, the wrapped databases fall back to the unpinned source, which is only sound
 * for single-threaded wirings (see {@link UnpinnedSnapshotSource}).
 */
public final class LayeredZeebeDbFactory<
        ColumnFamilyType extends Enum<ColumnFamilyType> & EnumValue>
    implements ZeebeDbFactory<ColumnFamilyType> {

  private final ZeebeDbFactory<ColumnFamilyType> delegate;
  private final LayeredZeebeDbConfig config;
  private final Class<ColumnFamilyType> columnFamilyType;

  private LayeredZeebeDbFactory(
      final ZeebeDbFactory<ColumnFamilyType> delegate,
      final LayeredZeebeDbConfig config,
      final Class<ColumnFamilyType> columnFamilyType) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.config = Objects.requireNonNull(config, "config");
    this.columnFamilyType = columnFamilyType;
  }

  /** Creates a factory whose databases fall back to the unpinned snapshot source. */
  public static <ColumnFamilyType extends Enum<ColumnFamilyType> & EnumValue>
      LayeredZeebeDbFactory<ColumnFamilyType> of(
          final ZeebeDbFactory<ColumnFamilyType> delegate, final LayeredZeebeDbConfig config) {
    return new LayeredZeebeDbFactory<>(delegate, config, null);
  }

  /**
   * Creates a factory whose databases read views through a pinning snapshot source over the
   * delegate's RocksDB instance, routed by the given column family enum.
   */
  public static <ColumnFamilyType extends Enum<ColumnFamilyType> & EnumValue>
      LayeredZeebeDbFactory<ColumnFamilyType> of(
          final ZeebeDbFactory<ColumnFamilyType> delegate,
          final LayeredZeebeDbConfig config,
          final Class<ColumnFamilyType> columnFamilyType) {
    return new LayeredZeebeDbFactory<>(
        delegate, config, Objects.requireNonNull(columnFamilyType, "columnFamilyType"));
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName, final boolean avoidFlush) {
    return wrap(delegate.createDb(pathName, avoidFlush));
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName) {
    return wrap(delegate.createDb(pathName));
  }

  @Override
  public ZeebeDb<ColumnFamilyType> openSnapshotOnlyDb(final File path) {
    return delegate.openSnapshotOnlyDb(path);
  }

  private ZeebeDb<ColumnFamilyType> wrap(final ZeebeDb<ColumnFamilyType> inner) {
    return new LayeredZeebeDb<>(inner, config, pinnedSnapshotSourceOf(inner));
  }

  private SnapshotSource pinnedSnapshotSourceOf(final ZeebeDb<ColumnFamilyType> inner) {
    if (columnFamilyType != null && inner instanceof final ZeebeTransactionDb<?> transactionDb) {
      return transactionDb.pinnedSnapshotSource(columnFamilyType);
    }
    return null;
  }
}
