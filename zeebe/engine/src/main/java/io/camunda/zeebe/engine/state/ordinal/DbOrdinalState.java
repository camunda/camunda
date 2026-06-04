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

public class DbOrdinalState implements MutableOrdinalState {

  // key to identify the currently active ordinal
  private static final String ACTIVE_ORDINAL_KEY_KEY = "ACTIVE_ORDINAL_KEY";

  /** Stores the active ordinal key which we can use to get it's ordinal state by key. */
  private final ColumnFamily<DbString, DbInt> activeOrdinalColumnFamily;

  private final DbString activeOrdinalKey = new DbString();
  private final DbInt ordinalKey = new DbInt();

  public DbOrdinalState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    activeOrdinalColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ORDINAL_STATE, transactionContext, activeOrdinalKey, ordinalKey);
  }

  @Override
  public boolean isInitialized() {
    activeOrdinalKey.wrapString(ACTIVE_ORDINAL_KEY_KEY);
    final var activeOrdinalKey = activeOrdinalColumnFamily.get(this.activeOrdinalKey);
    return activeOrdinalKey != null;
  }

  @Override
  public int getActiveOrdinalKey() {
    activeOrdinalKey.wrapString(ACTIVE_ORDINAL_KEY_KEY);
    final var activeOrdinalKey = activeOrdinalColumnFamily.get(this.activeOrdinalKey);
    if (activeOrdinalKey == null) {
      // 0-1000 are reserved ordinal keys,
      // using 101 as the default destination index when ordinals not initialized
      return 101;
    }
    return activeOrdinalKey.getValue();
  }

  @Override
  public void activate(final int ordinalKey) {
    activeOrdinalKey.wrapString(ACTIVE_ORDINAL_KEY_KEY);
    this.ordinalKey.wrapInt(ordinalKey);
    activeOrdinalColumnFamily.upsert(activeOrdinalKey, this.ordinalKey);
  }
}
