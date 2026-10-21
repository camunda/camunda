/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.logarchive.leader.LogArchiveReleaser.ReleaseState;
import io.camunda.zeebe.backup.logarchive.model.CompactionModes;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
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

final class LogArchiveReleaserTest {

  private static final int PARTITION = 1;
  private static final String TIMELINE = "tl-0";
  private static final Duration WINDOW = Duration.ofHours(1);
  private static final Duration INTERVAL = Duration.ofSeconds(10);
  private static final long HOUR = Duration.ofHours(1).toMillis();

  @RegisterExtension
  final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  private final FakeWriter writer = new FakeWriter();
  private LogArchiveMeta meta = meta(0, 0);
  private List<CoveredCheckpoint> backups = List.of();
  private List<ArchivedSnapshot> snapshots = List.of();
  private List<LogObject> merged = List.of();
  private long cleanedIndex = Long.MAX_VALUE;
  private LogArchiveReleaser releaser;

  @BeforeEach
  void setUp() {
    releaser =
        new LogArchiveReleaser(
            PARTITION,
            writer,
            () -> new ReleaseState(meta, backups, snapshots, merged),
            () -> cleanedIndex,
            WINDOW,
            INTERVAL);
    scheduler.submitActor(releaser);
    scheduler.workUntilDone();
  }

  @Test
  void shouldReleaseBelowTheSnapshotOfTheOldestBackupInsideTheWindow() {
    // given
    backups = List.of(backup(1, 10, -3 * HOUR), backup(2, 20, -HOUR / 2), backup(3, 30, -10));
    snapshots = List.of(snapshot(40, 5), snapshot(80, 15), snapshot(120, 25));
    merged = List.of(merged(1, 50), merged(51, 90), merged(91, 130));

    // when - checkpoint 2 is protected, its snapshot is the one at index 80 (positions below 20)
    recoverAndTick();

    // then
    assertThat(writer.released()).containsExactly(50L);
  }

  @Test
  void shouldNotReleasePastTheCleanedIndex() {
    // given - objects up to 90 end below the floor snapshot, but only up to 50 are cleaned
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(120, 5));
    merged = List.of(merged(1, 50), merged(51, 90));
    cleanedIndex = 70;

    // when
    recoverAndTick();

    // then
    assertThat(writer.released()).containsExactly(50L);
  }

  @Test
  void shouldReleaseNothingWhenTheCleanedIndexIsNotAboveTheReleasedIndex() {
    // given
    meta = meta(0, 0);
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(120, 5));
    merged = List.of(merged(1, 50));
    cleanedIndex = 49;

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldProtectTheNewestBackupWhenNoneIsInsideTheWindow() {
    // given
    backups = List.of(backup(1, 10, -5 * HOUR), backup(2, 20, -3 * HOUR));
    snapshots = List.of(snapshot(40, 5), snapshot(80, 15));
    merged = List.of(merged(1, 50), merged(51, 90));

    // when
    recoverAndTick();

    // then - checkpoint 2 is protected; its snapshot is at index 80
    assertThat(writer.released()).containsExactly(50L);
  }

  @Test
  void shouldReleaseNothingWithoutBackups() {
    // given
    snapshots = List.of(snapshot(80, 15));
    merged = List.of(merged(1, 50));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldReleaseNothingWhenNoMergedObjectEndsBelowTheFloorSnapshot() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(40, 5));
    merged = List.of(merged(1, 60));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldReleaseNothingWhenRIsNotAboveTheReleasedIndex() {
    // given
    meta = meta(50, 90);
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(80, 5));
    merged = List.of(merged(51, 90));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldStayIdleForStateWrittenBeforeMergesExisted() {
    // given - only START_TIMELINE initialises these, so older state has -1
    meta = meta(-1, -1);
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(80, 5));
    merged = List.of(merged(1, 50));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldStayIdleOnAnAuthorityTimeline() {
    // given
    meta = meta(0, 0).setCompactionMode(CompactionModes.AUTHORITY);
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(80, 5));
    merged = List.of(merged(1, 50));

    // when
    recoverAndTick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldWaitForTheEventBeforeWritingAgain() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(80, 5));
    merged = List.of(merged(1, 50), merged(51, 70));
    recoverAndTick();
    assertThat(writer.released()).containsExactly(70L);

    // when - the interval passes while the command is in flight
    tick();

    // then
    assertThat(writer.entries).hasSize(1);
  }

  @Test
  void shouldTryAgainAfterARejection() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(80, 5));
    merged = List.of(merged(1, 50));
    recoverAndTick();

    // when
    releaser.onReleaseLogRejected(0);
    scheduler.workUntilDone();
    tick();

    // then
    assertThat(writer.released()).containsExactly(50L, 50L);
  }

  @Test
  void shouldContinueAfterTheReleaseWasApplied() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(80, 5));
    merged = List.of(merged(1, 50));
    recoverAndTick();

    // when - the state moves on and a new object is merged
    meta = meta(50, 50);
    merged = List.of(merged(51, 70));
    releaser.onLogReleased(50);
    scheduler.workUntilDone();
    releaser.onLogMerged(70, 69);
    scheduler.workUntilDone();

    // then
    assertThat(writer.released()).containsExactly(50L, 70L);
  }

  @Test
  void shouldNotReleaseBeforeRecoveryWasChecked() {
    // given
    backups = List.of(backup(1, 10, -10));
    snapshots = List.of(snapshot(80, 5));
    merged = List.of(merged(1, 50));

    // when
    tick();

    // then
    assertThat(writer.entries).isEmpty();
  }

  @Test
  void shouldUseTheActorClockForTheWindow() {
    // given - a backup taken at 0 is inside the window now, but not after the window passed
    backups = List.of(backup(1, 10, 0), backup(2, 20, 0));
    snapshots = List.of(snapshot(40, 5), snapshot(80, 15));
    merged = List.of(merged(1, 50));
    recoverAndTick();
    final var insideWindow = writer.released();

    // when - both are outside the window later, so only the newest (2) is protected
    writer.entries.clear();
    releaser.onReleaseLogRejected(0);
    scheduler.workUntilDone();
    scheduler.updateClock(WINDOW.multipliedBy(2));
    scheduler.workUntilDone();

    // then - inside the window the oldest one (1) protects, so snapshot 40 floors R at nothing
    assertThat(insideWindow).isEmpty();
    assertThat(writer.released()).containsExactly(50L);
  }

  private void recoverAndTick() {
    releaser.onRecoveryChecked(meta, false);
    scheduler.workUntilDone();
    tick();
  }

  private void tick() {
    scheduler.updateClock(INTERVAL);
    scheduler.workUntilDone();
  }

  private static LogArchiveMeta meta(final long releasedIndex, final long mergedIndex) {
    return new LogArchiveMeta()
        .setTimelineId(TIMELINE)
        .setReleasedIndex(releasedIndex)
        .setMergedIndex(mergedIndex);
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

  private static LogObject merged(final long firstIndex, final long lastIndex) {
    return new LogObject().setFirstIndex(firstIndex).setLastIndex(lastIndex);
  }

  private static final class FakeWriter implements LogStreamWriter {
    final List<LogAppendEntry> entries = new ArrayList<>();

    List<Long> released() {
      return entries.stream()
          .peek(
              entry -> {
                assertThat(entry.recordMetadata().getValueType()).isEqualTo(ValueType.LOG_ARCHIVE);
                assertThat(entry.recordMetadata().getIntent())
                    .isEqualTo(LogArchiveIntent.RELEASE_LOG);
              })
          .map(entry -> ((LogArchiveRecord) entry.recordValue()).getReleasedIndex())
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
