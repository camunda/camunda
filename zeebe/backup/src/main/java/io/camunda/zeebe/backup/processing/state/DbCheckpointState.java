/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public final class DbCheckpointState implements CheckpointState {
  private static final String LATEST_CHECKPOINT_KEY = "checkpoint";
  private static final String LATEST_BACKUP_KEY = "backup";

  private final CheckpointInfo checkpointInfo = new CheckpointInfo();
  private final ColumnFamily<DbString, CheckpointInfo> checkpointColumnFamily;
  private final DbString checkpointInfoKey;

  public DbCheckpointState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    checkpointInfoKey = new DbString();
    checkpointInfoKey.wrapString(LATEST_CHECKPOINT_KEY);
    checkpointColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEFAULT, transactionContext, checkpointInfoKey, checkpointInfo);
  }

  @Override
  public long getLatestCheckpointId() {
    checkpointInfoKey.wrapString(LATEST_CHECKPOINT_KEY);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getId() : NO_CHECKPOINT;
  }

  @Override
  public long getLatestCheckpointPosition() {
    checkpointInfoKey.wrapString(LATEST_CHECKPOINT_KEY);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getPosition() : NO_CHECKPOINT;
  }

  @Override
  public void setLatestCheckpointInfo(final long checkpointId, final long checkpointPosition) {
    checkpointInfoKey.wrapString(LATEST_CHECKPOINT_KEY);
    checkpointInfo.setId(checkpointId).setPosition(checkpointPosition);
    checkpointColumnFamily.upsert(checkpointInfoKey, checkpointInfo);
  }

  @Override
  public void setLatestBackupInfo(final long checkpointId, final long checkpointPosition) {
    checkpointInfoKey.wrapString(LATEST_BACKUP_KEY);
    checkpointInfo.setId(checkpointId).setPosition(checkpointPosition);
    checkpointColumnFamily.upsert(checkpointInfoKey, checkpointInfo);
  }

  @Override
  public long getLatestBackupId() {
    checkpointInfoKey.wrapString(LATEST_BACKUP_KEY);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getId() : NO_CHECKPOINT;
  }

  @Override
  public long getLatestBackupPosition() {
    checkpointInfoKey.wrapString(LATEST_BACKUP_KEY);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getPosition() : NO_CHECKPOINT;
  }
}
