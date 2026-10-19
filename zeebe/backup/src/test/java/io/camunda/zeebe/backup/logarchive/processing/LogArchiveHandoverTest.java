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
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.confirmPublished;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.object;
import static io.camunda.zeebe.backup.logarchive.processing.LogArchiveHarness.startTimeline;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.HandoverJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LogArchiveHandoverTest {

  private static final LogArchiveProcessorConfig CONFIG = new LogArchiveProcessorConfig(1, 3, 100);
  private static final long HANDOVER_ID = 7;
  private static final long HANDOVER_POSITION = 15;

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

  // ---- accepting ------------------------------------------------------------------------------

  @Test
  void shouldCompleteHandoverAndTakeSeq() {
    // given
    archiveUpToAndConfirmHandover();
    final long nextSeq = harness.state().meta().getNextSeq();

    // when
    final var event = harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // then
    assertThat(harness.events.getLast().intent()).isEqualTo(LogArchiveIntent.HANDOVER_COMPLETED);
    assertThat(event.getTimelineId()).isEqualTo(TIMELINE);
    assertThat(event.getSeq()).isEqualTo(nextSeq);
    assertThat(event.getHandoverCheckpointId()).isEqualTo(HANDOVER_ID);
    assertThat(event.getHandoverPosition()).isEqualTo(HANDOVER_POSITION);
    assertThat(event.getArchivedPosition()).isEqualTo(20);

    final var meta = harness.state().meta();
    assertThat(meta.getHandoverCheckpointId()).isEqualTo(HANDOVER_ID);
    assertThat(meta.getHandoverPosition()).isEqualTo(HANDOVER_POSITION);
    assertThat(meta.getNextSeq()).isEqualTo(nextSeq + 1);
    assertThat(harness.state().outboxAfter(0)).extracting("seq").contains(nextSeq);
  }

  @Test
  void shouldRecordTheHandoverWhenTheCommandDoesNotKnowTheTimeline() {
    // given
    archiveUpToAndConfirmHandover();

    // when
    final var event =
        harness.accept(
            LogArchiveIntent.COMPLETE_HANDOVER,
            new LogArchiveRecord().setHandoverCheckpointId(HANDOVER_ID));

    // then
    assertThat(event.getTimelineId()).isEqualTo(TIMELINE);
  }

  @Test
  void shouldAcceptHandoverWhenArchivedPositionIsExactlyThePositionOfTheCheckpoint() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(HANDOVER_ID, 10, CheckpointType.HANDOVER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.confirmBackup(HANDOVER_ID, 10, CheckpointType.HANDOVER);

    // when
    final var event = harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // then
    assertThat(event.getHandoverPosition()).isEqualTo(10);
  }

  @Test
  void shouldWriteHandoverCommitEntryWithHandover() {
    // given
    archiveUpToAndConfirmHandover();

    // when
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // then
    final var request = harness.published.getLast();
    final var entry = LogArchiveJson.read(request.commitEntryJson(), CommitEntry.class);
    assertThat(entry.type()).isEqualTo(CommitEntry.Type.HANDOVER);
    assertThat(entry.seq()).isEqualTo(request.seq());
    assertThat(entry.handover()).isEqualTo(new HandoverJson(HANDOVER_ID, HANDOVER_POSITION));
    assertThat(entry.archivedPosition()).isEqualTo(20);
    assertThat(request.handover()).isEqualTo(new HandoverJson(HANDOVER_ID, HANDOVER_POSITION));
  }

  @Test
  void shouldWriteHandoverCommitEntryAsDeterministicJson() {
    // given
    archiveUpToAndConfirmHandover();

    // when
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // then
    final var json =
        new String(harness.published.getLast().commitEntryJson(), StandardCharsets.UTF_8);
    assertThat(json)
        .isEqualTo(
            "{\"formatVersion\":1,\"timelineId\":\"tl-0\",\"partitionId\":1,\"seq\":3,"
                + "\"type\":\"HANDOVER\",\"recordPosition\":"
                + harness.events.getLast().commandPosition()
                + ",\"recordTimestamp\":-1,\"archivedIndex\":20,\"archivedPosition\":20,"
                + "\"handover\":{\"checkpointId\":7,\"checkpointPosition\":15}}");
  }

  @Test
  void shouldAlwaysPublishRootWithHandover() {
    // given
    archiveUpToAndConfirmHandover();

    // when - the root cadence of this config would not attach a root to this seq
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // then
    final var request = harness.published.getLast();
    assertThat(request.rootJson()).isNotNull();
    final var root = LogArchiveJson.read(request.rootJson(), RootFile.class);
    assertThat(root.seq()).isEqualTo(request.seq());
    assertThat(root.handover()).isEqualTo(new HandoverJson(HANDOVER_ID, HANDOVER_POSITION));
    assertThat(new String(request.rootJson(), StandardCharsets.UTF_8))
        .endsWith(",\"handover\":{\"checkpointId\":7,\"checkpointPosition\":15}}");
  }

  @Test
  void shouldCarryHandoverInEveryLaterRoot() {
    // given
    archiveUpToAndConfirmHandover();
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // when
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(21, 30, 21, 30)));
    harness.recover();

    // then
    final var rootOnlyRequest = harness.published.getLast();
    final var root = LogArchiveJson.read(rootOnlyRequest.rootJson(), RootFile.class);
    assertThat(root.handover()).isEqualTo(new HandoverJson(HANDOVER_ID, HANDOVER_POSITION));
  }

  @Test
  void shouldNotWriteHandoverIntoRootsBeforeHandover() {
    // given
    archiveUpToAndConfirmHandover();

    // when
    harness.recover();

    // then
    final var root = LogArchiveJson.read(harness.published.getLast().rootJson(), RootFile.class);
    assertThat(root.handover()).isNull();
    assertThat(new String(harness.published.getLast().rootJson(), StandardCharsets.UTF_8))
        .doesNotContain("handover");
  }

  @Test
  void shouldNotifyNothingSpecialAndNotPublishWhenReplaying() throws Exception {
    // given
    archiveUpToAndConfirmHandover();
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // when
    try (final var replica = new LogArchiveHarness(replicaDirectory.toFile(), CONFIG)) {
      replica.replayLog(harness.log);

      // then
      assertThat(replica.published).isEmpty();
      assertThat(replica.fingerprint()).isEqualTo(harness.fingerprint());
      assertThat(replica.state().meta().getHandoverCheckpointId()).isEqualTo(HANDOVER_ID);
      assertThat(replica.state().outboxAfter(-1).getLast().commitEntryJson())
          .isEqualTo(harness.state().outboxAfter(-1).getLast().commitEntryJson());
    }
  }

  @Test
  void shouldRepublishHandoverOfOutboxOnRecovered() {
    // given
    archiveUpToAndConfirmHandover();
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());
    harness.published.clear();

    // when
    harness.recover();

    // then
    final var handoverRequests =
        harness.published.stream().filter(request -> request.handover() != null).toList();
    assertThat(handoverRequests).hasSize(1);
    assertThat(handoverRequests.getFirst().handover())
        .isEqualTo(new HandoverJson(HANDOVER_ID, HANDOVER_POSITION));
    assertThat(handoverRequests.getFirst().commitEntryJson()).isNotNull();
  }

  @Test
  void shouldNotRepublishHandoverOnceConfirmed() {
    // given
    archiveUpToAndConfirmHandover();
    final var event = harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());
    harness.accept(LogArchiveIntent.CONFIRM_PUBLISHED, confirmPublished(event.getSeq()));
    harness.published.clear();

    // when
    harness.recover();

    // then
    assertThat(harness.published).noneMatch(request -> request.handover() != null);
    assertThat(harness.state().meta().getHandoverCheckpointId()).isEqualTo(HANDOVER_ID);
  }

  @Test
  void shouldForgetHandoverWhenANewTimelineStarts() {
    // given
    archiveUpToAndConfirmHandover();
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // when
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline("tl-1", 100));

    // then
    final var meta = harness.state().meta();
    assertThat(meta.hasHandover()).isFalse();
    assertThat(meta.getHandoverCheckpointId()).isEqualTo(-1);
    assertThat(meta.getHandoverPosition()).isEqualTo(-1);
  }

  // ---- rejecting ------------------------------------------------------------------------------

  @Test
  void shouldRejectHandoverBeforeTimelineStarted() {
    // given
    harness.createCheckpoint(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);
    harness.confirmBackup(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);

    // then
    assertRejected(completeHandover(), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectHandoverWithoutCheckpointId() {
    // given
    archiveUpToAndConfirmHandover();

    // then
    assertRejected(new LogArchiveRecord(), RejectionType.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectHandoverOfCheckpointWithoutConfirmedBackup() {
    // given - created, but its backup is not confirmed
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 20, 1, 20)));

    // then
    assertRejected(completeHandover(), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectHandoverOfConfirmedBackupOfOtherType() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.SCHEDULED_BACKUP);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 20, 1, 20)));
    harness.confirmBackup(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.SCHEDULED_BACKUP);

    // then
    assertRejected(completeHandover(), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectHandoverOfOtherCheckpointThanTheConfirmedOne() {
    // given
    archiveUpToAndConfirmHandover();

    // then
    assertRejected(
        new LogArchiveRecord().setHandoverCheckpointId(HANDOVER_ID + 1),
        RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectHandoverBeforeArchiveReachedTheCheckpoint() {
    // given - F is 10, the checkpoint is at 15
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.confirmBackup(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);

    // then
    assertRejected(completeHandover(), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldAcceptHandoverOnceArchiveReachedTheCheckpointAfterRejection() {
    // given
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 10, 1, 10)));
    harness.confirmBackup(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);
    harness.process(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());
    assertThat(harness.rejections).hasSize(1);

    // when
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(11, 20, 11, 20)));
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // then
    assertThat(harness.state().meta().getHandoverCheckpointId()).isEqualTo(HANDOVER_ID);
  }

  @Test
  void shouldRejectRepeatedHandoverOfTheSameCheckpoint() {
    // given
    archiveUpToAndConfirmHandover();
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());

    // then
    assertRejected(completeHandover(), RejectionType.INVALID_STATE);
  }

  @Test
  void shouldRejectHandoverOfOtherCheckpointWhenAHandoverIsRecorded() {
    // given
    archiveUpToAndConfirmHandover();
    harness.accept(LogArchiveIntent.COMPLETE_HANDOVER, completeHandover());
    harness.createCheckpoint(HANDOVER_ID + 10, 18, CheckpointType.HANDOVER);
    harness.confirmBackup(HANDOVER_ID + 10, 18, CheckpointType.HANDOVER);

    // then
    assertRejected(
        new LogArchiveRecord().setHandoverCheckpointId(HANDOVER_ID + 10),
        RejectionType.INVALID_STATE);
  }

  // ---------------------------------------------------------------------------------------------

  /** F is 20, the handover checkpoint lies at 15 and its backup is confirmed. */
  private void archiveUpToAndConfirmHandover() {
    harness.accept(LogArchiveIntent.START_TIMELINE, startTimeline(TIMELINE, 1));
    harness.createCheckpoint(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);
    harness.accept(LogArchiveIntent.ARCHIVE_LOG, archiveLog(object(1, 20, 1, 20)));
    harness.confirmBackup(HANDOVER_ID, HANDOVER_POSITION, CheckpointType.HANDOVER);
  }

  private static LogArchiveRecord completeHandover() {
    return new LogArchiveRecord().setTimelineId(TIMELINE).setHandoverCheckpointId(HANDOVER_ID);
  }

  private void assertRejected(final LogArchiveRecord command, final RejectionType type) {
    final var before = harness.fingerprint();
    final int events = harness.events.size();
    final int rejections = harness.rejections.size();
    final int published = harness.published.size();

    harness.process(LogArchiveIntent.COMPLETE_HANDOVER, command);

    assertThat(harness.events).as("no event").hasSize(events);
    assertThat(harness.rejections).as("one rejection").hasSize(rejections + 1);
    assertThat(harness.rejections.getLast().intent()).isEqualTo(LogArchiveIntent.COMPLETE_HANDOVER);
    assertThat(harness.rejections.getLast().rejection()).isEqualTo(type);
    assertThat(harness.fingerprint()).as("state unchanged").isEqualTo(before);
    assertThat(harness.published).as("nothing published").hasSize(published);
  }
}
