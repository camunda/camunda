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
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import java.util.Optional;

public class DbRoleState implements MutableRoleState {

  private final DbLong roleKey;
  private final PersistedRole persistedRole = new PersistedRole();
  private final ColumnFamily<DbLong, PersistedRole> roleColumnFamily;

  private final DbForeignKey<DbLong> fkRoleKey;
  private final DbLong entityKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> fkRoleKeyAndEntityKey;
  private final EntityTypeValue entityTypeValue = new EntityTypeValue();
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbLong>, EntityTypeValue>
      entityTypeByRoleColumnFamily;

  private final DbString roleName;
  private final ColumnFamily<DbString, DbForeignKey<DbLong>> roleByNameColumnFamily;

  public DbRoleState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    roleKey = new DbLong();
    roleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROLES, transactionContext, roleKey, persistedRole);

    fkRoleKey = new DbForeignKey<>(roleKey, ZbColumnFamilies.ROLES);
    entityKey = new DbLong();
    fkRoleKeyAndEntityKey = new DbCompositeKey<>(fkRoleKey, entityKey);
    entityTypeByRoleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_ROLE,
            transactionContext,
            fkRoleKeyAndEntityKey,
            entityTypeValue);

    roleName = new DbString();
    roleByNameColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROLE_BY_NAME, transactionContext, roleName, fkRoleKey);
  }

  @Override
  public void createRole(final RoleRecord roleRecord) {
    roleKey.wrapLong(roleRecord.getRoleKey());
    persistedRole.setRole(roleRecord);
    roleColumnFamily.insert(roleKey, persistedRole);

    roleName.wrapString(roleRecord.getName());
    roleByNameColumnFamily.insert(roleName, fkRoleKey);
  }

  @Override
  public Optional<RoleRecord> getRole(final long roleKey) {
    this.roleKey.wrapLong(roleKey);
    final var persistedRole = roleColumnFamily.get(this.roleKey);
    return persistedRole != null ? Optional.of(persistedRole.getRole()) : Optional.empty();
  }

  @Override
  public long getRoleKeyByName(final String roleName) {
    this.roleName.wrapString(roleName);
    final var fkRoleKey = roleByNameColumnFamily.get(this.roleName);
    return fkRoleKey != null ? fkRoleKey.inner().getValue() : -1;
  }
}
