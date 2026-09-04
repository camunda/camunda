/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.backup.common.LoggingBackupStore;
import java.nio.file.Path;
import java.util.Collection;
<<<<<<< HEAD
=======
import java.util.List;
import java.util.Optional;
>>>>>>> 57406a47 (feat: page backup store listings by checkpoint id)
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.event.Level;

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

  CompletableFuture<Void> closeAsync();

  default BackupStore logging(final Logger logger, final Level level) {
    return new LoggingBackupStore(this, logger, level);
  }
}
