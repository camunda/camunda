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
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;

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
    return getCheckpointId(LATEST_CHECKPOINT_KEY);
  }

  @Override
  public long getLatestCheckpointPosition() {
    return getCheckpointPosition(LATEST_CHECKPOINT_KEY);
  }

  @Override
  public long getLatestCheckpointTimestamp() {
    return getCheckpointTimestamp(LATEST_CHECKPOINT_KEY);
  }

  @Override
  public CheckpointType getLatestCheckpointType() {
    return getCheckpointType(LATEST_CHECKPOINT_KEY);
  }

  @Override
  public void setLatestCheckpointInfo(
      final long checkpointId,
      final long checkpointPosition,
      final long timestamp,
      final CheckpointType type) {
    checkpointInfoKey.wrapString(LATEST_CHECKPOINT_KEY);
    checkpointInfo
        .setId(checkpointId)
        .setPosition(checkpointPosition)
        .setTimestamp(timestamp)
        .setType(type);
    checkpointColumnFamily.upsert(checkpointInfoKey, checkpointInfo);
  }

  @Override
  public void setLatestBackupInfo(
      final long checkpointId,
      final long checkpointPosition,
      final long timestamp,
      final CheckpointType type,
      final long firstLogPosition) {
    checkpointInfoKey.wrapString(LATEST_BACKUP_KEY);
    checkpointInfo
        .setId(checkpointId)
        .setPosition(checkpointPosition)
        .setTimestamp(timestamp)
        .setType(type)
        .setFirstLogPosition(firstLogPosition);
    checkpointColumnFamily.upsert(checkpointInfoKey, checkpointInfo);
  }

  @Override
  public long getLatestBackupId() {
    return getCheckpointId(LATEST_BACKUP_KEY);
  }

  @Override
  public long getLatestBackupPosition() {
    return getCheckpointPosition(LATEST_BACKUP_KEY);
  }

  @Override
  public long getLatestBackupTimestamp() {
    return getCheckpointTimestamp(LATEST_BACKUP_KEY);
  }

  @Override
  public CheckpointType getLatestBackupType() {
    return getCheckpointType(LATEST_BACKUP_KEY);
  }

  @Override
  public long getLatestBackupFirstLogPosition() {
    return getFirstLogPosition(LATEST_BACKUP_KEY);
  }

  private long getCheckpointId(final String key) {
    checkpointInfoKey.wrapString(key);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getId() : NO_CHECKPOINT;
  }

  private long getCheckpointPosition(final String key) {
    checkpointInfoKey.wrapString(key);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getPosition() : NO_CHECKPOINT;
  }

  private long getCheckpointTimestamp(final String key) {
    checkpointInfoKey.wrapString(key);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getTimestamp() : -1L;
  }

  private CheckpointType getCheckpointType(final String key) {
    checkpointInfoKey.wrapString(key);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getType() : null;
  }

  private long getFirstLogPosition(final String key) {
    checkpointInfoKey.wrapString(key);
    final CheckpointInfo info = checkpointColumnFamily.get(checkpointInfoKey);
    return info != null ? info.getFirstLogPosition() : -1L;
  }
}
