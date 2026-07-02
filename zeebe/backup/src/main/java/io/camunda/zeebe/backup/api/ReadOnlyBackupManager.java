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

/**
 * Read-only view of a partition's backups. Exposes only the query operations that do not mutate
 * backup state, so it can be served in two modes:
 *
 * <ul>
 *   <li><b>Processing mode</b> — by a partition leader via the full {@link BackupManager}, which is
 *       also read-write.
 *   <li><b>Recovery mode</b> — by a member whose partition has no RocksDB state yet, via {@code
 *       ReadOnlyBackupService}, which answers these queries straight from the backup store.
 *       Mutating operations are not available in this mode.
 * </ul>
 */
public interface ReadOnlyBackupManager {

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
  ActorFuture<Collection<BackupStatus>> listBackups(String pattern);

  /**
   * @return all backup ranges for the partition
   */
  ActorFuture<Collection<BackupRangeStatus>> getBackupRangeStatus();
}
