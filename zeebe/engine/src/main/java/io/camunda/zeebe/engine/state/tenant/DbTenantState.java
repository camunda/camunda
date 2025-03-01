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
  private final PersistedTenant persistedTenant = new PersistedTenant();
  private final ColumnFamily<DbString, PersistedTenant> tenantsColumnFamily;

  private final DbForeignKey<DbString> fkTenantId;
  private final DbString entityId = new DbString();
  private final EntityTypeValue entityType = new EntityTypeValue();
  private final DbCompositeKey<DbForeignKey<DbString>, DbString> entityIdByTenantId;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbString>, DbString>, EntityTypeValue>
      entityByTenantColumnFamily;

  public DbTenantState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    tenantsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TENANTS, transactionContext, tenantId, new PersistedTenant());

    fkTenantId = new DbForeignKey<>(tenantId, ZbColumnFamilies.TENANTS);

    entityIdByTenantId = new DbCompositeKey<>(fkTenantId, entityId);
    entityByTenantColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_TENANT, transactionContext, entityIdByTenantId, entityType);
  }

  @Override
  public void createTenant(final TenantRecord tenantRecord) {
    tenantId.wrapString(tenantRecord.getTenantId());
    persistedTenant.from(tenantRecord);
    tenantsColumnFamily.insert(tenantId, persistedTenant);
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
    tenantId.wrapString(tenantRecord.getTenantId());
    entityId.wrapString(tenantRecord.getEntityId());
    entityType.setEntityType(tenantRecord.getEntityType());
    entityByTenantColumnFamily.insert(entityIdByTenantId, entityType);
  }

  @Override
  public void removeEntity(final TenantRecord tenantRecord) {
    tenantId.wrapString(tenantRecord.getTenantId());
    entityId.wrapString(tenantRecord.getEntityId());
    entityByTenantColumnFamily.deleteExisting(entityIdByTenantId);
  }

  @Override
  public void delete(final TenantRecord tenantRecord) {
    tenantId.wrapString(tenantRecord.getTenantId());

    entityByTenantColumnFamily.whileEqualPrefix(
        fkTenantId,
        (compositeKey, entityTypeValue) -> {
          entityByTenantColumnFamily.deleteExisting(compositeKey);
        });

    tenantsColumnFamily.deleteExisting(tenantId);
  }

  @Override
  public Optional<EntityType> getEntityType(final String tenantId, final String entityId) {
    this.tenantId.wrapString(tenantId);
    this.entityId.wrapString(entityId);

    final var entityTypeValue = entityByTenantColumnFamily.get(entityIdByTenantId);

    if (entityTypeValue == null) {
      return Optional.empty();
    }

    return Optional.of(entityTypeValue.getEntityType());
  }

  @Override
  public Map<EntityType, List<String>> getEntitiesByType(final String tenantId) {
    final Map<EntityType, List<String>> entitiesMap = new HashMap<>();
    this.tenantId.wrapString(tenantId);

    entityByTenantColumnFamily.whileEqualPrefix(
        fkTenantId,
        (compositeKey, entityTypeValue) -> {
          final var entityType = entityTypeValue.getEntityType();
          final var entityId = compositeKey.second().toString();
          entitiesMap.putIfAbsent(entityType, new ArrayList<>());
          entitiesMap.get(entityType).add(entityId);
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
