/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import static io.camunda.zeebe.restore.logarchive.TimelineFixture.checkpoint;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.logarchive.model.HandoverFile;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.TestRestorableBackupStore;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.FromArchive;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.OldBackups;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PartitionRestoreSourcesTest {

  private final TestRestorableBackupStore backupStore = new TestRestorableBackupStore();
  private final InMemoryLogArchiveStore archiveStore = new InMemoryLogArchiveStore();

  @BeforeEach
  void setUp() throws Exception {
    for (final int partitionId : List.of(1, 2)) {
      saveBackup(partitionId, 4);
      saveBackup(partitionId, 5);
      backupStore
          .storeBackupMetadata(
              partitionId,
              BackupMetadataSyncer.MAPPER.writeValueAsBytes(
                  new BackupMetadata(
                      BackupMetadata.VERSION,
                      partitionId,
                      Instant.now(),
                      List.of(entry(4, 40, 31), entry(5, 50, 41)),
                      List.of(new RangeEntry(4, 5)))))
          .join();
      TimelineFixture.start(archiveStore, "tl", partitionId, 25, "")
          .archive(44, checkpoint(4, 40))
          .snapshot(42)
          .archive(54, checkpoint(5, 50))
          .handover(5, 50)
          .archive(64, checkpoint(6, 60));
    }
    // only partition 1 wrote its handover file before the restore
    backupStore
        .storeHandoverRecord(1, 5, LogArchiveJson.write(new HandoverFile(1, 1, 5, 50, "tl")))
        .join();
    TimelineFixture.start(archiveStore, "tl.r6", 1, 35, "tl")
        .snapshot(42)
        .archive(64, checkpoint(6, 60));
    TimelineFixture.start(archiveStore, "unrelated", 1, 1, "").archive(10);
  }

  @Test
  void shouldLoadBothSidesOfEveryPartition() {
    // when
    final var sources =
        PartitionRestoreSources.load(backupStore, archiveStore, 2, "tl", Optional.empty());

    // then
    final var partition1 = sources.get(1);
    assertThat(partition1.completedOldBackups()).containsExactly(4L, 5L);
    assertThat(partition1.oldMetadata()).hasValueSatisfying(m -> assertThat(m.ranges()).hasSize(1));
    assertThat(partition1.handovers()).extracting(HandoverFile::checkpointId).containsExactly(5L);
    assertThat(partition1.timelines())
        .extracting(LogArchiveView::timelineId)
        .containsExactly("tl", "tl.r6");
    final var partition2 = sources.get(2);
    assertThat(partition2.handovers()).isEmpty();
    assertThat(partition2.timelines()).extracting(LogArchiveView::timelineId).containsExactly("tl");
  }

  @Test
  void shouldSearchOnlyTheExplicitTimeline() {
    // when
    final var sources =
        PartitionRestoreSources.load(backupStore, archiveStore, 2, "tl", Optional.of("tl"));

    // then
    assertThat(sources.get(1).timelines())
        .extracting(LogArchiveView::timelineId)
        .containsExactly("tl");
  }

  @Test
  void shouldResolveEachSideFromTheLoadedSources() {
    // given
    final var sources =
        PartitionRestoreSources.load(backupStore, archiveStore, 2, "tl", Optional.empty());

    // when
    final var old =
        HandoverRestoreResolver.resolve(sources, HandoverRestoreRequest.ofBackupIds(List.of(5L)));
    final var archived =
        HandoverRestoreResolver.resolve(sources, HandoverRestoreRequest.ofBackupIds(List.of(6L)));

    // then
    assertThat(old.partitions()).containsValues(new OldBackups(List.of(5L)));
    assertThat(archived.partitions().get(1))
        .isInstanceOfSatisfying(
            FromArchive.class, plan -> assertThat(plan.timelineId()).isEqualTo("tl.r6"));
    assertThat(archived.partitions().get(2))
        .isInstanceOfSatisfying(
            FromArchive.class, plan -> assertThat(plan.timelineId()).isEqualTo("tl"));
  }

  private void saveBackup(final int partitionId, final long checkpointId) {
    backupStore
        .save(
            new BackupImpl(
                new BackupIdentifierImpl(0, partitionId, checkpointId),
                new BackupDescriptorImpl(
                    Optional.empty(),
                    OptionalLong.empty(),
                    10 * checkpointId,
                    2,
                    "8.10.0",
                    Instant.now(),
                    CheckpointType.SCHEDULED_BACKUP),
                new NamedFileSetImpl(Map.of()),
                new NamedFileSetImpl(Map.of())))
        .join();
  }

  private static CheckpointEntry entry(
      final long id, final long position, final long firstLogPosition) {
    return new CheckpointEntry(
        id,
        position,
        Instant.ofEpochMilli(TimelineFixture.timestamp(id)),
        CheckpointType.SCHEDULED_BACKUP,
        OptionalLong.of(firstLogPosition));
  }
}
