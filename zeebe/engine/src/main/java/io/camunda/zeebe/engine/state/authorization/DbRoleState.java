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

public class DbRoleState implements MutableRoleState {

  private final DbLong roleKey;
  private final ColumnFamily<DbLong, PersistedRole> roleColumnFamily;

  private final DbForeignKey<DbLong> fkRoleKey;
  private final DbLong entityKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> fkRoleKeyAndEntityKey;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbLong>, EntityTypeValue>
      entityTypeByRoleColumnFamily;

  private final DbString roleName;
  private final ColumnFamily<DbString, DbForeignKey<DbLong>> roleByNameColumnFamily;

  public DbRoleState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    roleKey = new DbLong();
    roleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROLE, transactionContext, roleKey, new PersistedRole());

    fkRoleKey = new DbForeignKey<>(roleKey, ZbColumnFamilies.ROLE);
    entityKey = new DbLong();
    fkRoleKeyAndEntityKey = new DbCompositeKey<>(fkRoleKey, entityKey);
    entityTypeByRoleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_ROLE,
            transactionContext,
            fkRoleKeyAndEntityKey,
            new EntityTypeValue());

    roleName = new DbString();
    roleByNameColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROLE_BY_NAME, transactionContext, roleName, fkRoleKey);
  }
}
