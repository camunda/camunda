/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state;

import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.eventbridge.streaming.state.StoreBuilder;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
import io.camunda.eventbridge.streaming.state.cache.CachingKeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryStateStoreProvider;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Model-A base projection on top of the {@code event-bridge-streaming} state library: the
 * materialized {@link ElementEntity} rows, the per-{@code (processInstanceKey, name)} variable
 * instances, and the {@link IncidentEntity} rows — all opened from a single {@link
 * StateStoreProvider} shared with the rollups (one RocksDB per stage).
 *
 * <p>Every store is a bounded {@link CachingKeyValueStore}: hot rows are held on the heap and reads
 * fall through to RocksDB, so the projection does not pin the whole dataset. {@link #checkpoint()}
 * flushes them all, so — called inside the runtime's checkpoint transaction alongside the rollups —
 * the base projection, the rollup cells and the consumed offset commit as one atomic cut. Backed by
 * RocksDB in production ({@link #fromProvider}) or the heap in tests ({@link #inMemory}); the fold
 * logic is identical against either.
 */
public final class StateBackedProjectionState implements MutableProjectionState, AutoCloseable {

  /** Per-store heap budget for the bounded caches. */
  private static final long DEFAULT_CACHE_BYTES_PER_STORE = 16L * 1024 * 1024;

  /** Guards the variable scope-hierarchy walk against a pathological/corrupt parent chain. */
  private static final int MAX_SCOPE_DEPTH = 1024;

  private final StateStoreProvider<AnalyticsColumnFamilies> provider;
  private final boolean ownsProvider;

  private final CachingKeyValueStore<DbLong, ElementEntity> elements;
  private final CachingKeyValueStore<DbBytes, DbString> variables;
  private final CachingKeyValueStore<DbLong, DbNil> variableScopes;
  private final CachingKeyValueStore<DbLong, IncidentEntity> incidents;
  private final List<CachingKeyValueStore<?, ?>> caches;

  private final DbLong elementKey = new DbLong();
  private final ElementEntity elementWrite = new ElementEntity();
  private final DbLong incidentKey = new DbLong();
  private final IncidentEntity incidentWrite = new IncidentEntity();
  private final DbBytes variableKey = new DbBytes();
  private final DbString variableValue = new DbString();
  private final DbBytes variablePrefix = new DbBytes();
  private final DbLong variableScopeKey = new DbLong();

  private StateBackedProjectionState(
      final StateStoreProvider<AnalyticsColumnFamilies> provider,
      final boolean ownsProvider,
      final long cacheBytesPerStore) {
    this.provider = provider;
    this.ownsProvider = ownsProvider;
    elements =
        StoreBuilder.keyValueStore(
                AnalyticsColumnFamilies.ELEMENT_ENTITY, DbLong::new, ElementEntity::new)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    variables =
        StoreBuilder.keyValueStore(
                AnalyticsColumnFamilies.VARIABLE_ENTRIES, DbBytes::new, DbString::new)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    incidents =
        StoreBuilder.keyValueStore(
                AnalyticsColumnFamilies.INCIDENT_ENTITY, DbLong::new, IncidentEntity::new)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    variableScopes =
        StoreBuilder.keyValueStore(
                AnalyticsColumnFamilies.VARIABLE_SCOPES, DbLong::new, () -> DbNil.INSTANCE)
            .withCaching(cacheBytesPerStore)
            .buildCache(provider);
    caches = List.of(elements, variables, variableScopes, incidents);
  }

  /** Shares an already-open provider (the caller owns its lifecycle) — the production wiring. */
  public static StateBackedProjectionState fromProvider(
      final StateStoreProvider<AnalyticsColumnFamilies> provider) {
    return new StateBackedProjectionState(provider, false, DEFAULT_CACHE_BYTES_PER_STORE);
  }

  /** A persistent state under {@code directory}, owning its own provider. */
  public static StateBackedProjectionState rocksDb(
      final File directory, final MeterRegistry meterRegistry) {
    return new StateBackedProjectionState(
        RocksDbStateStoreProvider.open(directory, meterRegistry),
        true,
        DEFAULT_CACHE_BYTES_PER_STORE);
  }

  /** A non-persistent state for offline unit tests. */
  public static StateBackedProjectionState inMemory() {
    return new StateBackedProjectionState(
        new InMemoryStateStoreProvider<>(), true, DEFAULT_CACHE_BYTES_PER_STORE);
  }

  @Override
  public ElementEntity element(final long elementInstanceKey) {
    elementKey.wrapLong(elementInstanceKey);
    return elements.get(elementKey).orElse(null);
  }

  @Override
  public IncidentEntity incident(final long elementInstanceKey) {
    incidentKey.wrapLong(elementInstanceKey);
    return incidents.get(incidentKey).orElse(null);
  }

  @Override
  public Map<String, String> variables(final long scopeKey) {
    // Resolve up the scope hierarchy (local scope first, then parents up to the process instance),
    // so a nearer scope's value wins — the engine's variable visibility. The parent chain is read
    // off the element rows (parents are still active while a child completes).
    final Map<String, String> resolved = new LinkedHashMap<>();
    long scope = scopeKey;
    for (int depth = 0; scope > 0 && depth < MAX_SCOPE_DEPTH; depth++) {
      variablePrefix.wrapBytes(instancePrefix(scope));
      variables.prefixScan(
          variablePrefix,
          (key, value) ->
              resolved.putIfAbsent(
                  variableName(key.getBytes()), BufferUtil.bufferAsString(value.getBuffer())));
      elementKey.wrapLong(scope);
      final ElementEntity row = elements.get(elementKey).orElse(null);
      if (row == null) {
        break; // no row for this scope — the chain ends
      }
      scope = row.parentScopeKey();
    }
    return resolved;
  }

  @Override
  public Map<String, String> variables(final long scopeKey, final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    // Resolve ONLY the requested names via point lookups (bloom-filter-friendly) up the scope
    // hierarchy — nearer scope wins — and stop as soon as every name is found, instead of a prefix
    // scan of the whole scope. This is the engine's job-activation read: fetch only what's needed.
    final Map<String, String> resolved = new LinkedHashMap<>();
    long scope = scopeKey;
    for (int depth = 0;
        scope > 0 && depth < MAX_SCOPE_DEPTH && resolved.size() < names.size();
        depth++) {
      for (final String name : names) {
        if (resolved.containsKey(name)) {
          continue; // a nearer scope already resolved this name
        }
        variableKey.wrapBytes(variableKey(scope, name));
        variables
            .get(variableKey)
            .ifPresent(value -> resolved.put(name, BufferUtil.bufferAsString(value.getBuffer())));
      }
      elementKey.wrapLong(scope);
      final ElementEntity row = elements.get(elementKey).orElse(null);
      if (row == null) {
        break; // no row for this scope — the chain ends
      }
      scope = row.parentScopeKey();
    }
    return resolved;
  }

  @Override
  public void activateElement(
      final long elementInstanceKey,
      final long startTimeMs,
      final boolean isProcess,
      final long parentScopeKey) {
    elementKey.wrapLong(elementInstanceKey);
    elements.put(elementKey, elementWrite.activate(startTimeMs, isProcess, parentScopeKey));
  }

  @Override
  public void completeElement(
      final long elementInstanceKey, final long endTimeMs, final ElementStatus status) {
    elementKey.wrapLong(elementInstanceKey);
    final ElementEntity current = elements.get(elementKey).orElse(null);
    if (current == null) {
      return; // no activation seen (out of order / already evicted) — nothing to finalize
    }
    final long start = current.start();
    final boolean isProcess = current.isProcess();
    final boolean hadIncident = current.hadIncident();
    final long parentScope = current.parentScopeKey();
    elementKey.wrapLong(elementInstanceKey);
    elements.put(
        elementKey,
        elementWrite
            .activate(start, isProcess, parentScope)
            .hadIncident(hadIncident)
            .complete(endTimeMs, status));
  }

  @Override
  public void markIncident(final long elementInstanceKey) {
    elementKey.wrapLong(elementInstanceKey);
    final ElementEntity current = elements.get(elementKey).orElse(null);
    if (current == null) {
      return; // the element instance's activation has not been folded yet
    }
    final long start = current.start();
    final boolean isProcess = current.isProcess();
    final long parentScope = current.parentScopeKey();
    elementKey.wrapLong(elementInstanceKey);
    elements.put(
        elementKey, elementWrite.activate(start, isProcess, parentScope).hadIncident(true));
  }

  @Override
  public void evictElement(final long elementInstanceKey) {
    elementKey.wrapLong(elementInstanceKey);
    elements.delete(elementKey);
  }

  @Override
  public void putVariable(final long scopeKey, final String name, final String value) {
    variableKey.wrapBytes(variableKey(scopeKey, name));
    variableValue.wrapString(value);
    variables.put(variableKey, variableValue);
    // Mark the scope as holding variables so eviction knows it must clear (see clearVariables).
    variableScopeKey.wrapLong(scopeKey);
    variableScopes.put(variableScopeKey, DbNil.INSTANCE);
  }

  @Override
  public void clearVariables(final long scopeKey) {
    // Most elements (gateways, sequence flows, plain tasks) never set a local variable. Skip the
    // VARIABLE_ENTRIES prefix scan — and the RocksDB iterator seek plus the transaction it opens —
    // for those scopes: a cheap marker lookup (cache hit or a bloom-filtered point read) tells us
    // whether there is anything to clear at all.
    variableScopeKey.wrapLong(scopeKey);
    if (!variableScopes.exists(variableScopeKey)) {
      return;
    }
    variablePrefix.wrapBytes(instancePrefix(scopeKey));
    // Key-only scan: we only need the keys to delete, so skip reading each variable value.
    final List<byte[]> keys = new ArrayList<>();
    variables.prefixScanKeys(variablePrefix, key -> keys.add(key.getBytes().clone()));
    for (final byte[] key : keys) {
      variableKey.wrapBytes(key);
      variables.delete(variableKey);
    }
    // The deletes above are buffered as heap tombstones and flushed together in the runtime's next
    // checkpoint cut (a single transaction) — no per-key transaction here.
    variableScopeKey.wrapLong(scopeKey);
    variableScopes.delete(variableScopeKey);
  }

  @Override
  public void openIncident(
      final long elementInstanceKey, final long createMs, final String errorType) {
    incidentKey.wrapLong(elementInstanceKey);
    incidents.put(incidentKey, incidentWrite.open(createMs, errorType));
  }

  @Override
  public void resolveIncident(final long elementInstanceKey, final long resolveMs) {
    incidentKey.wrapLong(elementInstanceKey);
    final IncidentEntity current = incidents.get(incidentKey).orElse(null);
    if (current == null) {
      return; // resolved without an open row (out of order) — nothing to finalize
    }
    final long createMs = current.createMs();
    final String errorType = current.errorType();
    incidentKey.wrapLong(elementInstanceKey);
    incidents.put(incidentKey, incidentWrite.open(createMs, errorType).resolve(resolveMs));
  }

  @Override
  public void evictIncident(final long elementInstanceKey) {
    incidentKey.wrapLong(elementInstanceKey);
    incidents.delete(incidentKey);
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
      checkpoint();
      provider.close();
    }
  }

  private static byte[] instancePrefix(final long processInstanceKey) {
    return ByteBuffer.allocate(Long.BYTES).putLong(processInstanceKey).array();
  }

  private static byte[] variableKey(final long processInstanceKey, final String name) {
    final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + nameBytes.length)
        .putLong(processInstanceKey)
        .put(nameBytes)
        .array();
  }

  private static String variableName(final byte[] key) {
    return new String(key, Long.BYTES, key.length - Long.BYTES, StandardCharsets.UTF_8);
  }
}
