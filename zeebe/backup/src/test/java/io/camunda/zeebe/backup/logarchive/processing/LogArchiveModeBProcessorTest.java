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
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.moveHold;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.object;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.releaseLog;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.snapshot;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.startTimeline;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.camunda.zeebe.backup.logarchive.model.TimelineFile;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mode per timeline and the restore hold (spec sections 25.1 and 25.2). */
final class LogArchiveModeBProcessorTest {

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

  // ---- mode per timeline
  // ------------------------------------------------------------------------

  @Test
  void shouldStartTimelineInClusterModeByDefault() {
    // when
    final var event = harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 11));

    // then
    assertThat(event.getCompactionMode()).isEqualTo("CLUSTER");
    final var meta = harness.state().meta();
    assertThat(meta.getCompactionMode()).isEqualTo("CLUSTER");
    assertThat(meta.isAuthority()).isFalse();
    assertThat(meta.getHoldIndex()).isEqualTo(11);
  }

  @Test
  void shouldStartTimelineInAuthorityModeWithHoldAtStartIndex() {
    // when
    final var event =
        harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 11, "AUTHORITY"));

    // then
    assertThat(event.getCompactionMode()).isEqualTo("AUTHORITY");
    final var meta = harness.state().meta();
    assertThat(meta.getCompactionMode()).isEqualTo("AUTHORITY");
    assertThat(meta.isAuthority()).isTrue();
    assertThat(meta.getHoldIndex()).isEqualTo(11);
    assertThat(harness.listener.calls).last().isEqualTo("timelineStarted " + TIMELINE);
  }

  @Test
  void shouldTakeModeOfTheNewTimelineWhenOneStartsAfterAnother() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1, "AUTHORITY"));

    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline("tl-1", 31));

    // then
    final var meta = harness.state().meta();
    assertThat(meta.getCompactionMode()).isEqualTo("CLUSTER");
    assertThat(meta.getHoldIndex()).isEqualTo(31);
  }

  @Test
  void shouldRejectStartTimelineWithUnknownMode() {
    // then
    assertRejected(
        LogArchiveIntent.START_TIMELINE,
        startTimeline(TIMELINE, 1, "SOMEONE"),
        RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldOmitModeFromCommitEntryAndTimelineFileOfClusterTimeline() {
    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));

    // then
    final var request = harness.published.getLast();
    assertThat(new String(request.commitEntryJson())).doesNotContain("compactionMode");
    assertThat(new String(LogArchiveJson.write(request.timeline())))
        .doesNotContain("compactionMode");
    assertThat(new String(request.commitEntryJson())).doesNotContain("holdIndex");
  }

  @Test
  void shouldWriteModeIntoCommitEntryAndTimelineFileOfAuthorityTimeline() {
    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1, "AUTHORITY"));

    // then
    final var request = harness.published.getLast();
    final var entry = LogArchiveJson.read(request.commitEntryJson(), CommitEntry.class);
    assertThat(entry.type()).isEqualTo(CommitEntry.Type.TIMELINE_STARTED);
    assertThat(entry.compactionMode()).isEqualTo("AUTHORITY");
    assertThat(entry.isAuthority()).isTrue();
    final TimelineFile file = request.timeline();
    assertThat(file.compactionMode()).isEqualTo("AUTHORITY");
  }

  // ---- MOVE_HOLD
  // ---------------------------------------------------------------------------------

  @Test
  void shouldMoveHoldToAnArchivedSnapshot() {
    // given
    authorityTimelineWithSnapshots();

    // when
    final var event = harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"));

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.HOLD_MOVED);
    assertThat(event.getSeq()).isEqualTo(6);
    assertThat(event.getHoldIndex()).isEqualTo(10);
    assertThat(event.getHoldSnapshotId()).isEqualTo("s10");
    assertThat(event.getArchivedIndex()).isEqualTo(20);
    assertThat(event.getArchivedPosition()).isEqualTo(20);
    final var meta = harness.state().meta();
    assertThat(meta.getHoldIndex()).isEqualTo(10);
    assertThat(meta.getNextSeq()).isEqualTo(7);
    assertThat(harness.listener.calls).last().isEqualTo("holdMoved 10");
  }

  @Test
  void shouldMoveHoldRepeatedlyUpwards() {
    // given
    authorityTimelineWithSnapshots();
    harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"));

    // when
    harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(20, "s20"));

    // then
    assertThat(harness.state().meta().getHoldIndex()).isEqualTo(20);
    assertThat(harness.state().meta().getNextSeq()).isEqualTo(8);
  }

  @Test
  void shouldNotTouchObjectsWhenMovingHold() {
    // given
    authorityTimelineWithSnapshots();
    final int open = harness.state().openObjects().size();
    final int unmerged = harness.state().unmergedObjects().size();

    // when
    harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"));

    // then
    assertThat(harness.state().openObjects()).hasSize(open);
    assertThat(harness.state().unmergedObjects()).hasSize(unmerged);
    assertThat(harness.state().snapshots()).hasSize(2);
  }

  @Test
  void shouldWriteCommitEntryOfHoldMoved() {
    // given
    authorityTimelineWithSnapshots();

    // when
    harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"));

    // then
    final var request = harness.published.getLast();
    assertThat(request.seq()).isEqualTo(6);
    final var entry = LogArchiveJson.read(request.commitEntryJson(), CommitEntry.class);
    assertThat(entry.type()).isEqualTo(CommitEntry.Type.HOLD_MOVED);
    assertThat(entry.seq()).isEqualTo(6);
    assertThat(entry.holdIndex()).isEqualTo(10L);
    assertThat(entry.holdSnapshotId()).isEqualTo("s10");
    assertThat(entry.archivedIndex()).isEqualTo(20L);
    assertThat(entry.archivedPosition()).isEqualTo(20L);
    assertThat(entry.objects()).isEmpty();
    assertThat(entry.mergedIndex()).isNull();
    assertThat(entry.releasedIndex()).isNull();
    assertThat(harness.state().outboxAfter(5)).extracting("seq").containsExactly(6L);
  }

  @Test
  void shouldRejectMoveHoldOfAnotherTimeline() {
    // given
    authorityTimelineWithSnapshots();

    // then
    assertRejected(
        LogArchiveIntent.MOVE_HOLD,
        moveHold(10, "s10").setTimelineId("other"),
        RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMoveHoldBeforeATimelineWasStarted() {
    // then
    assertRejected(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMoveHoldOnClusterTimeline() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s10", 10)));

    // then
    assertRejected(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"), RejectionType.INVALID_STATE);
    assertThat(harness.listener.calls).last().isEqualTo("moveHoldRejected 1");
  }

  @Test
  void shouldRejectMoveHoldThatDoesNotMoveUp() {
    // given
    authorityTimelineWithSnapshots();
    harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"));

    // then
    assertRejected(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"), RejectionType.INVALID_STATE);
    assertThat(harness.listener.calls).last().isEqualTo("moveHoldRejected 10");
    assertRejected(LogArchiveIntent.MOVE_HOLD, moveHold(5, "s5"), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMoveHoldToTheStartIndexOfTheTimeline() {
    // given - the hold starts there, so moving to it is not moving up
    authorityTimelineWithSnapshots();
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s1", 1)));

    // then
    assertRejected(LogArchiveIntent.MOVE_HOLD, moveHold(1, "s1"), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMoveHoldWithoutArchivedSnapshotAtThatIndex() {
    // given
    authorityTimelineWithSnapshots();

    // then
    assertRejected(LogArchiveIntent.MOVE_HOLD, moveHold(15, "s15"), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectMoveHoldWithAnotherSnapshotIdThanTheOneAtThatIndex() {
    // given
    authorityTimelineWithSnapshots();

    // then
    assertRejected(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s20"), RejectionType.INVALID_STATE);
    assertRejected(LogArchiveIntent.MOVE_HOLD, moveHold(10, ""), RejectionType.INVALID_STATE);
  }

  // ---- MERGE_LOG and RELEASE_LOG in mode B
  // ------------------------------------------------------------

  @Test
  void shouldRejectMergeLogOnAuthorityTimeline() {
    // given
    authorityTimelineWithSnapshots();

    // then
    assertRejected(
        LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10)), RejectionType.INVALID_STATE);
    assertThat(harness.listener.calls).last().isEqualTo("mergeLogRejected 0");
  }

  @Test
  void shouldRejectReleaseLogOnAuthorityTimeline() {
    // given
    authorityTimelineWithSnapshots();

    // then
    assertRejected(LogArchiveIntent.RELEASE_LOG, releaseLog(10), RejectionType.INVALID_STATE);
    assertThat(harness.listener.calls).last().isEqualTo("releaseLogRejected 0");
  }

  // ---- root
  // --------------------------------------------------------------------------------------

  @Test
  void shouldWriteHoldAndModeIntoTheRootOfAnAuthorityTimeline() {
    // given
    authorityTimelineWithSnapshots();
    harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"));
    harness.published.clear();

    // when
    harness.recover();

    // then
    final var root = LogArchiveJson.read(harness.published.getLast().rootJson(), RootFile.class);
    assertThat(root.isAuthority()).isTrue();
    assertThat(root.compactionMode()).isEqualTo("AUTHORITY");
    assertThat(root.holdIndex()).isEqualTo(10L);
  }

  @Test
  void shouldOmitHoldAndModeFromTheRootOfAClusterTimeline() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.published.clear();

    // when
    harness.recover();

    // then
    final var json = new String(harness.published.getLast().rootJson());
    assertThat(json).doesNotContain("holdIndex").doesNotContain("compactionMode");
    final var root = LogArchiveJson.read(harness.published.getLast().rootJson(), RootFile.class);
    assertThat(root.isAuthority()).isFalse();
    assertThat(root.holdIndex()).isNull();
  }

  // ---- replay
  // ------------------------------------------------------------------------------------

  @Test
  void shouldReachIdenticalStateWhenReplayingHoldMoves() throws Exception {
    // given accepted and rejected hold moves
    authorityTimelineWithSnapshots();
    harness.process(LogArchiveIntent.MOVE_HOLD, moveHold(15, "s15"));
    harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"));
    harness.process(LogArchiveIntent.MOVE_HOLD, moveHold(10, "s10"));
    harness.process(LogArchiveIntent.MERGE_LOG, mergeLog(merged(1, 10)));
    harness.accept(LogArchiveIntent.MOVE_HOLD, moveHold(20, "s20"));

    // when
    try (final var replica = new LogArchiveHarness(replicaDirectory.toFile(), CONFIG)) {
      replica.replayLog(harness.log);

      // then
      assertThat(replica.fingerprint()).isEqualTo(harness.fingerprint());
      assertThat(replica.state().meta().getHoldIndex()).isEqualTo(20);
      assertThat(replica.state().meta().isAuthority()).isTrue();
      final var leaderOutbox = harness.state().outboxAfter(-1);
      final var replicaOutbox = replica.state().outboxAfter(-1);
      assertThat(replicaOutbox).hasSameSizeAs(leaderOutbox);
      for (int i = 0; i < leaderOutbox.size(); i++) {
        assertThat(replicaOutbox.get(i).commitEntryJson())
            .isEqualTo(leaderOutbox.get(i).commitEntryJson());
      }
      assertThat(replica.listener.calls).isEmpty();
      assertThat(replica.published).isEmpty();
    }
  }

  // ---- helpers
  // -----------------------------------------------------------------------------------

  /** AUTHORITY timeline from index 1, objects [1, 10] and [11, 20], snapshots at 10 and 20. */
  private void authorityTimelineWithSnapshots() {
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1, "AUTHORITY"));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(11, 20, 11, 20)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s10", 10)));
    harness.accept(LogArchiveIntent.ARCHIVE_SNAPSHOT, archiveSnapshot(snapshot("s20", 20)));
    // seq: timeline 1, objects 2 and 3, snapshots 4 and 5
  }

  private static LogObject merged(final long firstIndex, final long lastIndex) {
    return object(firstIndex, lastIndex, firstIndex, lastIndex)
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
