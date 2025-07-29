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
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import io.camunda.zeebe.snapshots.RestorableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
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
   * Downloads a single backup and restores it to the partition's data directory. The backup is
   * truncated to the checkpoint position.
   */
  public void restore(final long backupId, final BackupValidator validator)
      throws IOException, FlushException {
    restore(new long[] {backupId}, validator);
  }

  /**
   * Downloads backups, restores them to the partition's data directory. Backups are truncated to
   * checkpoint positions.
   *
   * @param backupIds ids of the backups to restore from
   */
  public void restore(final long[] backupIds, final BackupValidator validator)
      throws IOException, FlushException {
    if (!FileUtil.isEmpty(rootDirectory)) {
      LOG.error(
          "Partition's data directory {} is not empty. Aborting restore to avoid overwriting data. Please restart with a clean directory.",
          rootDirectory);
      throw new DirectoryNotEmptyException(rootDirectory.toString());
    }
    validateAndSortBackupIds(backupIds);

    try (final var restoredJournal =
        SegmentedJournal.builder(partition.getMeterRegistry())
            .withDirectory(rootDirectory.toFile())
            .withName(partition.name())
            .withMetaStore(new InMemory())
            .build()) {
      Backup previousBackup = null;
      for (final var backupId : backupIds) {
        final var restoreTarget =
            rootDirectory.resolve("restoring-partition" + partitionId + "-backup-" + backupId);
        FileUtil.ensureDirectoryExists(restoreTarget);
        final var backup = download(backupId, restoreTarget, validator);
        if (previousBackup == null) {
          // Only take the first snapshot, all others are redundant because we have the full log.
          moveSnapshotFiles(backup);
        }
        copyBetweenCheckpoints(previousBackup, backup, restoreTarget, restoredJournal);
        previousBackup = backup;
        FileUtil.deleteFolder(restoreTarget);
      }
      restoredJournal.flush();
    }

    // TODO: As an additional consistency check:
    // - Validate journal.firstIndex <= snapshotIndex + 1
    // - Verify journal.lastEntry.asqn == checkpointPosition
  }

  /** Ensures that we have a valid array of backup ids. */
  private static void validateAndSortBackupIds(final long[] backupIds) {
    if (backupIds.length == 0) {
      throw new IllegalArgumentException("No backups to restore");
    }

    for (final long backupId : backupIds) {
      if (backupId < 0) {
        throw new IllegalArgumentException("Backup id must not be negative but was " + backupId);
      }
    }
    Arrays.sort(backupIds);
  }

  /**
   * This copies records from the journal in the source directory to the target journal. Records
   * outside of the checkpoint range <code>(previousBackup.checkpointPosition,
   * sourceBackup.checkpointPosition]</code> are skipped.
   *
   * <p>If this is the first backup we are restoring, the target journal will be reset to match the
   * index of the source journal.
   */
  private void copyBetweenCheckpoints(
      final Backup previousBackup,
      final Backup sourceBackup,
      final Path sourceDirectory,
      final Journal targetJournal) {
    try (final var sourceJournal =
            SegmentedJournal.builder(partition.getMeterRegistry())
                .withDirectory(sourceDirectory.toFile())
                .withName(partition.name())
                .withMetaStore(new InMemory())
                .build();
        final var sourceReader = sourceJournal.openReader()) {
      if (previousBackup != null) {
        skipOverCheckpoint(sourceReader, previousBackup.descriptor().checkpointPosition());
      }

      if (previousBackup == null) {
        LOG.debug(
            "Resetting target journal to index {} to match source journal",
            sourceReader.getNextIndex());
        targetJournal.reset(sourceReader.getNextIndex());
      }

      copyUntilCheckpoint(
          targetJournal, sourceReader, sourceBackup.descriptor().checkpointPosition());
    }
  }

  private static void skipOverCheckpoint(
      final JournalReader reader, final long checkpointPosition) {
    reader.seekToAsqn(checkpointPosition);
    if (!reader.hasNext()) {
      failedToFindCheckpointRecord(checkpointPosition, reader);
    }
    final var record = reader.next();
    if (record.asqn() != checkpointPosition) {
      failedToFindCheckpointRecord(checkpointPosition, reader);
    }
    LOG.debug("Skipped over checkpoint record {} from source journal", record);
  }

  private static void copyUntilCheckpoint(
      final Journal targetJournal,
      final JournalReader sourceReader,
      final long checkpointPosition) {
    final var recordWriter = new DirectBufferWriter();
    while (sourceReader.hasNext()) {
      final var record = sourceReader.next();
      targetJournal.append(record.asqn(), recordWriter.wrap(record.data()));
      if (record.asqn() == checkpointPosition) {
        LOG.debug("Copied up to checkpoint record {} from source journal", record);
        return;
      }
    }
    failedToFindCheckpointRecord(checkpointPosition, sourceReader);
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

  private Backup download(
      final long checkpointId, final Path tempRestoringDirectory, final BackupValidator validator) {
    final var validBackup = findValidBackup(checkpointId, validator);
    return backupStore.restore(validBackup, tempRestoringDirectory).join();
  }

  private BackupIdentifier findValidBackup(
      final long checkpointId, final BackupValidator validator) {
    LOG.info("Searching for a completed backup with id {}", checkpointId);
    final var statuses =
        backupStore
            .list(
                new BackupIdentifierWildcardImpl(
                    Optional.empty(), Optional.of(partitionId), Optional.of(checkpointId)))
            .join();
    final var validStatus =
        statuses.stream()
            .filter(status -> status.statusCode() == BackupStatusCode.COMPLETED)
            .findAny()
            .orElseThrow(() -> new BackupNotFoundException(checkpointId));
    validator.validateStatus(validStatus);
    return validStatus.id();
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
