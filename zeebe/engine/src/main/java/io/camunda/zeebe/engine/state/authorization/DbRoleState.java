/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Stores every version of a role under the key (roleId, policyVersion). A read returns the latest
 * version that is not newer than the current policy version, and a tombstone means no role. A
 * record written without a policy update has version 0, which is always current.
 */
public class DbRoleState implements MutableRoleState {

  private final DbString roleId = new DbString();
  private final DbLong policyVersion = new DbLong();
  private final DbCompositeKey<DbString, DbLong> versionedKey =
      new DbCompositeKey<>(roleId, policyVersion);
  private final PersistedRole persistedRole = new PersistedRole();
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, PersistedRole> roleColumnFamily;
  private final LongSupplier currentVersion;

  public DbRoleState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final LongSupplier currentVersion) {
    this.currentVersion = currentVersion;
    roleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROLES, transactionContext, versionedKey, new PersistedRole());
  }

  @Override
  public void create(final RoleRecord roleRecord) {
    write(roleRecord, false);
  }

  @Override
  public void update(final RoleRecord roleRecord) {
    write(roleRecord, false);
  }

  @Override
  public void delete(final RoleRecord roleRecord) {
    write(roleRecord, true);
  }

  @Override
  public Optional<PersistedRole> getRole(final String roleId, final long commandVersion) {
    final long current = Math.max(currentVersion.getAsLong(), commandVersion);
    this.roleId.wrapString(roleId);
    policyVersion.wrapLong(current);
    final var found = new PersistedRole[1];
    roleColumnFamily.whileTrueReverse(
        versionedKey,
        (key, value) -> {
          if (!value.getRoleId().equals(roleId)) {
            return false;
          }
          if (key.second().getValue() > current) {
            return true;
          }
          if (!value.isDeleted()) {
            found[0] = new PersistedRole();
            found[0].copyFrom(value);
          }
          return false;
        });
    return Optional.ofNullable(found[0]);
  }

  private void write(final RoleRecord roleRecord, final boolean deleted) {
    roleId.wrapString(roleRecord.getRoleId());
    policyVersion.wrapLong(roleRecord.getPolicyVersion());
    persistedRole.from(roleRecord);
    persistedRole.setDeleted(deleted);
    roleColumnFamily.upsert(versionedKey, persistedRole);
  }
}
