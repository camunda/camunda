/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableRpaState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.RpaRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class DbRpaState implements MutableRpaState {

  private static final int DEFAULT_VERSION_VALUE = 0;

  private final DbString tenantIdKey;
  private final DbLong dbRpaKey;
  private final DbTenantAwareKey<DbLong> tenantAwareRpaKey;
  private final DbForeignKey<DbTenantAwareKey<DbLong>> fkRpaKey;
  private final PersistedRpa dbPersistedRpa;
  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedRpa> rpasByKey;
  private final DbString dbRpaId;
  private final VersionManager versionManager;

  private final DbLong rpaVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>> tenantAwareIdAndVersionKey;
  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedRpa>
      rpaByIdAndVersionColumnFamily;

  private final DbLong dbDeploymentKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>
      tenantAwareRpaIdAndDeploymentKey;

  private final ColumnFamily<
      DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>,
      DbForeignKey<DbTenantAwareKey<DbLong>>>
      rpaKeyByRpaIdAndDeploymentKeyColumnFamily;

  private final DbString dbVersionTag;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbString>>
      tenantAwareRpaIdAndVersionTagKey;

  private final ColumnFamily<
      DbTenantAwareKey<DbCompositeKey<DbString, DbString>>,
      DbForeignKey<DbTenantAwareKey<DbLong>>>
      rpaKeyByRpaIdAndVersionTagColumnFamily;

  private final Cache<DbRpaState.TenantIdAndRpaId, PersistedRpa> rpasByTenantIdAndIdCache;

  public DbRpaState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final EngineConfiguration config) {
    tenantIdKey = new DbString();
    dbRpaKey = new DbLong();
    tenantAwareRpaKey = new DbTenantAwareKey<>(tenantIdKey, dbRpaKey, PlacementType.PREFIX);
    fkRpaKey = new DbForeignKey<>(tenantAwareRpaKey, ZbColumnFamilies.RPAS);
    dbPersistedRpa = new PersistedRpa();
    rpasByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RPAS, transactionContext, tenantAwareRpaKey, dbPersistedRpa);

    dbRpaId = new DbString();
    rpaVersion = new DbLong();
    idAndVersionKey = new DbCompositeKey<>(dbRpaId, rpaVersion);
    tenantAwareIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    rpaByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RPA_BY_ID_AND_VERSION,
            transactionContext,
            tenantAwareIdAndVersionKey,
            dbPersistedRpa);

    dbDeploymentKey = new DbLong();
    tenantAwareRpaIdAndDeploymentKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(dbRpaId, dbDeploymentKey), PlacementType.PREFIX);
    rpaKeyByRpaIdAndDeploymentKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RPA_KEY_BY_RPA_ID_AND_DEPLOYMENT_KEY,
            transactionContext,
            tenantAwareRpaIdAndDeploymentKey,
            fkRpaKey);

    dbVersionTag = new DbString();
    tenantAwareRpaIdAndVersionTagKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(dbRpaId, dbVersionTag), PlacementType.PREFIX);
    rpaKeyByRpaIdAndVersionTagColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RPA_KEY_BY_RPA_ID_AND_VERSION_TAG,
            transactionContext,
            tenantAwareRpaIdAndVersionTagKey,
            fkRpaKey);

    versionManager =
        new VersionManager(
            DEFAULT_VERSION_VALUE, zeebeDb, ZbColumnFamilies.RPA_VERSION, transactionContext);

    rpasByTenantIdAndIdCache =
        CacheBuilder.newBuilder().maximumSize(config.getRpaCacheCapacity()).build();
  }

  @Override
  public void storeRpaInRpaColumnFamily(final RpaRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbRpaKey.wrapLong(record.getRpaKey());
    dbPersistedRpa.wrap(record);
    rpasByKey.upsert(tenantAwareRpaKey, dbPersistedRpa);
    rpasByTenantIdAndIdCache.put(
        new DbRpaState.TenantIdAndRpaId(record.getTenantId(), record.getRpaIdBuffer()),
        dbPersistedRpa.copy());
  }

  @Override
  public void storeRpaInRpaByIdAndVersionColumnFamily(final RpaRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbRpaId.wrapString(record.getRpaId());
    rpaVersion.wrapLong(record.getVersion());
    dbPersistedRpa.wrap(record);
    rpaByIdAndVersionColumnFamily.upsert(tenantAwareIdAndVersionKey, dbPersistedRpa);
  }

  @Override
  public void storeRpaInRpaKeyByRpaIdAndDeploymentKeyColumnFamily(final RpaRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbRpaKey.wrapLong(record.getRpaKey());
    dbRpaId.wrapString(record.getRpaId());
    dbDeploymentKey.wrapLong(record.getDeploymentKey());
    rpaKeyByRpaIdAndDeploymentKeyColumnFamily.upsert(
        tenantAwareRpaIdAndDeploymentKey, fkRpaKey);
  }

  @Override
  public void storeRpaInRpaKeyByRpaIdAndVersionTagColumnFamily(final RpaRecord record) {
    final var versionTag = record.getVersionTag();
    if (!versionTag.isBlank()) {
      tenantIdKey.wrapString(record.getTenantId());
      dbRpaKey.wrapLong(record.getRpaKey());
      dbRpaId.wrapString(record.getRpaId());
      dbVersionTag.wrapString(versionTag);
      rpaKeyByRpaIdAndVersionTagColumnFamily.upsert(tenantAwareRpaIdAndVersionTagKey, fkRpaKey);
    }
  }

  @Override
  public void updateLatestVersion(final RpaRecord record) {
    versionManager.addResourceVersion(
        record.getRpaId(), record.getVersion(), record.getTenantId());
  }

  @Override
  public void deleteRpaInRpasColumnFamily(final RpaRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbRpaKey.wrapLong(record.getRpaKey());
    rpasByKey.deleteExisting(tenantAwareRpaKey);
    rpasByTenantIdAndIdCache.invalidate(
        new DbRpaState.TenantIdAndRpaId(record.getTenantId(), record.getRpaIdBuffer()));
  }

  @Override
  public void deleteRpaInRpaByIdAndVersionColumnFamily(final RpaRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbRpaId.wrapString(record.getRpaId());
    rpaVersion.wrapLong(record.getVersion());
    rpaByIdAndVersionColumnFamily.deleteExisting(tenantAwareIdAndVersionKey);
  }

  @Override
  public void deleteRpaInRpaVersionColumnFamily(final RpaRecord record) {
    versionManager.deleteResourceVersion(
        record.getRpaId(), record.getVersion(), record.getTenantId());
  }

  @Override
  public void deleteRpaInRpaKeyByRpaIdAndDeploymentKeyColumnFamily(final RpaRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbRpaId.wrapString(record.getRpaId());
    dbDeploymentKey.wrapLong(record.getDeploymentKey());
    rpaKeyByRpaIdAndDeploymentKeyColumnFamily.deleteIfExists(tenantAwareRpaIdAndDeploymentKey);
  }

  @Override
  public void deleteRpaInRpaKeyByRpaIdAndVersionTagColumnFamily(final RpaRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbRpaId.wrapString(record.getRpaId());
    dbVersionTag.wrapString(record.getVersionTag());
    rpaKeyByRpaIdAndVersionTagColumnFamily.deleteIfExists(tenantAwareRpaIdAndVersionTagKey);
  }

  @Override
  public Optional<PersistedRpa> findLatestRpaById(
      final DirectBuffer rpaId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    final Optional<PersistedRpa> cachedRpa = getRpaFromCache(tenantId, rpaId);
    if (cachedRpa.isPresent()) {
      return cachedRpa;
    }

    final PersistedRpa persistedRpa = getPersistedRpaById(rpaId, tenantId);
    if (persistedRpa == null) {
      return Optional.empty();
    }
    rpasByTenantIdAndIdCache.put(new DbRpaState.TenantIdAndRpaId(tenantId, rpaId), persistedRpa);
    return Optional.of(persistedRpa);
  }

  @Override
  public Optional<PersistedRpa> findRpaByKey(final long rpaKey, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbRpaKey.wrapLong(rpaKey);
    return Optional.ofNullable(rpasByKey.get(tenantAwareRpaKey)).map(PersistedRpa::copy);
  }

  @Override
  public Optional<PersistedRpa> findRpaByIdAndDeploymentKey(
      final DirectBuffer rpaId, final long deploymentKey, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbRpaId.wrapBuffer(rpaId);
    dbDeploymentKey.wrapLong(deploymentKey);
    return Optional.ofNullable(
            rpaKeyByRpaIdAndDeploymentKeyColumnFamily.get(tenantAwareRpaIdAndDeploymentKey))
        .flatMap(key -> findRpaByKey(key.inner().wrappedKey().getValue(), tenantId));
  }

  @Override
  public Optional<PersistedRpa> findRpaByIdAndVersionTag(
      final DirectBuffer rpaId, final String versionTag, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbRpaId.wrapBuffer(rpaId);
    dbVersionTag.wrapString(versionTag);
    return Optional.ofNullable(
            rpaKeyByRpaIdAndVersionTagColumnFamily.get(tenantAwareRpaIdAndVersionTagKey))
        .flatMap(key -> findRpaByKey(key.inner().wrappedKey().getValue(), tenantId));
  }

  @Override
  public int getNextRpaVersion(final String rpaId, final String tenantId) {
    return (int) versionManager.getHighestResourceVersion(rpaId, tenantId) + 1;
  }

  @Override
  public void clearCache() {
    rpasByTenantIdAndIdCache.invalidateAll();
    versionManager.clear();
  }

  private PersistedRpa getPersistedRpaById(final DirectBuffer rpaId, final String tenantId) {
    dbRpaId.wrapBuffer(rpaId);
    final long latestVersion = versionManager.getLatestResourceVersion(rpaId, tenantId);
    rpaVersion.wrapLong(latestVersion);
    final PersistedRpa persistedRpa =
        rpaByIdAndVersionColumnFamily.get(tenantAwareIdAndVersionKey);
    if (persistedRpa == null) {
      return null;
    }
    return persistedRpa.copy();
  }

  private Optional<PersistedRpa> getRpaFromCache(
      final String tenantId, final DirectBuffer rpaId) {
    return Optional.ofNullable(
        rpasByTenantIdAndIdCache.getIfPresent(new DbRpaState.TenantIdAndRpaId(tenantId, rpaId)));
  }

  private record TenantIdAndRpaId(String tenantId, DirectBuffer rpaId) {}
}
