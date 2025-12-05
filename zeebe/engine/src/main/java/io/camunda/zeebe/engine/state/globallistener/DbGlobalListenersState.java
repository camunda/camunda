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
  private final ConfigKeyAndElementKey pinnedConfigKey;
  private final ColumnFamily<ConfigKeyAndElementKey, DbNil> pinnedConfigColumnFamily;

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
    pinnedConfigKey = new ConfigKeyAndElementKey(versionKey, pinningElementKey);
    pinnedConfigColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GLOBAL_LISTENER_PINNED_CONFIG,
            transactionContext,
            pinnedConfigKey,
            DbNil.INSTANCE);
  }

  @Override
  public GlobalListenerBatchRecord getCurrentConfig() {
    return Optional.ofNullable(currentConfigColumnFamily.get(key))
        .map(PersistedGlobalListenersConfig::getGlobalListeners)
        .orElse(null);
  }

  @Override
  public GlobalListenerBatchRecord getVersionedConfig(final long versionKey) {
    this.versionKey.wrapLong(versionKey);
    return Optional.ofNullable(versionedConfigColumnFamily.get(this.versionKey))
        .map(PersistedGlobalListenersConfig::getGlobalListeners)
        .orElse(null);
  }

  @Override
  public boolean isConfigurationVersionStored(final long versionKey) {
    this.versionKey.wrapLong(versionKey);
    return versionedConfigColumnFamily.exists(this.versionKey);
  }

  @Override
  public boolean isConfigurationVersionPinned(final long versionKey) {
    // Check if any user task references the given config key
    this.versionKey.wrapLong(versionKey);
    final var referenced = new MutableBoolean(false);
    pinnedConfigColumnFamily.whileEqualPrefix(
        this.versionKey,
        (compositeKey, nil) -> {
          referenced.set(true);
          // Early exit as soon as we find a reference
          return false;
        });
    return referenced.get();
  }

  @Override
  public void updateCurrentConfiguration(final GlobalListenerBatchRecord record) {
    currentConfig.setGlobalListeners(record);
    currentConfigColumnFamily.upsert(key, currentConfig);
  }

  @Override
  public long storeConfigurationVersion(final GlobalListenerBatchRecord record) {
    final long configKey = record.getGlobalListenerBatchKey();
    versionKey.wrapLong(configKey);
    versionedConfig.setGlobalListeners(record);
    versionedConfigColumnFamily.insert(versionKey, versionedConfig);
    return configKey;
  }

  @Override
  public void deleteConfigurationVersion(final long versionKey) {
    this.versionKey.wrapLong(versionKey);
    versionedConfigColumnFamily.deleteIfExists(this.versionKey);
  }

  @Override
  public void pinConfiguration(final long versionKey, final long pinningElementKey) {
    this.versionKey.wrapLong(versionKey);
    this.pinningElementKey.wrapLong(pinningElementKey);
    pinnedConfigColumnFamily.insert(pinnedConfigKey, DbNil.INSTANCE);
  }

  @Override
  public void unpinConfiguration(final long listenersConfigKey, final long pinningElementKey) {
    versionKey.wrapLong(listenersConfigKey);
    this.pinningElementKey.wrapLong(pinningElementKey);
    pinnedConfigColumnFamily.deleteIfExists(pinnedConfigKey);
  }

  private static class ConfigKeyAndElementKey extends DbCompositeKey<DbForeignKey<DbLong>, DbLong> {
    public ConfigKeyAndElementKey(final DbLong configKey, final DbLong elementKey) {
      super(
          new DbForeignKey<>(configKey, ZbColumnFamilies.GLOBAL_LISTENER_VERSIONED_CONFIG),
          elementKey);
    }
  }
}
