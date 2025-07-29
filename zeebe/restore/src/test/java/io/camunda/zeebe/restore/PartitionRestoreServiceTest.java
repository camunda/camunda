/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.management.BackupService;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator.BackupNotValidException;
import io.camunda.zeebe.restore.RestoreManager.ValidatePartitionCount;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.CorruptedSnapshotException;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 60)
class PartitionRestoreServiceTest {

  private static ActorScheduler actorScheduler;
  private static final String SNAPSHOT_FILE_NAME = "file1";
  @TempDir Path dataDirectory;
  @TempDir Path dataDirectoryToRestore;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private TestRestorableBackupStore backupStore;
  private SegmentedJournal journal;
  private PartitionRestoreService restoreService;
  private FileBasedSnapshotStore snapshotStore;
  private BackupService backupService;
  private final int nodeId = 1;
  private final int partitionId = 1;

  @BeforeAll
  static void beforeAll() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
  }

  @AfterAll
  static void afterAll() throws ExecutionException, InterruptedException {
    actorScheduler.stop().get();
  }

  @BeforeEach
  void setUp() {
    backupStore = new TestRestorableBackupStore();

    snapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, dataDirectory, snapshotPath -> Map.of(), meterRegistry);
    actorScheduler.submitActor(snapshotStore, SchedulingHints.IO_BOUND);

    final var partitionMetadata =
        new PartitionMetadata(
            PartitionId.from("raft", partitionId), Set.of(), Map.of(), 1, new MemberId("1"));
    final var raftPartition =
        new RaftPartition(partitionMetadata, null, dataDirectoryToRestore.toFile(), meterRegistry);
    restoreService =
        new PartitionRestoreService(
            backupStore, raftPartition, nodeId, snapshotPath -> Map.of(), meterRegistry);

    journal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(dataDirectory.toFile())
            .withName(raftPartition.name())
            .withMetaStore(mock(JournalMetaStore.class))
            .build();

    backupService =
        new BackupService(
            nodeId,
            partitionId,
            1,
            backupStore,
            snapshotStore,
            dataDirectory,
            // RaftPartitions implements this interface, but the RaftServer is not started
            index -> CompletableFuture.completedFuture(journal.getTailSegments(index).values()),
            meterRegistry,
            (context, entries, source) -> Either.left(WriteFailure.CLOSED));
    actorScheduler.submitActor(backupService);
  }

  @AfterEach
  void afterEach() {
    backupService.close();
    snapshotStore.close();
  }

  @Test
  void shouldRestoreToDataDirectory() throws IOException, FlushException {
    // given
    // write something to the journal
    appendRecord(1, "data");
    appendRecord(2, "data");
    appendRecord(3, "data");
    appendRecord(4, "checkpoint");
    appendRecord(5, "data");
    appendRecord(6, "data");

    // take a snapshot
    final var snapshot = takeSnapshot(2, 3);

    // take backup
    final long backupId = 2;
    final var backup = takeBackup(backupId, 4);

    // when
    restoreService.restore(backupId, BackupValidator.none());

    // then
    assertThat(dataDirectoryToRestore).isNotEmptyDirectory();

    final Set<String> restoredSegmentFiles = getRegularFiles(dataDirectoryToRestore);
    assertThat(restoredSegmentFiles)
        .describedAs("All segment files has been restored")
        .containsExactlyInAnyOrderElementsOf(backup.segments().names());

    final Path snapshotsDirectory = dataDirectoryToRestore.resolve("snapshots");
    assertThat(snapshotsDirectory.toFile())
        .describedAs("Snapshot directory is created and snapshot checksum is restored.")
        .exists()
        .isDirectoryContaining("regex:.*/" + backup.descriptor().snapshotId().orElseThrow())
        .isDirectoryContaining("regex:.*/" + snapshot.getId() + ".checksum");

    final Set<String> expectedSnapshotFiles = getRegularFiles(snapshot.getPath());
    final Set<String> restoredSnapshotFiles =
        getRegularFiles(snapshotsDirectory.resolve(snapshot.getId()));
    assertThat(restoredSnapshotFiles)
        .describedAs("All snapshot files has been restored")
        .containsExactlyInAnyOrderElementsOf(expectedSnapshotFiles);
  }

  @Test
  void shouldFailToRestoreWhenCheckpointPositionNotFound() {
    // given
    // journal without record at checkpointPosition
    appendRecord(1, "data");
    appendRecord(2, "data");

    takeSnapshot(1, 2);

    final long backupId = 1;
    takeBackup(backupId, 5);

    // when - then
    assertThatThrownBy(() -> restoreService.restore(backupId, new ValidatePartitionCount(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot find a record at checkpoint position");
  }

  @Test
  void shouldFailToRestoreWhenSnapshotIsCorrupted() throws IOException {
    // given
    appendRecord(1, "data");
    appendRecord(2, "data");
    appendRecord(4, "checkpoint");

    takeSnapshot(1, 2);

    final long backupId = 2;
    final var backup = takeBackup(backupId, 4);

    // corrupt backup snapshot
    Files.write(
        backup.snapshot().namedFiles().get(SNAPSHOT_FILE_NAME),
        "corrupted".getBytes(),
        StandardOpenOption.APPEND);

    // when - then
    assertThatThrownBy(() -> restoreService.restore(backupId, new ValidatePartitionCount(1)))
        .isInstanceOf(CorruptedSnapshotException.class);
  }

  @Test
  void shouldFailToRestoreWhenPartitionCountIsDifferent() {
    // given
    appendRecord(1, "data");
    appendRecord(2, "data");
    appendRecord(3, "data");
    appendRecord(4, "checkpoint");

    takeSnapshot(2, 3);

    final long backupId = 1;
    takeBackup(backupId, 4);

    // when - then
    assertThatThrownBy(() -> restoreService.restore(backupId, new ValidatePartitionCount(2)))
        .isInstanceOf(BackupNotValidException.class)
        .hasMessageContaining("Expected backup to have 2 partitions, but has 1");
  }

  private Set<String> getRegularFiles(final Path directory) throws IOException {
    final Set<String> restoredSegmentFiles;
    try (final var stream = Files.list(directory)) {
      restoredSegmentFiles =
          stream
              .filter(Files::isRegularFile)
              .map(path -> path.getFileName().toString())
              .collect(Collectors.toSet());
    }
    return restoredSegmentFiles;
  }

  private Backup takeBackup(final long backupId, final long checkpointPosition) {
    final var backup =
        backupStore.waitForBackup(new BackupIdentifierImpl(nodeId, partitionId, backupId));
    backupService.takeBackup(backupId, checkpointPosition);
    return backup.orTimeout(30, TimeUnit.SECONDS).join();
  }

  private void appendRecord(final long asqn, final String data) {
    journal.append(asqn, new DirectBufferWriter().wrap(new UnsafeBuffer(data.getBytes())));
  }

  private PersistedSnapshot takeSnapshot(final long index, final long lastWrittenPosition) {
    final var transientSnapshot = snapshotStore.newTransientSnapshot(index, 1, 1, 1, false).get();
    transientSnapshot.take(
        path -> {
          try {
            FileUtil.ensureDirectoryExists(path);

            Files.write(
                path.resolve(SNAPSHOT_FILE_NAME),
                "This is the content".getBytes(),
                CREATE_NEW,
                StandardOpenOption.WRITE);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });

    return transientSnapshot.withLastFollowupEventPosition(lastWrittenPosition).persist().join();
  }
}
