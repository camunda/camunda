/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.engine.state.globallistener;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import java.util.Optional;

public final class DbGlobalListenersState implements MutableGlobalListenersState {

  private final DbString key = new DbString();
  private final PersistedGlobalListenersConfig currentConfig = new PersistedGlobalListenersConfig();
  private final ColumnFamily<DbString, PersistedGlobalListenersConfig> currentConfigColumnFamily;

  private final DbLong versionKey = new DbLong();
  private final PersistedGlobalListenersConfig versionedConfig =
      new PersistedGlobalListenersConfig();
  private final ColumnFamily<DbLong, PersistedGlobalListenersConfig> versionedConfigColumnFamily;

  public DbGlobalListenersState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    key.wrapString("CURRENT");
    currentConfigColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GLOBAL_LISTENER_CURRENT_CONFIG,
            transactionContext,
            key,
            currentConfig);

    versionedConfigColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GLOBAL_LISTENER_VERSIONED_CONFIG,
            transactionContext,
            versionKey,
            versionedConfig);
  }

  @Override
  public Optional<GlobalListenerBatchRecord> getCurrentConfig() {
    return Optional.ofNullable(currentConfigColumnFamily.get(key))
        .map(PersistedGlobalListenersConfig::getGlobalListeners);
  }

  @Override
  public Optional<GlobalListenerBatchRecord> getVersionedConfig(final long version) {
    if (version < 0) {
      return Optional.empty();
    }
    versionKey.wrapLong(version);
    return Optional.ofNullable(versionedConfigColumnFamily.get(versionKey))
        .map(PersistedGlobalListenersConfig::getGlobalListeners);
  }

  @Override
  public void updateCurrentConfiguration(final GlobalListenerBatchRecord record) {
    currentConfig.setGlobalListeners(record);
    currentConfigColumnFamily.upsert(key, currentConfig);
  }

  @Override
  public void pinCurrentConfiguration() {
    // Only pin the configuration if it exists
    getCurrentConfig()
        .ifPresent(
            currentConfig -> {
              final long currentConfigKey = currentConfig.getListenersConfigKey();
              versionKey.wrapLong(currentConfigKey);
              // Create versioned config if it does not exist
              if (!versionedConfigColumnFamily.exists(versionKey)) {
                versionedConfig.setGlobalListeners(currentConfig);
                versionedConfigColumnFamily.insert(versionKey, versionedConfig);
              }
            });
  }
}
