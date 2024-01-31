/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.Manifest;
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

  CompletableFuture<Void> closeAsync();

  static BackupStatus toStatus(final Manifest manifest) {
    return switch (manifest.statusCode()) {
      case IN_PROGRESS ->
          new BackupStatusImpl(
              manifest.id(),
              Optional.ofNullable(manifest.descriptor()),
              BackupStatusCode.IN_PROGRESS,
              Optional.empty(),
              Optional.ofNullable(manifest.createdAt()),
              Optional.ofNullable(manifest.modifiedAt()));
      case COMPLETED ->
          new BackupStatusImpl(
              manifest.id(),
              Optional.ofNullable(manifest.descriptor()),
              BackupStatusCode.COMPLETED,
              Optional.empty(),
              Optional.ofNullable(manifest.createdAt()),
              Optional.ofNullable(manifest.modifiedAt()));
      case FAILED ->
          new BackupStatusImpl(
              manifest.id(),
              Optional.ofNullable(manifest.descriptor()),
              BackupStatusCode.FAILED,
              Optional.ofNullable(manifest.asFailed().failureReason()),
              Optional.ofNullable(manifest.createdAt()),
              Optional.ofNullable(manifest.modifiedAt()));
    };
  }
}
