/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.processing.state;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;

public class DbCheckpointState implements CheckpointState {
  private static final String CHECKPOINT_KEY = "checkpoint";

  private final CheckpointInfo checkpointInfo = new CheckpointInfo();
  private final ColumnFamily<DbString, CheckpointInfo> checkpointColumnFamily;
  private final DbString checkpointInfoKey;

  public DbCheckpointState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    checkpointInfoKey = new DbString();
    checkpointInfoKey.wrapString(CHECKPOINT_KEY);
    checkpointColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEFAULT, transactionContext, checkpointInfoKey, checkpointInfo);
  }

  @Override
  public long getCheckpointId() {
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getId() : NO_CHECKPOINT;
  }

  @Override
  public long getCheckpointPosition() {
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getPosition() : NO_CHECKPOINT;
  }

  @Override
  public void setCheckpointInfo(final long checkpointId, final long checkpointPosition) {
    checkpointInfo.setId(checkpointId).setPosition(checkpointPosition);
    checkpointColumnFamily.upsert(checkpointInfoKey, checkpointInfo);
  }
}
