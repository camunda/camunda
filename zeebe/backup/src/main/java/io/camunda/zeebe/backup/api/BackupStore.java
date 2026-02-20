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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** A store where the backup is stored * */
public interface BackupStore {

  /** Saves the backup to the backup storage. */
  CompletableFuture<Void> save(Backup backup);

  /** Returns the status of the backup */
  CompletableFuture<BackupStatus> getStatus(BackupIdentifier id);

  /** Uses the given wildcard to list all backups matching this wildcard. */
  CompletableFuture<Collection<BackupStatus>> list(BackupIdentifierWildcard wildcard);

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

  /**
   * Stores backup metadata content to a named slot for the given partition. Slots "a" and "b" are
   * used alternately for crash-safe atomic swap. The content is an opaque byte array (JSON).
   *
   * @param partitionId the partition this metadata belongs to
   * @param slot the slot name ("a" or "b")
   * @param content the serialized metadata content
   */
  CompletableFuture<Void> storeBackupMetadata(int partitionId, String slot, byte[] content);

  /**
   * Loads backup metadata content from a named slot for the given partition. Returns empty if the
   * slot does not exist (fresh deployment or pre-migration).
   *
   * @param partitionId the partition this metadata belongs to
   * @param slot the slot name ("a" or "b")
   * @return the serialized metadata content, or empty if not found
   */
  CompletableFuture<Optional<byte[]>> loadBackupMetadata(int partitionId, String slot);

  CompletableFuture<Void> closeAsync();
}
