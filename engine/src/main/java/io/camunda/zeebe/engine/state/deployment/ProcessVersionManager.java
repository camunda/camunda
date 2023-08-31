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
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

public final class ProcessVersionManager {

  private final long initialValue;

  private final ColumnFamily<DbTenantAwareKey<DbString>, ProcessVersionInfo>
      processVersionInfoColumnFamily;
  private final DbString processIdKey;
  private final DbString tenantIdKey;
  private final DbTenantAwareKey<DbString> tenantAwareProcessIdKey;
  private final ProcessVersionInfo nextVersion = new ProcessVersionInfo();
  private final Map<String, Object2ObjectHashMap<String, ProcessVersionInfo>> versionByTenantCache;

  public ProcessVersionManager(
      final long initialValue,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext) {
    this.initialValue = initialValue;

    tenantIdKey = new DbString();
    processIdKey = new DbString();
    tenantAwareProcessIdKey =
        new DbTenantAwareKey<>(tenantIdKey, processIdKey, PlacementType.PREFIX);
    processVersionInfoColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_VERSION,
            transactionContext,
            tenantAwareProcessIdKey,
            nextVersion);
    versionByTenantCache = new HashMap<>();
  }

  private ProcessVersionInfo getVersionInfo() {
    final var versionInfo =
        versionByTenantCache
            .computeIfAbsent(tenantIdKey.toString(), key -> new Object2ObjectHashMap<>())
            .computeIfAbsent(
                processIdKey.toString(),
                (key) -> processVersionInfoColumnFamily.get(tenantAwareProcessIdKey));

    if (versionInfo == null) {
      return new ProcessVersionInfo().setHighestVersionIfHigher(initialValue);
    }

    return versionInfo;
  }

  public void addProcessVersion(final String processId, final long value, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processIdKey.wrapString(processId);
    final var versionInfo = getVersionInfo();
    versionInfo.addKnownVersion(value);
    processVersionInfoColumnFamily.upsert(tenantAwareProcessIdKey, versionInfo);
    versionByTenantCache
        .computeIfAbsent(tenantId, key -> new Object2ObjectHashMap<>())
        .put(processId, versionInfo);
  }

  /**
   * Deletes a specified version of a process
   *
   * @param processId the id of the process
   * @param version the version that needs to be deleted
   * @param tenantId the tenant id
   */
  public void deleteProcessVersion(
      final String processId, final long version, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processIdKey.wrapString(processId);
    final var versionInfo = getVersionInfo();
    versionInfo.removeKnownVersion(version);
    processVersionInfoColumnFamily.update(tenantAwareProcessIdKey, versionInfo);
    versionByTenantCache
        .computeIfAbsent(tenantId, key -> new Object2ObjectHashMap<>())
        .put(processId, versionInfo);
  }

  public void clear() {
    versionByTenantCache.clear();
  }

  /**
   * Returns the latest known version of a process. A process with this version exists in the state.
   *
   * @param processId the process id
   * @param tenantId the tenant id
   * @return the latest known version of this process
   */
  public long getLatestProcessVersion(final String processId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processIdKey.wrapString(processId);
    return getVersionInfo().getLatestVersion();
  }

  /**
   * Returns the latest known version of a process. A process with this version exists in the state.
   *
   * @param processId the process id
   * @param tenantId the tenant id
   * @return the latest known version of this process
   */
  public long getLatestProcessVersion(final DirectBuffer processId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processIdKey.wrapBuffer(processId);
    return getVersionInfo().getLatestVersion();
  }

  /**
   * Returns the highest version ever deployed for a given process. This process could already be
   * deleted from the state.
   *
   * @param processId the process id
   * @param tenantId the tenant id
   * @return the highest version ever deployed for this process id.
   */
  public long getHighestProcessVersion(final String processId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processIdKey.wrapString(processId);
    return getHighestProcessVersion();
  }

  /**
   * Returns the highest process id ever deployed for a given process. This process could already be
   * deleted from the state.
   *
   * @param processId the process id
   * @param tenantId the tenant id
   * @return the highest version ever deployed for this process id.
   */
  public long getHighestProcessVersion(final DirectBuffer processId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processIdKey.wrapBuffer(processId);
    return getHighestProcessVersion();
  }

  private long getHighestProcessVersion() {
    return getVersionInfo().getHighestVersion();
  }

  public Optional<Integer> findProcessVersionBefore(
      final String processId, final long version, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    processIdKey.wrapString(processId);
    return getVersionInfo().findVersionBefore(version);
  }
}
