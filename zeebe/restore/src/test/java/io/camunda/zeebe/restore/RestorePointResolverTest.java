/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class RestorePointResolverTest {

  @Test
  void shouldResolveWithSinglePartitionSingleBackup() {
    // given – one partition, one SCHEDULED_BACKUP checkpoint, one range covering it
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var cp = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var meta = metadata(1, List.of(cp), List.of(new RangeEntry(1, 1)));

    // when
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of());

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(1);
    assertThat(result.backupsByPartitionId()).hasSize(1);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp);
  }

  @Test
  void shouldPickLatestCommonCheckpointWhenNoToProvided() {
    // given – two partitions, both share checkpoints 1 and 2
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var cp1p1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2p1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp1p2 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2p2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));

    final var metadataByPartition =
        List.of(
            metadata(1, List.of(cp1p1, cp2p1), List.of(new RangeEntry(1, 2))),
            metadata(2, List.of(cp1p2, cp2p2), List.of(new RangeEntry(1, 2))));

    // when – no 'to' timestamp
    final var result = RestorePointResolver.resolve(metadataByPartition, null, null, Map.of());

    // then – it picks the checkpoint closest to now (= checkpoint 2)
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1p1, cp2p1);
    assertThat(result.backupsByPartitionId().get(2)).containsExactly(cp1p2, cp2p2);
  }

  @Test
  void shouldPickCheckpointClosestToTimestamp() {
    // given – two partitions, checkpoints at t1 and t2; 'to' is closer to t1
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-06-01T00:00:00Z");
    final var to = Instant.parse("2025-01-15T00:00:00Z"); // much closer to t1

    final var cp1p1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2p1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp1p2 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2p2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));

    final var metadataByPartition =
        List.of(
            metadata(1, List.of(cp1p1, cp2p1), List.of(new RangeEntry(1, 2))),
            metadata(2, List.of(cp1p2, cp2p2), List.of(new RangeEntry(1, 2))));

    // when
    final var result = RestorePointResolver.resolve(metadataByPartition, null, to, Map.of());

    // then – checkpoint 1 is closest to 'to'
    assertThat(result.globalCheckpointId()).isOne();
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1p1);
    assertThat(result.backupsByPartitionId().get(2)).containsExactly(cp1p2);
  }

  @Test
  void shouldSkipMarkerCheckpointsInBackupList() {
    // given – partition with MARKER then SCHEDULED_BACKUP checkpoints
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");

    final var marker = entry(1, 100, t1, CheckpointType.MARKER, OptionalLong.empty());
    final var backup1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var backup2 = entry(3, 300, t3, CheckpointType.MANUAL_BACKUP, OptionalLong.of(201));

    final var meta = metadata(1, List.of(marker, backup1, backup2), List.of(new RangeEntry(1, 3)));

    // when – target checkpoint 3
    final var result = RestorePointResolver.resolve(List.of(meta), null, t3, Map.of());

    // then – marker is excluded from backups, only backup-type entries are included
    assertThat(result.globalCheckpointId()).isEqualTo(3);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(backup1, backup2);
  }

  @Test
  void shouldStopCollectingBackupsAtCommonCheckpoint() {
    // given – partition with 3 backup checkpoints, common checkpoint is 2
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));

    final var meta = metadata(1, List.of(cp1, cp2, cp3), List.of(new RangeEntry(1, 3)));

    // when – 'to' is closest to t2
    final var result = RestorePointResolver.resolve(List.of(meta), null, t2, Map.of());

    // then – stops at checkpoint 2, does not include checkpoint 3
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1, cp2);
  }

  @Test
  void shouldFilterByFromTimestamp() {
    // given – two ranges: [1,2] starting at t1, [3,4] starting at t3
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta =
        metadata(
            1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 2), new RangeEntry(3, 4)));

    // when – from=t2 means range start timestamp must be <= t2 (not after t2)
    final var result = RestorePointResolver.resolve(List.of(meta), t2, null, Map.of());

    // then – range [3,4] is filtered out (start=t3 > from=t2), and within range [1,2],
    // cp1 is trimmed because its timestamp (t1) is before 'from' (t2)
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp2);
  }

  @Test
  void shouldFilterByExportedPosition() {
    // given – two ranges, 'exportedPosition' excludes the second range
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta =
        metadata(
            1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 2), new RangeEntry(3, 4)));

    // when – exportedPosition=100 means range start's firstLogPosition must be <= 100
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of(1, 100L));

    // then – only checkpoints from range [1,2]
    // cp1.firstLogPosition=50 <= 100 ✓, cp3.firstLogPosition=250 > 100 ✗
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1, cp2);
  }

  @Test
  void shouldThrowWhenNoUsableRangeFound() {
    // given – 'from' is before all range start timestamps, so all are filtered out
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));

    final var meta = metadata(1, List.of(cp1, cp2), List.of(new RangeEntry(1, 2)));

    // cp1 at 2025-01-01, from at 2024-01-01 -> cp1.isAfter(from)=true -> skip
    final var fromBeforeAll = Instant.parse("2024-01-01T00:00:00Z");

    // when/then
    assertThatThrownBy(
            () -> RestorePointResolver.resolve(List.of(meta), fromBeforeAll, null, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No usable range found")
        .hasMessageContaining("partition 1")
        .hasMessageContaining("from=2024-01-01");
  }

  @Test
  void shouldThrowWhenNoCommonCheckpointExists() {
    // given – two partitions with non-overlapping checkpoint ids
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var cp1p1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2p2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));

    final var metadataByPartition =
        List.of(
            metadata(1, List.of(cp1p1), List.of(new RangeEntry(1, 1))),
            metadata(2, List.of(cp2p2), List.of(new RangeEntry(2, 2))));

    // when/then
    assertThatThrownBy(
            () -> RestorePointResolver.resolve(metadataByPartition, null, null, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Could not find common checkpoint")
        .hasMessageContaining("Restorable checkpoint count per partition");
  }

  @Test
  void shouldHandleManualBackupType() {
    // given – single partition with MANUAL_BACKUP checkpoint
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var cp = entry(1, 100, t1, CheckpointType.MANUAL_BACKUP, OptionalLong.of(50));
    final var meta = metadata(1, List.of(cp), List.of(new RangeEntry(1, 1)));

    // when
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of());

    // then – MANUAL_BACKUP should be included (shouldCreateBackup() = true)
    assertThat(result.globalCheckpointId()).isEqualTo(1);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp);
  }

  @Test
  void shouldHandleMixedMarkerAndBackupCheckpoints() {
    // given – markers interspersed with backups; only backups should appear in result
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");
    final var t4 = Instant.parse("2025-01-04T00:00:00Z");
    final var t5 = Instant.parse("2025-01-05T00:00:00Z");

    final var marker1 = entry(1, 100, t1, CheckpointType.MARKER, OptionalLong.empty());
    final var backup1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var marker2 = entry(3, 300, t3, CheckpointType.MARKER, OptionalLong.empty());
    final var backup2 = entry(4, 400, t4, CheckpointType.MANUAL_BACKUP, OptionalLong.of(201));
    final var marker3 = entry(5, 500, t5, CheckpointType.MARKER, OptionalLong.empty());

    final var meta =
        metadata(
            1, List.of(marker1, backup1, marker2, backup2, marker3), List.of(new RangeEntry(1, 5)));

    // when – target closest to t4 (checkpoint 4)
    final var result = RestorePointResolver.resolve(List.of(meta), null, t4, Map.of());

    // then – only backup-type checkpoints, stopping at checkpoint 4
    assertThat(result.globalCheckpointId()).isEqualTo(4);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(backup1, backup2);
  }

  @Test
  void shouldHandleThreePartitionsWithCommonCheckpoint() {
    // given – 3 partitions, all share checkpoint 2
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101))),
                List.of(new RangeEntry(1, 2))),
            metadata(
                2,
                List.of(
                    entry(1, 110, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(60)),
                    entry(2, 210, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(111))),
                List.of(new RangeEntry(1, 2))),
            metadata(
                3,
                List.of(
                    entry(1, 120, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(70)),
                    entry(2, 220, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(121))),
                List.of(new RangeEntry(1, 2))));

    // when
    final var result = RestorePointResolver.resolve(metadataByPartition, null, null, Map.of());

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId()).hasSize(3);
    assertThat(result.backupsByPartitionId().get(1)).hasSize(2);
    assertThat(result.backupsByPartitionId().get(2)).hasSize(2);
    assertThat(result.backupsByPartitionId().get(3)).hasSize(2);
  }

  @Test
  void shouldSelectCommonCheckpointWhenPartitionsHaveDifferentCheckpoints() {
    // given – partition 1 has checkpoints [1,2,3], partition 2 has [2,3,4]
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-02-01T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101)),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201))),
                List.of(new RangeEntry(1, 3))),
            metadata(
                2,
                List.of(
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101)),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201)),
                    entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301))),
                List.of(new RangeEntry(2, 4))));

    // when – no 'to', so picks closest to now; checkpoint 3 is common and closer to now than 2
    final var result = RestorePointResolver.resolve(metadataByPartition, null, null, Map.of());

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(3);
  }

  @Test
  void shouldSelectCommonCheckpointClosestToToWhenMultipleCommonExist() {
    // given – two partitions, common checkpoints are 1, 2, 3; 'to' is closest to 2
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-06-01T00:00:00Z");
    final var t3 = Instant.parse("2025-12-01T00:00:00Z");
    final var to = Instant.parse("2025-05-15T00:00:00Z"); // closest to t2

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101)),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201))),
                List.of(new RangeEntry(1, 3))),
            metadata(
                2,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101)),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201))),
                List.of(new RangeEntry(1, 3))));

    // when
    final var result = RestorePointResolver.resolve(metadataByPartition, null, to, Map.of());

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).hasSize(2); // cp1, cp2
    assertThat(result.backupsByPartitionId().get(2)).hasSize(2);
  }

  @Test
  void shouldIncludeOnlyBackupsUpToCommonCheckpointWithMarkersInBetween() {
    // given – markers before and after the common checkpoint
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");

    final var marker = entry(1, 100, t1, CheckpointType.MARKER, OptionalLong.empty());
    final var backup = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var markerAfter = entry(3, 300, t3, CheckpointType.MARKER, OptionalLong.empty());

    final var meta =
        metadata(1, List.of(marker, backup, markerAfter), List.of(new RangeEntry(1, 3)));

    // when
    final var result = RestorePointResolver.resolve(List.of(meta), null, t2, Map.of());

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(backup);
  }

  @Test
  void shouldUseFromAndToTogether() {
    // given – single range with 4 checkpoints; from trims early ones, to limits the end
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-02-01T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta = metadata(1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 4)));

    // when – from=t2 trims cp1 (t1 < t2), to=t3 picks cp3 as common checkpoint
    final var result = RestorePointResolver.resolve(List.of(meta), t2, t3, Map.of());

    // then – cp1 trimmed by from, cp4 excluded by to → only cp2 and cp3
    assertThat(result.globalCheckpointId()).isEqualTo(3);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp2, cp3);
  }

  @Test
  void shouldHandleExportedPositionFilteringOutAllRanges() {
    // given – exportedPosition is too small for any range
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));

    final var meta = metadata(1, List.of(cp1), List.of(new RangeEntry(1, 1)));

    // when – exportedPosition=10 < cp1.firstLogPosition=50 -> range filtered out
    assertThatThrownBy(
            () -> RestorePointResolver.resolve(List.of(meta), null, null, Map.of(1, 10L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No usable range found")
        .hasMessageContaining("partition 1")
        .hasMessageContaining("exportedPosition=10");
  }

  @Test
  void shouldFilterOutRangeWhereEndCheckpointPositionIsLessThanExportedPosition() {
    // given – two ranges; the first range's end checkpoint position is less than exportedPosition
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta =
        metadata(
            1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 2), new RangeEntry(3, 4)));

    // when – exportedPosition=250; range [1,2] end cp2.checkpointPosition=200 < 250 -> filtered
    // range [3,4] start cp3.firstLogPosition=250 <= 250 ✓, end cp4.checkpointPosition=400 >= 250 ✓
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of(1, 250L));

    // then – only checkpoints from range [3,4]
    assertThat(result.globalCheckpointId()).isEqualTo(4);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp3, cp4);
  }

  @Test
  void shouldThrowWhenExportedPositionExceedsAllRangeEndPositions() {
    // given – exportedPosition is beyond the end checkpoint position of all ranges
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));

    final var meta = metadata(1, List.of(cp1, cp2), List.of(new RangeEntry(1, 2)));

    // when – exportedPosition=500 > cp2.checkpointPosition=200 -> range filtered out
    assertThatThrownBy(
            () -> RestorePointResolver.resolve(List.of(meta), null, null, Map.of(1, 500L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No usable range found")
        .hasMessageContaining("partition 1")
        .hasMessageContaining("exportedPosition=500");
  }

  @Test
  void shouldSelectBackupsBasedOnToTimestamp() {
    // given – partition has checkpoints [1,2,3,4], but 'to' limits to checkpoint 2
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");
    final var t4 = Instant.parse("2025-01-04T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta = metadata(1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 4)));

    // when – 'to' is closest to t2
    final var result = RestorePointResolver.resolve(List.of(meta), null, t2, Map.of());

    // then – only backups up to checkpoint 2 are returned
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1, cp2);
  }

  @Test
  void shouldFailWhenOnePartitionHasNoBackupsInTimeInterval() {
    // given – P1 has backups in interval, P2 has backups outside interval (from filters it out)
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101))),
                List.of(new RangeEntry(1, 2))),
            metadata(
                2,
                List.of(
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201)),
                    entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301))),
                List.of(new RangeEntry(3, 4))));

    // when/then – from=t2 filters out P2's range (t3 > t2)
    assertThatThrownBy(() -> RestorePointResolver.resolve(metadataByPartition, t2, null, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No usable range found")
        .hasMessageContaining("partition 2");
  }

  @Test
  void shouldFilterCommonCheckpointByPerPartitionExportedPosition() {
    // given – two partitions share checkpoints 2 and 3; partition 2's exported position
    // requires checkpoint position >= 200, so checkpoint 2 (position 110 on P2) is skipped
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(2, 100, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(3, 200, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101))),
                List.of(new RangeEntry(2, 3))),
            metadata(
                2,
                List.of(
                    entry(1, 10, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(109)),
                    entry(2, 110, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(60)),
                    entry(3, 210, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(111))),
                List.of(new RangeEntry(1, 3))));

    // when – P2 exportedPosition=200 means cp1 on P2 (checkpointPosition=110) < 200 -> skip cp1
    final var result =
        RestorePointResolver.resolve(metadataByPartition, null, null, Map.of(2, 200L));

    // then – checkpoint 3 is selected (cp2 on P2 has checkpointPosition=210 >= 200)
    assertThat(result.globalCheckpointId()).isEqualTo(3);
    // P1 has no exported position → all checkpoints kept
    assertThat(result.backupsByPartitionId().get(1)).hasSize(2);
    // P2 cp2 (pos=110) is kept as the safe starting snapshot (last before exportedPosition=200) but
    // cp1 is discarded based on the exporter position.
    assertThat(result.backupsByPartitionId().get(2)).hasSize(2);
  }

  @Test
  void shouldThrowWhenExportedPositionExceedsAllCommonCheckpoints() {
    // given – two partitions share checkpoint 1; partition 1's exported position exceeds it
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50))),
                List.of(new RangeEntry(1, 1))),
            metadata(
                2,
                List.of(entry(1, 110, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(60))),
                List.of(new RangeEntry(1, 1))));

    // when – P1 exportedPosition=500 > cp1.checkpointPosition=100 -> range filtered out
    assertThatThrownBy(
            () -> RestorePointResolver.resolve(metadataByPartition, null, null, Map.of(1, 500L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No usable range found")
        .hasMessageContaining("partition 1");
  }

  @Test
  void shouldUsePerPartitionExportedPositionsInRangeFiltering() {
    // given – two partitions with different exported positions filtering different ranges
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101)),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201)),
                    entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301))),
                List.of(new RangeEntry(1, 2), new RangeEntry(3, 4))),
            metadata(
                2,
                List.of(
                    entry(1, 110, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(60)),
                    entry(2, 210, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(111)),
                    entry(3, 310, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(211)),
                    entry(4, 410, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(311))),
                List.of(new RangeEntry(1, 2), new RangeEntry(3, 4))));

    // when – P1 exportedPosition=250 filters out range [1,2] (end cp2.pos=200 < 250)
    //        P2 exportedPosition=260 filters out range [1,2] (end cp2.pos=210 < 260)
    //        Both partitions use range [3,4]
    final var result =
        RestorePointResolver.resolve(metadataByPartition, null, null, Map.of(1, 250L, 2, 260L));

    // then – common checkpoint from range [3,4] -> cp 4
    assertThat(result.globalCheckpointId()).isEqualTo(4);
  }

  @Test
  void shouldFailWhenNoCommonCheckpointExistsAcrossMultiplePartitions() {
    // given – partitions have non-overlapping checkpoint sets
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");
    final var t4 = Instant.parse("2025-01-04T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101))),
                List.of(new RangeEntry(1, 2))),
            metadata(
                2,
                List.of(
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201)),
                    entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301))),
                List.of(new RangeEntry(3, 4))));

    // when/then
    assertThatThrownBy(
            () -> RestorePointResolver.resolve(metadataByPartition, null, null, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Could not find common checkpoint");
  }

  @Test
  void shouldThrowWhenBackupsHaveDataGap() {
    // given – two backup checkpoints with a gap: cp2.firstLogPosition (300) >
    // cp1.checkpointPosition
    // (100) + 1
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    // firstLogPosition=300 creates a gap with cp1.checkpointPosition=100
    final var cp2 = entry(2, 400, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(300));

    final var meta = metadata(1, List.of(cp1, cp2), List.of(new RangeEntry(1, 2)));

    // when/then – gap detected between cp1 and cp2
    assertThatThrownBy(() -> RestorePointResolver.resolve(List.of(meta), null, null, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unexpected data gaps")
        .hasMessageContaining("first log position");
  }

  @Test
  void shouldNotThrowWhenBackupsAreContiguous() {
    // given – two backup checkpoints where cp2.firstLogPosition (101) == cp1.checkpointPosition
    // (100) + 1
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));

    final var meta = metadata(1, List.of(cp1, cp2), List.of(new RangeEntry(1, 2)));

    // when
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of());

    // then – no exception, both backups included
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1, cp2);
  }

  @Test
  void shouldNotThrowWhenBackupsOverlap() {
    // given – two backup checkpoints where cp2.firstLogPosition (50) < cp1.checkpointPosition (100)
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(10));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));

    final var meta = metadata(1, List.of(cp1, cp2), List.of(new RangeEntry(1, 2)));

    // when
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of());

    // then – overlapping is fine, no exception
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1, cp2);
  }

  @Test
  void shouldDetectGapBetweenBackupsWhenMarkersAreInBetween() {
    // given – marker between two backups; gap check should be between the two backups only
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");

    final var backup1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var marker = entry(2, 200, t2, CheckpointType.MARKER, OptionalLong.empty());
    // firstLogPosition=300 creates a gap with backup1.checkpointPosition=100
    final var backup2 = entry(3, 400, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(300));

    final var meta = metadata(1, List.of(backup1, marker, backup2), List.of(new RangeEntry(1, 3)));

    // when/then – gap detected between backup1 and backup2 (marker is skipped)
    assertThatThrownBy(() -> RestorePointResolver.resolve(List.of(meta), null, null, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unexpected data gaps");
  }

  @Test
  void shouldResolveWhenExportedPositionIsNegativeOne() {
    // given – exported position -1 means nothing has been exported yet; the backup needs to start
    // from position 1 (the first exportable log entry). A range whose first log position is 1
    // should be accepted.
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var cp = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(1));
    final var meta = metadata(1, List.of(cp), List.of(new RangeEntry(1, 1)));

    // when – exportedPosition=-1 is normalized to 1; cp.firstLogPosition=1 <= 1 -> range accepted
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of(1, -1L));

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(1);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp);
  }

  @Test
  void shouldResolveMultiplePartitionsWhenOneHasExportedPositionNegativeOne() {
    // given – two partitions; partition 2 has exportedPosition=-1 (nothing exported yet)
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(1)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101))),
                List.of(new RangeEntry(1, 2))),
            metadata(
                2,
                List.of(
                    entry(1, 110, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(1)),
                    entry(2, 210, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(111))),
                List.of(new RangeEntry(1, 2))));

    // when – P1 exported 100, P2 exported nothing (-1 → normalized to 1)
    final var result =
        RestorePointResolver.resolve(metadataByPartition, null, null, Map.of(1, 100L, 2, -1L));

    // then – both partitions resolve successfully
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId()).hasSize(2);
  }

  @Test
  void shouldThrowWhenBackupsHaveDataGapAcrossMultiplePartitions() {
    // given – partition 1 is contiguous, partition 2 has a gap
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");

    final var metadataByPartition =
        List.of(
            metadata(
                1,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50)),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101))),
                List.of(new RangeEntry(1, 2))),
            metadata(
                2,
                List.of(
                    entry(1, 110, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(60)),
                    // firstLogPosition=500 creates a gap with cp1.checkpointPosition=110
                    entry(2, 600, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(500))),
                List.of(new RangeEntry(1, 2))));

    // when/then – gap detected on partition 2
    assertThatThrownBy(
            () -> RestorePointResolver.resolve(metadataByPartition, null, null, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unexpected data gaps");
  }

  @Test
  void shouldTrimCheckpointsBeforeExportedPosition() {
    // given – 4 checkpoints in one range, exportedPosition between cp2 and cp3
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");
    final var t4 = Instant.parse("2025-01-04T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta = metadata(1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 4)));

    // when – exportedPosition=250 is between cp2(pos=200) and cp3(pos=300)
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of(1, 250L));

    // then – cp1 is trimmed, but cp2 is kept as the starting snapshot because its exporter
    // position is guaranteed to be <= the RDBMS exported position
    assertThat(result.globalCheckpointId()).isEqualTo(4);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp2, cp3, cp4);
  }

  @Test
  void shouldTrimCheckpointsBeforeFromTimestamp() {
    // given – 4 checkpoints in one range, from between t2 and t3
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-02-01T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta = metadata(1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 4)));

    // when – from=Feb 15 means cp1(Jan 1) and cp2(Feb 1) are before the requested start
    final var from = Instant.parse("2025-02-15T00:00:00Z");
    final var result = RestorePointResolver.resolve(List.of(meta), from, null, Map.of());

    // then – cp1 and cp2 trimmed, only cp3 and cp4 are restored
    assertThat(result.globalCheckpointId()).isEqualTo(4);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp3, cp4);
  }

  @Test
  void shouldTrimDifferentlyPerPartitionBasedOnExportedPosition() {
    // given – two partitions with 4 checkpoints each and different exported positions
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");
    final var t4 = Instant.parse("2025-01-04T00:00:00Z");

    final var cp1p1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var cp2p1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp3p1 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(201));
    final var cp4p1 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var cp1p2 = entry(1, 110, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(60));
    final var cp2p2 = entry(2, 210, t2, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(111));
    final var cp3p2 = entry(3, 310, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(211));
    final var cp4p2 = entry(4, 410, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(311));

    final var metadataByPartition =
        List.of(
            metadata(1, List.of(cp1p1, cp2p1, cp3p1, cp4p1), List.of(new RangeEntry(1, 4))),
            metadata(2, List.of(cp1p2, cp2p2, cp3p2, cp4p2), List.of(new RangeEntry(1, 4))));

    // when – P1 exportedPosition=250 (between cp2 and cp3), P2 exportedPosition=350 (between cp3
    // and cp4)
    final var result =
        RestorePointResolver.resolve(metadataByPartition, null, null, Map.of(1, 250L, 2, 350L));

    // then – P1 starts from cp2 (last before 250), P2 starts from cp3 (last before 350)
    assertThat(result.globalCheckpointId()).isEqualTo(4);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp2p1, cp3p1, cp4p1);
    assertThat(result.backupsByPartitionId().get(2)).containsExactly(cp3p2, cp4p2);
  }

  @Test
  void shouldNotTrimSafeBackupWhenMarkerIsInterspersedBeforeExportedPosition() {
    // given – a MARKER sits between the last safe backup and the first backup >= exportedPosition
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");
    final var t4 = Instant.parse("2025-01-04T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var marker = entry(2, 200, t2, CheckpointType.MARKER, OptionalLong.empty());
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta = metadata(1, List.of(cp1, marker, cp3, cp4), List.of(new RangeEntry(1, 4)));

    // when – exportedPosition=250 is between marker(pos=200) and cp3(pos=300);
    // cp1(pos=100) is the last backup before exportedPosition and must be kept
    final var result = RestorePointResolver.resolve(List.of(meta), null, null, Map.of(1, 250L));

    // then – cp1 must not be trimmed; it is the safe starting snapshot
    assertThat(result.globalCheckpointId()).isEqualTo(4);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1, cp3, cp4);
  }

  @Test
  void shouldNotTrimSafeBackupWhenMarkerIsInterspersedBeforeFromTimestamp() {
    // given – a MARKER sits between the last backup before 'from' and the first backup >= 'from'
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-15T00:00:00Z");
    final var t3 = Instant.parse("2025-02-01T00:00:00Z");
    final var t4 = Instant.parse("2025-03-01T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(50));
    final var marker = entry(2, 200, t2, CheckpointType.MARKER, OptionalLong.empty());
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(101));
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, OptionalLong.of(301));

    final var meta = metadata(1, List.of(cp1, marker, cp3, cp4), List.of(new RangeEntry(1, 4)));

    // when – from=Jan 20 is between marker(t2=Jan 15) and cp3(t3=Feb 1);
    // the first backup-type checkpoint at or after 'from' is cp3
    final var from = Instant.parse("2025-01-20T00:00:00Z");
    final var result = RestorePointResolver.resolve(List.of(meta), from, null, Map.of());

    // then – cp1 is trimmed (before 'from'), marker is skipped, restore starts from cp3
    assertThat(result.globalCheckpointId()).isEqualTo(4);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp3, cp4);
  }

  private static CheckpointEntry entry(
      final long id,
      final long position,
      final Instant timestamp,
      final CheckpointType type,
      final OptionalLong firstLogPosition) {
    return new CheckpointEntry(id, position, timestamp, type, firstLogPosition);
  }

  private static BackupMetadata metadata(
      final int partitionId,
      final List<CheckpointEntry> checkpoints,
      final List<RangeEntry> ranges) {
    return new BackupMetadata(1, partitionId, Instant.EPOCH, checkpoints, ranges);
  }
}
