/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.rocksdb.transaction.SmallColumnFamily;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.state.MutableLastProcessedPositionState;

public final class DbLastProcessedPositionState implements MutableLastProcessedPositionState {

  private static final String LAST_PROCESSED_EVENT_KEY = "LAST_PROCESSED_EVENT_KEY";
  private static final long NO_EVENTS_PROCESSED = -1L;

  private final DbString positionKey;
  private final LastProcessedPosition position = new LastProcessedPosition();
  private final SmallColumnFamily<ZbColumnFamilies, DbString, LastProcessedPosition>
      positionColumnFamily;

  public DbLastProcessedPositionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    positionKey = new DbString();
    positionKey.wrapString(LAST_PROCESSED_EVENT_KEY);
    positionColumnFamily =
        zeebeDb.createCachedColumnFamily(
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
    positionColumnFamily.upsert(positionKey, this.position);
  }
}
