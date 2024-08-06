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
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.mutable.MutableClockControlState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockControlRecord;
import org.slf4j.Logger;

public final class DbClockControlState implements MutableClockControlState {
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final DbString key;

  private final ClockControlRaw clockControlRaw;
  private final ColumnFamily<DbString, ClockControlRaw> clockControlRawColumnFamily;

  public DbClockControlState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    key = new DbString();
    key.wrapString("PartitionClock");
    clockControlRaw = new ClockControlRaw();
    clockControlRawColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPLOYMENT_RAW, transactionContext, key, clockControlRaw);
  }

  @Override
  public void storeClockControlRecord(final ClockControlRecord value) {
    clockControlRaw.setClockControlRecord(value);
    clockControlRawColumnFamily.insert(key, clockControlRaw);
  }

  @Override
  public ClockControlRecord getStoredClockControlRecord() {

    final var storedClockControlRaw = clockControlRawColumnFamily.get(key);

    ClockControlRecord record = null;
    if (storedClockControlRaw != null) {
      record = storedClockControlRaw.getClockControlRecord();
    }

    return record;
  }
}
