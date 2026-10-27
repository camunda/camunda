/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clock;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableClockState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;

public class DbClockState implements MutableClockState {
  private static final String KEY = "MODIFICATION";

  private final ColumnFamily<DbString, DbClockModification> columnFamily;
  private final DbString key = new DbString();
  private final DbClockModification value = new DbClockModification();

  public DbClockState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.CLOCK, transactionContext, key, value);
  }

  @Override
  public MutableClockState pinAt(final long epochMillis) {
    value.pinAt(epochMillis);
    key.wrapString(KEY);
    columnFamily.upsert(key, value);
    return this;
  }

  @Override
  public MutableClockState offsetBy(final long offsetMillis) {
    value.offsetBy(offsetMillis);
    key.wrapString(KEY);
    columnFamily.upsert(key, value);
    return this;
  }

  @Override
  public MutableClockState reset() {
    value.reset();
    key.wrapString(KEY);
    columnFamily.upsert(key, value);
    return this;
  }

  @Override
  public Modification getModification() {
    key.wrapString(KEY);
    final var modification = columnFamily.get(key);

    if (modification == null) {
      return Modification.none();
    }

    return modification.modification();
  }
}
