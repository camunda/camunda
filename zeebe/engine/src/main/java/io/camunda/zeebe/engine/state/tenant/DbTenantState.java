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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import java.util.Optional;
import java.util.function.Function;

public class DbTenantState implements MutableTenantState {

  private final DbString tenantId = new DbString();
  private final PersistedTenant persistedTenant = new PersistedTenant();
  private final ColumnFamily<DbString, PersistedTenant> tenantsColumnFamily;

  public DbTenantState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    tenantsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TENANTS, transactionContext, tenantId, new PersistedTenant());
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
  public void delete(final TenantRecord tenantRecord) {
    tenantId.wrapString(tenantRecord.getTenantId());
    tenantsColumnFamily.deleteExisting(tenantId);
  }

  @Override
  public void forEachTenant(final Function<String, Boolean> callback) {
    tenantsColumnFamily.whileTrue((k, p) -> callback.apply(p.getTenantId()));
  }

  @Override
  public Optional<PersistedTenant> getTenantById(final String tenantId) {
    this.tenantId.wrapString(tenantId);
    final var persistedTenant = tenantsColumnFamily.get(this.tenantId, PersistedTenant::new);
    return Optional.ofNullable(persistedTenant);
  }
}
