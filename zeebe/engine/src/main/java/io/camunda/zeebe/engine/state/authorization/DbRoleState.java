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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import java.util.Optional;

public class DbRoleState implements MutableRoleState {

  private final DbString roleId;
  private final PersistedRole persistedRole = new PersistedRole();
  private final ColumnFamily<DbString, PersistedRole> roleColumnFamily;

  public DbRoleState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    roleId = new DbString();
    roleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROLES, transactionContext, roleId, new PersistedRole());
  }

  @Override
  public void create(final RoleRecord roleRecord) {
    roleId.wrapString(roleRecord.getRoleId());
    persistedRole.from(roleRecord);
    roleColumnFamily.insert(roleId, persistedRole);
  }

  @Override
  public void update(final RoleRecord roleRecord) {
    // retrieve record from the state
    roleId.wrapString(roleRecord.getRoleId());
    final var persistedRole = roleColumnFamily.get(roleId);
    persistedRole.from(roleRecord);
    roleColumnFamily.update(roleId, persistedRole);
  }

  @Override
  public void delete(final RoleRecord roleRecord) {
    roleId.wrapString(roleRecord.getRoleId());
    roleColumnFamily.deleteExisting(roleId);
  }

  @Override
  public Optional<PersistedRole> getRole(final String roleId) {
    this.roleId.wrapString(roleId);
    final var persistedRole = roleColumnFamily.get(this.roleId, PersistedRole::new);
    return Optional.ofNullable(persistedRole);
  }
}
