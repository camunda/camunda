/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.ordinal;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableOrdinalState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

// TODO: @yohanfernando >> requre proper implementation
public class DbOrdinalState implements MutableOrdinalState {

  private static final String ACTIVE_KEY = "ACTIVE_KEY";

  private final ColumnFamily<DbString, DbInt> columnFamily;
  private final DbString key = new DbString();
  private final DbInt value = new DbInt();

  public DbOrdinalState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.ORDINAL_STATE, transactionContext, key, value);
  }

  @Override
  public boolean isInitialized() {
    key.wrapString(ACTIVE_KEY);
    final var activeOrdinalKey = columnFamily.get(key);
    return activeOrdinalKey != null;
  }

  @Override
  public int getActiveOrdinalKey() {
    key.wrapString(ACTIVE_KEY);
    final var activeOrdinalKey = columnFamily.get(key);
    if (activeOrdinalKey == null) {
      // 0-1000 are reserved ordinal keys,
      // using 101 as the default destination index when ordinals not initialized
      return 101;
    }
    return activeOrdinalKey.getValue();
  }

  @Override
  public void activate(final int ordinalKey) {
    key.wrapString(ACTIVE_KEY);
    value.wrapInt(ordinalKey);
    columnFamily.upsert(key, value);
  }
}
