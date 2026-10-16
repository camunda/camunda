/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.TIMELINE;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.archiveLog;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.archiveSnapshot;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.confirmPublished;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.object;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.sealIndex;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.snapshot;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.startTimeline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkFile;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.RecordingListener;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LogArchiveRecordsProcessorTest {

  private static final int CHUNK_SIZE = 3;
  private static final int ROOT_EVERY = 4;
  private static final LogArchiveProcessorConfig CONFIG =
      new LogArchiveProcessorConfig(1, CHUNK_SIZE, ROOT_EVERY);

  @TempDir Path leaderDirectory;
  @TempDir Path replicaDirectory;

  private LogArchiveHarness harness;

  @BeforeEach
  void setUp() {
    harness = new LogArchiveHarness(leaderDirectory.toFile(), CONFIG);
  }

  @AfterEach
  void tearDown() throws Exception {
    harness.close();
  }

  // ---- START_TIMELINE ---------------------------------------------------------------------------

  @Test
  void shouldStartTimelineAndTakeSeqOne() {
    // when
    final var event = harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 501));

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.TIMELINE_STARTED);
    assertThat(event.getSeq()).isEqualTo(1);
    assertThat(event.getStartIndex()).isEqualTo(501);
    assertThat(event.getArchivedIndex()).isEqualTo(500);
    assertThat(event.getArchivedPosition()).isEqualTo(-1);

    final var meta = harness.state().meta();
    assertThat(meta.getTimelineId()).isEqualTo(TIMELINE);
    assertThat(meta.getParentTimelineId()).isEqualTo("parent");
    assertThat(meta.getStartIndex()).isEqualTo(501);
    assertThat(meta.getArchivedIndex()).isEqualTo(500);
    assertThat(meta.getArchivedPosition()).isEqualTo(-1);
    assertThat(meta.getNextSeq()).isEqualTo(2);
    assertThat(meta.getLastPublishedSeq()).isZero();
    assertThat(meta.getNextChunkNo()).isEqualTo(1);
    assertThat(meta.getFirstArchivedPosition()).isEqualTo(-1);
    assertThat(harness.state().outboxAfter(0)).extracting("seq").containsExactly(1L);
    assertThat(harness.listener.calls).containsExactly("timelineStarted " + TIMELINE);
  }

  @Test
  void shouldPublishCommitEntryOfTimelineStarted() {
    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    assertThat(harness.published).hasSize(1);
    final var request = harness.published.getFirst();
    assertThat(request.timelineId()).isEqualTo(TIMELINE);
    assertThat(request.seq()).isEqualTo(1);
    assertThat(request.rootJson()).isNull();
    assertThat(request.seal()).isNull();
    final var entry = LogArchiveJson.read(request.commitEntryJson(), CommitEntry.class);
    assertThat(entry.type()).isEqualTo(CommitEntry.Type.TIMELINE_STARTED);
    assertThat(entry.seq()).isEqualTo(1);
    assertThat(entry.partitionId()).isEqualTo(1);
    assertThat(entry.startIndex()).isEqualTo(1);
    assertThat(entry.parentTimelineId()).isEqualTo("parent");
    assertThat(entry.archivedIndex()).isZero();
    assertThat(entry.archivedPosition()).isEqualTo(-1);
    assertThat(entry.recordPosition()).isEqualTo(harness.events.getLast().commandPosition());
  }

  @Test
  void shouldRejectStartTimelineOfCurrentTimeline() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    assertRejected(
        LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 5), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectStartTimelineWithEmptyId() {
    assertRejected(
        LogArchiveIntent.START_TIMELINE, startTimeline("", 1), RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectStartTimelineWithoutPositiveStartIndex() {
    assertRejected(
        LogArchiveIntent.START_TIMELINE,
        startTimeline(TIMELINE, 0),
        RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldClearPreviousTimelineStateOnStartTimeline() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1), object(2)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-1", 2)));
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(2));
    harness.createCheckpoint(10, 50, CheckpointType.MANUAL_BACKUP);

    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline("tl-0.r7", 1));

    // then
    final var meta = harness.state().meta();
    assertThat(meta.getTimelineId()).isEqualTo("tl-0.r7");
    assertThat(meta.getNextSeq()).isEqualTo(2);
    assertThat(meta.getLastPublishedSeq()).isZero();
    assertThat(meta.getArchivedIndex()).isZero();
    assertThat(meta.getArchivedPosition()).isEqualTo(-1);
    assertThat(harness.state().openObjects()).isEmpty();
    assertThat(harness.state().snapshots()).isEmpty();
    assertThat(harness.state().chunks()).isEmpty();
    // pending checkpoints survive: one may still lie in the new timeline's archived range
    assertThat(harness.state().pendingCheckpointsUpTo(Long.MAX_VALUE))
        .extracting("checkpointId")
        .containsExactly(10L);
    assertThat(harness.state().outboxAfter(0)).extracting("seq").containsExactly(1L);
    assertThat(harness.events.getLast().value().getSeq()).isEqualTo(1);
  }

  // ---- ARCHIVE_LOG ------------------------------------------------------------------------------

  @Test
  void shouldArchiveLogAndMoveF() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // when
    final var event =
        harness.accept(
            LogArchiveIntent.ARCHIVE_LOG,
            archiveLog(object(1, 10, 100, 190), object(11, 20, 200, 290)));

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.LOG_ARCHIVED);
    assertThat(event.getSeq()).isEqualTo(2);
    assertThat(event.getArchivedIndex()).isEqualTo(20);
    assertThat(event.getArchivedPosition()).isEqualTo(290);
    assertThat(event.getObjects()).extracting("firstIndex").containsExactly(1L, 11L);

    final var meta = harness.state().meta();
    assertThat(meta.getArchivedIndex()).isEqualTo(20);
    assertThat(meta.getArchivedPosition()).isEqualTo(290);
    assertThat(meta.getFirstArchivedPosition()).isEqualTo(100);
    assertThat(meta.getNextSeq()).isEqualTo(3);
    assertThat(harness.state().openObjects())
        .extracting(open -> open.object().getFirstIndex())
        .containsExactly(1L, 11L);
    assertThat(harness.state().openObjectCount()).isEqualTo(2);
    assertThat(harness.listener.calls).contains("logArchived 20 290");
  }

  @Test
  void shouldNotMoveArchivedPositionForObjectsWithoutApplicationEntries() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 5, 10, 50)));

    // when
    final var event =
        harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(6, 9, -1, -1)));

    // then
    assertThat(event.getArchivedIndex()).isEqualTo(9);
    assertThat(event.getArchivedPosition()).isEqualTo(50);
    assertThat(harness.state().meta().getFirstArchivedPosition()).isEqualTo(10);
  }

  @Test
  void shouldSetFirstArchivedPositionFromFirstObjectWithApplicationEntries() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // when
    harness.accept(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 4, -1, -1), object(5, 8, 77, 90)));

    // then
    assertThat(harness.state().meta().getFirstArchivedPosition()).isEqualTo(77);
    assertThat(harness.state().meta().getArchivedPosition()).isEqualTo(90);
  }

  @Test
  void shouldRejectArchiveLogBeforeTimelineStarted() {
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectArchiveLogOfOtherTimeline() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog("other", object(1)), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectArchiveLogWithoutObjects() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    assertRejected(LogArchiveIntent.ARCHIVE_LOG, archiveLog(), RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectArchiveLogWhoseKeyDoesNotMatchItsRangeAndTerm() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    final var object = object(1, 5, 1, 5).setKey(LogArchiveKeys.dataKey(1, 6, 1));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object), RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectArchiveLogWithoutChecksum() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    final var object = object(1, 5, 1, 5).setSha256("");

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object), RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectArchiveLogWithGapToF() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 5, 1, 5)));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(7, 9, 7, 9)), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectArchiveLogThatMovesFBackwardsOrRepeats() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 5, 1, 5), object(6, 9, 6, 9)));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(6, 9, 6, 9)), RejectionType.INVALID_STATE);
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 5, 1, 5)), RejectionType.INVALID_STATE);
    assertThat(harness.state().meta().getArchivedIndex()).isEqualTo(9);
  }

  @Test
  void shouldRejectArchiveLogWithGapBetweenObjects() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG,
        archiveLog(object(1, 5, 1, 5), object(7, 9, 7, 9)),
        RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectArchiveLogWithWrongEntryCount() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG,
        archiveLog(object(1, 5, 1, 5).setEntryCount(4)),
        RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectArchiveLogWithFirstIndexAfterLastIndex() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_LOG,
        archiveLog(object(1, 5, 1, 5).setLastIndex(0).setEntryCount(0)),
        RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldNotifyListenerWithCurrentFOnRejectedArchiveLog() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 5, 1, 5)));

    // when
    harness.process(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(9, 12, 9, 12)));

    // then
    assertThat(harness.listener.calls).last().isEqualTo("archiveLogRejected 5");
  }

  // ---- covered checkpoints ----------------------------------------------------------------------

  @Test
  void shouldCoverCheckpointsUpToFAndDropThoseBeforeFirstArchivedPosition() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(10, 5, CheckpointType.MARKER);
    harness.createCheckpoint(20, 15, CheckpointType.MARKER);
    harness.createCheckpoint(30, 100, CheckpointType.MARKER);

    // when
    final var first =
        harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 10, 20)));

    // then: 10 lies before the first archived position, 30 beyond F
    assertThat(first.getCheckpoints()).extracting("checkpointId").containsExactly(20L);
    assertThat(first.getCheckpoints()).extracting("checkpointPosition").containsExactly(15L);
    assertThat(harness.state().pendingCheckpointsUpTo(Long.MAX_VALUE))
        .extracting("checkpointId")
        .containsExactly(30L);

    // when
    final var second =
        harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(11, 20, 21, 120)));

    // then
    assertThat(second.getCheckpoints()).extracting("checkpointId").containsExactly(30L);
    assertThat(harness.state().pendingCheckpointsUpTo(Long.MAX_VALUE)).isEmpty();
    assertThat(harness.state().openObjects().get(0).checkpoints())
        .extracting("checkpointId")
        .containsExactly(20L);
    assertThat(harness.state().openObjects().get(1).checkpoints())
        .extracting("checkpointId")
        .containsExactly(30L);

    final var entry =
        LogArchiveJson.read(
            harness.state().outboxAfter(2).getFirst().commitEntryJson(), CommitEntry.class);
    assertThat(entry.checkpoints()).hasSize(1);
    assertThat(entry.checkpoints().getFirst().checkpointType()).isEqualTo("MARKER");
  }

  @Test
  void shouldCoverBackupCheckpointWhoseBackupIsConfirmedOnlyAfterItsPositionIsArchived() {
    // given: the backup's checkpoint is created, but its backup is not yet confirmed
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(7, 210, CheckpointType.MANUAL_BACKUP);

    // when: the log up to the checkpoint is archived before the backup is confirmed
    final var event =
        harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 201, 210)));
    harness.confirmBackup(7, 210, CheckpointType.MANUAL_BACKUP);
    final var next =
        harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(11, 20, 211, 220)));

    // then: listed exactly once, by the event that archived its position
    assertThat(event.getCheckpoints()).extracting("checkpointId").containsExactly(7L);
    assertThat(event.getCheckpoints())
        .extracting("checkpointType")
        .containsExactly(CheckpointType.MANUAL_BACKUP);
    assertThat(harness.state().openObjects().getFirst().checkpoints())
        .extracting("checkpointId")
        .containsExactly(7L);
    assertThat(next.getCheckpoints()).isEmpty();
  }

  @Test
  void shouldCoverCheckpointsOfEveryType() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(1, 2, CheckpointType.MARKER);
    harness.createCheckpoint(2, 3, CheckpointType.MANUAL_BACKUP);
    harness.createCheckpoint(3, 4, CheckpointType.SCHEDULED_BACKUP);

    // when
    final var event = harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 5, 1, 5)));

    // then
    assertThat(event.getCheckpoints())
        .extracting("checkpointType")
        .containsExactly(
            CheckpointType.MARKER, CheckpointType.MANUAL_BACKUP, CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldAttachCheckpointToTheObjectContainingItsPosition() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(1, 12, CheckpointType.MARKER);
    harness.createCheckpoint(2, 31, CheckpointType.MANUAL_BACKUP);

    // when: the middle object holds no application entries
    harness.accept(
        LogArchiveIntent.ARCHIVE_LOG,
        archiveLog(object(1, 1, 11, 20), object(2, 2, -1, -1), object(3, 3, 30, 32)));

    // then
    final var open = harness.state().openObjects();
    assertThat(open.get(0).checkpoints()).extracting("checkpointId").containsExactly(1L);
    assertThat(open.get(1).checkpoints()).isEmpty();
    assertThat(open.get(2).checkpoints()).extracting("checkpointId").containsExactly(2L);
  }

  @Test
  void shouldNotCoverCheckpointsTwice() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(10, 15, CheckpointType.MARKER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 10, 20)));

    // when
    final var second =
        harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(11, 20, 21, 30)));

    // then
    assertThat(second.getCheckpoints()).isEmpty();
    assertThat(harness.state().pendingCheckpointsUpTo(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  void shouldCoverCheckpointCreatedBeforeTheTimelineStartedWhenItsPositionIsArchived() {
    // given
    harness.createCheckpoint(10, 15, CheckpointType.MANUAL_BACKUP);
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // when
    final var event =
        harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 10, 20)));

    // then
    assertThat(event.getCheckpoints()).extracting("checkpointId").containsExactly(10L);
    assertThat(harness.state().pendingCheckpointsUpTo(Long.MAX_VALUE)).isEmpty();
  }

  // ---- ARCHIVE_SNAPSHOT -------------------------------------------------------------------------

  @Test
  void shouldArchiveSnapshotAtOrBelowF() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));

    // when
    final var event =
        harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-1", 10)));

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.SNAPSHOT_ARCHIVED);
    assertThat(event.getSeq()).isEqualTo(3);
    assertThat(event.getSnapshot().getId()).isEqualTo("snap-1");
    assertThat(event.getArchivedIndex()).isEqualTo(10);
    assertThat(harness.state().snapshots()).extracting("id").containsExactly("snap-1");
    assertThat(harness.state().containsSnapshot("snap-1")).isTrue();
    assertThat(harness.state().containsSnapshot("snap-2")).isFalse();
    assertThat(harness.state().meta().getNextSeq()).isEqualTo(4);
    assertThat(harness.listener.calls).last().isEqualTo("snapshotArchived snap-1");
    final var entry =
        LogArchiveJson.read(harness.published.getLast().commitEntryJson(), CommitEntry.class);
    assertThat(entry.type()).isEqualTo(CommitEntry.Type.SNAPSHOT_ARCHIVED);
    assertThat(entry.snapshot().id()).isEqualTo("snap-1");
  }

  @Test
  void shouldArchiveSnapshotJustBeforeStartIndex() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 501));

    // when
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-1", 500)));

    // then
    assertThat(harness.state().snapshots()).hasSize(1);
  }

  @Test
  void shouldRejectSnapshotAboveF() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_SNAPSHOT,
        archiveSnapshot(snapshot("snap-1", 11)),
        RejectionType.INVALID_STATE);
    assertThat(harness.listener.calls).last().isEqualTo("snapshotRejected snap-1");
  }

  @Test
  void shouldRejectSnapshotBelowTimelineStart() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 501));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(501, 510, 1, 10)));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_SNAPSHOT,
        archiveSnapshot(snapshot("snap-1", 499)),
        RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectDuplicateSnapshotId() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-1", 5)));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_SNAPSHOT,
        archiveSnapshot(snapshot("snap-1", 6)),
        RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectSnapshotWithoutId() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    assertRejected(
        LogArchiveIntent.ARCHIVE_SNAPSHOT,
        archiveSnapshot(snapshot("", 0)),
        RejectionType.INVALID_ARGUMENT);
  }

  // ---- sealing ----------------------------------------------------------------------------------

  @Test
  void shouldNotRequestSealBeforeChunkIsFull() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // when
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2)));

    // then
    assertThat(harness.published).allSatisfy(request -> assertThat(request.seal()).isNull());
  }

  @Test
  void shouldRequestSealWhenOpenChunkReachesChunkSize() {
    // given
    fillOpenChunk();

    // then
    final var seal = harness.published.getLast().seal();
    assertThat(seal).isNotNull();
    final var chunk = seal.chunk();
    assertThat(chunk.getChunkNo()).isEqualTo(1);
    assertThat(chunk.getKey()).isEqualTo(LogArchiveKeys.indexKey(1, 3));
    assertThat(chunk.getFirstIndex()).isEqualTo(1);
    assertThat(chunk.getLastIndex()).isEqualTo(3);
    assertThat(chunk.getFirstPosition()).isEqualTo(1);
    assertThat(chunk.getLastPosition()).isEqualTo(3);
    assertThat(chunk.getObjectCount()).isEqualTo(3);
    assertThat(chunk.getFirstCheckpointId()).isEqualTo(-1);
    assertThat(chunk.getLastCheckpointId()).isEqualTo(-1);
    assertThat(chunk.getSha256()).hasSize(64);
    assertThat(seal.chunkJson()).isNotEmpty();
  }

  @Test
  void shouldSealIndexAndMoveObjectsToChunk() {
    // given
    fillOpenChunk();
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(4)));
    final var chunk = firstSealRequest().chunk();

    // when
    final var event = harness.accept(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk));

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.INDEX_SEALED);
    assertThat(event.getSeq()).isEqualTo(6);
    assertThat(event.getChunk().getChunkNo()).isEqualTo(1);
    assertThat(harness.state().openObjects())
        .extracting(open -> open.object().getFirstIndex())
        .containsExactly(4L);
    assertThat(harness.state().chunks()).hasSize(1);
    assertThat(harness.state().chunks().getFirst().getKey()).isEqualTo(chunk.getKey());
    assertThat(harness.state().chunks().getFirst().getSha256()).isEqualTo(chunk.getSha256());
    assertThat(harness.state().meta().getNextChunkNo()).isEqualTo(2);
    assertThat(harness.state().meta().getArchivedIndex()).isEqualTo(4);
    assertThat(harness.listener.calls).last().isEqualTo("indexSealed 1");
  }

  @Test
  void shouldAttachRootAndNoFurtherSealOnIndexSealed() {
    // given
    fillOpenChunk();
    final var chunk = firstSealRequest().chunk();

    // when
    harness.accept(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk));

    // then
    final var request = harness.published.getLast();
    assertThat(request.seq()).isEqualTo(5);
    assertThat(request.seal()).isNull();
    final var root = LogArchiveJson.read(request.rootJson(), RootFile.class);
    assertThat(root.seq()).isEqualTo(5);
    assertThat(root.chunks()).hasSize(1);
    assertThat(root.openObjects()).isEmpty();
  }

  @Test
  void shouldRequestSealOnlyOnceWhileChunkStaysFull() {
    // given
    fillOpenChunk();
    final int published = harness.published.size();

    // when
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(4)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(5)));

    // then
    assertThat(harness.published.subList(0, published))
        .filteredOn(request -> request.seal() != null)
        .hasSize(1);
    assertThat(harness.published.subList(published, harness.published.size()))
        .allSatisfy(request -> assertThat(request.seal()).isNull());
  }

  @Test
  void shouldRequestSealAgainAfterRejectedSealIndexIfStillFull() {
    // given
    fillOpenChunk();
    final var chunk = firstSealRequest().chunk();
    final int published = harness.published.size();

    // when
    harness.process(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk.setChunkNo(2)));

    // then
    assertThat(harness.published).hasSize(published + 1);
    final var retry = harness.published.getLast();
    assertThat(retry.seq()).isEqualTo(-1);
    assertThat(retry.commitEntryJson()).isNull();
    assertThat(retry.rootJson()).isNull();
    assertThat(retry.seal()).isNotNull();
    assertThat(retry.seal().chunk().getChunkNo()).isEqualTo(1);
  }

  @Test
  void shouldNotRequestSealAfterRejectedSealIndexIfChunkIsNotFull() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));
    final int published = harness.published.size();
    final var chunk = new IndexChunk().setChunkNo(1).setObjectCount(1);

    // when
    harness.process(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk));

    // then
    assertThat(harness.published).hasSize(published);
  }

  @Test
  void shouldRequestNextSealAfterIndexSealedIfStillFull() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(
        LogArchiveIntent.ARCHIVE_LOG,
        archiveLog(object(1), object(2), object(3), object(4), object(5), object(6)));
    final var chunk = harness.published.getLast().seal().chunk();
    assertThat(chunk.getObjectCount()).isEqualTo(3);

    // when
    harness.accept(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk));

    // then
    final var next = harness.published.getLast().seal();
    assertThat(next).isNotNull();
    assertThat(next.chunk().getChunkNo()).isEqualTo(2);
    assertThat(next.chunk().getFirstIndex()).isEqualTo(4);
    assertThat(next.chunk().getLastIndex()).isEqualTo(6);
  }

  @Test
  void shouldCarryCheckpointIdsAndRangeInChunk() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(10, 2, CheckpointType.MARKER);
    harness.createCheckpoint(20, 3, CheckpointType.MANUAL_BACKUP);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(3)));

    // then
    final var chunk = harness.published.getLast().seal().chunk();
    assertThat(chunk.getFirstCheckpointId()).isEqualTo(10);
    assertThat(chunk.getLastCheckpointId()).isEqualTo(20);
  }

  @Test
  void shouldListInChunkExactlyTheCheckpointsInItsRangeAndMatchIdsAndSha() {
    // given: 10 and 20 lie in the objects, 5 is before the first archived position
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(5, 1, CheckpointType.MARKER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 1, 11, 11)));
    harness.createCheckpoint(10, 12, CheckpointType.MARKER);
    harness.createCheckpoint(20, 31, CheckpointType.MARKER);
    harness.accept(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2, 2, 12, 13), object(3, 3, 30, 32)));

    // when
    final var seal = harness.published.getLast().seal();

    // then
    final var file = LogArchiveJson.read(seal.chunkJson(), IndexChunkFile.class);
    assertThat(file.checkpoints()).extracting("checkpointId").containsExactly(10L, 20L);
    assertThat(file.checkpoints())
        .allSatisfy(
            checkpoint ->
                assertThat(checkpoint.checkpointPosition())
                    .isBetween(seal.chunk().getFirstPosition(), seal.chunk().getLastPosition()));
    assertThat(seal.chunk().getFirstCheckpointId()).isEqualTo(10);
    assertThat(seal.chunk().getLastCheckpointId()).isEqualTo(20);
    assertThat(seal.chunk().getSha256()).isEqualTo(sha256Hex(seal.chunkJson()));
  }

  @Test
  void shouldFailIfAPendingCheckpointLiesInNoObjectOfTheEvent() {
    // given: a checkpoint whose position was archived before it was created, which log order
    // rules out
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 5, 10, 20)));
    harness.createCheckpoint(8, 15, CheckpointType.MANUAL_BACKUP);
    final var before = harness.fingerprint();

    // when
    assertThatThrownBy(
            () -> harness.process(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(6, 9, 21, 30))))
        // then
        .hasStackTraceContaining("Expected checkpoint 8 at position 15");
    assertThat(harness.fingerprint()).isEqualTo(before);
  }

  @Test
  void shouldRejectSealIndexWithWrongChunkNo() {
    // given
    fillOpenChunk();
    final var chunk = firstSealRequest().chunk().setChunkNo(2);

    // then
    assertRejected(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectSealIndexWithWrongLastIndex() {
    // given
    fillOpenChunk();
    final var chunk = firstSealRequest().chunk().setLastIndex(2);

    // then
    assertRejected(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectSealIndexWithWrongFirstIndex() {
    // given
    fillOpenChunk();
    final var chunk = firstSealRequest().chunk().setFirstIndex(2);

    // then
    assertRejected(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectSealIndexWithMoreObjectsThanOpen() {
    // given
    fillOpenChunk();
    final var chunk = firstSealRequest().chunk().setObjectCount(4);

    // then
    assertRejected(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectSealIndexWithoutObjects() {
    // given
    fillOpenChunk();
    final var chunk = firstSealRequest().chunk().setObjectCount(0);

    // then
    assertRejected(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk), RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectSealIndexTwice() {
    // given
    fillOpenChunk();
    final var chunk = firstSealRequest().chunk();
    harness.accept(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk));

    // then
    assertRejected(LogArchiveIntent.SEAL_INDEX, sealIndex(chunk), RejectionType.INVALID_STATE);
    assertThat(harness.listener.calls).last().isEqualTo("rejected SEAL_INDEX INVALID_STATE");
  }

  // ---- CONFIRM_PUBLISHED ------------------------------------------------------------------------

  @Test
  void shouldPruneOutboxOnConfirmPublished() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2)));
    assertThat(harness.state().outboxAfter(0)).extracting("seq").containsExactly(1L, 2L, 3L);

    // when
    final var event = harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(2));

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.PUBLISHED);
    assertThat(event.getPublishedSeq()).isEqualTo(2);
    assertThat(harness.state().outboxAfter(0)).extracting("seq").containsExactly(3L);
    assertThat(harness.state().meta().getLastPublishedSeq()).isEqualTo(2);
    assertThat(harness.state().meta().getNextSeq()).isEqualTo(4);
    assertThat(harness.listener.calls).last().isEqualTo("published 2");
  }

  @Test
  void shouldRejectStaleOrDuplicateConfirmPublished() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2)));
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(2));

    // then
    assertRejected(
        LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(2), RejectionType.INVALID_STATE);
    assertRejected(
        LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(1), RejectionType.INVALID_STATE);
    assertRejected(
        LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(0), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectConfirmPublishedOfSeqNotYetTaken() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then: nextSeq is 2
    assertRejected(
        LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(2), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldNotTakeSeqForPublished() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));

    // when
    final var published = harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(2));
    final var next = harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2)));

    // then
    assertThat(published.getSeq()).isEqualTo(-1);
    assertThat(next.getSeq()).isEqualTo(3);
    assertThat(harness.published).extracting(PublishRequest::seq).containsExactly(1L, 2L, 3L);
  }

  // ---- seq and roots ----------------------------------------------------------------------------

  @Test
  void shouldTakeConsecutiveSeqsPerEventWithCommitEntry() {
    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-1", 1)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2)));
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(3));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(3)));

    // then
    assertThat(harness.events)
        .extracting(event -> event.value().getSeq())
        .containsExactly(1L, 2L, 3L, 4L, -1L, 5L);
    assertThat(harness.state().meta().getNextSeq()).isEqualTo(6);
    assertThat(harness.state().outboxAfter(0)).extracting("seq").containsExactly(4L, 5L);
  }

  @Test
  void shouldAttachRootExactlyEveryRootEverySeq() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // when seq 2..9
    for (int i = 1; i <= 8; i++) {
      harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s" + i, 0)));
    }

    // then
    for (final var request : harness.published) {
      if (request.seq() % ROOT_EVERY == 0) {
        assertThat(request.rootJson()).as("root at seq %d", request.seq()).isNotNull();
        final var root = LogArchiveJson.read(request.rootJson(), RootFile.class);
        assertThat(root.seq()).isEqualTo(request.seq());
        assertThat(root.snapshots()).hasSize((int) request.seq() - 1);
      } else {
        assertThat(request.rootJson()).as("root at seq %d", request.seq()).isNull();
      }
    }
    assertThat(harness.published).hasSize(9);
  }

  @Test
  void shouldBuildRootFromStateAtItsSeq() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 5));
    harness.createCheckpoint(10, 1, CheckpointType.MARKER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(5)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-1", 5)));

    // when seq 4 -> root
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(6)));

    // then
    final var request = harness.published.getLast();
    assertThat(request.seq()).isEqualTo(4);
    final var root = LogArchiveJson.read(request.rootJson(), RootFile.class);
    assertThat(root.timelineId()).isEqualTo(TIMELINE);
    assertThat(root.parentTimelineId()).isEqualTo("parent");
    assertThat(root.partitionId()).isEqualTo(1);
    assertThat(root.startIndex()).isEqualTo(5);
    assertThat(root.archivedIndex()).isEqualTo(6);
    assertThat(root.archivedPosition()).isEqualTo(6);
    assertThat(root.openObjects()).extracting("firstIndex").containsExactly(5L, 6L);
    assertThat(root.snapshots()).extracting("id").containsExactly("snap-1");
    assertThat(root.chunks()).isEmpty();
  }

  @Test
  void shouldIncludeCoveredCheckpointsInRootUntilSealed() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(10, 2, CheckpointType.MARKER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1), object(2), object(3)));
    final var seal = harness.published.getLast().seal();
    harness.published.clear();
    harness.recover();
    final var beforeSeal =
        LogArchiveJson.read(harness.published.getLast().rootJson(), RootFile.class);

    // when
    harness.accept(LogArchiveIntent.SEAL_INDEX, sealIndex(seal.chunk()));

    // then
    assertThat(beforeSeal.openCheckpoints()).extracting("checkpointId").containsExactly(10L);
    final var afterSeal =
        LogArchiveJson.read(harness.published.getLast().rootJson(), RootFile.class);
    assertThat(afterSeal.openCheckpoints()).isEmpty();
    assertThat(afterSeal.chunks()).hasSize(1);
    assertThat(afterSeal.chunks().getFirst().firstCheckpointId()).isEqualTo(10);
  }

  // ---- replay and determinism -------------------------------------------------------------------

  @Test
  void shouldNotNotifyOrPublishWhenReplaying() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));

    try (final var replica = new LogArchiveHarness(replicaDirectory.toFile(), CONFIG)) {
      // when
      harness.events.forEach(replica::replay);

      // then
      assertThat(replica.listener.calls).isEmpty();
      assertThat(replica.published).isEmpty();
      assertThat(replica.state().meta().getNextSeq()).isEqualTo(3);
    } catch (final Exception e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void shouldReachIdenticalStateWhenReplayingProcessedEvents() throws Exception {
    // given a mixed sequence, including rejections, seals, checkpoints and confirmations
    harness.createCheckpoint(1, 0, CheckpointType.MARKER);
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(10, 5, CheckpointType.MARKER);
    harness.createCheckpoint(20, 15, CheckpointType.MANUAL_BACKUP);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.createCheckpoint(30, 25, CheckpointType.SCHEDULED_BACKUP);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(11, 20, 11, 20)));
    harness.confirmBackup(20, 15, CheckpointType.MANUAL_BACKUP);
    harness.process(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(30, 40, 30, 40)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-1", 12)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(21, 30, 21, 30)));
    harness.accept(LogArchiveIntent.SEAL_INDEX, sealIndex(firstSealRequest().chunk()));
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(4));
    harness.process(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(3));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(31, 35, 31, 35), object(36)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-2", 30)));
    harness.createCheckpoint(40, 50, CheckpointType.MANUAL_BACKUP);
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(8));

    // when
    try (final var replica = new LogArchiveHarness(replicaDirectory.toFile(), CONFIG)) {
      replica.replayLog(harness.log);

      // then
      assertThat(harness.state().pendingCheckpointsUpTo(Long.MAX_VALUE))
          .extracting("checkpointId")
          .containsExactly(40L);
      assertThat(replica.fingerprint()).isEqualTo(harness.fingerprint());
      assertThat(harness.state().meta().getNextSeq()).isEqualTo(9);

      final var leaderOutbox = harness.state().outboxAfter(-1);
      final var replicaOutbox = replica.state().outboxAfter(-1);
      assertThat(replicaOutbox).hasSameSizeAs(leaderOutbox);
      for (int i = 0; i < leaderOutbox.size(); i++) {
        assertThat(replicaOutbox.get(i).seq()).isEqualTo(leaderOutbox.get(i).seq());
        assertThat(replicaOutbox.get(i).commitEntryJson())
            .isEqualTo(leaderOutbox.get(i).commitEntryJson());
      }
    }
  }

  @Test
  void shouldWriteSameCommitEntryBytesWhenReplayingAnEvent() throws Exception {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(10, 5, CheckpointType.MANUAL_BACKUP);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    final var published = harness.published.getLast().commitEntryJson();

    // when
    try (final var replica = new LogArchiveHarness(replicaDirectory.toFile(), CONFIG)) {
      replica.replayLog(harness.log);

      // then
      final var replayed = replica.state().outboxAfter(1).getFirst().commitEntryJson();
      assertThat(replayed).isEqualTo(published);
      assertThat(Arrays.equals(replayed, published)).isTrue();
    }
  }

  // ---- recovery ---------------------------------------------------------------------------------

  @Test
  void shouldRepublishOutboxInSeqOrderThenRootOnRecovered() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2)));
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(1));
    final var outbox = harness.state().outboxAfter(0);
    harness.published.clear();
    harness.listener.calls.clear();

    // when
    harness.recover();

    // then
    assertThat(harness.listener.calls).containsExactly("recovered 4");
    assertThat(harness.published).extracting(PublishRequest::seq).containsExactly(2L, 3L, 3L);
    assertThat(harness.published.get(0).commitEntryJson())
        .isEqualTo(outbox.get(0).commitEntryJson());
    assertThat(harness.published.get(1).commitEntryJson())
        .isEqualTo(outbox.get(1).commitEntryJson());
    assertThat(harness.published.get(0).rootJson()).isNull();
    assertThat(harness.published.get(1).rootJson()).isNull();

    final var rootOnly = harness.published.get(2);
    assertThat(rootOnly.commitEntryJson()).isNull();
    assertThat(rootOnly.seal()).isNull();
    final var root = LogArchiveJson.read(rootOnly.rootJson(), RootFile.class);
    assertThat(root.seq()).isEqualTo(3);
    assertThat(root.openObjects()).hasSize(2);
  }

  @Test
  void shouldRequestSealOnRecoveredIfOpenChunkIsFull() {
    // given
    fillOpenChunk();
    final var sealBefore = harness.published.getLast().seal();
    harness.published.clear();

    // when
    harness.recover();

    // then
    final var last = harness.published.getLast();
    assertThat(last.commitEntryJson()).isNull();
    assertThat(last.seal()).isNotNull();
    assertThat(last.seal().chunkJson()).isEqualTo(sealBefore.chunkJson());
    assertThat(last.seal().chunk().getSha256()).isEqualTo(sealBefore.chunk().getSha256());
  }

  @Test
  void shouldRequestSealOnlyOnRecoveredIfEverythingIsPublished() {
    // given
    fillOpenChunk();
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(4));
    harness.published.clear();

    // when
    harness.recover();

    // then
    assertThat(harness.published).hasSize(1);
    assertThat(harness.published.getFirst().seq()).isEqualTo(-1);
    assertThat(harness.published.getFirst().rootJson()).isNull();
    assertThat(harness.published.getFirst().seal()).isNotNull();
  }

  @Test
  void shouldPublishNothingOnRecoveredIfNothingIsDue() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(1));
    harness.published.clear();

    // when
    harness.recover();

    // then
    assertThat(harness.published).isEmpty();
  }

  @Test
  void shouldPublishNothingOnRecoveredWithoutTimeline() {
    // when
    harness.recover();

    // then
    assertThat(harness.published).isEmpty();
    assertThat(harness.listener.calls).containsExactly("recovered 1");
  }

  @Test
  void shouldDeliverNewestArchivedSnapshotOnRecovered() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-old", 5)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("snap-new", 10)));
    harness.listener.newestSnapshotsOnRecovery.clear();

    // when
    harness.recover();

    // then
    assertThat(harness.listener.newestSnapshotsOnRecovery)
        .containsExactly(new RecordingListener.NewestSnapshot("snap-new", 10));
  }

  @Test
  void shouldDeliverNoArchivedSnapshotOnRecoveredWithoutSnapshots() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.listener.newestSnapshotsOnRecovery.clear();

    // when
    harness.recover();

    // then
    assertThat(harness.listener.newestSnapshotsOnRecovery)
        .containsExactly(new RecordingListener.NewestSnapshot("", -1));
  }

  // ---- helpers ----------------------------------------------------------------------------------

  /** Timeline start plus three single-entry objects: seq 1 to 4, a full chunk at seq 4. */
  private void fillOpenChunk() {
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(2)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(3)));
  }

  private static String sha256Hex(final byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (final NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private SealRequest firstSealRequest() {
    return harness.published.stream()
        .map(PublishRequest::seal)
        .filter(seal -> seal != null)
        .findFirst()
        .orElseThrow();
  }

  private void assertRejected(
      final LogArchiveIntent intent, final LogArchiveRecord command, final RejectionType type) {
    final var before = harness.fingerprint();
    final int events = harness.events.size();
    final int rejections = harness.rejections.size();
    final int published = harness.published.size();

    harness.process(intent, command);

    assertThat(harness.events).as("no event").hasSize(events);
    assertThat(harness.rejections).as("one rejection").hasSize(rejections + 1);
    assertThat(harness.rejections.getLast().intent()).isEqualTo(intent);
    assertThat(harness.rejections.getLast().rejection()).isEqualTo(type);
    assertThat(harness.fingerprint()).as("state unchanged").isEqualTo(before);
    if (intent != LogArchiveIntent.SEAL_INDEX) {
      assertThat(harness.published).as("nothing published").hasSize(published);
    }
  }
}
