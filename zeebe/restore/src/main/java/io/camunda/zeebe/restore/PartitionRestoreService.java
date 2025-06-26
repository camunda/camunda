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
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import io.camunda.zeebe.snapshots.RestorableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
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
  final Path rootDirectory;
  private final Path routingPath;
  private final RaftPartition partition;
  private final int brokerId;
  private final CRC32CChecksumProvider checksumProvider;
  private final MeterRegistry meterRegistry;

  public PartitionRestoreService(
      final BackupStore backupStore,
      final Path routingPath,
      final RaftPartition partition,
      final int brokerId,
      final CRC32CChecksumProvider checksumProvider,
      final MeterRegistry meterRegistry) {
    this.backupStore = backupStore;
    this.routingPath = routingPath;
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
      final long backupId, final BackupValidator validator) {
    return getTargetDirectory(backupId)
        .thenCompose(targetDirectory -> download(backupId, targetDirectory, validator))
        .thenApply(this::moveFilesToDataDirectory)
        .thenApply(
            backup -> {
              resetLogToCheckpointPosition(backup.descriptor().checkpointPosition(), rootDirectory);
              return backup.descriptor();
            })
        .thenCompose(
            backupDescriptor ->
                // if there is no snapshot, then there's no routing info to restore.
                backupDescriptor.snapshotId().isEmpty()
                    ? CompletableFuture.completedFuture(backupDescriptor)
                    : restoreRoutingInfo(
                            rootDirectory
                                .resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY)
                                .resolve(backupDescriptor.snapshotId().get()))
                        .thenApply(ok -> backupDescriptor))
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
      final String name, final Path source, final Path targetDirectory) {
    final var targetFilePath = targetDirectory.resolve(name);
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

  private CompletableFuture<Void> restoreRoutingInfo(final Path snapshotPath) {
    final var factory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, partition.id().id()),
            () -> meterRegistry);
    return CompletableFuture.runAsync(
        () -> {
          try (final var db = factory.createDb(snapshotPath.toFile())) {
            final var ctx = db.createContext();
            final var state = new DbRoutingState(db, ctx);
            final var routingState = RoutingUtil.routingState(2L, state);
            LOG.debug(
                "Restoring RoutingState for partition {}: {}", partition.id().id(), routingState);
            final var bytes = new ProtoBufSerializer().serializeRoutingState(routingState);
            final var routingInfoFile =
                routingPath.resolve(
                    PersistedClusterConfiguration.PERSISTED_ROUTING_INFO_FILENAME_FORMAT.formatted(
                        partition.id().id()));
            try (final var file =
                FileChannel.open(
                    routingInfoFile,
                    Set.of(
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE))) {
              if (file.write(ByteBuffer.wrap(bytes)) < bytes.length) {
                throw new IOException(
                    "Failed to write completely routing info to file, written %d bytes: %d, expected bytes: %d"
                        .formatted(file.position(), bytes.length));
              }
              file.force(true);
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          }
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
