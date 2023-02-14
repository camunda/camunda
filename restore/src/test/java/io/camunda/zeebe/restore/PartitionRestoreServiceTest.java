/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.restore;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.management.BackupService;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.CorruptedSnapshotException;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
        (FileBasedSnapshotStore)
            new FileBasedSnapshotStoreFactory(actorScheduler, nodeId)
                .createReceivableSnapshotStore(dataDirectory, partitionId);

    backupService =
        new BackupService(
            nodeId,
            partitionId,
            1,
            List.of(1, 2),
            backupStore,
            snapshotStore,
            dataDirectory,
            path -> path.toString().endsWith(".log"));
    actorScheduler.submitActor(backupService);

    final var raftPartition =
        new RaftPartition(
            PartitionId.from("raft", partitionId), null, dataDirectoryToRestore.toFile());
    restoreService = new PartitionRestoreService(backupStore, raftPartition, Set.of(1, 2), nodeId);

    journal =
        SegmentedJournal.builder()
            .withDirectory(dataDirectory.toFile())
            .withName(raftPartition.name())
            .withMetaStore(mock(JournalMetaStore.class))
            .build();
  }

  @AfterEach
  void afterEach() {
    backupService.close();
    snapshotStore.close();
  }

  @Test
  void shouldRestoreToDataDirectory() throws IOException {
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
    restoreService.restore(backupId).join();

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
    assertThat(restoreService.restore(backupId))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("Cannot find a record at checkpoint position");
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
    assertThat(restoreService.restore(backupId))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(CorruptedSnapshotException.class);
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
    final var transientSnapshot = snapshotStore.newTransientSnapshot(index, 1, 1, 1).get();
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
