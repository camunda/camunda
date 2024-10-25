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
import java.util.Optional;

public class DbTenantState implements MutableTenantState {

  private final DbString tenantId = new DbString();
  private final DbLong tenantKey = new DbLong();
  private final PersistedTenant persistedTenant = new PersistedTenant();
  private final ColumnFamily<DbLong, PersistedTenant> tenantsColumnFamily;

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
            ZbColumnFamilies.TENANTS, transactionContext, tenantKey, persistedTenant);

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
    persistedTenant.wrap(tenantRecord);
    tenantsColumnFamily.insert(tenantKey, persistedTenant);
    tenantByIdColumnFamily.insert(tenantId, fkTenantKey);
  }

  @Override
  public void updateTenant(final TenantRecord updatedTenantRecord) {
    tenantKey.wrapLong(updatedTenantRecord.getTenantKey());
    final PersistedTenant persistedTenant = tenantsColumnFamily.get(tenantKey);

    if (persistedTenant != null) {
      final String oldTenantId = persistedTenant.getTenantId();
      final String newTenantId = updatedTenantRecord.getTenantId();

      if (!oldTenantId.equals(newTenantId)) {
        tenantId.wrapString(oldTenantId);
        tenantByIdColumnFamily.deleteExisting(tenantId);

        tenantId.wrapString(newTenantId);
        tenantByIdColumnFamily.insert(tenantId, fkTenantKey);
      }

      persistedTenant.wrap(updatedTenantRecord);
      tenantsColumnFamily.update(tenantKey, persistedTenant);
    }
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
  public Optional<TenantRecord> getTenantByKey(final long tenantKey) {
    this.tenantKey.wrapLong(tenantKey);
    final PersistedTenant persistedTenant = tenantsColumnFamily.get(this.tenantKey);

    if (persistedTenant != null) {
      final TenantRecord tenantRecord = new TenantRecord();
      tenantRecord
          .setTenantKey(persistedTenant.getTenantKey())
          .setTenantId(persistedTenant.getTenantId())
          .setName(persistedTenant.getName());

      // Retrieve entityKey if it exists for the tenant
      final EntityTypeValue entityTypeValue =
          entityByTenantColumnFamily.get(new DbCompositeKey<>(fkTenantKey, entityKey));

      if (entityTypeValue != null) {
        tenantRecord.setEntityKey(entityKey.getValue());
      }

      return Optional.of(tenantRecord);
    }

    return Optional.empty();
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
}
