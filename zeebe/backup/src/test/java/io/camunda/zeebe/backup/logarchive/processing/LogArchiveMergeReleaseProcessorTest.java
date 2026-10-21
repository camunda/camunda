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
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.mergeLog;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.object;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.releaseLog;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.snapshot;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.startTimeline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.UnmergedObject;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The rules of {@code MERGE_LOG} and {@code RELEASE_LOG} (spec sections 20.1 and 21.2). */
final class LogArchiveMergeReleaseProcessorTest {

  private static final LogArchiveProcessorConfig CONFIG = new LogArchiveProcessorConfig(1, 100, 4);

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

  // ---- LOG_ARCHIVED
  // ------------------------------------------------------------------------------

  @Test
  void shouldTrackArchivedObjectsAsUnmerged() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // when
    harness.accept(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10), object(11, 20, -1, -1)));

    // then
    assertThat(harness.state().unmergedObjects())
        .containsExactly(new UnmergedObject(1, 10, 10), new UnmergedObject(11, 20, -1));
    assertThat(harness.state().openObjects()).hasSize(2);
  }

  @Test
  void shouldStartTimelineWithMergedAndReleasedIndexBeforeStartIndex() {
    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 501));

    // then
    final var meta = harness.state().meta();
    assertThat(meta.getMergedIndex()).isEqualTo(500);
    assertThat(meta.getMergedPosition()).isEqualTo(-1);
    assertThat(meta.getReleasedIndex()).isEqualTo(500);
    assertThat(meta.getReleasedPosition()).isEqualTo(-1);
  }

  @Test
  void shouldResetMergeAndReleaseStateWhenANewTimelineStarts() {
    // given
    archiveThreeObjects();
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s", 25)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(11, 20, 11, 20)));
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(10));

    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline("tl-1", 31));

    // then
    final var state = harness.state();
    final var meta = state.meta();
    assertThat(meta.getMergedIndex()).isEqualTo(30);
    assertThat(meta.getMergedPosition()).isEqualTo(-1);
    assertThat(meta.getReleasedIndex()).isEqualTo(30);
    assertThat(meta.getReleasedPosition()).isEqualTo(-1);
    assertThat(state.unmergedObjects()).isEmpty();
    assertThat(state.mergedObjects()).isEmpty();
  }

  // ---- MERGE_LOG
  // ---------------------------------------------------------------------------------

  @Test
  void shouldMergeARunOfObjectsAtAnObjectBoundary() {
    // given
    archiveThreeObjects();

    // when
    final var event = harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 20, 1, 20)));

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.LOG_MERGED);
    assertThat(event.getSeq()).isEqualTo(5);
    assertThat(event.getMergedIndex()).isEqualTo(20);
    assertThat(event.getMergedPosition()).isEqualTo(20);
    assertThat(event.getArchivedIndex()).isEqualTo(30);
    assertThat(event.getArchivedPosition()).isEqualTo(30);
    assertThat(event.getObjects()).extracting("lastIndex").containsExactly(20L);

    final var state = harness.state();
    assertThat(state.unmergedObjects()).containsExactly(new UnmergedObject(21, 30, 30));
    assertThat(state.mergedObjects())
        .extracting("firstIndex", "lastIndex")
        .containsExactly(tuple(1L, 20L));
    final var meta = state.meta();
    assertThat(meta.getMergedIndex()).isEqualTo(20);
    assertThat(meta.getMergedPosition()).isEqualTo(20);
    assertThat(meta.getNextSeq()).isEqualTo(6);
    assertThat(meta.getArchivedIndex()).isEqualTo(30);
    assertThat(harness.listener.calls).last().isEqualTo("logMerged 20 20");
  }

  @Test
  void shouldKeepArchivedObjectsOpenWhenTheyAreMerged() {
    // given
    archiveThreeObjects();

    // when
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));

    // then
    assertThat(harness.state().openObjects()).hasSize(3);
  }

  @Test
  void shouldMergeARunOfOneObjectWithTheKeyOfTheArchivedObject() {
    // given
    archiveThreeObjects();
    final var archivedKey = object(1, 10, 1, 10).getKey();

    // when
    final var event = harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));

    // then
    assertThat(event.getObjects().getFirst().getKey()).isEqualTo(archivedKey);
    assertThat(harness.state().mergedObjects()).extracting("key").containsExactly(archivedKey);
    assertThat(harness.state().meta().getMergedIndex()).isEqualTo(10);
  }

  @Test
  void shouldMergeConsecutiveRuns() {
    // given
    archiveThreeObjects();
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));

    // when
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(11, 30, 11, 30)));

    // then
    final var state = harness.state();
    assertThat(state.unmergedObjects()).isEmpty();
    assertThat(state.mergedObjects()).extracting("lastIndex").containsExactly(10L, 30L);
    assertThat(state.meta().getMergedIndex()).isEqualTo(30);
    assertThat(state.meta().getMergedPosition()).isEqualTo(30);
  }

  @Test
  void shouldNotMoveMergedPositionWhenTheMergedObjectHasNoApplicationEntries() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, -1, -1)));

    // when
    final var event = harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, -1, -1)));

    // then
    assertThat(event.getMergedIndex()).isEqualTo(10);
    assertThat(event.getMergedPosition()).isEqualTo(-1);
    assertThat(harness.state().meta().getMergedPosition()).isEqualTo(-1);
  }

  @Test
  void shouldKeepMergedPositionWhenALaterMergedObjectHasNoApplicationEntries() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(
        LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10), object(11, 20, -1, -1)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));

    // when
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(11, 20, -1, -1)));

    // then
    assertThat(harness.state().meta().getMergedIndex()).isEqualTo(20);
    assertThat(harness.state().meta().getMergedPosition()).isEqualTo(10);
  }

  @Test
  void shouldWriteCommitEntryOfLogMerged() {
    // given
    archiveThreeObjects();

    // when
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 20, 1, 20)));

    // then
    final var request = harness.published.getLast();
    assertThat(request.seq()).isEqualTo(5);
    final var entry = LogArchiveJson.read(request.commitEntryJson(), CommitEntry.class);
    assertThat(entry.type()).isEqualTo(CommitEntry.Type.LOG_MERGED);
    assertThat(entry.seq()).isEqualTo(5);
    assertThat(entry.mergedIndex()).isEqualTo(20L);
    assertThat(entry.mergedPosition()).isEqualTo(20L);
    assertThat(entry.archivedIndex()).isEqualTo(30L);
    assertThat(entry.archivedPosition()).isEqualTo(30L);
    assertThat(entry.objects()).hasSize(1);
    assertThat(entry.objects().getFirst().lastRecordTimestamp()).isEqualTo(1_700_000_000_000L);
    assertThat(entry.checkpoints()).isEmpty();
    assertThat(entry.releasedIndex()).isNull();
    assertThat(entry.releasedPosition()).isNull();
    assertThat(harness.state().outboxAfter(4)).extracting("seq").containsExactly(5L);
  }

  @Test
  void shouldRejectMergeLogOfAnotherTimeline() {
    // given
    archiveThreeObjects();
    final var command = mergeLog(merged(1, 10, 1, 10)).setTimelineId("other");

    // then
    assertRejected(LogArchiveIntent.MERGE_LOG, command, RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMergeLogBeforeATimelineWasStarted() {
    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMergeLogWithoutObject() {
    // given
    archiveThreeObjects();

    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG,
        new LogArchiveRecord().setTimelineId(TIMELINE),
        RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectMergeLogWithMoreThanOneObject() {
    // given
    archiveThreeObjects();
    final var command = mergeLog(merged(1, 10, 1, 10)).addObject(merged(11, 20, 11, 20));

    // then
    assertRejected(LogArchiveIntent.MERGE_LOG, command, RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectMergeLogWithInconsistentEntryCount() {
    // given
    archiveThreeObjects();
    final var command = mergeLog(merged(1, 10, 1, 10).setEntryCount(9));

    // then
    assertRejected(LogArchiveIntent.MERGE_LOG, command, RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectMergeLogThatDoesNotContinueAtMergedIndex() {
    // given
    archiveThreeObjects();

    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(11, 20, 11, 20)), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectStaleMergeLogAfterAnotherMergeMovedMergedIndex() {
    // given - a new leader merged [1, 20] while the old leader proposed [1, 10]
    archiveThreeObjects();
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 20, 1, 20)));

    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)), RejectionType.INVALID_STATE);
    assertThat(harness.listener.calls).last().isEqualTo("mergeLogRejected 20");
  }

  @Test
  void shouldRejectMergeLogBeyondArchivedIndex() {
    // given
    archiveThreeObjects();

    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 40, 1, 40)), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMergeLogThatEndsInsideAnArchivedObject() {
    // given
    archiveThreeObjects();

    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 15, 1, 15)), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMergeLogWithAnotherLastPositionThanTheRunsObjects() {
    // given
    archiveThreeObjects();

    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 20, 1, 19)), RejectionType.INVALID_STATE);
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 20, 1, -1)), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMergeLogWithApplicationPositionWhenTheRunHoldsNone() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, -1, -1)));

    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)), RejectionType.INVALID_STATE);
  }

  // ---- RELEASE_LOG
  // -------------------------------------------------------------------------------

  @Test
  void shouldReleaseUpToTheLastIndexOfAMergedObject() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);

    // when
    final var event = harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(10));

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.LOG_RELEASED);
    assertThat(event.getSeq()).isEqualTo(8);
    assertThat(event.getReleasedIndex()).isEqualTo(10);
    assertThat(event.getReleasedPosition()).isEqualTo(10);
    assertThat(event.getArchivedIndex()).isEqualTo(30);

    final var state = harness.state();
    assertThat(state.mergedObjects()).extracting("lastIndex").containsExactly(20L);
    final var meta = state.meta();
    assertThat(meta.getReleasedIndex()).isEqualTo(10);
    assertThat(meta.getReleasedPosition()).isEqualTo(10);
    assertThat(meta.getMergedIndex()).isEqualTo(20);
    assertThat(meta.getNextSeq()).isEqualTo(9);
    assertThat(state.snapshots()).hasSize(1);
    assertThat(state.openObjects()).hasSize(3);
    assertThat(harness.listener.calls).last().isEqualTo("logReleased 10");
  }

  @Test
  void shouldReleaseAllMergedObjectsUpToTheProposedIndex() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);

    // when
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(20));

    // then
    assertThat(harness.state().mergedObjects()).isEmpty();
    assertThat(harness.state().meta().getReleasedIndex()).isEqualTo(20);
    assertThat(harness.state().meta().getReleasedPosition()).isEqualTo(20);
  }

  @Test
  void shouldKeepReleasedPositionWhenReleasedObjectsHoldNoApplicationEntries() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(
        LogArchiveIntent.ARCHIVE_LOG,
        archiveLog(object(1, 10, 1, 10), object(11, 20, -1, -1), object(21, 30, 21, 30)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s", 25)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(11, 20, -1, -1)));
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(10));

    // when
    final var event = harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(20));

    // then
    assertThat(event.getReleasedIndex()).isEqualTo(20);
    assertThat(event.getReleasedPosition()).isEqualTo(10);
    assertThat(harness.state().meta().getReleasedPosition()).isEqualTo(10);
  }

  @Test
  void shouldWriteCommitEntryOfLogReleased() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);

    // when
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(10));

    // then
    final var request = harness.published.getLast();
    assertThat(request.seq()).isEqualTo(8);
    final var entry = LogArchiveJson.read(request.commitEntryJson(), CommitEntry.class);
    assertThat(entry.type()).isEqualTo(CommitEntry.Type.LOG_RELEASED);
    assertThat(entry.releasedIndex()).isEqualTo(10L);
    assertThat(entry.releasedPosition()).isEqualTo(10L);
    assertThat(entry.archivedIndex()).isEqualTo(30L);
    assertThat(entry.mergedIndex()).isNull();
    assertThat(entry.mergedPosition()).isNull();
    assertThat(entry.objects()).isEmpty();
    assertThat(harness.state().outboxAfter(7)).extracting("seq").containsExactly(8L);
  }

  @Test
  void shouldRejectReleaseLogOfAnotherTimeline() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);

    // then
    assertRejected(
        LogArchiveIntent.RELEASE_LOG,
        releaseLog(10).setTimelineId("other"),
        RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectReleaseLogBeforeATimelineWasStarted() {
    // then
    assertRejected(LogArchiveIntent.RELEASE_LOG, releaseLog(10), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectReleaseLogThatDoesNotAdvanceReleasedIndex() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(10));

    // then
    assertRejected(LogArchiveIntent.RELEASE_LOG, releaseLog(10), RejectionType.INVALID_STATE);
    assertRejected(LogArchiveIntent.RELEASE_LOG, releaseLog(0), RejectionType.INVALID_STATE);
    assertThat(harness.listener.calls).last().isEqualTo("releaseLogRejected 10");
  }

  @Test
  void shouldRejectReleaseLogThatIsNotTheLastIndexOfAMergedObject() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);

    // then
    assertRejected(LogArchiveIntent.RELEASE_LOG, releaseLog(15), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectReleaseLogOfAnObjectThatIsNotMergedYet() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);

    // then
    assertRejected(LogArchiveIntent.RELEASE_LOG, releaseLog(30), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectReleaseLogWithoutAnArchivedSnapshotAboveTheReleasedIndex() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(15);

    // then
    assertRejected(LogArchiveIntent.RELEASE_LOG, releaseLog(20), RejectionType.INVALID_STATE);
    assertThat(harness.state().snapshots()).hasSize(1);
  }

  @Test
  void shouldRejectReleaseLogWhenTheOnlySnapshotIsAtTheReleasedIndex() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(10);

    // then
    assertRejected(LogArchiveIntent.RELEASE_LOG, releaseLog(10), RejectionType.INVALID_STATE);
  }

  // ---- root
  // --------------------------------------------------------------------------------------

  @Test
  void shouldWriteMergedAndReleasedStateIntoTheRoot() {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(10));
    harness.published.clear();

    // when
    harness.recover();

    // then
    final var root = LogArchiveJson.read(harness.published.getLast().rootJson(), RootFile.class);
    assertThat(root.seq()).isEqualTo(8);
    assertThat(root.mergedIndex()).isEqualTo(20);
    assertThat(root.mergedPosition()).isEqualTo(20);
    assertThat(root.releasedIndex()).isEqualTo(10);
    assertThat(root.releasedPosition()).isEqualTo(10);
    assertThat(root.mergedObjects()).extracting("lastIndex").containsExactly(20L);
    assertThat(root.openObjects()).hasSize(3);
  }

  @Test
  void shouldWriteInitialMergedAndReleasedIndexIntoTheRootOfAFreshTimeline() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 501));
    harness.published.clear();

    // when
    harness.recover();

    // then
    final var root = LogArchiveJson.read(harness.published.getLast().rootJson(), RootFile.class);
    assertThat(root.mergedIndex()).isEqualTo(500);
    assertThat(root.mergedPosition()).isEqualTo(-1);
    assertThat(root.releasedIndex()).isEqualTo(500);
    assertThat(root.releasedPosition()).isEqualTo(-1);
    assertThat(root.mergedObjects()).isEmpty();
  }

  // ---- replay
  // ------------------------------------------------------------------------------------

  @Test
  void shouldReachIdenticalStateWhenReplayingMergesAndReleases() throws Exception {
    // given a sequence with accepted and rejected merges and releases
    archiveThreeObjects();
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s", 25)));
    harness.process(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 15, 1, 15)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));
    harness.process(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(11, 20, 11, 20)));
    harness.process(LogArchiveIntent.RELEASE_LOG, releaseLog(15));
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(10));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(31, 40, 31, 40)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(21, 40, 21, 40)));
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(20));

    // when
    try (final var replica = new LogArchiveHarness(replicaDirectory.toFile(), CONFIG)) {
      replica.replayLog(harness.log);

      // then
      assertThat(replica.fingerprint()).isEqualTo(harness.fingerprint());
      assertThat(replica.state().meta().getMergedIndex()).isEqualTo(40);
      assertThat(replica.state().meta().getReleasedIndex()).isEqualTo(20);
      final var leaderOutbox = harness.state().outboxAfter(-1);
      final var replicaOutbox = replica.state().outboxAfter(-1);
      assertThat(replicaOutbox).hasSameSizeAs(leaderOutbox);
      for (int i = 0; i < leaderOutbox.size(); i++) {
        assertThat(replicaOutbox.get(i).commitEntryJson())
            .isEqualTo(leaderOutbox.get(i).commitEntryJson());
      }
    }
  }

  @Test
  void shouldNotNotifyWhenReplayingMergesAndReleases() throws Exception {
    // given
    mergeTwoRunsAndArchiveSnapshotAt(25);
    harness.accept(LogArchiveIntent.RELEASE_LOG, releaseLog(10));

    // when
    try (final var replica = new LogArchiveHarness(replicaDirectory.toFile(), CONFIG)) {
      replica.replayLog(harness.log);

      // then
      assertThat(replica.listener.calls).isEmpty();
      assertThat(replica.published).isEmpty();
    }
  }

  // ---- helpers
  // -----------------------------------------------------------------------------------

  /** Timeline start (seq 1) and three objects [1, 10], [11, 20], [21, 30]: seq 2 to 4. */
  private void archiveThreeObjects() {
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(11, 20, 11, 20)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(21, 30, 21, 30)));
  }

  /** Three objects, a snapshot (seq 5), and merges of [1, 10] and [11, 20] (seq 6 and 7). */
  private void mergeTwoRunsAndArchiveSnapshotAt(final long snapshotIndex) {
    archiveThreeObjects();
    harness.accept(
        LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s", snapshotIndex)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.MERGE_LOG, mergeLog(merged(11, 20, 11, 20)));
  }

  private static LogObject merged(
      final long firstIndex,
      final long lastIndex,
      final long firstPosition,
      final long lastPosition) {
    return object(firstIndex, lastIndex, firstPosition, lastPosition)
        .setLastRecordTimestamp(1_700_000_000_000L);
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
    assertThat(harness.published).as("nothing published").hasSize(published);
  }
}
