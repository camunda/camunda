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

/**
 * Backup control operations that fan out to the partitions of a single physical tenant.
 *
 * <p>Every operation takes the target {@code physicalTenantId}: partitions are enumerated from that
 * tenant's topology and every broker request is stamped with it as its partition group. Callers
 * that are not yet physical-tenant-aware pass {@link
 * io.camunda.cluster.PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID}.
 */
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
   * @param physicalTenantId the physical tenant whose partitions are targeted
   * @param backupId the id of the backup to be taken
   * @return the backupId
   */
  CompletionStage<Long> takeBackup(String physicalTenantId, long backupId);

  /**
   * Triggers backup on all partitions of the given physical tenant using the current timestamp as
   * the backupId. An optional offset may be added to the timestamp to maintain consistency with
   * existing backup ID schemes (e.g., if migrating from a different timestamp format).
   *
   * @param physicalTenantId the physical tenant whose partitions are targeted
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
   * @param physicalTenantId the physical tenant whose partitions are targeted
   * @return the status of the backup
   */
  CompletionStage<BackupStatus> getStatus(String physicalTenantId, long backupId);

  /**
   * @param physicalTenantId the physical tenant whose partitions are targeted
   * @return the latest checkpoint and state for all partitions
   */
  CompletionStage<CheckpointStateResponse> getCheckpointState(String physicalTenantId);

  /**
   * @param physicalTenantId the physical tenant whose partitions are targeted
   * @return all backup ranges for all partitions
   */
  CompletionStage<BackupRangesResponse> getBackupRanges(String physicalTenantId);

  /**
   * @param physicalTenantId the physical tenant whose partitions are targeted
   * @return a list of available backups
   */
  default CompletionStage<List<BackupStatus>> listBackups(final String physicalTenantId) {
    return listBackups(physicalTenantId, WILDCARD);
  }

  /**
   * Returns a list of backups with ids matching the prefix.
   *
   * @param physicalTenantId the physical tenant whose partitions are targeted
   * @param prefix A string that backup ids must match. Must end in a single `*`.
   */
  CompletionStage<List<BackupStatus>> listBackups(String physicalTenantId, String prefix);

  /**
   * Deletes the backup with the given id
   *
   * @param physicalTenantId the physical tenant whose partitions are targeted
   * @param backupId id of the backup to delete
   */
  CompletionStage<Void> deleteBackup(String physicalTenantId, long backupId);

  /**
   * Force-write backup metadata for all partitions.
   *
   * @param physicalTenantId the physical tenant whose partitions are targeted
   */
  CompletionStage<BackupRangesResponse> syncMetadata(String physicalTenantId);

  /**
   * Resets the backup runtime state on all partitions. Clears all checkpoint info, backup info,
   * checkpoint metadata, and backup ranges. Used when switching backup stores.
   *
   * @param physicalTenantId the physical tenant whose partitions are targeted
   */
  CompletionStage<Void> deleteRuntimeState(String physicalTenantId);
}
