/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

final class TestRestorableBackupStore implements BackupStore {

  final Map<BackupIdentifier, Backup> backups = new HashMap<>();
  CompletableFuture<Backup> backupFuture;

  CompletableFuture<Backup> getBackupFuture() {
    return backupFuture;
  }

  void setBackupFuture(final CompletableFuture<Backup> backupFuture) {
    this.backupFuture = backupFuture;
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    backups.put(backup.id(), backup);
    if (backupFuture != null) {
      backupFuture.complete(backup);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    final var backup = backups.get(id);
    final BackupStatus backupStatus;
    if (backup != null) {
      backupStatus =
          new BackupStatusImpl(
              id,
              Optional.of(backup.descriptor()),
              BackupStatusCode.COMPLETED,
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
    } else {
      backupStatus =
          new BackupStatusImpl(
              id,
              Optional.empty(),
              BackupStatusCode.DOES_NOT_EXIST,
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
    }
    return CompletableFuture.completedFuture(backupStatus);
  }

  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    final var matchingBackups =
        backups.values().stream()
            .filter(backup -> wildcard.matches(backup.id()))
            .map(
                backup ->
                    (BackupStatus)
                        new BackupStatusImpl(
                            backup.id(),
                            Optional.of(backup.descriptor()),
                            BackupStatusCode.COMPLETED,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()))
            .toList();
    return CompletableFuture.completedFuture(matchingBackups);
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    return null;
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    final var backup = backups.get(id);
    final var snapshotFiles = copyNamedFileSet(targetFolder, backup.snapshot().namedFiles());

    final var segmentFiles = copyNamedFileSet(targetFolder, backup.segments().namedFiles());
    final var restoredBackup = new BackupImpl(id, backup.descriptor(), snapshotFiles, segmentFiles);
    return CompletableFuture.completedFuture(restoredBackup);
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    return null;
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    return null;
  }

  private NamedFileSet copyNamedFileSet(
      final Path targetFolder, final Map<String, Path> snapshotFiles) {
    return new NamedFileSetImpl(
        snapshotFiles.keySet().stream()
            .map(
                name -> {
                  try {
                    Files.copy(snapshotFiles.get(name), targetFolder.resolve(name));
                    return Map.entry(name, targetFolder.resolve(name));
                  } catch (final IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
  }
}
