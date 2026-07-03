/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.eventbridge.streaming.state.StoreBuilder;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
import io.camunda.eventbridge.streaming.state.cache.CachingKeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryStateStoreProvider;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link BaseProjectionStore} on top of the {@code event-bridge-streaming} state library: one
 * store for per-instance variables, one for in-flight element start times (the process instance
 * keys into it like any element), one for the per-partition consumed position, and the incident
 * flags/starts — all opened from a single {@link StateStoreProvider} shared with the rollups (one
 * RocksDB per stage).
 *
 * <p>Every store is a bounded {@link CachingKeyValueStore}: hot entries are held on the heap up to
 * a per-store byte budget and a read miss falls through to RocksDB, so the projection no longer
 * pins the whole dataset in memory. {@link #checkpoint()} flushes all of them, so — called inside
 * the driver's checkpoint transaction alongside the rollups — the base projection, the rollup
 * cells, and the consumed offset commit as one atomic cut (no write-through per record). When a
 * cache fills with buffered writes it cannot evict, {@link #needsCheckpoint()} asks the runtime to
 * run that barrier early, keeping heap bounded without flushing state ahead of the offset. Backed
 * by RocksDB in production ({@link #fromProvider}/{@link #rocksDb}) or by the heap in tests ({@link
 * #inMemory}) — the fold logic is identical against either.
 */
public final class StateBackedProjectionStore implements BaseProjectionStore, AutoCloseable {

  /** Per-store heap budget for the bounded caches. */
  private static final long DEFAULT_CACHE_BYTES_PER_STORE = 16L * 1024 * 1024;

  private final StateStoreProvider<AnalyticsColumnFamilies> provider;
  private final boolean ownsProvider;

  private final DbLong instanceKey = new DbLong();
  private final PersistedVariables persistedVariables = new PersistedVariables();
  private final CachingKeyValueStore<DbLong, PersistedVariables> variables;

  private final DbInt positionKey = new DbInt();
  private final DbLong positionValue = new DbLong();
  private final CachingKeyValueStore<DbInt, DbLong> consumedPosition;

  private final DbString elementStartKey = new DbString();
  private final DbLong elementStartValue = new DbLong();
  private final CachingKeyValueStore<DbString, DbLong> elementStarts;

  private final DbLong incidentKey = new DbLong();
  private final DbLong incidentFlag = new DbLong();
  private final CachingKeyValueStore<DbLong, DbLong> incidents;

  private final DbLong incidentStartKey = new DbLong();
  private final DbLong incidentStartValue = new DbLong();
  private final CachingKeyValueStore<DbLong, DbLong> incidentStarts;

  private final List<CachingKeyValueStore<?, ?>> caches;

  private StateBackedProjectionStore(
      final StateStoreProvider<AnalyticsColumnFamilies> provider,
      final boolean ownsProvider,
      final long cacheBytesPerStore) {
    this.provider = provider;
    this.ownsProvider = ownsProvider;
    variables =
        StoreBuilder.keyValueStore(
                AnalyticsColumnFamilies.INSTANCE_VARIABLES, DbLong::new, PersistedVariables::new)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    consumedPosition =
        StoreBuilder.keyValueStore(
                AnalyticsColumnFamilies.CONSUMED_POSITION, DbInt::new, DbLong::new)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    elementStarts =
        StoreBuilder.keyValueStore(
                AnalyticsColumnFamilies.ELEMENT_START, DbString::new, DbLong::new)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    incidents =
        StoreBuilder.keyValueStore(
                AnalyticsColumnFamilies.INSTANCE_INCIDENT, DbLong::new, DbLong::new)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    incidentStarts =
        StoreBuilder.keyValueStore(AnalyticsColumnFamilies.INCIDENT_START, DbLong::new, DbLong::new)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    // Checkpoint/capacity order: the base-projection stores then the consumed position, all inside
    // the one commit transaction, so they land as a single atomic cut.
    caches = List.of(variables, elementStarts, incidents, incidentStarts, consumedPosition);
  }

  /** Shares an already-open provider (the caller owns its lifecycle) — the production wiring. */
  public static StateBackedProjectionStore fromProvider(
      final StateStoreProvider<AnalyticsColumnFamilies> provider) {
    return new StateBackedProjectionStore(provider, false, DEFAULT_CACHE_BYTES_PER_STORE);
  }

  /** A persistent store under {@code directory} (created if needed), owning its own provider. */
  public static StateBackedProjectionStore rocksDb(
      final File directory, final MeterRegistry meterRegistry) {
    return new StateBackedProjectionStore(
        RocksDbStateStoreProvider.open(directory, meterRegistry),
        true,
        DEFAULT_CACHE_BYTES_PER_STORE);
  }

  /** A non-persistent store for offline unit tests. */
  public static StateBackedProjectionStore inMemory() {
    return new StateBackedProjectionStore(
        new InMemoryStateStoreProvider<>(), true, DEFAULT_CACHE_BYTES_PER_STORE);
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
  public void recordElementStart(
      final long processInstanceKey, final String elementId, final long startTimeMs) {
    elementStartKey.wrapString(processInstanceKey + ":" + elementId);
    elementStartValue.wrapLong(startTimeMs);
    elementStarts.put(elementStartKey, elementStartValue);
  }

  @Override
  public long takeElementStart(final long processInstanceKey, final String elementId) {
    elementStartKey.wrapString(processInstanceKey + ":" + elementId);
    final long start = elementStarts.get(elementStartKey).map(DbLong::getValue).orElse(NO_POSITION);
    if (start != NO_POSITION) {
      elementStarts.delete(elementStartKey);
    }
    return start;
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
    caches.forEach(CachingKeyValueStore::checkpoint);
  }

  @Override
  public boolean needsCheckpoint() {
    for (final CachingKeyValueStore<?, ?> cache : caches) {
      if (cache.overCapacity()) {
        return true;
      }
    }
    return false;
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
