/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

final class InProgressBackupImpl implements InProgressBackup {

  private final BackupIdentifier backupId;
  private final long checkpointPosition;
  private final int numberOfPartitions;
  private final ConcurrencyControl concurrencyControl;

  InProgressBackupImpl(
      final BackupIdentifier backupId,
      final long checkpointPosition,
      final int numberOfPartitions,
      final ConcurrencyControl concurrencyControl) {
    this.backupId = backupId;
    this.checkpointPosition = checkpointPosition;
    this.numberOfPartitions = numberOfPartitions;
    this.concurrencyControl = concurrencyControl;
  }

  @Override
  public long checkpointId() {
    return backupId.checkpointId();
  }

  @Override
  public long checkpointPosition() {
    return checkpointPosition;
  }

  @Override
  public ActorFuture<Void> findValidSnapshot() {
    return concurrencyControl.createCompletedFuture();
  }

  @Override
  public ActorFuture<Void> reserveSnapshot() {
    return concurrencyControl.createCompletedFuture();
  }

  @Override
  public ActorFuture<Void> findSnapshotFiles() {
    return concurrencyControl.createCompletedFuture();
  }

  @Override
  public ActorFuture<Void> findSegmentFiles() {
    return concurrencyControl.createCompletedFuture();
  }

  @Override
  public ActorFuture<Void> save(final BackupStore store) {
    // save backup
    return concurrencyControl.createCompletedFuture();
  }

  @Override
  public void fail(final Throwable error) {
    // To be implemented
  }

  @Override
  public void close() {
    // To be implemented
  }
}
