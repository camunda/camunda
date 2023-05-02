/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.restore;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Restores a backup from the given {@link io.camunda.zeebe.backup.api.BackupStore}. */
public class PartitionRestoreService {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionRestoreService.class);
  final BackupStore backupStore;
  final int partitionId;

  // All members of the cluster. A backup could have been taken by any broker. So we have to iterate
  // over all of them to find a valid backup for this partition with the given id.
  final Set<Integer> brokerIds;
  final Path rootDirectory;
  private final RaftPartition partition;
  private final int localBrokerId;

  public PartitionRestoreService(
      final BackupStore backupStore,
      final RaftPartition partition,
      final Set<Integer> brokerIds,
      final int localNodeId) {
    this.backupStore = backupStore;
    partitionId = partition.id().id();
    rootDirectory = partition.dataDirectory().toPath();
    this.partition = partition;
    this.brokerIds = brokerIds;
    localBrokerId = localNodeId;
  }

  /**
   * Downloads backup from the backup file, restore it to the partition's data directory. After
   * restoring, it truncates the journal to the checkpointPosition so that the last record in the
   * journal will be the checkpoint record at checkpointPosition.
   *
   * @param backupId id of the backup to restore from
   * @return the descriptor of the backup it restored
   */
  public CompletableFuture<BackupDescriptor> restore(final long backupId) {
    return getTargetDirectory(backupId)
        .thenCompose(targetDirectory -> download(backupId, targetDirectory))
        .thenApply(this::moveFilesToDataDirectory)
        .thenApply(
            backup -> {
              resetLogToCheckpointPosition(backup.descriptor().checkpointPosition(), rootDirectory);
              return backup.descriptor();
            })
        .toCompletableFuture();

    // TODO: As an additional consistency check:
    // - Validate journal.firstIndex <= snapshotIndex + 1
    // - Verify journal.lastEntry.asqn == checkpointPosition
  }

  private CompletionStage<Path> getTargetDirectory(final long backupId) {
    try {
      if (!FileUtil.isEmpty(rootDirectory)) {
        LOG.error(
            "Partition's data directory {} is not empty. Aborting restore to avoid overwriting data. Please restart with a clean directory.",
            rootDirectory);
        return CompletableFuture.failedFuture(
            new DirectoryNotEmptyException(rootDirectory.toString()));
      }

      // First download the contents to a temporary directory and then move it to the correct
      // locations.
      final var tempTargetDirectory = rootDirectory.resolve("restoring-" + backupId);
      FileUtil.ensureDirectoryExists(tempTargetDirectory);
      return CompletableFuture.completedFuture(tempTargetDirectory);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  // While taking the backup, we add all log segments. But the backup must only have entries upto
  // the checkpoint position. So after restoring, we truncate the journal until the
  // checkpointPosition.
  private void resetLogToCheckpointPosition(
      final long checkpointPosition, final Path dataDirectory) {

    try (final var journal =
        SegmentedJournal.builder()
            .withDirectory(dataDirectory.toFile())
            .withName(partition.name())
            .withMetaStore(
                // A NoopMetastore
                new JournalMetaStore() {
                  @Override
                  public void storeLastFlushedIndex(final long index) {
                    // noop
                  }

                  @Override
                  public long loadLastFlushedIndex() {
                    return 0;
                  }
                })
            .build()) {

      resetJournal(checkpointPosition, journal);
    }
  }

  private void resetJournal(final long checkpointPosition, final SegmentedJournal journal) {
    try (final var reader = journal.openReader()) {
      reader.seekToAsqn(checkpointPosition);
      if (reader.hasNext()) {
        final var checkpointRecord = reader.next();
        // Here the assumption is the checkpointRecord is the only entry in the journal record. So
        // the checkpointPosition will be the asqn of the record.
        if (checkpointRecord.asqn() != checkpointPosition) {
          failedToFindCheckpointRecord(checkpointPosition, reader);
        }
        journal.deleteAfter(checkpointRecord.index());
      } else {
        failedToFindCheckpointRecord(checkpointPosition, reader);
      }
    }
  }

  private static void failedToFindCheckpointRecord(
      final long checkpointPosition, final JournalReader reader) {
    reader.seekToFirst();
    final var firstEntry = reader.hasNext() ? reader.next() : null;
    reader.seekToLast();
    final var lastEntry = reader.hasNext() ? reader.next() : null;
    LOG.error(
        "Cannot find the checkpoint record at position {}. Log contains first record: (index = {}, position= {}) last record: (index = {}, position= {}). Restoring from this state can lead to inconsistent state. Aborting restore.",
        checkpointPosition,
        firstEntry != null ? firstEntry.index() : -1,
        firstEntry != null ? firstEntry.asqn() : -1,
        lastEntry != null ? lastEntry.index() : -1,
        lastEntry != null ? lastEntry.asqn() : -1);
    throw new IllegalStateException(
        "Failed to restore from backup. Cannot find a record at checkpoint position %d in the log."
            .formatted(checkpointPosition));
  }

  // Move contents of restored backup from the temp directory to partition's root data directory.
  // After this is done, the contents of the data directory follow the expected directory
  // structure. That is - segments in rootDirectory, snapshot in
  // rootDirectory/snapshots/<snapshotId>/
  private Backup moveFilesToDataDirectory(final Backup backup) {
    moveSegmentFiles(backup);
    moveSnapshotFiles(backup);
    return backup;
  }

  private void moveSegmentFiles(final Backup backup) {
    LOG.info("Moving journal segment files to {}", rootDirectory);
    final var segmentFileSet = backup.segments().namedFiles();
    final var segmentFileNames = segmentFileSet.keySet();
    segmentFileNames.forEach(
        name -> copyNamedFileToDirectory(name, segmentFileSet.get(name), rootDirectory));

    try {
      FileUtil.flushDirectory(rootDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void moveSnapshotFiles(final Backup backup) {
    if (backup.descriptor().snapshotId().isEmpty()) {
      return;
    }

    final var snapshotStore =
        FileBasedSnapshotStoreFactory.createRestorableSnapshotStore(
            partition.dataDirectory().toPath(), partition.id().id(), localBrokerId);

    try {
      snapshotStore.restore(
          backup.descriptor().snapshotId().orElseThrow(), backup.snapshot().namedFiles());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void copyNamedFileToDirectory(
      final String name, final Path source, final Path targetDirectory) {
    final var targetFilePath = targetDirectory.resolve(name);
    try {
      Files.move(source, targetFilePath);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private CompletionStage<Backup> download(
      final long checkpointId, final Path tempRestoringDirectory) {
    return findValidBackup(checkpointId)
        .thenCompose(
            backup -> {
              LOG.info("Downloading backup {} to {}", backup, tempRestoringDirectory);
              return backupStore.restore(backup, tempRestoringDirectory);
            });
  }

  private CompletionStage<BackupIdentifier> findValidBackup(final long checkpointId) {
    LOG.info("Searching for a completed backup with id {}", checkpointId);
    final var futures =
        brokerIds.stream()
            .map(brokerId -> new BackupIdentifierImpl(brokerId, partitionId, checkpointId))
            .map(backupStore::getStatus)
            .toList();

    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .thenApply(
            ignore -> {
              final var backupStatuses = futures.stream().map(CompletableFuture::join).toList();
              return findCompletedBackup(backupStatuses)
                  .orElseThrow(
                      () -> {
                        LOG.error(
                            "Could not find a valid backup with id {}. Found {}",
                            checkpointId,
                            backupStatuses);
                        return new BackupNotFoundException(checkpointId);
                      });
            });
  }

  private Optional<BackupIdentifier> findCompletedBackup(final List<BackupStatus> backupStatuses) {
    return backupStatuses.stream()
        .filter(s -> s.statusCode() == BackupStatusCode.COMPLETED)
        .findFirst()
        .map(BackupStatus::id);
  }
}
