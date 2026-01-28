/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Collection;

/** Manages backup of a partition * */
public interface BackupManager {

  /**
   * Takes backup with id checkpointId using the provided partition count. The method returns
   * immediately after triggering the backup. {@link BackupManager#getBackupStatus(long)} must be
   * used to check if the backup is completed or not. If a completed backup with same id already
   * exists, no new backup will be taken.
   *
   * <p>The implementation of this method should be non-blocking to not block the caller.
   *
   * @param checkpointId id of the backup to take
   * @param backupDescriptor descriptor of the checkpoint triggering the backup
   * @return an ActorFuture with the result of the backup
   */
  ActorFuture<Void> takeBackup(final long checkpointId, BackupDescriptor backupDescriptor);

  /**
   * Get the status of the backup
   *
   * @param checkpointId id of the backup to get status for
   * @return backup status
   */
  ActorFuture<BackupStatus> getBackupStatus(long checkpointId);

  /**
   * Get all available backups where status is one of {@link BackupStatusCode#COMPLETED}, {@link
   * BackupStatusCode#FAILED}, {@link BackupStatusCode#IN_PROGRESS}
   *
   * @param pattern null, empty, a prefix ending in '*' or an exact backup id
   * @return all backups with ids matching the pattern
   */
  ActorFuture<Collection<BackupStatus>> listBackups(final String pattern);

  /**
   * Deletes the backup.
   *
   * @param checkpointId id of the backup to delete
   * @return future which will be completed after the backup is deleted.
   */
  ActorFuture<Void> deleteBackup(long checkpointId);

  /** Close Backup manager */
  ActorFuture<Void> closeAsync();

  void failInProgressBackup(long lastCheckpointId);

  /**
   * Creates a backup with failed status. This is used when a backup cannot be taken due to system
   * constraints (e.g., scaling in progress) but the backup entry needs to be recorded.
   *
   * @param checkpointId id of the backup to create
   * @param backupDescriptor descriptor of the checkpoint triggering the backup
   * @param failureReason reason why the backup failed
   */
  void createFailedBackup(
      final long checkpointId, BackupDescriptor backupDescriptor, String failureReason);

  void extendRange(final long previousCheckpointId, final long newCheckpointId);

  void startNewRange(final long checkpointId);
}
