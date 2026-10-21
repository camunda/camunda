/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static io.camunda.zeebe.restore.BucketFixture.CLUSTER;
import static io.camunda.zeebe.restore.BucketFixture.LOG;
import static io.camunda.zeebe.restore.BucketFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.eventplane.backup.BackupLog;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.BackupLogException.NoBackupLog;
import io.camunda.zeebe.restore.BackupLogException.NotUploaded;
import io.camunda.zeebe.restore.EventPlaneLogBackupStore.PublishingHistory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(60)
final class EventPlaneLogBackupStoreTest {

  private static final UUID FIRST = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID SECOND = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final Instant CREATED = Instant.parse("2026-10-21T12:00:00Z");
  private static final BackupIdentifier ID = new BackupIdentifierImpl(0, 1, 17);

  @TempDir private Path bucket;
  @TempDir private Path journalDirectory;
  @TempDir private Path otherJournalDirectory;
  @TempDir private Path localFiles;
  @TempDir private Path restoreTarget;

  private FileSystemStore store;
  private BucketFixture fixture;
  private RaftEntries journal;
  private TestRestorableBackupStore delegate;

  @BeforeEach
  void setUp() throws IOException {
    store = new FileSystemStore(bucket);
    fixture = new BucketFixture(store);
    journal = new RaftEntries(journalDirectory, 1);
    delegate = new TestRestorableBackupStore();
    fixture.history(FIRST, Instant.EPOCH, Base.NOTHING);
    // entry n holds the positions 2n - 1 and 2n
    for (int i = 0; i < 20; i++) {
      journal.records();
    }
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  void shouldSaveTheBackupWithoutSegmentsAndWriteWhatItNeedsOfTheLog() throws Exception {
    // given: the checkpoint is entry 12's position 24, the backup's log starts at entry 7's 13
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(6, 12));
    fixture.segment(FIRST, 0, journal.entries(13, 20));
    final var backup = backup(24, 13);

    // when
    store(Duration.ofSeconds(5), FIRST).save(backup).join();

    // then
    final var saved = delegate.backups.get(ID);
    assertThat(saved.segments().names()).isEmpty();
    assertThat(saved.snapshot().names()).containsExactly("snapshot-file");
    assertThat(saved.descriptor()).isEqualTo(backup.descriptor());
    assertThat(backupLog()).isEqualTo(Optional.of(new BackupLog(1, 17, 24, FIRST, 6, 12, CREATED)));
  }

  @Test
  void shouldNeedThePathFromItsStartWhenTheBackupNamesNoFirstPosition() throws Exception {
    // given
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(6, 12));

    // when
    store(Duration.ofSeconds(5), FIRST).save(backup(24, -1)).join();

    // then
    assertThat(backupLog().orElseThrow().firstIndex()).isOne();
    assertThat(backupLog().orElseThrow().lastIndex()).isEqualTo(12);
  }

  @Test
  void shouldWaitUntilTheBucketHoldsTheCheckpoint() throws Exception {
    // given
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    final var saved = store(Duration.ofSeconds(30), FIRST).save(backup(24, 13));

    // when
    Thread.sleep(1_000);
    final boolean doneBefore = saved.isDone();
    fixture.segment(FIRST, 0, journal.entries(6, 12));
    saved.get(10, TimeUnit.SECONDS);

    // then
    assertThat(doneBefore).isFalse();
    assertThat(backupLog().orElseThrow().lastIndex()).isEqualTo(12);
  }

  @Test
  void shouldFailTheBackupWhenTheBucketDoesNotHoldTheCheckpointInTime() throws Exception {
    // given
    fixture.segment(FIRST, 0, journal.entries(1, 5));

    // when
    final var saved = store(Duration.ofSeconds(1), FIRST).save(backup(24, 13));

    // then
    assertThatThrownBy(saved::join)
        .isInstanceOf(CompletionException.class)
        .rootCause()
        .isInstanceOf(NotUploaded.class)
        .hasMessage(
            "Expected the Event Plane's bucket to hold position 24 of partition 1, the checkpoint of backup 17, within PT1S, but it reaches position 10 only");
    assertThat(delegate.backups).isEmpty();
    assertThat(backupLog()).isEmpty();
  }

  @Test
  void shouldNotTakeTheLogFromAnotherHistoryThanThePublishers() throws Exception {
    // given: a restore went back to entry 10, and the leader publishes in the second history, whose
    // part isn't in the bucket yet; the first history's entries 11 and 12 hold the same positions
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(6, 12));

    // when
    final var saved = store(Duration.ofSeconds(1), SECOND).save(backup(24, 13));

    // then
    assertThatThrownBy(saved::join).rootCause().isInstanceOf(NotUploaded.class);
    assertThat(backupLog()).isEmpty();
  }

  @Test
  void shouldFollowThePathThroughTheHistoryItsHistoryTookOver() throws Exception {
    // given: the second history took the first one over at entry 10, position 20
    final var second = new RaftEntries(otherJournalDirectory, 2);
    second.takeOver(journal.entries(1, 10));
    for (int i = 0; i < 4; i++) {
      second.records();
    }
    fixture.history(SECOND, CREATED, new Base(FIRST, 10, 20));
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(6, 12));
    fixture.segment(SECOND, 0, second.entries(11, 14));
    final var store = store(Duration.ofSeconds(5), SECOND);

    // when: the checkpoint is the second history's entry 13, at position 26
    store.save(backup(26, 13)).join();
    final var restored = store.restore(ID, restoreTarget).join();

    // then
    assertThat(backupLog())
        .isEqualTo(Optional.of(new BackupLog(1, 17, 26, SECOND, 6, 14, CREATED)));
    assertThat(restoredJournal()).isEqualTo(concat(journal.entries(6, 10), second.entries(11, 13)));
    assertThat(restored.segments().names()).isNotEmpty();
    second.close();
  }

  @Test
  void shouldRestoreTheLogFromTheBucketAsTheJournalOfTheBackup() throws Exception {
    // given
    fixture.segment(FIRST, 0, journal.entries(1, 5));
    fixture.segment(FIRST, 0, journal.entries(6, 12));
    fixture.segment(FIRST, 0, journal.entries(13, 20));
    final var store = store(Duration.ofSeconds(5), FIRST);
    store.save(backup(24, 13)).join();

    // when
    final var restored = store.restore(ID, restoreTarget).join();

    // then: from the segment's first entry, up to the checkpoint's entry
    assertThat(restoredJournal()).isEqualTo(journal.entries(6, 12));
    assertThat(restored.segments().names())
        .isNotEmpty()
        .allMatch(name -> name.startsWith("default-partition-1-") && name.endsWith(".log"));
    assertThat(restored.snapshot().names()).containsExactly("snapshot-file");
    assertThat(restored.descriptor().checkpointPosition()).isEqualTo(24);
  }

  @Test
  void shouldRestoreABackupWithSegmentFilesAsItIs() throws Exception {
    // given: a backup taken without the Event Plane's log
    final var backup = backup(24, 13);
    delegate.save(backup).join();

    // when
    final var restored = store(Duration.ofSeconds(5), FIRST).restore(ID, restoreTarget).join();

    // then
    assertThat(restored.segments().names()).containsExactly("segment-file");
  }

  @Test
  void shouldFailARestoreWithoutTheBackupsLog() {
    // given
    final var backup = backup(24, 13);
    delegate
        .save(
            new BackupImpl(
                backup.id(),
                backup.descriptor(),
                backup.snapshot(),
                new NamedFileSetImpl(Map.of())))
        .join();

    // when
    final var restored = store(Duration.ofSeconds(5), FIRST).restore(ID, restoreTarget);

    // then
    assertThatThrownBy(restored::join)
        .rootCause()
        .isInstanceOf(NoBackupLog.class)
        .hasMessage(
            "Expected backup 17 of partition 1, which has no segment files, to have its log at 'c/default/1/backups/00000000000000000017.json', but there is none");
  }

  @Test
  void shouldDeleteWhatTheBackupNeedsOfTheLogWithTheBackup() throws Exception {
    // given
    fixture.segment(FIRST, 0, journal.entries(1, 12));
    final var store = store(Duration.ofSeconds(5), FIRST);
    store.save(backup(24, 13)).join();

    // when
    store.delete(ID).join();

    // then
    assertThat(delegate.backups).isEmpty();
    assertThat(backupLog()).isEmpty();
  }

  @Test
  void shouldDeleteWhatTheBackupNeedsOfTheLogWhenTheBackupFails() throws Exception {
    // given
    fixture.segment(FIRST, 0, journal.entries(1, 12));
    final var store = store(Duration.ofSeconds(5), FIRST);
    store.save(backup(24, 13)).join();

    // when
    store.markFailed(ID, "failed").join();

    // then
    assertThat(backupLog()).isEmpty();
  }

  private EventPlaneLogBackupStore store(final Duration uploadTimeout, final UUID publishing) {
    final PublishingHistory history = partition -> Optional.of(publishing);
    return new EventPlaneLogBackupStore(
        delegate, store, CLUSTER, TENANT, uploadTimeout, history, InstantSource.system());
  }

  private Backup backup(final long checkpointPosition, final long firstLogPosition) {
    try {
      final var snapshotFile = Files.writeString(localFiles.resolve("snapshot-file"), "state");
      final var segmentFile = Files.writeString(localFiles.resolve("segment-file"), "journal");
      return new BackupImpl(
          ID,
          new BackupDescriptorImpl(
              Optional.of("12-1-24-24"),
              firstLogPosition < 0 ? OptionalLong.empty() : OptionalLong.of(firstLogPosition),
              checkpointPosition,
              1,
              "8.11.0",
              CREATED,
              CheckpointType.MANUAL_BACKUP),
          new NamedFileSetImpl(Map.of("snapshot-file", snapshotFile)),
          new NamedFileSetImpl(Map.of("segment-file", segmentFile)));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * What the backup needs of the log, if it is in the bucket, as if written at {@link #CREATED}.
   */
  private Optional<BackupLog> backupLog() throws IOException {
    final var json = store.get(ObjectNames.backupLog(LOG, 17));
    if (json.isEmpty()) {
      return Optional.empty();
    }
    final var read = BackupLog.fromJson(json.get());
    return Optional.of(
        new BackupLog(
            read.version(),
            read.checkpointId(),
            read.checkpointPosition(),
            read.history(),
            read.firstIndex(),
            read.lastIndex(),
            CREATED));
  }

  private List<Entry> restoredJournal() {
    final var entries = new ArrayList<Entry>();
    try (final var restored =
            SegmentedJournal.builder(new SimpleMeterRegistry())
                .withDirectory(restoreTarget.toFile())
                .withName("default-partition-1")
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

  private static List<Entry> concat(final List<Entry> first, final List<Entry> second) {
    final var all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }
}
