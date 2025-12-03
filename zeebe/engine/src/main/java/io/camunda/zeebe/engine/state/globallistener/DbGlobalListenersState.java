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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Optional;
import org.agrona.collections.MutableBoolean;

public final class DbGlobalListenersState implements MutableGlobalListenersState {

  private final DbString key = new DbString();
  private final PersistedGlobalListenersConfig currentConfig = new PersistedGlobalListenersConfig();
  private final ColumnFamily<DbString, PersistedGlobalListenersConfig> currentConfigColumnFamily;

  private final DbLong versionKey = new DbLong();
  private final PersistedGlobalListenersConfig versionedConfig =
      new PersistedGlobalListenersConfig();
  private final ColumnFamily<DbLong, PersistedGlobalListenersConfig> versionedConfigColumnFamily;

  // The pinningElementKey could be either a user task key (when pinning user task listeners)
  // or an element instance key (when pinning execution listeners)
  private final DbLong pinningElementKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> pinnedConfigKey;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbLong>, DbNil>
      pinnedConfigColumnFamily;

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

    pinningElementKey = new DbLong();
    final var fkVersionedConfig =
        new DbForeignKey<>(versionKey, ZbColumnFamilies.GLOBAL_LISTENER_VERSIONED_CONFIG);
    pinnedConfigKey = new DbCompositeKey<>(fkVersionedConfig, pinningElementKey);
    pinnedConfigColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GLOBAL_LISTENER_PINNED_CONFIG,
            transactionContext,
            pinnedConfigKey,
            DbNil.INSTANCE);
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
  public void pinCurrentConfiguration(final UserTaskRecord userTaskRecord) {
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

              // Create pinned entry
              pinningElementKey.wrapLong(userTaskRecord.getUserTaskKey());
              pinnedConfigColumnFamily.upsert(pinnedConfigKey, DbNil.INSTANCE);

              // Update user task record to reference the pinned config
              userTaskRecord.setListenersConfigKey(currentConfigKey);
            });
  }

  @Override
  public void unpinConfiguration(final UserTaskRecord userTaskRecord) {
    final long pinnedConfigKey = userTaskRecord.getListenersConfigKey();
    // Only unpin if there is a pinned config
    if (pinnedConfigKey != -1L) {
      // Remove pinned entry
      versionKey.wrapLong(pinnedConfigKey);
      pinningElementKey.wrapLong(userTaskRecord.getUserTaskKey());
      pinnedConfigColumnFamily.deleteIfExists(this.pinnedConfigKey);

      // If no other user task references this config, remove the versioned config
      if (!isConfigurationReferenced(pinnedConfigKey)) {
        versionedConfigColumnFamily.deleteIfExists(versionKey);
      }

      // Update user task record to no longer reference a pinned config
      userTaskRecord.setListenersConfigKey(-1L);
    }
  }

  public boolean isConfigurationReferenced(final long configKey) {
    // Check if any user task references the given config key
    versionKey.wrapLong(configKey);
    final var referenced = new MutableBoolean(false);
    pinnedConfigColumnFamily.whileEqualPrefix(
        versionKey,
        (compositeKey, nil) -> {
          referenced.set(true);
          // Early exit as soon as we find a reference
          return false;
        });
    return referenced.get();
  }
}
