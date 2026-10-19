/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.schedule.HandoverDecision.Action;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionLogArchiveState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import org.junit.jupiter.api.Test;

final class HandoverDecisionTest {

  private static final long NONE = -1L;
  private static final long OLD_BACKUP = 100L;
  private static final long HANDOVER = 200L;

  private final CheckpointStateResponse state = new CheckpointStateResponse();

  @Test
  void shouldTakeScheduledBackupWhenLogArchiveIsDisabled() {
    // given - everything is ready for the handover
    archive(1, true, 50, 10, NONE);
    backup(1, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 40);

    // when
    final var decision = HandoverDecision.decide(state, false);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
    assertThat(decision.backupType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldTakeScheduledBackupWhenNothingIsKnown() {
    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
  }

  @Test
  void shouldTakeScheduledBackupWhileATimelineIsNotStarted() {
    // given
    archive(1, true, 50, 10, NONE);
    archive(2, false, 0, NONE, NONE);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
  }

  @Test
  void shouldTakeScheduledBackupWhenAPartitionHasNoLogArchiveState() {
    // given - partition 2 only appears in the backup states
    archive(1, true, 50, 10, NONE);
    backup(2, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 40);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
  }

  @Test
  void shouldTakeHandoverBackupWhenArchiveCaughtUpAndSnapshotArchived() {
    // given
    archive(1, true, 50, 10, NONE);
    archive(2, true, 70, 20, NONE);
    backup(1, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 50);
    backup(2, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 60);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.HANDOVER_BACKUP);
    assertThat(decision.backupType()).isEqualTo(CheckpointType.HANDOVER);
  }

  @Test
  void shouldTakeHandoverBackupWhenNoBackupWasTakenYet() {
    // given
    archive(1, true, 50, 10, NONE);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.HANDOVER_BACKUP);
  }

  @Test
  void shouldTakeScheduledBackupWhileArchiveIsBehindTheLastBackup() {
    // given
    archive(1, true, 50, 10, NONE);
    archive(2, true, 59, 20, NONE);
    backup(1, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 50);
    backup(2, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 60);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
  }

  @Test
  void shouldTakeScheduledBackupWhileASnapshotIsNotArchived() {
    // given
    archive(1, true, 50, 10, NONE);
    archive(2, true, 70, NONE, NONE);
    backup(1, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 50);
    backup(2, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 60);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
  }

  @Test
  void shouldCompleteHandoverWhenConfirmedAndArchivedEverywhere() {
    // given
    archive(1, true, 80, 10, NONE);
    archive(2, true, 90, 20, NONE);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER, CheckpointType.HANDOVER, 85);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision).isEqualTo(new HandoverDecision(Action.COMPLETE_HANDOVER, HANDOVER));
  }

  @Test
  void shouldNotCompleteHandoverWhileNotArchivedEverywhere() {
    // given - partition 2 has not archived H yet
    archive(1, true, 80, 10, NONE);
    archive(2, true, 84, 20, NONE);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER, CheckpointType.HANDOVER, 85);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then - neither completed nor a new handover backup, which needs the archive to pass H
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
  }

  @Test
  void shouldTakeNewHandoverBackupWhenHandoverIsNotConfirmedEverywhere() {
    // given - H is confirmed on partition 1 only, partition 2 still has the old backup last
    archive(1, true, 80, 10, NONE);
    archive(2, true, 90, 20, NONE);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, OLD_BACKUP, CheckpointType.SCHEDULED_BACKUP, 60);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.HANDOVER_BACKUP);
  }

  @Test
  void shouldNotCompleteHandoverWhenPartitionsConfirmedDifferentHandoverBackups() {
    // given
    archive(1, true, 80, 10, NONE);
    archive(2, true, 90, 20, NONE);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER + 1, CheckpointType.HANDOVER, 85);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.HANDOVER_BACKUP);
  }

  @Test
  void shouldNotCompleteHandoverWhenAPartitionHasNoBackup() {
    // given
    archive(1, true, 80, 10, NONE);
    archive(2, true, 90, 20, NONE);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.HANDOVER_BACKUP);
  }

  @Test
  void shouldCompleteHandoverOnRemainingPartitionsWhenRecordedOnSomeOnly() {
    // given - partition 1 recorded H; partition 2 missed the request
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, NONE);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER, CheckpointType.HANDOVER, 85);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision).isEqualTo(new HandoverDecision(Action.COMPLETE_HANDOVER, HANDOVER));
  }

  @Test
  void shouldCompleteRecordedHandoverEvenIfALaterBackupIsConfirmed() {
    // given - a manual backup after H is confirmed on partition 2; the partition still knows H
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, NONE);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER + 1, CheckpointType.MANUAL_BACKUP, 88);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision).isEqualTo(new HandoverDecision(Action.COMPLETE_HANDOVER, HANDOVER));
  }

  @Test
  void shouldTakeScheduledBackupWhenHandoverIsRecordedEverywhere() {
    // given
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, HANDOVER);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER, CheckpointType.HANDOVER, 85);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
    assertThat(decision.backupType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldTakeScheduledBackupWhenPartitionsRecordedDifferentHandovers() {
    // given
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, HANDOVER + 1);
    archive(3, true, 90, 20, NONE);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
  }

  @Test
  void shouldCompleteHandoverAndKeepTakingBackupsWhenOnlyPartitionsAddedAfterItLackIt() {
    // given - partitions 1 and 2 recorded H; partition 3 was added later, its archive starts at its
    // first record, and it took old-style backups before it was activated
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, HANDOVER);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER, CheckpointType.HANDOVER, 85);
    addedAfterHandover(3, 30, 5);
    backup(3, HANDOVER + 10, CheckpointType.SCHEDULED_BACKUP, 12);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision)
        .isEqualTo(new HandoverDecision(Action.COMPLETE_HANDOVER_AND_BACKUP, HANDOVER));
    assertThat(decision.completesHandover()).isTrue();
    assertThat(decision.takesBackup()).isTrue();
    assertThat(decision.backupType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldOnlyCompleteHandoverWhenALaggingPartitionTookPartEvenIfItsArchiveStartsAtItsStart() {
    // given - partition 1 recorded H; partition 2 took part (H is its last confirmed backup) and
    // only missed the request; its archive starts at its first record because the cluster
    // activated the archive before anything was compacted
    archive(1, true, 100, 10, HANDOVER);
    addedAfterHandover(2, 90, 20);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER, CheckpointType.HANDOVER, 85);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then - no backup meanwhile: it could not complete while partition 2 lags behind
    assertThat(decision).isEqualTo(new HandoverDecision(Action.COMPLETE_HANDOVER, HANDOVER));
    assertThat(decision.takesBackup()).isFalse();
  }

  @Test
  void shouldKeepTakingBackupsWhileThePartitionAddedAfterTheHandoverHasNoSnapshotArchived() {
    // given - partition 3 cannot record H before a snapshot is archived; backups go on meanwhile
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, HANDOVER);
    addedAfterHandover(3, 30, NONE);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision)
        .isEqualTo(new HandoverDecision(Action.COMPLETE_HANDOVER_AND_BACKUP, HANDOVER));
  }

  @Test
  void shouldStopBackupsWhenAPartitionThatTookPartInTheHandoverLacksIt() {
    // given - partition 2 took part in the handover, its archive starts after its first record;
    // partition 3 was added later
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, NONE);
    backup(1, HANDOVER, CheckpointType.HANDOVER, 80);
    backup(2, HANDOVER, CheckpointType.HANDOVER, 85);
    addedAfterHandover(3, 30, 5);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision).isEqualTo(new HandoverDecision(Action.COMPLETE_HANDOVER, HANDOVER));
    assertThat(decision.takesBackup()).isFalse();
  }

  @Test
  void shouldTakeScheduledBackupWhenThePartitionAddedAfterTheHandoverRecordedIt() {
    // given
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, HANDOVER);
    state.getLogArchiveStates().add(new PartitionLogArchiveState(3, true, 30, 5, HANDOVER, 1));

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
    assertThat(decision.completesHandover()).isFalse();
  }

  @Test
  void shouldTakeScheduledBackupWhileThePartitionAddedAfterTheHandoverIsNotActivated() {
    // given
    archive(1, true, 100, 10, HANDOVER);
    archive(2, true, 90, 20, HANDOVER);
    archive(3, false, NONE, NONE, NONE);

    // when
    final var decision = HandoverDecision.decide(state, true);

    // then
    assertThat(decision.action()).isEqualTo(Action.SCHEDULED_BACKUP);
  }

  private void archive(
      final int partitionId,
      final boolean timelineStarted,
      final long archivedPosition,
      final long newestSnapshotPosition,
      final long handoverCheckpointId) {
    state
        .getLogArchiveStates()
        .add(
            new PartitionLogArchiveState(
                partitionId,
                timelineStarted,
                archivedPosition,
                newestSnapshotPosition,
                handoverCheckpointId));
  }

  /** A partition whose log archive starts at its first record and has no handover recorded. */
  private void addedAfterHandover(
      final int partitionId, final long archivedPosition, final long newestSnapshotPosition) {
    state
        .getLogArchiveStates()
        .add(
            new PartitionLogArchiveState(
                partitionId, true, archivedPosition, newestSnapshotPosition, NONE, 1));
  }

  private void backup(
      final int partitionId, final long id, final CheckpointType type, final long position) {
    state
        .getBackupStates()
        .add(new PartitionCheckpointState(partitionId, id, type, id, position, 1));
  }
}
