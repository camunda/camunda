/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

public final class ProcessVersionManager {

  private final long initialValue;

  private final ColumnFamily<DbString, NextValue> nextValueColumnFamily;
  private final DbString processIdKey;
  private final NextValue nextVersion = new NextValue();
  private final Object2ObjectHashMap<String, NextValue> versionCache;

  public ProcessVersionManager(
      final long initialValue,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext) {
    this.initialValue = initialValue;

    processIdKey = new DbString();
    nextValueColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_VERSION, transactionContext, processIdKey, nextVersion);
    versionCache = new Object2ObjectHashMap<>();
  }

  /**
   * Updates the current version of the process. The current version is the maximum version number
   * we know of this process. When we create a new process it will be this value + 1. This is not
   * necessarily the latest version, as the latest version could be deleted.
   *
   * @param processId id of the process
   * @param version the version number
   */
  public void updateCurrentVersion(final String processId, final long version) {
    processIdKey.wrapString(processId);
    final var versionInfo = getProcessVersionInfo();

    if (versionInfo.get() < version) {
      versionInfo.set(version);
    }

    nextValueColumnFamily.upsert(processIdKey, nextVersion);
    versionCache.put(processId, versionInfo);
  }

  /**
   * Updates the latest version of the process. The latest version is the version of a process we
   * will start when no version is specified. This is not necessarily the current version, as when
   * the latest version of a process gets deleted, this version gets decremented.
   *
   * @param processId id of the process
   * @param version the version number
   */
  public void updateLatestVersion(final String processId, final long version) {
    processIdKey.wrapString(processId);

    final var versionInfo = getProcessVersionInfo();
    versionInfo.setLatestVersion(version);
    nextValueColumnFamily.upsert(processIdKey, nextVersion);
    versionCache.put(processId, versionInfo);
  }

  public long getCurrentProcessVersion(final String processId) {
    processIdKey.wrapString(processId);
    return getProcessVersionInfo().get();
  }

  public long getCurrentProcessVersion(final DirectBuffer processId) {
    processIdKey.wrapBuffer(processId);
    return getProcessVersionInfo().get();
  }

  public long getLatestProcessVersion(final String processId) {
    processIdKey.wrapString(processId);
    return getProcessVersionInfo().getLatestVersion();
  }

  private NextValue getProcessVersionInfo() {
    return versionCache.computeIfAbsent(
        processIdKey.toString(), (key) -> getProcessVersionFromDB());
  }

  private NextValue getProcessVersionFromDB() {
    final NextValue readValue = nextValueColumnFamily.get(processIdKey);
    if (readValue != null) {
      return readValue;
    }
    return new NextValue().setLatestVersion(initialValue).set(initialValue);
  }

  public void deleteProcessVersion(final String processId, final long version) {
    processIdKey.wrapString(processId);
    if (version >= 1) {
      updateLatestVersion(processId, version - 1);
    }
  }

  public void clear() {
    versionCache.clear();
  }
}
