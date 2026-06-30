/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Optional;

/**
 * RocksDB-backed {@link BaseProjectionStore} — the local fast cache for the base projection. State
 * is co-located with the source partition and recovered by replaying the source stream from the
 * persisted {@link #getConsumedPosition() consumed position}; this is the local-mode authoritative
 * store (an external backing can be plugged in behind the SPI for the outsourced mode).
 */
public final class RocksDbBaseProjectionStore implements BaseProjectionStore, AutoCloseable {

  private static final long POSITION_ENTRY_KEY = 0L;

  private final ZeebeDb<AnalyticsColumnFamilies> zeebeDb;
  private final TransactionContext context;

  private final DbLong instanceKey = new DbLong();
  private final PersistedProjection persistedProjection = new PersistedProjection();
  private final ColumnFamily<DbLong, PersistedProjection> projections;

  private final DbLong positionKey = new DbLong();
  private final DbLong positionValue = new DbLong();
  private final ColumnFamily<DbLong, DbLong> consumedPosition;

  public RocksDbBaseProjectionStore(final ZeebeDb<AnalyticsColumnFamilies> zeebeDb) {
    this.zeebeDb = zeebeDb;
    context = zeebeDb.createContext();
    projections =
        zeebeDb.createColumnFamily(
            AnalyticsColumnFamilies.PROCESS_INSTANCE_PROJECTION,
            context,
            instanceKey,
            persistedProjection);
    consumedPosition =
        zeebeDb.createColumnFamily(
            AnalyticsColumnFamilies.CONSUMED_POSITION, context, positionKey, positionValue);
  }

  /** Opens a RocksDB store in {@code directory}, creating it (and parents) if needed. */
  public static RocksDbBaseProjectionStore open(
      final File directory, final MeterRegistry meterRegistry) {
    try {
      Files.createDirectories(directory.toPath());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create projection store directory " + directory, e);
    }
    final ZeebeDbFactory<AnalyticsColumnFamilies> factory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            () -> meterRegistry);
    // avoidFlush=false: this store has no snapshots, so flush on close for durability.
    return new RocksDbBaseProjectionStore(factory.createDb(directory, false));
  }

  @Override
  public Optional<ProcessInstanceProjection> get(final long processInstanceKey) {
    instanceKey.wrapLong(processInstanceKey);
    final PersistedProjection stored = projections.get(instanceKey);
    return stored == null ? Optional.empty() : Optional.of(stored.toProjection(processInstanceKey));
  }

  @Override
  public void put(final ProcessInstanceProjection projection) {
    context.runInTransaction(
        () -> {
          instanceKey.wrapLong(projection.processInstanceKey());
          // fresh value so the variables array does not accumulate across reuse of a flyweight
          projections.upsert(instanceKey, new PersistedProjection().wrap(projection));
        });
  }

  @Override
  public long getConsumedPosition() {
    positionKey.wrapLong(POSITION_ENTRY_KEY);
    final DbLong stored = consumedPosition.get(positionKey);
    return stored == null ? NO_POSITION : stored.getValue();
  }

  @Override
  public void setConsumedPosition(final long position) {
    context.runInTransaction(
        () -> {
          positionKey.wrapLong(POSITION_ENTRY_KEY);
          positionValue.wrapLong(position);
          consumedPosition.upsert(positionKey, positionValue);
        });
  }

  @Override
  public void close() throws Exception {
    zeebeDb.close();
  }
}
