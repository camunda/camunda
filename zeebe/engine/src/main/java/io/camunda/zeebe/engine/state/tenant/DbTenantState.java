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
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Stores every version of a tenant under the key (tenantId, policyVersion). A read returns the
 * latest version that is not newer than the visible version, and a tombstone means no tenant.
 */
public class DbTenantState implements MutableTenantState {

  private final DbString tenantId = new DbString();
  private final DbLong policyVersion = new DbLong();
  private final DbCompositeKey<DbString, DbLong> versionedKey =
      new DbCompositeKey<>(tenantId, policyVersion);
  private final PersistedTenant persistedTenant = new PersistedTenant();
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, PersistedTenant> tenantsColumnFamily;
  private final LongSupplier currentVersion;

  public DbTenantState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final LongSupplier currentVersion) {
    this.currentVersion = currentVersion;
    tenantsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TENANTS, transactionContext, versionedKey, new PersistedTenant());
  }

  @Override
  public void createTenant(final TenantRecord tenantRecord) {
    write(tenantRecord, false);
  }

  @Override
  public void updateTenant(final TenantRecord updatedTenantRecord) {
    final var existing =
        getTenantById(updatedTenantRecord.getTenantId(), updatedTenantRecord.getPolicyVersion())
            .orElseThrow();
    final var updated = existing.copy();
    updated.setName(updatedTenantRecord.getName());
    updated.setDescription(updatedTenantRecord.getDescription());
    tenantId.wrapString(updatedTenantRecord.getTenantId());
    policyVersion.wrapLong(updatedTenantRecord.getPolicyVersion());
    updated.setDeleted(false);
    tenantsColumnFamily.upsert(versionedKey, updated);
  }

  @Override
  public void delete(final TenantRecord tenantRecord) {
    write(tenantRecord, true);
  }

  /** Visits every visible tenant once, skipping tombstones. */
  @Override
  public void forEachTenant(final Function<String, Boolean> callback) {
    final long current = currentVersion.getAsLong();
    final var visible = new String[1];
    final var visibleDeleted = new boolean[] {true};
    tenantsColumnFamily.whileTrue(
        (key, value) -> {
          final var id = key.first().toString();
          if (visible[0] != null && !visible[0].equals(id)) {
            if (!visibleDeleted[0] && !callback.apply(visible[0])) {
              return false;
            }
            visible[0] = null;
          }
          if (key.second().getValue() <= current) {
            visible[0] = value.getTenantId();
            visibleDeleted[0] = value.isDeleted();
          }
          return true;
        });
    if (visible[0] != null && !visibleDeleted[0]) {
      callback.apply(visible[0]);
    }
  }

  @Override
  public Optional<PersistedTenant> getTenantById(final String tenantId, final long commandVersion) {
    final long current = Math.max(currentVersion.getAsLong(), commandVersion);
    this.tenantId.wrapString(tenantId);
    policyVersion.wrapLong(current);
    final var found = new PersistedTenant[1];
    tenantsColumnFamily.whileTrueReverse(
        versionedKey,
        (key, value) -> {
          if (!value.getTenantId().equals(tenantId)) {
            return false;
          }
          if (key.second().getValue() > current) {
            return true;
          }
          if (!value.isDeleted()) {
            found[0] = value.copy();
          }
          return false;
        });
    return Optional.ofNullable(found[0]);
  }

  private void write(final TenantRecord tenantRecord, final boolean deleted) {
    tenantId.wrapString(tenantRecord.getTenantId());
    policyVersion.wrapLong(tenantRecord.getPolicyVersion());
    persistedTenant.from(tenantRecord);
    persistedTenant.setDeleted(deleted);
    tenantsColumnFamily.upsert(versionedKey, persistedTenant);
  }
}
