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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

public final class VersionManager {

  private final long initialValue;

  private final ColumnFamily<DbString, VersionInfo> versionInfoColumnFamily;
  private final DbString idKey;
  private final DbString tenantIdKey;
  private final VersionInfo nextVersion = new VersionInfo();
  private final Map<String, Object2ObjectHashMap<String, VersionInfo>> versionByTenantCache;

  public VersionManager(
      final long initialValue,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final ZbColumnFamilies columnFamily,
      final TransactionContext transactionContext) {
    this.initialValue = initialValue;

    tenantIdKey = new DbString();
    idKey = new DbString();
    versionInfoColumnFamily =
        zeebeDb.createTenantAwareColumnFamily(
            columnFamily, transactionContext, idKey, nextVersion, tenantIdKey);
    versionByTenantCache = new HashMap<>();
  }

  private VersionInfo getVersionInfo() {
    final var versionInfo =
        versionByTenantCache
            .computeIfAbsent(tenantIdKey.toString(), key -> new Object2ObjectHashMap<>())
            .computeIfAbsent(idKey.toString(), (key) -> versionInfoColumnFamily.get(idKey));

    if (versionInfo == null) {
      return new VersionInfo().setHighestVersionIfHigher(initialValue);
    }

    return versionInfo;
  }

  public void addResourceVersion(final String resourceId, final long value, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    idKey.wrapString(resourceId);
    final var versionInfo = getVersionInfo();
    versionInfo.addKnownVersion(value);
    versionInfoColumnFamily.upsert(idKey, versionInfo);
    versionByTenantCache
        .computeIfAbsent(tenantId, key -> new Object2ObjectHashMap<>())
        .put(resourceId, versionInfo);
  }

  /**
   * Deletes a specified version of a resource
   *
   * @param resourceId the id of the resource
   * @param version the version that needs to be deleted
   * @param tenantId the tenant id
   */
  public void deleteResourceVersion(
      final String resourceId, final long version, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    idKey.wrapString(resourceId);
    final var versionInfo = getVersionInfo();
    versionInfo.removeKnownVersion(version);
    versionInfoColumnFamily.update(idKey, versionInfo);
    versionByTenantCache
        .computeIfAbsent(tenantId, key -> new Object2ObjectHashMap<>())
        .put(resourceId, versionInfo);
  }

  public void clear() {
    versionByTenantCache.clear();
  }

  /**
   * Returns the latest known version of a resource. A resource with this version exists in the
   * state.
   *
   * @param resourceId the resource id
   * @param tenantId the tenant id
   * @return the latest known version of this resource
   */
  public long getLatestResourceVersion(final String resourceId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    idKey.wrapString(resourceId);
    return getVersionInfo().getLatestVersion();
  }

  /**
   * Returns the latest known version of a resource. A resource with this version exists in the
   * state.
   *
   * @param resourceId the resource id
   * @param tenantId the tenant id
   * @return the latest known version of this resource
   */
  public long getLatestResourceVersion(final DirectBuffer resourceId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    idKey.wrapBuffer(resourceId);
    return getVersionInfo().getLatestVersion();
  }

  /**
   * Returns the highest version ever deployed for a given resource. This resource could already be
   * deleted from the state.
   *
   * @param resourceId the resource id
   * @param tenantId the tenant id
   * @return the highest version ever deployed for this resource id.
   */
  public long getHighestResourceVersion(final String resourceId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    idKey.wrapString(resourceId);
    return getHighestResourceVersion();
  }

  /**
   * Returns the highest resource id ever deployed for a given resource. This resource could already
   * be deleted from the state.
   *
   * @param resourceId the resource id
   * @param tenantId the tenant id
   * @return the highest version ever deployed for this resource id.
   */
  public long getHighestResourceVersion(final DirectBuffer resourceId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    idKey.wrapBuffer(resourceId);
    return getHighestResourceVersion();
  }

  private long getHighestResourceVersion() {
    return getVersionInfo().getHighestVersion();
  }

  public Optional<Integer> findResourceVersionBefore(
      final String resourceId, final long version, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    idKey.wrapString(resourceId);
    return getVersionInfo().findVersionBefore(version);
  }
}
