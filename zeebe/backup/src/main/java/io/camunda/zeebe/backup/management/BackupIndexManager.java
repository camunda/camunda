/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupIndex;
import io.camunda.zeebe.backup.api.BackupIndex.IndexedBackup;
import io.camunda.zeebe.backup.api.BackupIndexFile;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.index.CompactBackupIndex;
import io.camunda.zeebe.backup.index.CompactBackupIndex.IndexCorruption;
import io.camunda.zeebe.backup.index.CompactBackupIndex.PartialIndexCorruption;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Manages a backup index for a specific node and partition. Handles restoring, updating, and
 * persisting the index. On index corruption, it will rebuild the index from the backups in the
 * store.
 */
final class BackupIndexManager implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(BackupIndexManager.class);
  private final ExecutorService executor =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("backup-index-", 0).factory());
  private final BackupStore store;
  private final Path path;
  private final ReentrantLock writeLock = new ReentrantLock();
  private final BackupIndexIdentifier indexId;
  private CompactBackupIndex index;
  private BackupIndexFile indexHandle;

  BackupIndexManager(
      final BackupStore store, final Path path, final BackupIndexIdentifier indexId) {
    this.store = store;
    this.path = path;
    this.indexId = indexId;
  }

  BackupIndexIdentifier indexId() {
    return indexId;
  }

  CompletableFuture<Void> add(final InProgressBackup backup) {
    return CompletableFuture.runAsync(
        () -> {
          MDC.put("index", indexId.toString());
          MDC.put("backup", backup.id().toString());
          addInternal(backup);
        },
        executor);
  }

  CompletableFuture<Void> remove(final BackupIdentifier backupId) {
    return CompletableFuture.runAsync(
        () -> {
          MDC.put("index", indexId.toString());
          MDC.put("backup", backupId.toString());
          removeInternal(backupId);
        },
        executor);
  }

  CompletableFuture<Stream<IndexedBackup>> indexedBackups() {
    return CompletableFuture.supplyAsync(
        () -> {
          MDC.put("index", indexId.toString());
          return internalIndexedBackups();
        },
        executor);
  }

  private void addInternal(final InProgressBackup backup) {
    try {
      if (!writeLock.tryLock(10, TimeUnit.SECONDS)) {
        LOG.atDebug().setMessage("Waiting >10s for index lock to add backup").log();
        writeLock.lockInterruptibly();
      }

      if (index == null) {
        restore();
      }
      modifyIndex(
          index -> {
            LOG.atDebug().setMessage("Adding backup to index").log();
            index.add(
                new IndexedBackup(
                    backup.id().checkpointId(),
                    // TODO: Use first log position from descriptor when available
                    -1L,
                    backup.backupDescriptor().checkpointPosition()));
          });
    } catch (final InterruptedException e) {
      LOG.atWarn()
          .setMessage("Interrupted while trying to add backup to index, backup not added")
          .log();
      throw new RuntimeException(
          "Interrupted while trying to remove backup %s from index %s"
              .formatted(backup.id(), indexId),
          e);
    } finally {
      writeLock.unlock();
    }
  }

  private void removeInternal(final BackupIdentifier backupId) {
    try {
      if (!writeLock.tryLock(10, TimeUnit.SECONDS)) {
        LOG.atDebug().setMessage("Waiting >10s for index lock to remove backup").log();
        writeLock.lockInterruptibly();
      }

      if (index == null) {
        restore();
      }
      LOG.atDebug().setMessage("Removing backup from index").log();
      modifyIndex(index -> index.remove(backupId.checkpointId()));
    } catch (final InterruptedException e) {
      LOG.atWarn()
          .setMessage("Interrupted while trying to remove backup from index, backup not removed")
          .log();
      Thread.currentThread().interrupt();
    } finally {
      writeLock.unlock();
    }
  }

  private Stream<IndexedBackup> internalIndexedBackups() {
    if (index == null) {
      try {
        if (!writeLock.tryLock(10, TimeUnit.SECONDS)) {
          LOG.atDebug().setMessage("Waiting >10s for index lock to restore index").log();
          writeLock.lockInterruptibly();
        }
        restore();
      } catch (final InterruptedException e) {
        LOG.atWarn().setMessage("Interrupted while trying to restore index").log();
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while trying to restore index", e);
      } finally {
        writeLock.unlock();
      }
    }
    return index.all();
  }

  /**
   * Modifies the index with the given modifier function. To handle concurrent modifications, it
   * will retry up to 10 times, by restoring the index again from the store and applying the
   * modifier again.
   */
  private void modifyIndex(final Consumer<BackupIndex> modifier) {
    var attempt = 0;
    while (attempt++ < 10) {
      try {
        if (index == null) {
          restore();
        }
        modifier.accept(index);
        store.storeIndex(indexHandle).join();
        LOG.atDebug().addKeyValue("attempt", attempt).setMessage("Stored updated index").log();
        return;
      } catch (final ConcurrentModificationException concurrentModificationException) {
        LOG.atWarn()
            .addKeyValue("attempt", attempt)
            .setMessage("Failed to update index due to concurrent modification, retrying")
            .setCause(concurrentModificationException)
            .log();
        try {
          index.close();
          Files.deleteIfExists(path);
        } catch (final IOException e) {
          LOG.atError()
              .setCause(e)
              .setMessage(
                  "Failed to delete outdated index file to recover from concurrent modification")
              .log();
          throw new UncheckedIOException(e);
        }

        index = null;
        indexHandle = null;
      }
    }
    LOG.atError()
        .addKeyValue("attempt", attempt)
        .setMessage(
            "Failed to modify index due concurrent modifications, index needs to be rebuilt")
        .log();
  }

  private void restore() {
    LOG.atDebug().setMessage("Restoring backup index from store").log();
    try {
      Files.deleteIfExists(path);
    } catch (final IOException e) {
      LOG.atError()
          .setMessage("Failed to delete existing local index file before restoring")
          .setCause(e)
          .log();
      throw new UncheckedIOException(e);
    }
    indexHandle = store.restoreIndex(indexId, path).join();
    try {
      index = CompactBackupIndex.open(indexHandle.path());
    } catch (final IndexCorruption | PartialIndexCorruption e) {
      LOG.atWarn().setMessage("Restored backup index is corrupt").setCause(e).log();
      rebuild();
    } catch (final IOException e) {
      LOG.atError().setMessage("Failed to open restored backup index from store").setCause(e).log();
      throw new UncheckedIOException(e);
    }
  }

  private void rebuild() {
    LOG.atInfo().setMessage("Rebuilding backup index from backups in store").log();
    try {
      Files.deleteIfExists(path);
    } catch (final IOException e) {
      LOG.atError().setMessage("Failed to delete existing index, cannot rebuild").setCause(e).log();
      throw new UncheckedIOException(e);
    }

    try {
      index = CompactBackupIndex.create(path);
    } catch (final IOException e) {
      LOG.atError().setMessage("Failed to create backup index for rebuilding").setCause(e).log();
      throw new UncheckedIOException(e);
    }
    final Collection<BackupStatus> backups;
    try {
      backups =
          store
              .list(
                  new BackupIdentifierWildcardImpl(
                      Optional.of(indexId.nodeId()),
                      Optional.of(indexId.partitionId()),
                      CheckpointPattern.any()))
              .join();
    } catch (final Exception listException) {
      LOG.atError()
          .setMessage("Failed to list backups from store to rebuild backup index")
          .setCause(listException)
          .log();
      throw listException;
    }
    LOG.atTrace()
        .setMessage("Found {} entries to rebuild backup index")
        .addArgument(backups::size)
        .log();

    for (final var backup : backups) {
      index.add(
          new IndexedBackup(
              backup.id().checkpointId(),
              // TODO: Use first log position from descriptor when available
              -1L,
              backup.descriptor().orElseThrow().checkpointPosition()));
    }

    try {
      indexHandle = store.storeIndex(indexHandle).join();
    } catch (final Exception storeException) {
      LOG.atError()
          .setMessage("Failed to store rebuilt backup index")
          .setCause(storeException)
          .log();
      throw storeException;
    }

    LOG.atInfo()
        .setMessage("Rebuilt backup index with {} entries")
        .addArgument(() -> index.all().count())
        .log();
  }

  @Override
  public void close() {
    try {
      executor.close();
      LOG.atDebug().addKeyValue("index", indexId).setMessage("Closing backup index").log();
      if (index != null) {
        index.close();
      }
    } catch (final IOException closeException) {
      LOG.atError()
          .addKeyValue("index", indexId)
          .setMessage("Failed to close backup index")
          .setCause(closeException)
          .log();
      throw new UncheckedIOException(closeException);
    }
  }
}
