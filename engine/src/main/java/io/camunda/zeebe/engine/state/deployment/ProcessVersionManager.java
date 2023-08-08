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

  private final ColumnFamily<DbString, ProcessVersionInfo> processVersionInfoColumnFamily;
  private final DbString processIdKey;
  private final ProcessVersionInfo nextVersion = new ProcessVersionInfo();
  private final Object2ObjectHashMap<String, ProcessVersionInfo> versionCache;

  public ProcessVersionManager(
      final long initialValue,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext) {
    this.initialValue = initialValue;

    processIdKey = new DbString();
    processVersionInfoColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_VERSION, transactionContext, processIdKey, nextVersion);
    versionCache = new Object2ObjectHashMap<>();
  }

  public void addProcessVersion(final String processId, final long value) {
    processIdKey.wrapString(processId);
    final var versionInfo = getVersionInfo();
    versionInfo.addKnownVersion(value);
    processVersionInfoColumnFamily.upsert(processIdKey, versionInfo);
    versionCache.put(processId, versionInfo);
  }

  /**
   * Returns the latest known version of a process. A process with this version exists in the state.
   *
   * @param processId the process id
   * @return the latest known version of this process
   */
  public long getLatestProcessVersion(final String processId) {
    processIdKey.wrapString(processId);
    return getVersionInfo().getLatestVersion();
  }

  /**
   * Returns the latest known version of a process. A process with this version exists in the state.
   *
   * @param processId the process id
   * @return the latest known version of this process
   */
  public long getLatestProcessVersion(final DirectBuffer processId) {
    processIdKey.wrapBuffer(processId);
    return getVersionInfo().getLatestVersion();
  }

  /**
   * Returns the highest version ever deployed for a given process. This process could already be
   * deleted from the state.
   *
   * @param processId the process id
   * @return the highest version ever deployed for this process id.
   */
  public long getHighestProcessVersion(final String processId) {
    processIdKey.wrapString(processId);
    return getHighestProcessVersion();
  }

  /**
   * Returns the highest process id ever deployed for a given process. This process could already be
   * deleted from the state.
   *
   * @param processId the process id
   * @return the highest version ever deployed for this process id.
   */
  public long getHighestProcessVersion(final DirectBuffer processId) {
    processIdKey.wrapBuffer(processId);
    return getHighestProcessVersion();
  }

  private long getHighestProcessVersion() {
    return getVersionInfo().getHighestVersion();
  }

  private ProcessVersionInfo getVersionInfo() {
    final var versionInfo =
        versionCache.computeIfAbsent(
            processIdKey.toString(), (key) -> processVersionInfoColumnFamily.get(processIdKey));

    if (versionInfo == null) {
      return new ProcessVersionInfo().setHighestVersionIfHigher(initialValue);
    }

    return versionInfo;
  }

  /**
   * Deletes a specified version of a process
   *
   * @param processId the id of the process
   * @param version the version that needs to be deleted
   */
  public void deleteProcessVersion(final String processId, final long version) {
    processIdKey.wrapString(processId);
    final var versionInfo = getVersionInfo();
    versionInfo.removeKnownVersion(version);
    processVersionInfoColumnFamily.update(processIdKey, versionInfo);
    versionCache.put(processId, versionInfo);
  }

  public void clear() {
    versionCache.clear();
  }
}
