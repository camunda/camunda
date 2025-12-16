/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.backup;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIndexFile;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMockBackupStore implements BackupStore, AutoCloseable {

  private final ConcurrentHashMap<BackupIdentifier, CompletableFuture<Void>> saveFutures =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<BackupIdentifier, BackupStatus> backupStatusMap =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<BackupIdentifier, Backup> backupMap = new ConcurrentHashMap<>();

  public Set<BackupIdentifier> backupInProgress() {
    return backupMap.keySet();
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    backupMap.put(backup.id(), backup);
    final var fut = new CompletableFuture<Void>();
    saveFutures.put(backup.id(), fut);
    return fut;
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    return CompletableFuture.completedFuture(backupStatusMap.get(id));
  }

  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    return CompletableFuture.completedFuture(
        backupStatusMap.entrySet().stream()
            .filter(e -> wildcard.matches(e.getKey()))
            .map(Entry::getValue)
            .toList());
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    backupStatusMap.remove(id);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    return CompletableFuture.completedFuture(backupMap.get(id));
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    throw new UnsupportedOperationException("Not yet implemented; implemented it when required");
  }

  @Override
  public CompletableFuture<Void> storeIndex(final BackupIndexFile indexFile) {
    throw new UnsupportedOperationException("Not yet implemented; implement it when required");
  }

  @Override
  public CompletableFuture<BackupIndexFile> restoreIndex(
      final BackupIndexIdentifier id, final Path targetPath) {
    throw new UnsupportedOperationException("Not yet implemented; implement it when required");
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    return CompletableFuture.completedFuture(null);
  }

  public void completeSaveFutures() {
    for (final var entry : saveFutures.entrySet()) {
      final var future = entry.getValue();
      final var id = entry.getKey();
      // complete the future if all files exist
      final var backup = backupMap.get(id);
      completeFuture(backup, future);
    }
    saveFutures.clear();
  }

  private void completeFuture(final Backup backup, final CompletableFuture<Void> future) {
    final var allFiles = new ArrayList<Path>();
    allFiles.addAll(backup.snapshot().files());
    allFiles.addAll(backup.segments().files());
    for (final var path : allFiles) {
      if (!Files.exists(path)) {
        future.completeExceptionally(
            new FileNotFoundException("File not found %s".formatted(path)));
        break;
      }
    }
    if (!future.isCompletedExceptionally()) {
      future.complete(null);
    }
  }

  @Override
  public void close() throws Exception {
    completeSaveFutures();
  }
}
