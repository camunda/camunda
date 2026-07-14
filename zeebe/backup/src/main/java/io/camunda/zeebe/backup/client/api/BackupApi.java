/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface BackupApi {
  String WILDCARD = "*";
  String STATE = "state";
  String SYNC = "sync";

  /**
   * Triggers backup on all partitions of the given physical tenant. Returned future is completed
   * successfully after all partitions have processed the request. Returned future fails if the
   * request was not processed by at least one partition.
   *
   * <p>TODO: check if it makes more sense to return a {@link java.util.concurrent.Future} if we're
   * always blocking on the result and never combining.
   *
   * @param physicalTenantId the physical tenant (partition group) to target
   * @param backupId the id of the backup to be taken
   * @return the backupId
   */
  CompletionStage<Long> takeBackup(String physicalTenantId, long backupId);

  /**
   * Triggers backup on all partitions of the given physical tenant using the current timestamp as
   * the backupId. An optional offset may be added to the timestamp to maintain consistency with
   * existing backup ID schemes (e.g., if migrating from a different timestamp format).
   *
   * @param physicalTenantId the physical tenant (partition group) to target
   * @return the generated backupId (current timestamp + offset)
   */
  CompletionStage<Long> takeBackup(String physicalTenantId);

  /**
   * Returns the status of the backup. The future fails if the request was not processed by at least
   * one partition.
   *
   * <p>TODO: check if it makes more sense to return a {@link java.util.concurrent.Future} if we're
   * always blocking on the result and never combining.
   *
   * @param physicalTenantId the physical tenant (partition group) to target
   * @return the status of the backup
   */
  CompletionStage<BackupStatus> getStatus(String physicalTenantId, long backupId);

  /**
   * @param physicalTenantId the physical tenant (partition group) to target
   * @return the latest checkpoint and state for all partitions
   */
  CompletionStage<CheckpointStateResponse> getCheckpointState(String physicalTenantId);

  /**
   * @param physicalTenantId the physical tenant (partition group) to target
   * @return all backup ranges for all partitions
   */
  CompletionStage<BackupRangesResponse> getBackupRanges(String physicalTenantId);

  /**
   * @param physicalTenantId the physical tenant (partition group) to target
   * @return a list of available backups
   */
  default CompletionStage<List<BackupStatus>> listBackups(final String physicalTenantId) {
    return listBackups(physicalTenantId, WILDCARD);
  }

  /**
   * Returns a list of backups with ids matching the prefix.
   *
   * @param physicalTenantId the physical tenant (partition group) to target
   * @param prefix A string that backup ids must match. Must end in a single `*`.
   */
  CompletionStage<List<BackupStatus>> listBackups(String physicalTenantId, String prefix);

  /**
   * Deletes the backup with the given id
   *
   * @param physicalTenantId the physical tenant (partition group) to target
   * @param backupId id of the backup to delete
   */
  CompletionStage<Void> deleteBackup(String physicalTenantId, long backupId);

  /**
   * Force-write backup metadata for all partitions of the given physical tenant.
   *
   * @param physicalTenantId the physical tenant (partition group) to target
   */
  CompletionStage<BackupRangesResponse> syncMetadata(String physicalTenantId);

  /**
   * Resets the backup runtime state on all partitions of the given physical tenant. Clears all
   * checkpoint info, backup info, checkpoint metadata, and backup ranges. Used when switching
   * backup stores.
   *
   * @param physicalTenantId the physical tenant (partition group) to target
   */
  CompletionStage<Void> deleteRuntimeState(String physicalTenantId);
}
