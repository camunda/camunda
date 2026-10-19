/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.backup.management.BackupService;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.PartitionRestoreService;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.restore.TestRestorableBackupStore;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.BridgedFromArchive;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The bridged restore with real stores (spec section 18.5a): old continuous backups of a source
 * journal in a {@link TestRestorableBackupStore}, the same journal archived from index 15 on in a
 * log archive, and a restore that joins both into one journal.
 *
 * <p>The history: entries 1 to 30 with asqn = index, except the non-application entry at index 17;
 * old backups 1 at position 10 and 2 (the handover) at position 20; the timeline starts at index 15
 * and covers checkpoint 30 at position 28, with an archived snapshot at index 22.
 */
@Timeout(60)
final class BridgedRestoreTest {

  private static final String TIMELINE = "tl-0";
  private static final int BROKER_ID = 0;
  private static final int PARTITION_ID = 1;
  private static final long SWITCH_INDEX = 15;
  private static final long CHECKPOINT_ID = 30;
  private static ActorScheduler actorScheduler;

  @TempDir Path temp;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final InMemoryLogArchiveStore archiveStore = new InMemoryLogArchiveStore();
  private final TestRestorableBackupStore backupStore = new TestRestorableBackupStore();
  private final List<AutoCloseable> resources = new ArrayList<>();
  private final LogArchiveRestore restore =
      new LogArchiveRestore(archiveStore, path -> SnapshotFilesInfo.none(), meterRegistry);
  private RaftPartition raftPartition;
  private TestLogArchive archive;

  @BeforeAll
  static void startScheduler() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
  }

  @AfterAll
  static void stopScheduler() throws ExecutionException, InterruptedException {
    actorScheduler.stop().get();
  }

  @BeforeEach
  void setUp() throws IOException {
    raftPartition =
        new RaftPartition(
            new PartitionMetadata(
                new PartitionId("raft", PARTITION_ID), Set.of(), Map.of(), 1, new MemberId("0")),
            null,
            temp.resolve("restored").toFile(),
            meterRegistry);

    archive =
        new TestLogArchive(
            archiveStore,
            TIMELINE,
            PARTITION_ID,
            Files.createDirectories(temp.resolve("source")),
            meterRegistry);
    resources.add(archive);
    for (long index = 1; index <= 30; index++) {
      archive.append(index == 17 ? -1 : index);
    }
  }

  @AfterEach
  void closeResources() throws Exception {
    for (final var resource : resources.reversed()) {
      resource.close();
    }
  }

  @Test
  void shouldJoinOldBackupsAndArchiveIntoOneJournal() throws Exception {
    // given
    final var frames = sourceFrames();
    takeOldBackups(frames, -1);
    final var snapshot = archiveFromSwitchIndex();
    final var bridged = bridgedPlan(List.of(1L, 2L), 5);

    // when
    restoreOldLog(bridged);
    final var result = restore.restoreBridgedPartition(bridged, target());

    // then - every entry from the first old one up to the checkpoint, byte-identical
    final var expected = frames.stream().filter(frame -> frame.index() <= 28).toList();
    assertThat(readJournal()).containsExactlyElementsOf(expected);
    assertThat(result.firstIndex()).isOne();
    assertThat(result.lastIndex()).isEqualTo(28);
    assertThat(result.checkpointPosition()).isEqualTo(28);
    assertThat(result.snapshotId()).hasValue(snapshot.getId());
    assertThat(result.timelineId()).isEqualTo("tl-0.r30");
    final var snapshots = dataDirectory().resolve("snapshots");
    try (final var installed = Files.list(snapshots)) {
      assertThat(installed.map(path -> path.getFileName().toString()))
          .describedAs("only the archived snapshot is installed, not the old backup's")
          .containsExactlyInAnyOrder(snapshot.getId(), snapshot.getId() + ".checksum");
    }
    assertThat(dataDirectory().resolve(LogArchiveRestore.TIMELINE_FILE_NAME))
        .hasContent("tl-0.r30");
  }

  @Test
  void shouldFailWhenTheOldBackupsAndTheArchiveDifferAtTheSeam() throws Exception {
    // given - the old backups hold another entry at index 16 than the archive
    takeOldBackups(sourceFrames(), 16);
    archiveFromSwitchIndex();
    final var bridged = bridgedPlan(List.of(1L, 2L), 5);
    restoreOldLog(bridged);

    // when - then
    assertThatThrownBy(() -> restore.restoreBridgedPartition(bridged, target()))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("the old backups and the log archive differ at index 16");
  }

  @Test
  void shouldFailWhenTheOldBackupsDoNotReachTheSwitchIndex() throws Exception {
    // given - only old backup 1, up to position 10
    takeOldBackups(sourceFrames(), -1);
    archiveFromSwitchIndex();
    final var bridged = bridgedPlan(List.of(1L), 5);
    restoreOldLog(bridged);

    // when - then
    assertThatThrownBy(() -> restore.restoreBridgedPartition(bridged, target()))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("holds indexes 1 to 10")
        .hasMessageContaining("reach the switch index 15");
  }

  private List<LogArchiveFrame> sourceFrames() {
    return archive.sourceFrames();
  }

  /**
   * Writes the old journal from the source frames, one entry differing if {@code differingIndex} is
   * positive, and takes old backups 1 at position 10 and 2 at position 20 of it.
   */
  private void takeOldBackups(final List<LogArchiveFrame> frames, final long differingIndex)
      throws IOException {
    final var oldDirectory = Files.createDirectories(temp.resolve("old"));
    final var journal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(oldDirectory.toFile())
            .withName(raftPartition.name())
            .withMetaStore(new InMemory())
            .build();
    resources.add(journal);
    for (final var frame : frames) {
      if (frame.index() == differingIndex) {
        journal.append(
            frame.asqn(),
            new DirectBufferWriter(
                new UnsafeBuffer("another entry".getBytes(StandardCharsets.UTF_8))));
      } else {
        journal.append(frame.checksum(), frame.serializedRecord());
      }
    }

    final var snapshotStore =
        new FileBasedSnapshotStore(
            BROKER_ID, PARTITION_ID, oldDirectory, path -> SnapshotFilesInfo.none(), meterRegistry);
    actorScheduler.submitActor(snapshotStore, SchedulingHints.IO_BOUND).join();
    resources.add(snapshotStore::close);
    takeSnapshot(snapshotStore, 2);

    final var backupService =
        new BackupService(
            BrokerMemberId.from(BROKER_ID),
            new PartitionId("raft", PARTITION_ID),
            backupStore,
            snapshotStore,
            oldDirectory,
            index -> CompletableFuture.completedFuture(journal.getTailSegments(index)),
            meterRegistry,
            (context, entries, source) -> Either.left(WriteFailure.CLOSED),
            null,
            null);
    actorScheduler.submitActor(backupService).join();
    resources.add(backupService::close);
    takeBackup(backupService, 1, 10, CheckpointType.SCHEDULED_BACKUP);
    takeBackup(backupService, 2, 20, CheckpointType.HANDOVER);
  }

  /** Archives the source journal from the switch index on, with a snapshot and the handover. */
  private PersistedSnapshot archiveFromSwitchIndex() throws IOException {
    archive.startTimeline(SWITCH_INDEX, "");
    archive.checkpoint(CHECKPOINT_ID, 28);
    archive.archive(22);
    archive.archive(30);
    final var snapshotStore =
        new FileBasedSnapshotStore(
            BROKER_ID,
            PARTITION_ID,
            Files.createDirectories(temp.resolve("archived-snapshot")),
            path -> SnapshotFilesInfo.none(),
            meterRegistry);
    actorScheduler.submitActor(snapshotStore, SchedulingHints.IO_BOUND).join();
    resources.add(snapshotStore::close);
    final var snapshot = takeSnapshot(snapshotStore, 22);
    archive.uploadSnapshot(snapshot);
    archive.handover(2, 20);
    return snapshot;
  }

  private BridgedFromArchive bridgedPlan(final List<Long> oldBackupIds, final long exported) {
    final var view = LogArchiveView.read(archiveStore, TIMELINE, PARTITION_ID);
    return new BridgedFromArchive(
        oldBackupIds,
        view,
        view.findCheckpoint(CHECKPOINT_ID).orElseThrow(),
        exported,
        SWITCH_INDEX);
  }

  private void restoreOldLog(final BridgedFromArchive bridged) throws Exception {
    new PartitionRestoreService(
            backupStore, raftPartition, BROKER_ID, path -> SnapshotFilesInfo.none(), meterRegistry)
        .restoreLog(
            bridged.oldBackupIds().stream().mapToLong(Long::longValue).toArray(),
            BackupValidator.none());
  }

  private void takeBackup(
      final BackupService backupService,
      final long backupId,
      final long checkpointPosition,
      final CheckpointType type) {
    final var backup =
        backupStore.waitForBackup(new BackupIdentifierImpl(BROKER_ID, PARTITION_ID, backupId));
    backupService.takeBackup(
        backupId, new BackupDescriptorImpl(checkpointPosition, 1, "test", Instant.now(), type));
    backup.orTimeout(30, TimeUnit.SECONDS).join();
  }

  private static PersistedSnapshot takeSnapshot(
      final FileBasedSnapshotStore snapshotStore, final long index) {
    final var transientSnapshot =
        snapshotStore.newTransientSnapshot(index, 1, index, index, false).get();
    transientSnapshot
        .take(
            path -> {
              try {
                Files.createDirectories(path);
                Files.writeString(path.resolve("data"), "state at index " + index);
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .join();
    return transientSnapshot
        .withLastFollowupEventPosition(index)
        .withMaxExportedPosition(index)
        .persist()
        .join();
  }

  private Path dataDirectory() {
    return raftPartition.dataDirectory().toPath();
  }

  private LogArchiveRestoreTarget target() {
    return LogArchiveRestoreTarget.of(raftPartition, BROKER_ID);
  }

  private List<LogArchiveFrame> readJournal() {
    final var frames = new ArrayList<LogArchiveFrame>();
    try (final var journal =
            SegmentedJournal.builder(meterRegistry)
                .withDirectory(dataDirectory().toFile())
                .withName(raftPartition.name())
                .withMetaStore(new InMemory())
                .build();
        final var reader = journal.openReader()) {
      while (reader.hasNext()) {
        final var record = reader.next();
        final var serialized = new byte[record.serializedRecord().capacity()];
        record.serializedRecord().getBytes(0, serialized);
        final long position = record.asqn() >= 0 ? record.asqn() : -1;
        frames.add(
            new LogArchiveFrame(
                record.index(),
                TestLogArchive.TERM,
                record.asqn(),
                position,
                position,
                record.checksum(),
                serialized));
      }
    }
    return frames;
  }
}
