/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;

/** A store where the backup is stored * */
public interface BackupStore {

  /** Saves the backup to the backup storage. */
  CompletableFuture<Void> save(Backup backup);

  /** Returns the status of the backup */
  CompletableFuture<BackupStatus> getStatus(BackupIdentifier id);

  /** Lists all backups matching the wildcard, newest first. */
  default CompletableFuture<Collection<BackupStatus>> list(
      final BackupIdentifierWildcard wildcard) {
    return list(wildcard, ListOptions.all()).thenApply(statuses -> statuses);
  }

  /**
   * Lists the backups matching the wildcard that fall into the page described by the options,
   * ordered by checkpoint id as the options request. Stores enumerate the keys matching the
   * wildcard, select the page with {@link ListOptions#select} and read only the selected manifests.
   */
  CompletableFuture<List<BackupStatus>> list(
      BackupIdentifierWildcard wildcard, ListOptions options);

  /**
   * Delete all state related to the backup from the storage. Backups with status{@link
   * BackupStatusCode#IN_PROGRESS} is not deleted. The caller of this method must first mark it as
   * failed.
   */
  CompletableFuture<Void> delete(BackupIdentifier id);

  /** Restores the backup */
  CompletableFuture<Backup> restore(BackupIdentifier id, Path targetFolder);

  /**
   * Marks the backup as failed. If saving a backup failed, the backups store must mark it as
   * failed. This method can be used if we want to explicitly mark a partial backup as failed.
   */
  CompletableFuture<BackupStatusCode> markFailed(BackupIdentifier id, final String failureReason);

  CompletableFuture<BackupStatusCode> markDeleted(BackupIdentifier id);

  /**
   * Stores backup metadata content for the given partition. The content is an opaque byte array
   * (JSON).
   *
   * @param partitionId the partition this metadata belongs to
   * @param content the serialized metadata content
   */
  CompletableFuture<Void> storeBackupMetadata(int partitionId, byte[] content);

  /**
   * Loads backup metadata content for the given partition. Returns empty if the slot does not exist
   * (fresh deployment or pre-migration).
   *
   * @param partitionId the partition this metadata belongs to
   * @return the serialized metadata content, or empty if not found
   */
  CompletableFuture<Optional<byte[]>> loadBackupMetadata(int partitionId);

  /**
   * Stores the handover record of the partition for the given handover checkpoint, at {@code
   * metadata/<partitionId>/handover/<checkpointId>.json}. It is a new object next to the backup
   * metadata and never changes the metadata or any backup. The content is an opaque byte array
   * (JSON).
   *
   * <p>Idempotent: storing the same checkpoint again replaces the record, which is fine as long as
   * the content is the same.
   *
   * @param partitionId the partition the handover belongs to
   * @param checkpointId the id of the handover checkpoint
   * @param content the serialized handover record
   */
  CompletableFuture<Void> storeHandoverRecord(int partitionId, long checkpointId, byte[] content);

  /**
   * Loads all handover records of the partition. There can be more than one if a restore to a point
   * before a handover led to another handover on a new branch.
   *
   * @param partitionId the partition the handover records belong to
   * @return the serialized handover records by handover checkpoint id, ascending; empty if there is
   *     none
   */
  CompletableFuture<SortedMap<Long, byte[]>> loadHandoverRecords(int partitionId);

  CompletableFuture<Void> closeAsync();

  CompletableFuture<Void> verifyConnection();
}
