/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.logarchive.leader.LogArchiveHoldMover.HoldState;
import io.camunda.zeebe.backup.logarchive.model.CompactionModes;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class LogArchiveHoldMoverTest {

  private static final int PARTITION = 1;
  private static final String TIMELINE = "tl-0";
  private static final Duration WINDOW = Duration.ofHours(1);
  private static final Duration INTERVAL = Duration.ofSeconds(10);
  private static final long HOUR = Duration.ofHours(1).toMillis();

  @RegisterExtension
  final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  private final FakeWriter writer = new FakeWriter();
  private LogArchiveMeta meta = meta(CompactionModes.AUTHORITY, 1);
  private List<CoveredCheckpoint> backups = List.of();
  private List<ArchivedSnapshot> snapshots = List.of();
  private LogArchiveHoldMover holdMover;

  @BeforeEach
  void setUp() {
    holdMover = newHoldMover(WINDOW);
  }

  private LogArchiveHoldMover newHoldMover(final Duration window) {
    final var mover =
        new LogArchiveHoldMover(
            PARTITION, writer, () -> new HoldState(meta, backups, snapshots), window, INTERVAL);
    scheduler.submitActor(mover);
    scheduler.workUntilDone();
    return mover;
  }

  @Test
  void shouldMoveTheHoldToTheSnapshotOfTheOldestBackupInsideTheWindow() {
    // given
    backups = List.of(backup(1, 10, -3 * HOUR), backup(2, 20, -HOUR / 2), backup(3, 30, -10));
    snapshots = List.of(snapshot(40, 5), snapshot(80, 15), snapshot(120, 25));

    // when - checkpoint 2 is protected, its snapshot is the one at index 80
    recoverAndTick();

    // then
    assertThat(writer.moves()).containsExactly("80:80-15");
  }

  @Test
  void shouldProtectTheNewestBackupWhenNoneIsInsideTheWindow() {
    // given
    backups = List.of(backup(1, 10, -5 * HOUR), backup(2, 20, -3 * HOUR));
    snapshots = List.of(snapshot(40, 5), snapshot(80, 15));

    // when
    recoverAndTick();

    // then
    assertThat(writer.moves()).containsExactly("80:80-15");
  }

  @Test
  void shouldNeverMoveTheHoldDown() {
    // given - the floor snapshot is at 40, but the hold is already at 80
    meta = meta(CompactionModes.AUTHORITY, 80);
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5), snapshot(80, 15));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldNotMoveTheHoldToItself() {
    // given
    meta = meta(CompactionModes.AUTHORITY, 40);
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldMoveNothingWithoutBackupsOrSnapshots() {
    // given
    snapshots = List.of(snapshot(40, 5));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldStayIdleOnAClusterTimeline() {
    // given
    meta = meta(CompactionModes.CLUSTER, 1);
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldStayIdleWhenTheBucketIsAhead() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));

    // when
    holdMover.onRecoveryChecked(meta, true);
    scheduler.workUntilDone();
    tick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldStayIdleBeforeRecoveryWasChecked() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));

    // when
    tick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldMoveAfterASnapshotWasArchived() {
    // given
    backups = List.of(backup(1, 10, -10));
    recoverAndTick();
    assertThat(writer.entries).isEmpty();

    // when
    snapshots = List.of(snapshot(40, 5));
    holdMover.onSnapshotArchived("40-5");
    scheduler.workUntilDone();

    // then
    assertThat(writer.moves()).containsExactly("40:40-5");
  }

  @Test
  void shouldWaitForTheEventBeforeWritingAgain() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));
    recoverAndTick();

    // when
    tick();

    // then
    assertThat(writer.entries).hasSize(1);
  }

  @Test
  void shouldTryAgainAfterARejection() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));
    recoverAndTick();

    // when
    holdMover.onMoveHoldRejected(40);
    scheduler.workUntilDone();
    tick();

    // then
    assertThat(writer.moves()).containsExactly("40:40-5", "40:40-5");
  }

  @Test
  void shouldContinueAfterTheHoldMoved() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));
    recoverAndTick();

    // when - the hold moved, and a newer snapshot below the checkpoint is archived
    meta = meta(CompactionModes.AUTHORITY, 40);
    snapshots = List.of(snapshot(40, 5), snapshot(80, 8));
    holdMover.onHoldMoved(40);
    scheduler.workUntilDone();
    holdMover.onSnapshotArchived("80-8");
    scheduler.workUntilDone();

    // then
    assertThat(writer.moves()).containsExactly("40:40-5", "80:80-8");
  }

  @Test
  void shouldReevaluateTheModeOnATimelineSwitch() {
    // given - a cluster timeline is running
    meta = meta(CompactionModes.CLUSTER, 1);
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));
    recoverAndTick();
    assertThat(writer.entries).isEmpty();

    // when - the next timeline is an authority one
    meta = meta(CompactionModes.AUTHORITY, 1);
    holdMover.onTimelineStarted("tl-1");
    scheduler.workUntilDone();

    // then
    assertThat(writer.moves()).containsExactly("40:40-5");
  }

  private void recoverAndTick() {
    holdMover.onRecoveryChecked(meta, false);
    scheduler.workUntilDone();
    tick();
  }

  private void tick() {
    scheduler.updateClock(INTERVAL);
    scheduler.workUntilDone();
  }

  private static LogArchiveMeta meta(final String mode, final long holdIndex) {
    return new LogArchiveMeta()
        .setTimelineId(TIMELINE)
        .setCompactionMode(mode)
        .setHoldIndex(holdIndex);
  }

  private CoveredCheckpoint backup(final long id, final long position, final long age) {
    return new CoveredCheckpoint()
        .setCheckpointId(id)
        .setCheckpointPosition(position)
        .setCheckpointType(CheckpointType.SCHEDULED_BACKUP)
        .setCheckpointTimestamp(scheduler.getClock().getTimeMillis() + age);
  }

  private static ArchivedSnapshot snapshot(final long index, final long position) {
    return new ArchivedSnapshot()
        .setId(index + "-" + position)
        .setIndex(index)
        .setProcessedPosition(position)
        .setLastFollowupEventPosition(position)
        .setMaxExportedPosition(position);
  }

  private static final class FakeWriter implements LogStreamWriter {
    final List<LogAppendEntry> entries = new ArrayList<>();

    /** The moves as {@code holdIndex:holdSnapshotId}. */
    List<String> moves() {
      return entries.stream()
          .map(
              entry -> {
                assertThat(entry.recordMetadata().getValueType()).isEqualTo(ValueType.LOG_ARCHIVE);
                assertThat(entry.recordMetadata().getIntent())
                    .isEqualTo(LogArchiveIntent.MOVE_HOLD);
                final var record = (LogArchiveRecord) entry.recordValue();
                return record.getHoldIndex() + ":" + record.getHoldSnapshotId();
              })
          .toList();
    }

    @Override
    public Either<WriteFailure, Long> tryWrite(
        final WriteContext context,
        final List<LogAppendEntry> appendEntries,
        final long sourcePosition) {
      entries.addAll(appendEntries);
      return Either.right(1L);
    }
  }
}
