/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.tenant;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.authorization.EntityTypeValue;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class DbTenantState implements MutableTenantState {

  private final DbString tenantId = new DbString();
  private final DbLong tenantKey = new DbLong();
  private final PersistedTenant persistedTenant = new PersistedTenant();
  private final ColumnFamily<DbString, PersistedTenant> tenantsColumnFamily;

  private final DbForeignKey<DbLong> fkTenantKey;
  private final DbLong entityKey = new DbLong();
  private final EntityTypeValue entityType = new EntityTypeValue();
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> entityByTenantKey;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbLong>, EntityTypeValue>
      entityByTenantColumnFamily;

  private final ColumnFamily<DbString, DbForeignKey<DbLong>> tenantByIdColumnFamily;

  public DbTenantState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    tenantsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TENANTS, transactionContext, tenantId, new PersistedTenant());

    fkTenantKey = new DbForeignKey<>(tenantKey, ZbColumnFamilies.TENANTS);

    entityByTenantKey = new DbCompositeKey<>(fkTenantKey, entityKey);
    entityByTenantColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_TENANT, transactionContext, entityByTenantKey, entityType);

    tenantByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TENANT_BY_ID, transactionContext, tenantId, fkTenantKey);
  }

  @Override
  public void createTenant(final TenantRecord tenantRecord) {
    tenantKey.wrapLong(tenantRecord.getTenantKey());
    tenantId.wrapString(tenantRecord.getTenantId());
    persistedTenant.from(tenantRecord);
    tenantsColumnFamily.insert(tenantId, persistedTenant);
    tenantByIdColumnFamily.insert(tenantId, fkTenantKey);
  }

  @Override
  public void updateTenant(final TenantRecord updatedTenantRecord) {
    tenantId.wrapString(updatedTenantRecord.getTenantId());
    final var persistedTenant = tenantsColumnFamily.get(tenantId);
    persistedTenant.setName(updatedTenantRecord.getName());
    persistedTenant.setDescription(updatedTenantRecord.getDescription());
    tenantsColumnFamily.update(tenantId, persistedTenant);
  }

  @Override
  public void addEntity(final TenantRecord tenantRecord) {
    tenantKey.wrapLong(tenantRecord.getTenantKey());
    entityKey.wrapLong(tenantRecord.getEntityKey());
    entityType.setEntityType(tenantRecord.getEntityType());
    entityByTenantColumnFamily.insert(entityByTenantKey, entityType);
  }

  @Override
  public void removeEntity(final long tenantKey, final long entityKey) {
    this.tenantKey.wrapLong(tenantKey);
    this.entityKey.wrapLong(entityKey);
    entityByTenantColumnFamily.deleteExisting(entityByTenantKey);
  }

  @Override
  public void delete(final TenantRecord tenantRecord) {
    tenantKey.wrapLong(tenantRecord.getTenantKey());
    tenantId.wrapString(tenantRecord.getTenantId());

    tenantByIdColumnFamily.deleteExisting(tenantId);

    entityByTenantColumnFamily.whileEqualPrefix(
        fkTenantKey,
        (compositeKey, entityTypeValue) -> {
          entityByTenantColumnFamily.deleteExisting(compositeKey);
        });

    tenantsColumnFamily.deleteExisting(tenantId);
  }

  @Override
  public Optional<PersistedTenant> getTenantByKey(final long tenantKey) {
    this.tenantKey.wrapLong(tenantKey);
    final PersistedTenant persistedTenant = tenantsColumnFamily.get(this.tenantKey);
    return Optional.ofNullable(persistedTenant);
  }

  @Override
  public Optional<Long> getTenantKeyById(final String tenantId) {
    this.tenantId.wrapString(tenantId);
    return Optional.ofNullable(tenantByIdColumnFamily.get(this.tenantId))
        .map(fkTenantKey -> fkTenantKey.inner().getValue());
  }

  @Override
  public Optional<EntityType> getEntityType(final long tenantKey, final long entityKey) {
    this.tenantKey.wrapLong(tenantKey);
    this.entityKey.wrapLong(entityKey);

    final var entityTypeValue = entityByTenantColumnFamily.get(entityByTenantKey);

    if (entityTypeValue == null) {
      return Optional.empty();
    }

    return Optional.of(entityTypeValue.getEntityType());
  }

  @Override
  public boolean isEntityAssignedToTenant(final long entityKey, final long tenantKey) {
    this.tenantKey.wrapLong(tenantKey);
    this.entityKey.wrapLong(entityKey);
    return entityByTenantColumnFamily.exists(entityByTenantKey);
  }

  @Override
  public Map<EntityType, List<Long>> getEntitiesByType(final long tenantKey) {
    final Map<EntityType, List<Long>> entitiesMap = new HashMap<>();
    this.tenantKey.wrapLong(tenantKey);

    entityByTenantColumnFamily.whileEqualPrefix(
        fkTenantKey,
        (compositeKey, entityTypeValue) -> {
          final var entityType = entityTypeValue.getEntityType();
          final var entityKey = compositeKey.second().getValue();
          entitiesMap.putIfAbsent(entityType, new ArrayList<>());
          entitiesMap.get(entityType).add(entityKey);
        });

    return entitiesMap;
  }

  @Override
  public void forEachTenant(final Function<String, Boolean> callback) {
    tenantsColumnFamily.whileTrue((k, p) -> callback.apply(p.getTenantId()));
  }

  @Override
  public Optional<PersistedTenant> getTenantById(final String tenantId) {
    this.tenantId.wrapString(tenantId);
    final var persistedTenant = tenantsColumnFamily.get(this.tenantId);
    return Optional.ofNullable(persistedTenant);
  }
}
