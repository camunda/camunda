/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import static io.camunda.zeebe.restore.logarchive.TimelineFixture.checkpoint;
import static io.camunda.zeebe.restore.logarchive.TimelineFixture.timestamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.backup.logarchive.model.HandoverFile;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.RestorePointResolver;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.FromArchive;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.OldBackups;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The brownfield history of the tests, per partition: old backups 1 to 5 at positions 10 to 50, the
 * last one the handover H = 5; the archive timeline {@code tl} started at index 25 and covers
 * checkpoints 3 to 7 at positions 30 to 70, with a snapshot at index 42. Positions equal indexes.
 */
final class HandoverRestoreResolverTest {

  private static final String TIMELINE = "tl";
  private final InMemoryLogArchiveStore store = new InMemoryLogArchiveStore();

  @Test
  void shouldRestoreBackupIdsAsGivenWithoutLogArchive() {
    // given
    final var sources = Map.of(1, oldOnly(1), 2, oldOnly(2));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(3));

    // then
    assertThat(plan.checkpointId()).isEqualTo(3);
    assertThat(plan.allFromOldBackups()).isTrue();
    assertThat(plan.partitions()).containsValues(new OldBackups(List.of(3L)));
  }

  @Test
  void shouldResolveTimeRangeLikeTodayWithoutLogArchive() {
    // given
    final var sources = Map.of(1, oldOnly(1), 2, oldOnly(2));
    final var from = Instant.ofEpochMilli(timestamp(1));
    final var to = Instant.ofEpochMilli(timestamp(4));
    final var exported = Map.of(1, 15L, 2, 15L);

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources, HandoverRestoreRequest.ofTimeRange(from, to, exported));

    // then
    final var expected =
        RestorePointResolver.resolve(
            List.of(oldMetadata(1, 5), oldMetadata(2, 5)), from, to, exported);
    assertThat(plan.checkpointId()).isEqualTo(expected.globalCheckpointId());
    assertThat(plan.partitions())
        .containsEntry(1, new OldBackups(ids(expected.backupsByPartitionId().get(1))))
        .containsEntry(2, new OldBackups(ids(expected.backupsByPartitionId().get(2))));
  }

  @Test
  void shouldRestoreOldBackupBeforeTheHandover() {
    // given
    final var sources = Map.of(1, brownfield(1));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(4));

    // then
    assertThat(plan.partitions()).containsEntry(1, new OldBackups(List.of(4L)));
  }

  @Test
  void shouldRestoreTheHandoverFromItsOldBackup() {
    // given
    final var sources = Map.of(1, brownfield(1));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(5));

    // then
    assertThat(plan.partitions()).containsEntry(1, new OldBackups(List.of(5L)));
  }

  @Test
  void shouldRestoreCheckpointAfterTheHandoverFromTheArchive() {
    // given
    final var sources = Map.of(1, brownfield(1), 2, brownfield(2));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(7));

    // then
    assertThat(plan.checkpointId()).isEqualTo(7);
    assertThat(plan.partitions().values())
        .allSatisfy(
            partition ->
                assertThat(partition)
                    .isInstanceOfSatisfying(
                        FromArchive.class,
                        archived -> {
                          assertThat(archived.timelineId()).isEqualTo(TIMELINE);
                          assertThat(archived.checkpoint().checkpointPosition()).isEqualTo(70);
                          assertThat(archived.exportedPosition()).isEmpty();
                        }));
  }

  @Test
  void shouldLetPartitionsTakeDifferentPaths() {
    // given - the old backup 5 of partition 2 never completed, but its archive covers 5
    final var partition2 = brownfield(2);
    final var sources =
        Map.of(
            1,
            brownfield(1),
            2,
            new PartitionRestoreSources(
                2,
                partition2.oldMetadata(),
                new TreeSet<>(Set.of(1L, 2L, 3L, 4L)),
                partition2.handovers(),
                partition2.timelines()));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(5));

    // then
    assertThat(plan.partitions()).containsEntry(1, new OldBackups(List.of(5L)));
    assertThat(plan.partitions().get(2)).isInstanceOf(FromArchive.class);
    assertThat(plan.allFromOldBackups()).isFalse();
    assertThat(plan.oldBackupIds()).containsOnlyKeys(1);
  }

  @Test
  void shouldTakeTheOldPathOnABranchStartedBeforeTheHandover() {
    // given - restored to old backup 3, the branch tl.b3 took old backups 8 and 9 (H2) again
    final var sources = Map.of(1, branchAfterRestoreBeforeHandover(1, true));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(8));

    // then - 8 comes after H = 5 in time, but it is an old backup
    assertThat(plan.partitions()).containsEntry(1, new OldBackups(List.of(8L)));
  }

  @Test
  void shouldFindTheBranchTimelineAfterTheSecondHandover() {
    // given
    final var sources = Map.of(1, branchAfterRestoreBeforeHandover(1, true));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(10));

    // then
    assertThat(plan.partitions().get(1))
        .isInstanceOfSatisfying(
            FromArchive.class, archived -> assertThat(archived.timelineId()).isEqualTo("tl.b3"));
  }

  @Test
  void shouldFindTheBranchTimelineWithoutItsHandoverFile() {
    // given - the leader died before writing handover/9.json
    final var sources = Map.of(1, branchAfterRestoreBeforeHandover(1, false));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(10));

    // then
    assertThat(plan.partitions().get(1))
        .isInstanceOfSatisfying(
            FromArchive.class, archived -> assertThat(archived.timelineId()).isEqualTo("tl.b3"));
  }

  @Test
  void shouldFindTheArchiveThroughTheConfiguredTimelineWithoutHandoverFile() {
    // given - no handover record on the old side; the timeline is only known from configuration
    final var archive = brownfieldArchive(1);
    final var timelines =
        LogArchiveTimelines.select(List.of(archive.view()), List.of(TIMELINE)); // configured
    final var sources =
        Map.of(
            1,
            new PartitionRestoreSources(
                1, Optional.of(oldMetadata(1, 5)), completed(1, 5), List.of(), timelines));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(6));

    // then
    assertThat(plan.partitions().get(1)).isInstanceOf(FromArchive.class);
  }

  @Test
  void shouldPreferTheNewestTimelineOfOneHistory() {
    // given - tl was restored to 6; tl.r6 re-archived 5 and 6 at the same positions
    final var source = brownfield(1);
    final var restored =
        TimelineFixture.start(store, "tl.r6", 1, 35, TIMELINE)
            .archive(44)
            .snapshot(42)
            .archive(54, checkpoint(5, 50))
            .archive(64, checkpoint(6, 60))
            .archive(74, checkpoint(11, 66));
    final var sources = Map.of(1, withTimelines(source, restored.view()));

    // when
    final var plan = HandoverRestoreResolver.resolve(sources, byBackupId(6));

    // then
    assertThat(plan.partitions().get(1))
        .isInstanceOfSatisfying(
            FromArchive.class, archived -> assertThat(archived.timelineId()).isEqualTo("tl.r6"));
  }

  @Test
  void shouldFailWhenTheCheckpointIsInTwoHistories() {
    // given - the branch reused the manual checkpoint id 7 at another position
    final var source = branchAfterRestoreBeforeHandover(1, true);
    final var branch =
        TimelineFixture.start(store, "tl.b4", 1, 41, TIMELINE)
            .snapshot(42)
            .archive(50)
            .archive(60, checkpoint(7, 57));
    final var sources = Map.of(1, withTimelines(source, branch.view()));

    // when - then
    assertThatThrownBy(() -> HandoverRestoreResolver.resolve(sources, byBackupId(7)))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("Checkpoint 7 of partition 1 is covered by more than one")
        .hasMessageContaining("tl (position 70)")
        .hasMessageContaining("tl.b4 (position 57)")
        .hasMessageContaining("--logArchive.timeline");
  }

  @Test
  void shouldFailWhenTheCheckpointExistsNowhere() {
    // given
    final var sources = Map.of(1, brownfield(1), 2, brownfield(2));

    // when - then
    assertThatThrownBy(() -> HandoverRestoreResolver.resolve(sources, byBackupId(42)))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("Checkpoint 42 cannot be restored on partition 1")
        .hasMessageContaining("Newest old backup of partition 1: 5")
        .hasMessageContaining("newest log archive checkpoint: [7 in 'tl']");
  }

  @Test
  void shouldPickTheLatestCheckpointOnBothSides() {
    // given - the archive of partition 2 only reached checkpoint 6
    final var partition2 =
        new PartitionRestoreSources(
            2,
            Optional.of(oldMetadata(2, 5)),
            completed(1, 5),
            List.of(handover(2)),
            List.of(
                TimelineFixture.start(store, TIMELINE, 2, 25, "")
                    .archive(34, checkpoint(3, 30))
                    .archive(44, checkpoint(4, 40))
                    .snapshot(42)
                    .archive(54, checkpoint(5, 50))
                    .handover(5, 50)
                    .archive(64, checkpoint(6, 60))
                    .view()));
    final var sources = Map.of(1, brownfield(1), 2, partition2);

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources, HandoverRestoreRequest.ofTimeRange(null, null, Map.of()));

    // then
    assertThat(plan.checkpointId()).isEqualTo(6);
    assertThat(plan.partitions().values()).allMatch(FromArchive.class::isInstance);
  }

  @Test
  void shouldPickACheckpointAfterTheHandoverInATimeRange() {
    // given
    final var sources = Map.of(1, brownfield(1), 2, brownfield(2));

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources,
            HandoverRestoreRequest.ofTimeRange(
                Instant.ofEpochMilli(timestamp(4)),
                Instant.ofEpochMilli(timestamp(7) - 1_000),
                Map.of()));

    // then
    assertThat(plan.checkpointId()).isEqualTo(7);
    assertThat(plan.partitions().values()).allMatch(FromArchive.class::isInstance);
  }

  @Test
  void shouldPickAnOldCheckpointBeforeTheHandoverInATimeRange() {
    // given
    final var sources = Map.of(1, brownfield(1), 2, brownfield(2));

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources,
            HandoverRestoreRequest.ofTimeRange(
                Instant.ofEpochMilli(timestamp(4)),
                Instant.ofEpochMilli(timestamp(4) + 1_000),
                Map.of()));

    // then - the old backups that restore 4, discarding those before `from` like today
    assertThat(plan.checkpointId()).isEqualTo(4);
    assertThat(plan.partitions()).containsEntry(1, new OldBackups(List.of(4L)));
    assertThat(plan.partitions()).containsEntry(2, new OldBackups(List.of(4L)));
  }

  @Test
  void shouldStartTheJournalAtTheExportedPositionInsideTheArchive() {
    // given - the RDBMS exported up to position 33, below the snapshot at index 42
    final var sources = Map.of(1, brownfield(1));

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources, HandoverRestoreRequest.ofTimeRange(null, null, Map.of(1, 33L)));

    // then
    assertThat(plan.checkpointId()).isEqualTo(7);
    final var archived = (FromArchive) plan.partitions().get(1);
    assertThat(archived.exportedPosition()).contains(33L);
    final var archivePlan =
        LogArchiveRestorePlanner.plan(
            archived.view(), archived.checkpoint(), archived.exportedPosition());
    assertThat(archivePlan.snapshotIndex()).isEqualTo(42);
    assertThat(archivePlan.firstIndex()).isEqualTo(25);
  }

  @Test
  void shouldFallBackToTheOldBackupsWhenTheRdbmsIsBehindTheArchive() {
    // given - the RDBMS was reset to position 15, before the archive starts at 25
    final var sources = Map.of(1, brownfield(1), 2, brownfield(2));

    // when
    final var plan =
        HandoverRestoreResolver.resolve(
            sources, HandoverRestoreRequest.ofTimeRange(null, null, Map.of(1, 15L, 2, 15L)));

    // then - the newest old checkpoint that covers E
    assertThat(plan.checkpointId()).isEqualTo(5);
    assertThat(plan.allFromOldBackups()).isTrue();
    assertThat(plan.partitions().get(1))
        .isInstanceOfSatisfying(OldBackups.class, old -> assertThat(old.backupIds()).endsWith(5L));
  }

  @Test
  void shouldFailWhenTheRdbmsIsBehindTheArchiveAndNoOldBackupFits() {
    // given - the old metadata is gone (retention), the archive starts after E + 1
    final var brownfield = brownfield(1);
    final var sources =
        Map.of(
            1,
            new PartitionRestoreSources(
                1,
                Optional.empty(),
                new TreeSet<>(),
                brownfield.handovers(),
                brownfield.timelines()));

    // when - then
    assertThatThrownBy(
            () ->
                HandoverRestoreResolver.resolve(
                    sources, HandoverRestoreRequest.ofTimeRange(null, null, Map.of(1, 15L))))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("No checkpoint")
        .hasMessageContaining("exported position 15")
        .hasMessageContaining("old checkpoints none")
        .hasMessageContaining("'tl' positions 25..74");
  }

  private PartitionRestoreSources brownfield(final int partitionId) {
    return new PartitionRestoreSources(
        partitionId,
        Optional.of(oldMetadata(partitionId, 5)),
        completed(1, 5),
        List.of(handover(partitionId)),
        List.of(brownfieldArchive(partitionId).view()));
  }

  private TimelineFixture brownfieldArchive(final int partitionId) {
    return TimelineFixture.start(store, TIMELINE, partitionId, 25, "")
        .archive(34, checkpoint(3, 30))
        .archive(44, checkpoint(4, 40))
        .snapshot(42)
        .archive(54, checkpoint(5, 50))
        .handover(5, 50)
        .archive(64, checkpoint(6, 60))
        .archive(74, checkpoint(7, 70));
  }

  /**
   * The brownfield history restored to old backup 3; the branch timeline tl.b3 (parent tl) took old
   * backups 8 and 9 again, then handed over at H2 = 9 and covered 10.
   */
  private PartitionRestoreSources branchAfterRestoreBeforeHandover(
      final int partitionId, final boolean withSecondHandoverFile) {
    final var original = brownfieldArchive(partitionId).view();
    final var branch =
        TimelineFixture.start(store, "tl.b3", partitionId, 31, TIMELINE)
            .archive(40, checkpoint(8, 35))
            .snapshot(44)
            .archive(50, checkpoint(9, 45))
            .handover(9, 45)
            .archive(60, checkpoint(10, 55))
            .view();
    final var checkpoints = new ArrayList<>(oldCheckpoints(partitionId, 3));
    checkpoints.add(oldEntry(8, 35, 31, CheckpointType.SCHEDULED_BACKUP));
    checkpoints.add(oldEntry(9, 45, 36, CheckpointType.HANDOVER));
    final var metadata =
        new BackupMetadata(
            BackupMetadata.VERSION,
            partitionId,
            Instant.now(),
            checkpoints,
            List.of(new RangeEntry(1, 3), new RangeEntry(8, 9)));
    final var handovers = new ArrayList<HandoverFile>();
    handovers.add(handover(partitionId));
    if (withSecondHandoverFile) {
      handovers.add(new HandoverFile(1, partitionId, 9, 45, "tl.b3"));
    }
    final var completed = completed(1, 5);
    completed.addAll(List.of(8L, 9L));
    final var seeds = new ArrayList<String>();
    handovers.forEach(h -> seeds.add(h.logArchiveTimeline()));
    seeds.add(TIMELINE);
    return new PartitionRestoreSources(
        partitionId,
        Optional.of(metadata),
        completed,
        handovers,
        LogArchiveTimelines.select(List.of(original, branch), seeds));
  }

  private static PartitionRestoreSources withTimelines(
      final PartitionRestoreSources source, final LogArchiveView... more) {
    final var timelines = new ArrayList<>(source.timelines());
    timelines.addAll(List.of(more));
    return new PartitionRestoreSources(
        source.partitionId(),
        source.oldMetadata(),
        source.completedOldBackups(),
        source.handovers(),
        timelines);
  }

  private static PartitionRestoreSources oldOnly(final int partitionId) {
    return new PartitionRestoreSources(
        partitionId,
        Optional.of(oldMetadata(partitionId, 5)),
        completed(1, 5),
        List.of(),
        List.of());
  }

  private static HandoverFile handover(final int partitionId) {
    return new HandoverFile(1, partitionId, 5, 50, TIMELINE);
  }

  private static TreeSet<Long> completed(final long first, final long last) {
    final var ids = new TreeSet<Long>();
    for (long id = first; id <= last; id++) {
      ids.add(id);
    }
    return ids;
  }

  /** Old checkpoints 1 to {@code last} at positions 10 to 10 * last; the 5th is the handover. */
  private static BackupMetadata oldMetadata(final int partitionId, final long last) {
    return new BackupMetadata(
        BackupMetadata.VERSION,
        partitionId,
        Instant.now(),
        oldCheckpoints(partitionId, last),
        List.of(new RangeEntry(1, last)));
  }

  private static List<CheckpointEntry> oldCheckpoints(final int partitionId, final long last) {
    final var checkpoints = new ArrayList<CheckpointEntry>();
    for (long id = 1; id <= last; id++) {
      checkpoints.add(
          oldEntry(
              id,
              10 * id,
              id == 1 ? 1 : 10 * (id - 1) + 1,
              id == 5 ? CheckpointType.HANDOVER : CheckpointType.SCHEDULED_BACKUP));
    }
    return checkpoints;
  }

  private static CheckpointEntry oldEntry(
      final long id, final long position, final long firstLogPosition, final CheckpointType type) {
    return new CheckpointEntry(
        id, position, Instant.ofEpochMilli(timestamp(id)), type, OptionalLong.of(firstLogPosition));
  }

  private static HandoverRestoreRequest byBackupId(final long backupId) {
    return HandoverRestoreRequest.ofBackupIds(List.of(backupId));
  }

  private static List<Long> ids(final List<CheckpointEntry> entries) {
    return entries.stream().map(CheckpointEntry::checkpointId).toList();
  }
}
