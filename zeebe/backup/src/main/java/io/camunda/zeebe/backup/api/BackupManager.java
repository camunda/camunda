/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Collection;

/** Manages backup of a partition * */
public interface BackupManager {

  /**
   * Takes backup with id checkpointId. The method returns immediately after triggering the backup.
   * {@link BackupManager#getBackupStatus(long)} must be used to check if the backup is completed or
   * not. If a completed backup with same id already exists, no new backup will be taken.
   *
   * <p>The implementation of this method should be non-blocking to not block the caller.
   *
   * @param checkpointId id of the backup
   * @param checkpointPosition position of the record until which must be included in the backup.
   * @return an ActorFuture with the result of the backup
   */
  ActorFuture<Void> takeBackup(long checkpointId, long checkpointPosition);

  /**
   * Get the status of the backup
   *
   * @param checkpointId
   * @return backup status
   */
  ActorFuture<BackupStatus> getBackupStatus(long checkpointId);

  /**
   * Get all available backups where status is one of {@link BackupStatusCode#COMPLETED}, {@link
   * BackupStatusCode#FAILED}, {@link BackupStatusCode#IN_PROGRESS}
   *
   * @return a collection of backup status
   */
  ActorFuture<Collection<BackupStatus>> listBackups(final String pattern);

  /**
   * Deletes the backup.
   *
   * @param checkpointId
   * @return future which will be completed after the backup is deleted.
   */
  ActorFuture<Void> deleteBackup(long checkpointId);

  /** Close Backup manager */
  ActorFuture<Void> closeAsync();

  void failInProgressBackup(long lastCheckpointId);
}
