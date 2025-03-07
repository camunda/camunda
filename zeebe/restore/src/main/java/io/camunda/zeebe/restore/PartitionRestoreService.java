/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import io.camunda.zeebe.snapshots.RestorableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Restores a backup from the given {@link io.camunda.zeebe.backup.api.BackupStore}. */
public class PartitionRestoreService {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionRestoreService.class);
  final BackupStore backupStore;
  final int partitionId;

  final Path rootDirectory;
  private final RaftPartition partition;
  private final int brokerId;
  private final CRC32CChecksumProvider checksumProvider;
  private final MeterRegistry meterRegistry;

  public PartitionRestoreService(
      final BackupStore backupStore,
      final RaftPartition partition,
      final int brokerId,
      final CRC32CChecksumProvider checksumProvider,
      final MeterRegistry meterRegistry) {
    this.backupStore = backupStore;
    partitionId = partition.id().id();
    rootDirectory = partition.dataDirectory().toPath();
    this.partition = partition;
    this.brokerId = brokerId;
    this.checksumProvider = Objects.requireNonNull(checksumProvider);
    this.meterRegistry = meterRegistry;
  }

  /**
   * Downloads backup from the backup file, restore it to the partition's data directory. After
   * restoring, it truncates the journal to the checkpointPosition so that the last record in the
   * journal will be the checkpoint record at checkpointPosition.
   *
   * @param backupId id of the backup to restore from
   * @return the descriptor of the backup it restored
   */
  public CompletableFuture<BackupDescriptor> restore(
      final long backupId, final BackupValidator validator, final long exporterPosition) {
    final var allBackupStatuses =
        backupStore
            .list(
                new BackupIdentifierWildcardImpl(
                    Optional.empty(), Optional.of(partitionId), Optional.empty()))
            .join();
    final AtomicReference<Backup> lastBackupStatus = new AtomicReference<>();
    final List<Backup> allBackups = new ArrayList<>();
    for (final var backupStatus : allBackupStatuses) {
      if (backupStatus.statusCode() != BackupStatusCode.COMPLETED) {
        LOG.info("Backup {} is not completed. Skipping restore.", backupStatus);
        continue;
      }
      getTargetDirectory(backupStatus.id().checkpointId())
          .thenCompose(
              targetDirectory ->
                  download(backupStatus.id().checkpointId(), targetDirectory, validator))
          .thenApply(this::moveFilesToDataDirectory)
          .thenApply(
              backup -> {
                // No need to reset, this is done before uploading the backup.
                //                resetLogToCheckpointPosition(
                //                    backup.descriptor().checkpointPosition(), rootDirectory);
                LOG.trace("Restored backup position {}", backup.descriptor().checkpointPosition());
                lastBackupStatus.set(backup);
                allBackups.add(backup);
                return backup.descriptor();
              })
          .toCompletableFuture()
          .join();
    }

    restoreSnapshot(allBackups, exporterPosition);

    return CompletableFuture.completedFuture(lastBackupStatus.get().descriptor());

    // TODO: As an additional consistency check:
    // - Validate journal.firstIndex <= snapshotIndex + 1
    // - Verify journal.lastEntry.asqn == checkpointPosition
  }

  private void restoreSnapshot(final List<Backup> backups, final long exporterPosition) {
    // Find snapshot before the last exported position
    final var backupWithMatchingSnapshot =
        backups.stream()
            .filter(backup -> backup.descriptor().snapshotId().isPresent())
            .filter(backup -> exporterPositionOfBackup(backup) < exporterPosition)
            .max(Comparator.comparingLong(PartitionRestoreService::exporterPositionOfBackup));

    LOG.info(
        "Picked {} as snapshot to match exporter position {}, backups: {}",
        backupWithMatchingSnapshot,
        exporterPosition,
        backups);
    // Restore snapshot
    backupWithMatchingSnapshot.ifPresent(this::moveSnapshotFiles);
  }

  private static long exporterPositionOfBackup(final Backup backup) {
    return FileBasedSnapshotId.ofFileName(backup.descriptor().snapshotId().orElseThrow())
        .orElseThrow()
        .getExportedPosition();
  }

  private CompletionStage<Path> getTargetDirectory(final long backupId) {
    try {
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
        SegmentedJournal.builder(partition.getMeterRegistry())
            .withDirectory(dataDirectory.toFile())
            .withName(partition.name())
            .withMetaStore(new InMemory())
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
    return backup;
  }

  private void moveSegmentFiles(final Backup backup) {
    LOG.info("Moving journal segment files to {}", rootDirectory);
    final var segmentFileSet = backup.segments().namedFiles();
    final var segmentFileNames = segmentFileSet.keySet();
    segmentFileNames.forEach(
        name ->
            copyNamedFileToDirectory(backup.id(), name, segmentFileSet.get(name), rootDirectory));

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

    @SuppressWarnings("resource")
    final RestorableSnapshotStore snapshotStore =
        new FileBasedSnapshotStore(
            brokerId,
            partition.id().id(),
            partition.dataDirectory().toPath(),
            checksumProvider,
            meterRegistry);

    try {
      snapshotStore.restore(
          backup.descriptor().snapshotId().orElseThrow(), backup.snapshot().namedFiles());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void copyNamedFileToDirectory(
      final BackupIdentifier id, final String name, final Path source, final Path targetDirectory) {
    final var targetFilePath =
        targetDirectory.resolve(
            name.substring(0, name.length() - 4) + id.nodeId() + id.checkpointId() + ".log");
    try {
      Files.move(source, targetFilePath);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private CompletionStage<Backup> download(
      final long checkpointId, final Path tempRestoringDirectory, final BackupValidator validator) {
    return findValidBackup(checkpointId, validator)
        .thenCompose(
            backup -> {
              LOG.info("Downloading backup {} to {}", backup, tempRestoringDirectory);
              return backupStore.restore(backup, tempRestoringDirectory);
            });
  }

  private CompletionStage<BackupIdentifier> findValidBackup(
      final long checkpointId, final BackupValidator validator) {
    LOG.info("Searching for a completed backup with id {}", checkpointId);
    return backupStore
        .list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.of(partitionId), Optional.of(checkpointId)))
        .thenApply(
            statuses ->
                statuses.stream()
                    .filter(status -> status.statusCode() == BackupStatusCode.COMPLETED)
                    .findAny()
                    .orElseThrow(() -> new BackupNotFoundException(checkpointId)))
        .thenApply(validator::validateStatus)
        .thenApply(BackupStatus::id);
  }

  @FunctionalInterface
  public interface BackupValidator {
    BackupStatus validateStatus(BackupStatus status) throws BackupNotValidException;

    static BackupValidator none() {
      return status -> status;
    }

    class BackupNotValidException extends RuntimeException {
      public BackupNotValidException(final BackupStatus status, final String message) {
        super("Backup is not valid: " + message + ". Backup status: " + status);
      }
    }
  }
}
