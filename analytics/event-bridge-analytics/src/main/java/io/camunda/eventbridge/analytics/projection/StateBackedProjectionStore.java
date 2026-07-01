/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.analytics.streaming.state.api.KeyValueStore;
import io.camunda.analytics.streaming.state.api.StateStoreProvider;
import io.camunda.analytics.streaming.state.memory.InMemoryStateStoreProvider;
import io.camunda.analytics.streaming.state.rocksdb.RocksDbStateStoreProvider;
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
 * keys into it like any element), and one for the per-partition consumed position — all opened from
 * a single {@link StateStoreProvider}. Backed by RocksDB in production ({@link #rocksDb}) or by the
 * heap in tests ({@link #inMemory}) — the fold logic is identical against either.
 */
public final class StateBackedProjectionStore implements BaseProjectionStore, AutoCloseable {

  private final StateStoreProvider<AnalyticsColumnFamilies> provider;

  private final DbLong instanceKey = new DbLong();
  private final PersistedVariables persistedVariables = new PersistedVariables();
  private final KeyValueStore<DbLong, PersistedVariables> variables;

  private final DbInt positionKey = new DbInt();
  private final DbLong positionValue = new DbLong();
  private final KeyValueStore<DbInt, DbLong> consumedPosition;

  private final KeyValueStore<DbString, DbLong> elementStarts;

  private final DbLong incidentKey = new DbLong();
  private final DbLong incidentFlag = new DbLong();
  private final KeyValueStore<DbLong, DbLong> incidents;

  private StateBackedProjectionStore(final StateStoreProvider<AnalyticsColumnFamilies> provider) {
    this.provider = provider;
    variables =
        provider.keyValueStore(
            AnalyticsColumnFamilies.INSTANCE_VARIABLES, instanceKey, persistedVariables);
    consumedPosition =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, positionKey, positionValue);
    elementStarts =
        provider.keyValueStore(AnalyticsColumnFamilies.ELEMENT_START, new DbString(), new DbLong());
    incidents =
        provider.keyValueStore(
            AnalyticsColumnFamilies.INSTANCE_INCIDENT, new DbLong(), new DbLong());
  }

  /**
   * The per-element-instance activation-time store, on the same backing as the variables — so the
   * whole base projection (variables + in-flight element starts) is one RocksDB instance. The
   * process instance's own start is held here too, keyed like any element.
   */
  public KeyValueStore<DbString, DbLong> elementStarts() {
    return elementStarts;
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
  public void close() throws Exception {
    provider.close();
  }
}
