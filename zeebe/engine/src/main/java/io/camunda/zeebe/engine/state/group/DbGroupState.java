/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.group;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.authorization.EntityTypeValue;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public class DbGroupState implements MutableGroupState {

  private final DbLong groupKey;
  private final PersistedGroup persistedGroup = new PersistedGroup();
  private final ColumnFamily<DbLong, PersistedGroup> groupColumnFamily;

  private final DbForeignKey<DbLong> fkGroupKey;
  private final DbLong entityKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> fkGroupKeyAndEntityKey;
  private final EntityTypeValue entityTypeValue = new EntityTypeValue();
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbLong>, EntityTypeValue>
      entityTypeByGroupColumnFamily;

  private final DbString groupName;
  private final ColumnFamily<DbString, DbForeignKey<DbLong>> groupByNameColumnFamily;

  public DbGroupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    groupKey = new DbLong();
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUPS, transactionContext, groupKey, persistedGroup);

    fkGroupKey = new DbForeignKey<>(groupKey, ZbColumnFamilies.GROUPS);
    entityKey = new DbLong();
    fkGroupKeyAndEntityKey = new DbCompositeKey<>(fkGroupKey, entityKey);
    entityTypeByGroupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_GROUP,
            transactionContext,
            fkGroupKeyAndEntityKey,
            entityTypeValue);

    groupName = new DbString();
    groupByNameColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUP_BY_NAME, transactionContext, groupName, fkGroupKey);
  }
}
