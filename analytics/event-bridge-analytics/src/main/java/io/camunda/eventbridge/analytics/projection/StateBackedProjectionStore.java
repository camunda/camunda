/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
import io.camunda.eventbridge.streaming.state.cache.WriteBackKeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryStateStoreProvider;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@link BaseProjectionStore} on top of the {@code analytics-streaming} state library: one
 * store for per-instance variables, one for in-flight element start times (the process instance
 * keys into it like any element), one for the per-partition consumed position, and the incident
 * flags/starts — all opened from a single {@link StateStoreProvider} shared with the rollups (one
 * RocksDB per stage).
 *
 * <p>Every store is a {@link WriteBackKeyValueStore}: the projection is held on the heap and reads
 * and writes never touch RocksDB during processing. {@link #checkpoint()} flushes all of them, so —
 * called inside the driver's checkpoint transaction alongside the rollups — the base projection,
 * the rollup cells, and the consumed offset commit as one atomic cut (no write-through per record).
 * Backed by RocksDB in production ({@link #fromProvider}/{@link #rocksDb}) or by the heap in tests
 * ({@link #inMemory}) — the fold logic is identical against either.
 */
public final class StateBackedProjectionStore implements BaseProjectionStore, AutoCloseable {

  private final StateStoreProvider<AnalyticsColumnFamilies> provider;
  private final boolean ownsProvider;

  private final DbLong instanceKey = new DbLong();
  private final PersistedVariables persistedVariables = new PersistedVariables();
  private final WriteBackKeyValueStore<DbLong, PersistedVariables> variables;

  private final DbInt positionKey = new DbInt();
  private final DbLong positionValue = new DbLong();
  private final WriteBackKeyValueStore<DbInt, DbLong> consumedPosition;

  private final WriteBackKeyValueStore<DbString, DbLong> elementStarts;

  private final DbLong incidentKey = new DbLong();
  private final DbLong incidentFlag = new DbLong();
  private final WriteBackKeyValueStore<DbLong, DbLong> incidents;

  private final DbLong incidentStartKey = new DbLong();
  private final DbLong incidentStartValue = new DbLong();
  private final WriteBackKeyValueStore<DbLong, DbLong> incidentStarts;

  private StateBackedProjectionStore(
      final StateStoreProvider<AnalyticsColumnFamilies> provider, final boolean ownsProvider) {
    this.provider = provider;
    this.ownsProvider = ownsProvider;
    variables =
        new WriteBackKeyValueStore<>(
            provider.keyValueStore(
                AnalyticsColumnFamilies.INSTANCE_VARIABLES, new DbLong(), new PersistedVariables()),
            new DbLong(),
            new PersistedVariables());
    consumedPosition =
        new WriteBackKeyValueStore<>(
            provider.keyValueStore(
                AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong()),
            new DbInt(),
            new DbLong());
    elementStarts =
        new WriteBackKeyValueStore<>(
            provider.keyValueStore(
                AnalyticsColumnFamilies.ELEMENT_START, new DbString(), new DbLong()),
            new DbString(),
            new DbLong());
    incidents =
        new WriteBackKeyValueStore<>(
            provider.keyValueStore(
                AnalyticsColumnFamilies.INSTANCE_INCIDENT, new DbLong(), new DbLong()),
            new DbLong(),
            new DbLong());
    incidentStarts =
        new WriteBackKeyValueStore<>(
            provider.keyValueStore(
                AnalyticsColumnFamilies.INCIDENT_START, new DbLong(), new DbLong()),
            new DbLong(),
            new DbLong());
  }

  /**
   * The per-element-instance activation-time store. The process instance's own start is held here
   * too, keyed like any element. Write-back cached, flushed on {@link #checkpoint()}.
   */
  public KeyValueStore<DbString, DbLong> elementStarts() {
    return elementStarts;
  }

  /** Shares an already-open provider (the caller owns its lifecycle) — the production wiring. */
  public static StateBackedProjectionStore fromProvider(
      final StateStoreProvider<AnalyticsColumnFamilies> provider) {
    return new StateBackedProjectionStore(provider, false);
  }

  /** A persistent store under {@code directory} (created if needed), owning its own provider. */
  public static StateBackedProjectionStore rocksDb(
      final File directory, final MeterRegistry meterRegistry) {
    return new StateBackedProjectionStore(
        RocksDbStateStoreProvider.open(directory, meterRegistry), true);
  }

  /** A non-persistent store for offline unit tests. */
  public static StateBackedProjectionStore inMemory() {
    return new StateBackedProjectionStore(new InMemoryStateStoreProvider<>(), true);
  }

  @Override
  public Map<String, String> getVariables(final long processInstanceKey) {
    instanceKey.wrapLong(processInstanceKey);
    return variables.get(instanceKey).map(PersistedVariables::toMap).orElseGet(HashMap::new);
  }

  @Override
  public void putVariable(final long processInstanceKey, final String name, final String value) {
    final Map<String, String> current = getVariables(processInstanceKey);
    current.put(name, value);
    instanceKey.wrapLong(processInstanceKey);
    variables.put(instanceKey, persistedVariables.wrap(current));
  }

  @Override
  public void deleteVariables(final long processInstanceKey) {
    instanceKey.wrapLong(processInstanceKey);
    variables.delete(instanceKey);
  }

  @Override
  public boolean markIncident(final long processInstanceKey) {
    incidentKey.wrapLong(processInstanceKey);
    final boolean first = incidents.get(incidentKey).isEmpty();
    incidentFlag.wrapLong(1L);
    incidents.put(incidentKey, incidentFlag);
    return first;
  }

  @Override
  public boolean hasIncident(final long processInstanceKey) {
    incidentKey.wrapLong(processInstanceKey);
    return incidents.get(incidentKey).isPresent();
  }

  @Override
  public void clearIncident(final long processInstanceKey) {
    incidentKey.wrapLong(processInstanceKey);
    incidents.delete(incidentKey);
  }

  @Override
  public void putIncidentStart(final long elementInstanceKey, final long createTimeMs) {
    incidentStartKey.wrapLong(elementInstanceKey);
    incidentStartValue.wrapLong(createTimeMs);
    incidentStarts.put(incidentStartKey, incidentStartValue);
  }

  @Override
  public long takeIncidentStart(final long elementInstanceKey) {
    incidentStartKey.wrapLong(elementInstanceKey);
    final long start =
        incidentStarts.get(incidentStartKey).map(DbLong::getValue).orElse(NO_POSITION);
    if (start != NO_POSITION) {
      incidentStarts.delete(incidentStartKey);
    }
    return start;
  }

  @Override
  public long getConsumedPosition(final int partitionId) {
    positionKey.wrapInt(partitionId);
    return consumedPosition.get(positionKey).map(DbLong::getValue).orElse(NO_POSITION);
  }

  @Override
  public void setConsumedPosition(final int partitionId, final long position) {
    positionKey.wrapInt(partitionId);
    positionValue.wrapLong(position);
    consumedPosition.put(positionKey, positionValue);
  }

  @Override
  public Map<Integer, Long> consumedPositions() {
    final Map<Integer, Long> positions = new HashMap<>();
    consumedPosition.forEach(
        (partition, position) -> positions.put(partition.getValue(), position.getValue()));
    return positions;
  }

  @Override
  public void checkpoint() {
    variables.checkpoint();
    elementStarts.checkpoint();
    incidents.checkpoint();
    incidentStarts.checkpoint();
    consumedPosition.checkpoint();
  }

  @Override
  public void close() throws Exception {
    if (ownsProvider) {
      // We own the lifecycle (standalone/tests): flush the working state before closing so it
      // survives a reopen. When the provider is shared, the driver's checkpoint owns durability.
      checkpoint();
      provider.close();
    }
  }
}
