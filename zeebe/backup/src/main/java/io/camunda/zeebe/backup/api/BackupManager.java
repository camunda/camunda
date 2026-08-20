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
import java.util.SequencedCollection;

/**
 * Manages backup of a partition. This is the full, read-write view of a partition's backups: it
 * inherits the read-only query operations from {@link ReadOnlyBackupManager} and adds the mutating
 * operations (take, delete, sync, clear). Only a partition leader with access to its RocksDB state
 * can serve this interface. A member serving requests while in recovery mode exposes just the
 * {@link ReadOnlyBackupManager} query subset instead — see {@code ReadOnlyBackupService}.
 */
public interface BackupManager extends ReadOnlyBackupManager {

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
   * Deletes the backup.
   *
   * @param checkpointId id of the backup to delete
   * @return future which will be completed after the backup is deleted.
   */
  ActorFuture<Void> requestBackupDeletion(long checkpointId);

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

  /**
   * Force-write the backup metadata file for this partition using the provided checkpoints and
   * ranges.
   */
  ActorFuture<Collection<BackupRangeStatus>> syncMetadata(
      SequencedCollection<Checkpoint> checkpoints, SequencedCollection<BackupRange> ranges);

  /**
   * Clears the backup runtime state for this partition by writing a CLEAR_STATE command to the log.
   * This clears all checkpoint info, backup info, checkpoint metadata, and backup ranges. Used when
   * switching backup stores.
   *
   * @return future which will be completed after the CLEAR_STATE command is written to the log
   */
  ActorFuture<Void> requestStateClear();
}
