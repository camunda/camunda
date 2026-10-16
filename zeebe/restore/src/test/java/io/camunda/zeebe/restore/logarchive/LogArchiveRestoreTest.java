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

import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
final class LogArchiveRestoreTest {

  private static final String TIMELINE = "tl-0";
  private static final int BROKER_ID = 0;
  private static ActorScheduler actorScheduler;

  @TempDir Path temp;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final InMemoryLogArchiveStore store = new InMemoryLogArchiveStore();
  private final List<AutoCloseable> resources = new ArrayList<>();
  private final LogArchiveRestore restore =
      new LogArchiveRestore(store, path -> SnapshotFilesInfo.none(), meterRegistry);

  @BeforeAll
  static void startScheduler() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
  }

  @AfterAll
  static void stopScheduler() throws ExecutionException, InterruptedException {
    actorScheduler.stop().get();
  }

  @AfterEach
  void closeResources() throws Exception {
    for (final var resource : resources.reversed()) {
      resource.close();
    }
  }

  @Test
  void shouldRebuildJournalByteIdenticalUpToCheckpoint() throws IOException {
    // given
    final var archive = archive(1);
    archive.appendAll(1, 2);
    archive.append(-1);
    archive.appendAll(5, 6);
    archive.append(-1);
    archive.appendAll(9, 12); // index 8 holds the checkpoint at position 12
    archive.append(-1);
    archive.appendAll(13, 14);
    archive.checkpoint(1_000L, 12);
    archive.archive(3);
    archive.archive(6);
    archive.archive(9);
    archive.archive(11);
    final var snapshot = archive.uploadSnapshot(takeSnapshot("p1", 2, 2, 2, 2));
    final var target = target(1);

    // when
    final var result =
        restore.restorePartition(LogArchiveView.read(store, TIMELINE, 1), 1_000L, target);

    // then
    final var expected =
        archive.sourceFrames().stream().filter(frame -> frame.index() <= 8).toList();
    final var restored = readJournal(target);
    assertThat(restored).hasSize(8).containsExactlyElementsOf(expected);
    assertThat(restored.getLast().asqn()).isEqualTo(12);
    assertThat(result.firstIndex()).isEqualTo(1);
    assertThat(result.lastIndex()).isEqualTo(8);
    assertThat(result.checkpointPosition()).isEqualTo(12);
    assertThat(result.snapshotId()).hasValue(snapshot.id());
    assertThat(target.dataDirectory().resolve("snapshots").resolve(snapshot.id()))
        .isDirectoryContaining("glob:**/data")
        .isDirectoryContaining("glob:**/zeebe.metadata");
    assertThat(target.dataDirectory().resolve("snapshots").resolve(snapshot.id() + ".checksum"))
        .isRegularFile();
    assertThat(target.dataDirectory())
        .isDirectoryNotContaining("glob:**/restoring-log-archive-snapshot-*");
  }

  @Test
  void shouldStartJournalAtObjectContainingSnapshotIndex() throws IOException {
    // given
    final var archive = archive(1);
    archive.appendAll(1, 2, 3, 4, 5, 6, 7, 8);
    archive.checkpoint(1_000L, 7);
    archive.archive(2);
    archive.archive(4);
    archive.archive(6);
    archive.seal(2);
    archive.archive(8);
    archive.uploadSnapshot(takeSnapshot("p1", 4, 4, 4, 4));
    archive.publishRoot();
    final var target = target(1);

    // when
    restore.restorePartition(LogArchiveView.read(store, TIMELINE, 1), 1_000L, target);

    // then
    final var expected =
        archive.sourceFrames().stream()
            .filter(frame -> frame.index() >= 3 && frame.index() <= 7)
            .toList();
    assertThat(readJournal(target)).containsExactlyElementsOf(expected);
  }

  @Test
  void shouldRestoreFromIndexOneWithoutSnapshot() throws IOException {
    // given
    final var archive = archive(1);
    archive.appendAll(1, 2, 3);
    archive.checkpoint(1_000L, 2);
    archive.archive(3);
    final var target = target(1);

    // when
    final var result =
        restore.restorePartition(LogArchiveView.read(store, TIMELINE, 1), 1_000L, target);

    // then
    assertThat(result.snapshotId()).isEmpty();
    assertThat(readJournal(target)).containsExactlyElementsOf(archive.sourceFrames().subList(0, 2));
  }

  @Test
  void shouldFailWhenNoEntryHasCheckpointPositionAsAsqn() throws IOException {
    // given
    final var archive = archive(1);
    archive.appendAll(1, 5, 8);
    archive.checkpoint(1_000L, 6); // inside the batch that starts at position 5
    archive.archive(3);
    final var target = target(1);
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // when - then
    assertThatThrownBy(() -> restore.restorePartition(view, 1_000L, target))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("found no entry with asqn equal to the checkpoint position 6");
  }

  @Test
  void shouldFailWhenObjectDoesNotMatchItsChecksum() throws IOException {
    // given
    final var archive = archive(1);
    archive.appendAll(1, 2);
    archive.checkpoint(1_000L, 2);
    final var object = archive.archive(2);
    final var key = archive.prefix() + object.key();
    final var corrupted = store.get(key);
    corrupted[corrupted.length / 2] ^= 1;
    store.replace(key, corrupted);
    final var target = target(1);
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // when - then
    assertThatThrownBy(() -> restore.restorePartition(view, 1_000L, target))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("does not match its size");
  }

  @Test
  void shouldFailWhenDataDirectoryIsNotEmpty() throws IOException {
    // given
    final var archive = archive(1);
    archive.appendAll(1, 2);
    archive.checkpoint(1_000L, 2);
    archive.archive(2);
    final var target = target(1);
    Files.createDirectories(target.dataDirectory());
    Files.writeString(target.dataDirectory().resolve("leftover"), "x");
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // when - then
    assertThatThrownBy(() -> restore.restorePartition(view, 1_000L, target))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("to be empty");
  }

  @Test
  void shouldWriteDeterministicTimelineFile() throws IOException {
    // given
    final var archive = archive(1);
    archive.appendAll(1, 2);
    archive.checkpoint(1_792_137_600_000L, 2);
    archive.archive(2);
    final var target = target(1);

    // when
    final var result =
        restore.restorePartition(
            LogArchiveView.read(store, TIMELINE, 1), 1_792_137_600_000L, target);

    // then
    assertThat(result.timelineId()).isEqualTo("tl-0.r1792137600000");
    assertThat(
            Files.readString(
                target.dataDirectory().resolve(LogArchiveRestore.TIMELINE_FILE_NAME),
                StandardCharsets.UTF_8))
        .isEqualTo("tl-0.r1792137600000");
  }

  @Test
  void shouldRestoreLocalPartitionsToLatestCheckpointOfAllPartitions() throws IOException {
    // given
    final var partition1 = archive(1);
    partition1.appendAll(1, 2, 3, 4);
    partition1.checkpoint(10, 2);
    partition1.checkpoint(20, 4);
    partition1.archive(4);

    final var partition2 = archive(2);
    partition2.appendAll(5, 6, 7);
    partition2.checkpoint(10, 6);
    partition2.checkpoint(20, 8); // not archived on partition 2 yet
    partition2.archive(3);

    final var partition3 = archive(3);
    partition3.appendAll(2, 3);
    partition3.checkpoint(10, 3);
    partition3.checkpoint(20, 4);
    partition3.archive(2);

    final var target1 = target(1);
    final var target2 = target(2);

    // when - this broker hosts partitions 1 and 2 of 3
    final var result =
        restore.restorePartitions(TIMELINE, 3, Optional.empty(), List.of(target1, target2));

    // then
    assertThat(result.restorePoint().checkpointId()).isEqualTo(10);
    assertThat(result.timelineId()).isEqualTo("tl-0.r10");
    assertThat(result.partitions()).containsOnlyKeys(1, 2);
    assertThat(readJournal(target1))
        .containsExactlyElementsOf(partition1.sourceFrames().subList(0, 2));
    assertThat(readJournal(target2))
        .containsExactlyElementsOf(partition2.sourceFrames().subList(0, 2));
    assertThat(target1.dataDirectory().resolve(LogArchiveRestore.TIMELINE_FILE_NAME))
        .hasContent("tl-0.r10");
    assertThat(target2.dataDirectory().resolve(LogArchiveRestore.TIMELINE_FILE_NAME))
        .hasContent("tl-0.r10");
  }

  private TestLogArchive archive(final int partitionId) throws IOException {
    final var archive =
        new TestLogArchive(
            store,
            TIMELINE,
            partitionId,
            Files.createDirectories(temp.resolve("source-" + partitionId)),
            meterRegistry);
    resources.add(archive);
    archive.startTimeline(1, "");
    return archive;
  }

  private LogArchiveRestoreTarget target(final int partitionId) {
    return new LogArchiveRestoreTarget(
        partitionId,
        temp.resolve("restored-" + partitionId),
        TestLogArchive.JOURNAL_NAME,
        BROKER_ID);
  }

  /** Takes a real snapshot with a data file, the way the state controller would. */
  private PersistedSnapshot takeSnapshot(
      final String name,
      final long index,
      final long processedPosition,
      final long lastFollowupEventPosition,
      final long maxExportedPosition)
      throws IOException {
    final var snapshotStore =
        new FileBasedSnapshotStore(
            BROKER_ID,
            1,
            Files.createDirectories(temp.resolve("snapshot-source-" + name)),
            path -> SnapshotFilesInfo.none(),
            meterRegistry);
    actorScheduler.submitActor(snapshotStore, SchedulingHints.IO_BOUND).join();
    resources.add(snapshotStore::close);

    final var transientSnapshot =
        snapshotStore
            .newTransientSnapshot(index, 1, processedPosition, processedPosition, false)
            .get();
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
        .withLastFollowupEventPosition(lastFollowupEventPosition)
        .withMaxExportedPosition(maxExportedPosition)
        .persist()
        .join();
  }

  private List<LogArchiveFrame> readJournal(final LogArchiveRestoreTarget target) {
    final var frames = new ArrayList<LogArchiveFrame>();
    try (final var journal =
            SegmentedJournal.builder(meterRegistry)
                .withDirectory(target.dataDirectory().toFile())
                .withName(target.journalName())
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
