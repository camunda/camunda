/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clusterversion;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableClusterVersionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class DbClusterVersionState implements MutableClusterVersionState {

  private static final String SINGLETON_KEY = "ACTIVE";

  private final ColumnFamily<DbString, DbClusterVersionEntry> activeCf;
  private final DbString activeKey = new DbString();
  private final DbClusterVersionEntry activeValue = new DbClusterVersionEntry();

  // Suppressed-flags column family: key = flag name, value = presence sentinel. Storing as a
  // separate CF lets the rest of the code stay tight; the in-memory mirror below makes lookups
  // O(1) without a RocksDB hit on the hot path.
  private final ColumnFamily<DbString, DbNil> suppressedFlagsCf;
  private final DbString suppressedFlagKey = new DbString();

  // In-memory mirror of the active row. Lazy load on first read, write-through in activate(...).
  private int cachedLine = INITIAL_LINE;
  private int cachedOrdinal = INITIAL_ORDINAL;
  private boolean activeLoaded;

  // In-memory mirror of suppressed flag names. Loaded lazily on first read.
  private final Set<String> suppressedFlags = new HashSet<>();
  private boolean suppressedLoaded;

  public DbClusterVersionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    activeCf =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLUSTER_VERSION, transactionContext, activeKey, activeValue);
    suppressedFlagsCf =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLUSTER_VERSION_SUPPRESSED_FLAGS,
            transactionContext,
            suppressedFlagKey,
            DbNil.INSTANCE);
  }

  @Override
  public int getActiveLine() {
    ensureActiveLoaded();
    return cachedLine;
  }

  @Override
  public int getActiveOrdinal() {
    ensureActiveLoaded();
    return cachedOrdinal;
  }

  @Override
  public boolean isSuppressed(final String flagName) {
    ensureSuppressedLoaded();
    return suppressedFlags.contains(flagName);
  }

  @Override
  public Set<String> getSuppressedFlags() {
    ensureSuppressedLoaded();
    return Collections.unmodifiableSet(suppressedFlags);
  }

  @Override
  public void activate(final int line, final int ordinal) {
    activeKey.wrapString(SINGLETON_KEY);
    activeValue.set(line, ordinal);
    activeCf.upsert(activeKey, activeValue);
    cachedLine = line;
    cachedOrdinal = ordinal;
    activeLoaded = true;
  }

  @Override
  public void suppressFlag(final String flagName) {
    suppressedFlagKey.wrapString(flagName);
    suppressedFlagsCf.upsert(suppressedFlagKey, DbNil.INSTANCE);
    ensureSuppressedLoaded();
    suppressedFlags.add(flagName);
  }

  @Override
  public void unsuppressFlag(final String flagName) {
    suppressedFlagKey.wrapString(flagName);
    suppressedFlagsCf.deleteIfExists(suppressedFlagKey);
    ensureSuppressedLoaded();
    suppressedFlags.remove(flagName);
  }

  private void ensureActiveLoaded() {
    if (activeLoaded) {
      return;
    }
    activeKey.wrapString(SINGLETON_KEY);
    final var entry = activeCf.get(activeKey);
    if (entry == null) {
      cachedLine = INITIAL_LINE;
      cachedOrdinal = INITIAL_ORDINAL;
    } else {
      cachedLine = entry.getLine();
      cachedOrdinal = entry.getOrdinal();
    }
    activeLoaded = true;
  }

  private void ensureSuppressedLoaded() {
    if (suppressedLoaded) {
      return;
    }
    suppressedFlagsCf.forEach(
        (key, ignored) ->
            suppressedFlags.add(
                io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString(key.getBuffer())));
    suppressedLoaded = true;
  }
}
