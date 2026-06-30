/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.analytics.state.api.KeyValueStore;
import io.camunda.analytics.state.api.StateStoreProvider;
import io.camunda.analytics.state.memory.InMemoryStateStoreProvider;
import io.camunda.analytics.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Optional;

/**
 * The {@link BaseProjectionStore} on top of the {@code analytics-state-store} library: the
 * projection lives in one key-value store and the consumed source position in another, both opened
 * from a single {@link StateStoreProvider}. Backed by RocksDB in production ({@link #rocksDb}) or
 * by the heap in tests ({@link #inMemory}) — the fold logic is identical against either.
 */
public final class StateBackedProjectionStore implements BaseProjectionStore, AutoCloseable {

  /** Single-entry key for the consumed-position store. */
  private static final long POSITION_ENTRY_KEY = 0L;

  private final StateStoreProvider<AnalyticsColumnFamilies> provider;

  private final DbLong instanceKey = new DbLong();
  private final PersistedProjection persistedProjection = new PersistedProjection();
  private final KeyValueStore<DbLong, PersistedProjection> projections;

  private final DbLong positionKey = new DbLong();
  private final DbLong positionValue = new DbLong();
  private final KeyValueStore<DbLong, DbLong> consumedPosition;

  private StateBackedProjectionStore(final StateStoreProvider<AnalyticsColumnFamilies> provider) {
    this.provider = provider;
    projections =
        provider.keyValueStore(
            AnalyticsColumnFamilies.PROCESS_INSTANCE_PROJECTION, instanceKey, persistedProjection);
    consumedPosition =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, positionKey, positionValue);
  }

  /**
   * A persistent store under {@code directory} (created if needed) — the production local cache.
   */
  public static StateBackedProjectionStore rocksDb(
      final File directory, final MeterRegistry meterRegistry) {
    return new StateBackedProjectionStore(RocksDbStateStoreProvider.open(directory, meterRegistry));
  }

  /** A non-persistent store for offline unit tests. */
  public static StateBackedProjectionStore inMemory() {
    return new StateBackedProjectionStore(new InMemoryStateStoreProvider<>());
  }

  @Override
  public Optional<ProcessInstanceProjection> get(final long processInstanceKey) {
    instanceKey.wrapLong(processInstanceKey);
    return projections.get(instanceKey).map(p -> p.toProjection(processInstanceKey));
  }

  @Override
  public void put(final ProcessInstanceProjection projection) {
    instanceKey.wrapLong(projection.processInstanceKey());
    projections.put(instanceKey, persistedProjection.wrap(projection));
  }

  @Override
  public long getConsumedPosition() {
    positionKey.wrapLong(POSITION_ENTRY_KEY);
    return consumedPosition.get(positionKey).map(DbLong::getValue).orElse(NO_POSITION);
  }

  @Override
  public void setConsumedPosition(final long position) {
    positionKey.wrapLong(POSITION_ENTRY_KEY);
    positionValue.wrapLong(position);
    consumedPosition.put(positionKey, positionValue);
  }

  @Override
  public void close() throws Exception {
    provider.close();
  }
}
