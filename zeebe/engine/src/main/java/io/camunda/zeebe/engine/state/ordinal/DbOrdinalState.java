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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableOrdinalState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public final class DbOrdinalState implements MutableOrdinalState {

  private static final String KEY = "CURRENT";

  private int current = -1;
  private final ColumnFamily<DbString, DbOrdinalEntry> columnFamily;
  private final DbString key = new DbString();
  private final DbOrdinalEntry value = new DbOrdinalEntry();

  public DbOrdinalState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.ORDINAL, transactionContext, key, value);
  }

  @Override
  public int getCurrentOrdinal() {
    final var entry = getEntry();
    current = current != -1 ? current : entry != null ? entry.getOrdinal() : 0;
    return current;
  }

  @Override
  public long getCurrentDateTime() {
    final var entry = getEntry();
    return entry != null ? entry.getDateTime() : 0L;
  }

  @Override
  public int incrementOrdinal(final long dateTimeMillis) {
    final var nextOrdinal = getCurrentOrdinal() + 1;
    current = nextOrdinal;

    key.wrapString(KEY);
    value.set(nextOrdinal, dateTimeMillis);
    columnFamily.upsert(key, value);
    return nextOrdinal;
  }

  private DbOrdinalEntry getEntry() {
    key.wrapString(KEY);
    return columnFamily.get(key);
  }
}
