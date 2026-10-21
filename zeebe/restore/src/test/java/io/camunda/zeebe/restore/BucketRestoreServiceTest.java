/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static io.camunda.zeebe.restore.BucketFixture.CLUSTER;
import static io.camunda.zeebe.restore.BucketRestoreService.LATEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.restore.BucketRestoreException.LogEndsBeforeTarget;
import io.camunda.zeebe.restore.BucketRestoreException.MissingEntry;
import io.camunda.zeebe.restore.BucketRestoreException.NoHistory;
import io.camunda.zeebe.restore.BucketRestoreService.RestoredFromBucket;
import io.camunda.zeebe.restore.PartitionRestoreService.RestoredCheckpoint;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
final class BucketRestoreServiceTest {

  private static final UUID FIRST = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID SECOND = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final Instant CREATED = Instant.parse("2026-10-20T12:00:00Z");

  private static ActorScheduler actorScheduler;

  @TempDir private Path bucket;
  @TempDir private Path journalDirectory;
  @TempDir private Path otherJournalDirectory;
  @TempDir private Path snapshotDirectory;
  @TempDir private Path partitionDirectory;
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private FileSystemStore store;
  private BucketFixture fixture;
  private RaftEntries journal;
  private FileBasedSnapshotStore snapshots;
  private RaftPartition partition;
  private BucketRestoreService service;

  @BeforeAll
  static void startScheduler() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
  }

  @AfterAll
  static void stopScheduler() throws Exception {
    actorScheduler.stop().get();
  }

  @BeforeEach
  void setUp() throws IOException {
    store = new FileSystemStore(bucket);
    fixture = new BucketFixture(store);
    journal = new RaftEntries(journalDirectory, 1);
    snapshots =
        new FileBasedSnapshotStore(
            0, 1, snapshotDirectory, path -> SnapshotFilesInfo.none(), meterRegistry);
    actorScheduler.submitActor(snapshots, SchedulingHints.IO_BOUND).join();
    partition =
        new RaftPartition(
            new PartitionMetadata(
                new PartitionId(BucketFixture.TENANT, BucketFixture.PARTITION),
                Set.of(),
                Map.of(),
                1,
                new MemberId("1")),
            null,
            partitionDirectory.toFile(),
            meterRegistry);
    service =
        new BucketRestoreService(
            store, CLUSTER, partition, 1, path -> SnapshotFilesInfo.none(), meterRegistry);
    fixture.history(FIRST, Instant.EPOCH, Base.NOTHING);
  }

  @AfterEach
  void tearDown() {
    snapshots.close();
    journal.close();
  }

  @Test
  void shouldRestoreTheJournalAfterTheSnapshotByteForByte() throws Exception {
    // given
    records(20);
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(6, 12));
    fixture.segment(FIRST, 0, journal.entries(13, 20));
    final var snapshot = fixture.snapshot(takeSnapshot(8, 17, 18), FIRST);

    // when
    final var restored = service.restore(LATEST);

    // then
    assertThat(restored)
        .isEqualTo(
            new RestoredFromBucket(FIRST, snapshot.snapshotId(), new RestoredCheckpoint(20, 40)));
    assertThat(restoredJournal()).isEqualTo(journal.entries(9, 20));
    assertThat(restoredSnapshotId()).isEqualTo(snapshot.snapshotId());
    assertThat(partitionDirectory.resolve("snapshots").resolve(snapshot.snapshotId() + ".checksum"))
        .exists();
    try (final var files = Files.list(partitionDirectory)) {
      assertThat(files.map(file -> file.getFileName().toString()))
          .describedAs("no download left behind")
          .noneMatch(name -> name.startsWith("restoring-"));
    }
  }

  @Test
  void shouldStopBeforeTheFirstEntryWithARecordPastTheTarget() throws Exception {
    // given: entry 11 has no records, entry 12 holds the positions 21 and 22
    records(10);
    journal.initial();
    records(9);
    fixture.segment(FIRST, 0, journal.entries(1, 6));
    fixture.segment(FIRST, 0, journal.entries(7, 14));
    fixture.segment(FIRST, 0, journal.entries(15, 20));

    // when
    final var restored = service.restore(21);

    // then: the entry without records right before it counts
    assertThat(restored)
        .isEqualTo(new RestoredFromBucket(FIRST, null, new RestoredCheckpoint(11, 20)));
    assertThat(restoredJournal()).isEqualTo(journal.entries(1, 11));
  }

  @Test
  void shouldRestoreTheEntryWhoseLastRecordIsTheTarget() throws Exception {
    // given
    records(10);
    journal.initial();
    records(9);
    fixture.segment(FIRST, 0, journal.entries(1, 20));

    // when
    final var restored = service.restore(22);

    // then
    assertThat(restored.checkpoint()).isEqualTo(new RestoredCheckpoint(12, 22));
    assertThat(restoredJournal()).isEqualTo(journal.entries(1, 12));
  }

  @Test
  void shouldTakeTheNewestSnapshotWhosePositionsTheTargetCovers() throws Exception {
    // given
    records(20);
    fixture.segment(FIRST, 0, journal.entries(1, 10));
    fixture.segment(FIRST, 0, journal.entries(11, 20));
    final var older = fixture.snapshot(takeSnapshot(4, 9, 10), FIRST);
    fixture.snapshot(takeSnapshot(15, 31, 32), FIRST);

    // when
    final var restored = service.restore(25);

    // then
    assertThat(restored)
        .isEqualTo(
            new RestoredFromBucket(FIRST, older.snapshotId(), new RestoredCheckpoint(12, 24)));
    assertThat(restoredJournal()).isEqualTo(journal.entries(5, 12));
    assertThat(restoredSnapshotId()).isEqualTo(older.snapshotId());
  }

  @Test
  void shouldTakeAnOlderSnapshotIfTheLogEndsBeforeTheNewestRelies() throws Exception {
    // given: the newest snapshot relies on records the bucket doesn't have yet
    records(20);
    fixture.segment(FIRST, 0, journal.entries(1, 10));
    fixture.segment(FIRST, 0, journal.entries(11, 20));
    final var older = fixture.snapshot(takeSnapshot(4, 9, 10), FIRST);
    fixture.snapshot(takeSnapshot(18, 37, 50), FIRST);

    // when
    final var restored = service.restore(LATEST);

    // then
    assertThat(restored.snapshotId()).isEqualTo(older.snapshotId());
    assertThat(restored.checkpoint()).isEqualTo(new RestoredCheckpoint(20, 40));
    assertThat(restoredJournal()).isEqualTo(journal.entries(5, 20));
  }

  @Test
  void shouldReadTheHighestLevelAndToleratePublishersDuplicates() throws Exception {
    // given: two leaders uploaded 1-5, a rollup covers 1-12, and a leader cut 13-16 short
    records(20);
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 1, journal.entries(1, 12));
    fixture.segment(FIRST, 0, journal.entries(6, 12));
    fixture.segment(FIRST, 0, journal.entries(13, 16));
    fixture.segment(FIRST, 0, journal.entries(13, 20));

    // when
    final var restored = service.restore(LATEST);

    // then
    assertThat(restored.checkpoint()).isEqualTo(new RestoredCheckpoint(20, 40));
    assertThat(restoredJournal()).isEqualTo(journal.entries());
  }

  @Test
  void shouldFollowThePathThroughTheHistoryTheCurrentOneTookOver() throws Exception {
    // given: the second history took the first one over at entry 10, position 20; the first one's
    // entries 11 and 12 are off the path, and so is its snapshot at 11
    records(12);
    final var second = new RaftEntries(otherJournalDirectory, 2);
    second.takeOver(journal.entries(1, 10));
    for (int i = 0; i < 4; i++) {
      second.records();
    }
    fixture.history(SECOND, CREATED, new Base(FIRST, 10, 20));
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(6, 12));
    fixture.segment(SECOND, 0, second.entries(8, 14));
    final var onThePath = fixture.snapshot(takeSnapshot(3, 7, 8), FIRST);
    fixture.snapshot(takeSnapshot(11, 23, 24), FIRST);

    // when
    final var restored = service.restore(LATEST);

    // then
    assertThat(restored)
        .isEqualTo(
            new RestoredFromBucket(SECOND, onThePath.snapshotId(), new RestoredCheckpoint(14, 28)));
    assertThat(restoredJournal()).isEqualTo(second.entries(4, 14));
    assertThat(second.entries(11, 11)).isNotEqualTo(journal.entries(11, 11));
    second.close();
  }

  @Test
  void shouldFailIfTheBucketEndsBeforeTheTarget() throws Exception {
    // given
    records(20);
    fixture.segment(FIRST, 0, journal.entries(1, 20));

    // when, then
    assertThatThrownBy(() -> service.restore(100))
        .isInstanceOf(LogEndsBeforeTarget.class)
        .hasMessage(
            "Expected the bucket to hold partition 1 up to position 100, but it ends at index 20, position 40");
  }

  @Test
  void shouldFailIfTheEntryAfterTheSnapshotIsMissing() throws Exception {
    // given
    records(20);
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(11, 20));
    fixture.snapshot(takeSnapshot(6, 13, 14), FIRST);

    // when, then
    assertThatThrownBy(() -> service.restore(LATEST))
        .isInstanceOf(MissingEntry.class)
        .hasMessage(
            "Expected a segment of the current path with entry 7 of partition 1, but got none");
  }

  @Test
  void shouldFailWithoutAHistory() throws Exception {
    // given
    store.delete(ObjectNames.historyPart(BucketFixture.LOG, FIRST));

    // when, then
    assertThatThrownBy(() -> service.restore(LATEST))
        .isInstanceOf(NoHistory.class)
        .hasMessage(
            "Expected a history of partition 1 of cluster 'c' and tenant 'default' in the bucket, but got none");
  }

  private void records(final int count) {
    for (int i = 0; i < count; i++) {
      journal.records();
    }
  }

  /**
   * Takes a snapshot whose positions are at most {@code processedPosition} but for the last
   * follow-up event's, and the highest exported one's, which are {@code lastPosition}.
   */
  private PersistedSnapshot takeSnapshot(
      final long index, final long processedPosition, final long lastPosition) {
    final var transientSnapshot =
        snapshots.newTransientSnapshot(index, 1, processedPosition, processedPosition, false).get();
    transientSnapshot
        .take(
            path -> {
              try {
                Files.createDirectories(path);
                Files.write(
                    path.resolve("state"), ("state at " + index).getBytes(StandardCharsets.UTF_8));
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .join();
    return transientSnapshot
        .withLastFollowupEventPosition(lastPosition)
        .withMaxExportedPosition(lastPosition)
        .persist()
        .join();
  }

  private List<Entry> restoredJournal() {
    final var entries = new ArrayList<Entry>();
    try (final var restored =
            SegmentedJournal.builder(meterRegistry)
                .withDirectory(partitionDirectory.toFile())
                .withName(partition.name())
                .withMetaStore(new JournalMetaStore.InMemory())
                .build();
        final var reader = restored.openReader()) {
      while (reader.hasNext()) {
        final var record = reader.next();
        final var bytes = new byte[record.serializedRecord().capacity()];
        record.serializedRecord().getBytes(0, bytes);
        entries.add(new Entry(record.checksum(), bytes));
      }
    }
    return entries;
  }

  private String restoredSnapshotId() {
    final var restored =
        new FileBasedSnapshotStore(
            0, 1, partitionDirectory, path -> SnapshotFilesInfo.none(), meterRegistry);
    actorScheduler.submitActor(restored, SchedulingHints.IO_BOUND).join();
    try {
      return restored.getLatestSnapshot().orElseThrow().getId();
    } finally {
      restored.close();
    }
  }
}
