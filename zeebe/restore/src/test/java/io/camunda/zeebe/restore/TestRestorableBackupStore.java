/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.ListOptions;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

final class TestRestorableBackupStore implements BackupStore {

  final Map<BackupIdentifier, Backup> backups = new ConcurrentHashMap<>();
  final Map<BackupIdentifier, CompletableFuture<Backup>> waiters = new ConcurrentHashMap<>();
  final Map<Integer, byte[]> metadataByPartition = new ConcurrentHashMap<>();
  private final @Nullable Path copyDirectory;

  /** A store that keeps references to the files of a backup, which must outlive the restore. */
  TestRestorableBackupStore() {
    this(null);
  }

  /**
   * A store that copies the files of a backup into the given directory when saving it, as a real
   * store uploads them, so the backup survives compaction and newer snapshots.
   */
  TestRestorableBackupStore(final @Nullable Path copyDirectory) {
    this.copyDirectory = copyDirectory;
  }

  /**
   * Must be called before a backup is saved or marked as failed.
   *
   * @return A future that completes with the backup that was taken.
   */
  CompletableFuture<Backup> waitForBackup(final BackupIdentifier id) {
    return waiters.computeIfAbsent(id, (ignored) -> new CompletableFuture<>());
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    final var saved = copyDirectory == null ? backup : copy(backup, copyDirectory);
    backups.put(saved.id(), saved);
    Optional.ofNullable(waiters.remove(saved.id())).ifPresent(waiter -> waiter.complete(saved));
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
  public CompletableFuture<List<BackupStatus>> list(
      final BackupIdentifierWildcard wildcard, final ListOptions options) {
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
    return CompletableFuture.completedFuture(options.select(matchingBackups, BackupStatus::id));
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
    Optional.ofNullable(waiters.remove(id))
        .ifPresent(
            waiter ->
                waiter.completeExceptionally(
                    new RuntimeException("Backup failed: %s".formatted(failureReason))));
    return CompletableFuture.completedFuture(BackupStatusCode.FAILED);
  }

  @Override
  public CompletableFuture<BackupStatusCode> markDeleted(final BackupIdentifier id) {
    return CompletableFuture.completedFuture(BackupStatusCode.DELETED);
  }

  @Override
  public CompletableFuture<Void> storeBackupMetadata(final int partitionId, final byte[] content) {
    metadataByPartition.put(partitionId, content);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Optional<byte[]>> loadBackupMetadata(final int partitionId) {
    return CompletableFuture.completedFuture(
        Optional.ofNullable(metadataByPartition.get(partitionId)));
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    waiters
        .values()
        .forEach(waiter -> waiter.completeExceptionally(new RuntimeException("Store was closed")));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> verifyConnection() {
    return CompletableFuture.completedFuture(null);
  }

  private Backup copy(final Backup backup, final Path directory) {
    final var target =
        directory.resolve("%d-%d".formatted(backup.id().partitionId(), backup.id().checkpointId()));
    try {
      final var snapshot =
          copyNamedFileSet(
              Files.createDirectories(target.resolve("snapshot")), backup.snapshot().namedFiles());
      final var segments =
          copyNamedFileSet(
              Files.createDirectories(target.resolve("segments")), backup.segments().namedFiles());
      return new BackupImpl(backup.id(), backup.descriptor(), snapshot, segments);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
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
