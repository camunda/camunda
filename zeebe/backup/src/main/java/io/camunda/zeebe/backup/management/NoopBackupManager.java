/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopBackupManager implements BackupManager {

  private static final Logger LOG = LoggerFactory.getLogger(NoopBackupManager.class);
  private final String errorMessage;

  /**
   * @param errorMessage reason for installing NoopBackupManager. All operations will fail with this
   *     message.
   */
  public NoopBackupManager(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @Override
  public ActorFuture<Void> takeBackup(
      final long checkpointId, final BackupDescriptor backupDescriptor) {
    final var result = new CompletableActorFuture<Void>();
    result.completeExceptionally(new UnsupportedOperationException(errorMessage));
    return result;
  }

  @Override
  public ActorFuture<BackupStatus> getBackupStatus(final long checkpointId) {
    return CompletableActorFuture.completedExceptionally(
        new UnsupportedOperationException(errorMessage));
  }

  @Override
  public ActorFuture<Collection<BackupStatus>> listBackups(final String pattern) {
    return CompletableActorFuture.completedExceptionally(
        new UnsupportedOperationException(errorMessage));
  }

  @Override
  public ActorFuture<Void> deleteBackup(final long checkpointId) {
    return CompletableActorFuture.completedExceptionally(
        new UnsupportedOperationException(errorMessage));
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public void failInProgressBackup(final long lastCheckpointId) {
    if (lastCheckpointId == CheckpointState.NO_CHECKPOINT) {
      return;
    }
    LOG.warn("Attempted to update in progress backup, but cannot do it. {}", errorMessage);
  }

  @Override
  public void createFailedBackup(
      final long checkpointId,
      final BackupDescriptor backupDescriptor,
      final String failureReason) {
    LOG.warn("Attempted to create failed backup, but cannot do it. {}", errorMessage);
  }
}
