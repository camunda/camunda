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
import java.util.ConcurrentModificationException;
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
   * Stores a given {@link BackupIndexHandle}.
   *
   * @param indexHandle an opaque holder of the actual {@link BackupIndex}, as retrieved via {@link
   *     #restoreIndex(BackupIndexIdentifier, Path)}.
   * @return a new {@link BackupIndexHandle} representing the stored index; may contain metadata
   *     used to detect concurrent modifications.
   * @implNote Some implementations may fail the future with {@link
   *     java.util.ConcurrentModificationException} if they detect that the file has unexpectedly
   *     changed in the meantime.
   */
  CompletableFuture<BackupIndexHandle> storeIndex(BackupIndexHandle indexHandle)
      throws ConcurrentModificationException;

  /**
   * Makes the stored {@link BackupIndexHandle} available locally, allowing callers to construct the
   * actual {@link BackupIndex} from it.
   *
   * @param id the identifier of the index to restore
   * @param targetPath the local path where the index file should be downloaded
   * @implNote Some implementations may store metadata in the returned {@link BackupIndexHandle}
   *     that allows them to detect concurrent modifications during {@link
   *     #storeIndex(BackupIndexHandle)}.
   */
  CompletableFuture<BackupIndexHandle> restoreIndex(BackupIndexIdentifier id, Path targetPath);

  CompletableFuture<Void> closeAsync();
}
