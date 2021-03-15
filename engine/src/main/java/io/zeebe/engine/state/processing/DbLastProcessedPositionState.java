/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.processing;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.mutable.MutableLastProcessedPositionState;

public final class DbLastProcessedPositionState implements MutableLastProcessedPositionState {

  private static final String LAST_PROCESSED_EVENT_KEY = "LAST_PROCESSED_EVENT_KEY";
  private static final long NO_EVENTS_PROCESSED = -1L;

  private final DbString positionKey;
  private final LastProcessedPosition position = new LastProcessedPosition();
  private final ColumnFamily<DbString, LastProcessedPosition> positionColumnFamily;

  public DbLastProcessedPositionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    positionKey = new DbString();
    positionKey.wrapString(LAST_PROCESSED_EVENT_KEY);
    positionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEFAULT, transactionContext, positionKey, position);
  }

  @Override
  public long getLastSuccessfulProcessedRecordPosition() {
    final LastProcessedPosition position = positionColumnFamily.get(positionKey);
    return position != null ? position.get() : NO_EVENTS_PROCESSED;
  }

  @Override
  public void markAsProcessed(final long position) {
    this.position.set(position);
    positionColumnFamily.put(positionKey, this.position);
  }
}
