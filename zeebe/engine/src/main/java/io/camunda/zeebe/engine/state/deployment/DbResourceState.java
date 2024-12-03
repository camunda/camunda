/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
import io.camunda.zeebe.engine.state.mutable.MutableResourceState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class DbResourceState implements MutableResourceState {

  private static final int DEFAULT_VERSION_VALUE = 0;

  private final DbString tenantIdKey;
  private final DbLong dbResourceKey;
  private final DbTenantAwareKey<DbLong> tenantAwareResourceKey;
  private final DbForeignKey<DbTenantAwareKey<DbLong>> fkResourceKey;
  private final PersistedResource dbPersistedResource;
  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedResource> resourcesByKey;
  private final DbString dbResourceId;
  private final VersionManager versionManager;

  private final DbLong resourceVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>> tenantAwareIdAndVersionKey;
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>,
          DbForeignKey<DbTenantAwareKey<DbLong>>>
      resourceByIdAndVersionColumnFamily;

  private final DbLong dbDeploymentKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>
      tenantAwareResourceIdAndDeploymentKey;

  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>,
          DbForeignKey<DbTenantAwareKey<DbLong>>>
      resourceKeyByResourceIdAndDeploymentKeyColumnFamily;

  private final DbString dbVersionTag;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbString>>
      tenantAwareResourceIdAndVersionTagKey;

  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbString>>,
          DbForeignKey<DbTenantAwareKey<DbLong>>>
      resourceKeyByResourceIdAndVersionTagColumnFamily;

  private final LoadingCache<TenantIdAndResourceId, PersistedResource>
      resourcesByTenantIdAndIdCache;

  public DbResourceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final EngineConfiguration config) {
    tenantIdKey = new DbString();
    dbResourceKey = new DbLong();
    tenantAwareResourceKey =
        new DbTenantAwareKey<>(tenantIdKey, dbResourceKey, PlacementType.PREFIX);
    fkResourceKey = new DbForeignKey<>(tenantAwareResourceKey, ZbColumnFamilies.RESOURCES);
    dbPersistedResource = new PersistedResource();
    resourcesByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RESOURCES,
            transactionContext,
            tenantAwareResourceKey,
            dbPersistedResource);

    dbResourceId = new DbString();
    resourceVersion = new DbLong();
    idAndVersionKey = new DbCompositeKey<>(dbResourceId, resourceVersion);
    tenantAwareIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    resourceByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RESOURCE_BY_ID_AND_VERSION,
            transactionContext,
            tenantAwareIdAndVersionKey,
            fkResourceKey);

    dbDeploymentKey = new DbLong();
    tenantAwareResourceIdAndDeploymentKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(dbResourceId, dbDeploymentKey), PlacementType.PREFIX);
    resourceKeyByResourceIdAndDeploymentKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RESOURCE_KEY_BY_RESOURCE_ID_AND_DEPLOYMENT_KEY,
            transactionContext,
            tenantAwareResourceIdAndDeploymentKey,
            fkResourceKey);

    dbVersionTag = new DbString();
    tenantAwareResourceIdAndVersionTagKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(dbResourceId, dbVersionTag), PlacementType.PREFIX);
    resourceKeyByResourceIdAndVersionTagColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RESOURCE_KEY_BY_RESOURCE_ID_AND_VERSION_TAG,
            transactionContext,
            tenantAwareResourceIdAndVersionTagKey,
            fkResourceKey);

    versionManager =
        new VersionManager(
            DEFAULT_VERSION_VALUE, zeebeDb, ZbColumnFamilies.RESOURCE_VERSION, transactionContext);

    resourcesByTenantIdAndIdCache =
        CacheBuilder.newBuilder()
            .maximumSize(config.getResourceCacheCapacity())
            .build(
                new CacheLoader<>() {
                  @Override
                  public PersistedResource load(final TenantIdAndResourceId key) {
                    return findLatestResourceById(key.resourceId, key.tenantId).orElse(null);
                  }
                });
  }

  @Override
  public void storeResourceInResourceColumnFamily(final ResourceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbResourceKey.wrapLong(record.getResourceKey());
    dbPersistedResource.wrap(record);
    resourcesByKey.upsert(tenantAwareResourceKey, dbPersistedResource);
    resourcesByTenantIdAndIdCache.put(
        new DbResourceState.TenantIdAndResourceId(record.getTenantId(), record.getResourceId()),
        dbPersistedResource.copy());
  }

  @Override
  public void storeResourceInResourceByIdAndVersionColumnFamily(final ResourceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbResourceId.wrapString(record.getResourceId());
    resourceVersion.wrapLong(record.getVersion());
    dbPersistedResource.wrap(record);
    resourceByIdAndVersionColumnFamily.upsert(tenantAwareIdAndVersionKey, fkResourceKey);
  }

  @Override
  public void storeResourceInResourceKeyByResourceIdAndDeploymentKeyColumnFamily(
      final ResourceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbResourceKey.wrapLong(record.getResourceKey());
    dbResourceId.wrapString(record.getResourceId());
    dbDeploymentKey.wrapLong(record.getDeploymentKey());
    resourceKeyByResourceIdAndDeploymentKeyColumnFamily.upsert(
        tenantAwareResourceIdAndDeploymentKey, fkResourceKey);
  }

  @Override
  public void storeResourceInResourceKeyByResourceIdAndVersionTagColumnFamily(
      final ResourceRecord record) {
    final var versionTag = record.getVersionTag();
    if (!versionTag.isBlank()) {
      tenantIdKey.wrapString(record.getTenantId());
      dbResourceKey.wrapLong(record.getResourceKey());
      dbResourceId.wrapString(record.getResourceId());
      dbVersionTag.wrapString(versionTag);
      resourceKeyByResourceIdAndVersionTagColumnFamily.upsert(
          tenantAwareResourceIdAndVersionTagKey, fkResourceKey);
    }
  }

  @Override
  public void updateLatestVersion(final ResourceRecord record) {
    versionManager.addResourceVersion(
        record.getResourceId(), record.getVersion(), record.getTenantId());
  }

  @Override
  public void deleteResourceInResourcesColumnFamily(final ResourceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbResourceKey.wrapLong(record.getResourceKey());
    resourcesByKey.deleteExisting(tenantAwareResourceKey);
    resourcesByTenantIdAndIdCache.invalidate(
        new DbResourceState.TenantIdAndResourceId(record.getTenantId(), record.getResourceId()));
  }

  @Override
  public void deleteResourceInResourceByIdAndVersionColumnFamily(final ResourceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbResourceId.wrapString(record.getResourceId());
    resourceVersion.wrapLong(record.getVersion());
    resourceByIdAndVersionColumnFamily.deleteExisting(tenantAwareIdAndVersionKey);
  }

  @Override
  public void deleteResourceInResourceVersionColumnFamily(final ResourceRecord record) {
    versionManager.deleteResourceVersion(
        record.getResourceId(), record.getVersion(), record.getTenantId());
  }

  @Override
  public void deleteResourceInResourceKeyByResourceIdAndDeploymentKeyColumnFamily(
      final ResourceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbResourceId.wrapString(record.getResourceId());
    dbDeploymentKey.wrapLong(record.getDeploymentKey());
    resourceKeyByResourceIdAndDeploymentKeyColumnFamily.deleteIfExists(
        tenantAwareResourceIdAndDeploymentKey);
  }

  @Override
  public void deleteResourceInResourceKeyByResourceIdAndVersionTagColumnFamily(
      final ResourceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbResourceId.wrapString(record.getResourceId());
    dbVersionTag.wrapString(record.getVersionTag());
    resourceKeyByResourceIdAndVersionTagColumnFamily.deleteIfExists(
        tenantAwareResourceIdAndVersionTagKey);
  }

  @Override
  public Optional<PersistedResource> findLatestResourceById(
      final String resourceId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    final Optional<PersistedResource> cachedResource = getResourceFromCache(tenantId, resourceId);
    if (cachedResource.isPresent()) {
      return cachedResource;
    }

    final PersistedResource persistedResource = getPersistedResourceById(resourceId, tenantId);
    if (persistedResource == null) {
      return Optional.empty();
    }
    return Optional.of(persistedResource);
  }

  @Override
  public Optional<PersistedResource> findResourceByKey(
      final long resourceKey, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbResourceKey.wrapLong(resourceKey);
    return Optional.ofNullable(resourcesByKey.get(tenantAwareResourceKey))
        .map(PersistedResource::copy);
  }

  @Override
  public Optional<PersistedResource> findResourceByIdAndDeploymentKey(
      final DirectBuffer resourceId, final long deploymentKey, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbResourceId.wrapBuffer(resourceId);
    dbDeploymentKey.wrapLong(deploymentKey);
    return Optional.ofNullable(
            resourceKeyByResourceIdAndDeploymentKeyColumnFamily.get(
                tenantAwareResourceIdAndDeploymentKey))
        .flatMap(key -> findResourceByKey(key.inner().wrappedKey().getValue(), tenantId));
  }

  @Override
  public Optional<PersistedResource> findResourceByIdAndVersionTag(
      final DirectBuffer resourceId, final String versionTag, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbResourceId.wrapBuffer(resourceId);
    dbVersionTag.wrapString(versionTag);
    return Optional.ofNullable(
            resourceKeyByResourceIdAndVersionTagColumnFamily.get(
                tenantAwareResourceIdAndVersionTagKey))
        .flatMap(key -> findResourceByKey(key.inner().wrappedKey().getValue(), tenantId));
  }

  @Override
  public int getNextResourceVersion(final String resourceId, final String tenantId) {
    return (int) versionManager.getHighestResourceVersion(resourceId, tenantId) + 1;
  }

  @Override
  public void clearCache() {
    resourcesByTenantIdAndIdCache.invalidateAll();
    versionManager.clear();
  }

  private PersistedResource getPersistedResourceById(
      final String resourceId, final String tenantId) {
    dbResourceId.wrapString(resourceId);
    final long latestVersion = versionManager.getLatestResourceVersion(resourceId, tenantId);
    resourceVersion.wrapLong(latestVersion);
    final Optional<PersistedResource> persistedResource =
        Optional.ofNullable(resourceByIdAndVersionColumnFamily.get(tenantAwareIdAndVersionKey))
            .flatMap(key -> findResourceByKey(key.inner().wrappedKey().getValue(), tenantId));
    return persistedResource.map(PersistedResource::copy).orElse(null);
  }

  private Optional<PersistedResource> getResourceFromCache(
      final String tenantId, final String resourceId) {
    return Optional.ofNullable(
        resourcesByTenantIdAndIdCache.getIfPresent(
            new DbResourceState.TenantIdAndResourceId(tenantId, resourceId)));
  }

  private record TenantIdAndResourceId(String tenantId, String resourceId) {}
}
