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
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.MutableBoolean;

public final class DbGlobalListenersState implements MutableGlobalListenersState {

  private final DbString key = new DbString();
  private final DbLong currentConfigKey = new DbLong();
  private final ColumnFamily<DbString, DbLong> currentConfigKeyColumnFamily;

  private final DbLong versionKey = new DbLong();
  private final PersistedGlobalListenersConfig versionedConfig =
      new PersistedGlobalListenersConfig();
  private final ColumnFamily<DbLong, PersistedGlobalListenersConfig> versionedConfigColumnFamily;
  // The pinningElementKey could be either a user task key (when pinning user task listeners)
  // or an element instance key (when pinning execution listeners)
  private final DbLong pinningElementKey;
  private final ConfigKeyAndElementKey pinnedConfigKey;
  private final ColumnFamily<ConfigKeyAndElementKey, DbNil> pinnedConfigColumnFamily;

  private final DbEnumValue<GlobalListenerType> listenerType;
  private final DbString listenerId;
  private final ListenerTypeAndIdKey listenerTypeAndIdKey;
  private final PersistedGlobalListener globalListener;
  private final ColumnFamily<ListenerTypeAndIdKey, PersistedGlobalListener>
      globalListenersColumnFamily;

  public DbGlobalListenersState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    key.wrapString("CURRENT");
    currentConfigKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GLOBAL_LISTENER_CURRENT_CONFIG,
            transactionContext,
            key,
            currentConfigKey);

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

    listenerType = new DbEnumValue<>(GlobalListenerType.class);
    listenerId = new DbString();
    listenerTypeAndIdKey = new ListenerTypeAndIdKey(listenerType, listenerId);
    globalListener = new PersistedGlobalListener();
    globalListenersColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GLOBAL_LISTENERS,
            transactionContext,
            listenerTypeAndIdKey,
            globalListener);
  }

  @Override
  public GlobalListenerBatchRecord getCurrentConfig() {
    // Retrieve configuration key
    final var configKey = getCurrentConfigKey();
    if (configKey == null) {
      return null;
    }
    final var currentConfig = new GlobalListenerBatchRecord();
    currentConfig.setGlobalListenerBatchKey(configKey);

    // Retrieve listeners list
    final List<GlobalListenerRecord> currentListeners = new ArrayList<>();
    globalListenersColumnFamily.forEach(
        listener -> {
          // Note: the copy is necessary because the same instance is reused by the column family
          // iterator
          final GlobalListenerRecord record = new GlobalListenerRecord();
          record.copyFrom(listener.getGlobalListener());
          currentListeners.add(record);
        });
    currentListeners.sort(GlobalListenerRecord.PRIORITY_COMPARATOR);
    currentListeners.forEach(currentConfig::addListener);

    return currentConfig;
  }

  @Override
  public Long getCurrentConfigKey() {
    final var configKey = currentConfigKeyColumnFamily.get(key);
    if (configKey == null) {
      return null;
    }
    return configKey.getValue();
  }

  @Override
  public GlobalListenerBatchRecord getVersionedConfig(final long versionKey) {
    this.versionKey.wrapLong(versionKey);
    final var versionedConfig = versionedConfigColumnFamily.get(this.versionKey);
    if (versionedConfig == null) {
      return null;
    }
    return versionedConfig.getGlobalListeners();
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
  public GlobalListenerRecord getGlobalListener(
      final GlobalListenerType listenerType, final String id) {
    this.listenerType.setValue(listenerType);
    listenerId.wrapString(id);
    final var persistedListener = globalListenersColumnFamily.get(listenerTypeAndIdKey);
    if (persistedListener == null) {
      return null;
    }
    return persistedListener.getGlobalListener();
  }

  @Override
  public void create(final GlobalListenerRecord record) {
    listenerType.setValue(record.getListenerType());
    listenerId.wrapString(record.getId());
    globalListener.setGlobalListener(record);
    globalListenersColumnFamily.insert(listenerTypeAndIdKey, globalListener);
  }

  @Override
  public void update(final GlobalListenerRecord record) {
    listenerType.setValue(record.getListenerType());
    listenerId.wrapString(record.getId());
    globalListener.setGlobalListener(record);
    globalListenersColumnFamily.update(listenerTypeAndIdKey, globalListener);
  }

  @Override
  public void delete(final GlobalListenerRecord record) {
    listenerType.setValue(record.getListenerType());
    listenerId.wrapString(record.getId());
    globalListenersColumnFamily.deleteExisting(listenerTypeAndIdKey);
  }

  @Override
  public void updateConfigKey(final long configKey) {
    currentConfigKey.wrapLong(configKey);
    currentConfigKeyColumnFamily.upsert(key, currentConfigKey);
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

  private static class ListenerTypeAndIdKey
      extends DbCompositeKey<DbEnumValue<GlobalListenerType>, DbString> {
    public ListenerTypeAndIdKey(
        final DbEnumValue<GlobalListenerType> listenerType, final DbString id) {
      super(listenerType, id);
    }
  }
}
