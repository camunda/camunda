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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class RestoreSolverTest {

  @Test
  void shouldSolveWithSinglePartitionSingleBackup() {
    // given – one partition, one SCHEDULED_BACKUP checkpoint, one range covering it
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var cp = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var meta = metadata(1, List.of(cp), List.of(new RangeEntry(1, 1)));

    // when
    final var result = RestoreSolver.solve(List.of(meta), null, null, null);

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

    final var cp1p1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2p1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var cp1p2 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2p2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);

    final var metadataByPartition =
        List.of(
            metadata(1, List.of(cp1p1, cp2p1), List.of(new RangeEntry(1, 2))),
            metadata(2, List.of(cp1p2, cp2p2), List.of(new RangeEntry(1, 2))));

    // when – no 'to' timestamp
    final var result = RestoreSolver.solve(metadataByPartition, null, null, null);

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

    final var cp1p1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2p1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var cp1p2 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2p2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);

    final var metadataByPartition =
        List.of(
            metadata(1, List.of(cp1p1, cp2p1), List.of(new RangeEntry(1, 2))),
            metadata(2, List.of(cp1p2, cp2p2), List.of(new RangeEntry(1, 2))));

    // when
    final var result = RestoreSolver.solve(metadataByPartition, null, to, null);

    // then – checkpoint 1 is closest to 'to'
    assertThat(result.globalCheckpointId()).isEqualTo(1);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1p1);
    assertThat(result.backupsByPartitionId().get(2)).containsExactly(cp1p2);
  }

  @Test
  void shouldSkipMarkerCheckpointsInBackupList() {
    // given – partition with MARKER then SCHEDULED_BACKUP checkpoints
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");

    final var marker = entry(1, 100, t1, CheckpointType.MARKER, 50);
    final var backup1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var backup2 = entry(3, 300, t3, CheckpointType.MANUAL_BACKUP, 250);

    final var meta = metadata(1, List.of(marker, backup1, backup2), List.of(new RangeEntry(1, 3)));

    // when – target checkpoint 3
    final var result = RestoreSolver.solve(List.of(meta), null, t3, null);

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

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250);

    final var meta = metadata(1, List.of(cp1, cp2, cp3), List.of(new RangeEntry(1, 3)));

    // when – 'to' is closest to t2
    final var result = RestoreSolver.solve(List.of(meta), null, t2, null);

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

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250);
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, 350);

    final var meta =
        metadata(
            1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 2), new RangeEntry(3, 4)));

    // when – from=t2 means range start timestamp must be <= t2 (not after t2)
    final var result = RestoreSolver.solve(List.of(meta), t2, null, null);

    // then – only checkpoints from range [1,2] are usable
    // range [1,2] start=cp1 at t1, t1.isAfter(t2)=false -> qualifies
    // range [3,4] start=cp3 at t3, t3.isAfter(t2)=true -> filtered out
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1, cp2);
  }

  @Test
  void shouldFilterByExportedPosition() {
    // given – two ranges, 'exportedPosition' excludes the second range
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250);
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, 350);

    final var meta =
        metadata(
            1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 2), new RangeEntry(3, 4)));

    // when – exportedPosition=100 means range start's firstLogPosition must be <= 100
    final var result = RestoreSolver.solve(List.of(meta), null, null, 100L);

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

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);

    final var meta = metadata(1, List.of(cp1, cp2), List.of(new RangeEntry(1, 2)));

    // cp1 at 2025-01-01, from at 2024-01-01 -> cp1.isAfter(from)=true -> skip
    final var fromBeforeAll = Instant.parse("2024-01-01T00:00:00Z");

    // when/then
    assertThatThrownBy(() -> RestoreSolver.solve(List.of(meta), fromBeforeAll, null, null))
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

    final var cp1p1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2p2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);

    final var metadataByPartition =
        List.of(
            metadata(1, List.of(cp1p1), List.of(new RangeEntry(1, 1))),
            metadata(2, List.of(cp2p2), List.of(new RangeEntry(2, 2))));

    // when/then
    assertThatThrownBy(() -> RestoreSolver.solve(metadataByPartition, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Could not find common checkpoint")
        .hasMessageContaining("Restorable checkpoint count per partition");
  }

  @Test
  void shouldHandleManualBackupType() {
    // given – single partition with MANUAL_BACKUP checkpoint
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var cp = entry(1, 100, t1, CheckpointType.MANUAL_BACKUP, 50);
    final var meta = metadata(1, List.of(cp), List.of(new RangeEntry(1, 1)));

    // when
    final var result = RestoreSolver.solve(List.of(meta), null, null, null);

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

    final var marker1 = entry(1, 100, t1, CheckpointType.MARKER, 50);
    final var backup1 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var marker2 = entry(3, 300, t3, CheckpointType.MARKER, 250);
    final var backup2 = entry(4, 400, t4, CheckpointType.MANUAL_BACKUP, 350);
    final var marker3 = entry(5, 500, t5, CheckpointType.MARKER, 450);

    final var meta =
        metadata(
            1, List.of(marker1, backup1, marker2, backup2, marker3), List.of(new RangeEntry(1, 5)));

    // when – target closest to t4 (checkpoint 4)
    final var result = RestoreSolver.solve(List.of(meta), null, t4, null);

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
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150)),
                List.of(new RangeEntry(1, 2))),
            metadata(
                2,
                List.of(
                    entry(1, 110, t1, CheckpointType.SCHEDULED_BACKUP, 60),
                    entry(2, 210, t2, CheckpointType.SCHEDULED_BACKUP, 160)),
                List.of(new RangeEntry(1, 2))),
            metadata(
                3,
                List.of(
                    entry(1, 120, t1, CheckpointType.SCHEDULED_BACKUP, 70),
                    entry(2, 220, t2, CheckpointType.SCHEDULED_BACKUP, 170)),
                List.of(new RangeEntry(1, 2))));

    // when
    final var result = RestoreSolver.solve(metadataByPartition, null, null, null);

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
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250)),
                List.of(new RangeEntry(1, 3))),
            metadata(
                2,
                List.of(
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250),
                    entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, 350)),
                List.of(new RangeEntry(2, 4))));

    // when – no 'to', so picks closest to now; checkpoint 3 is common and closer to now than 2
    final var result = RestoreSolver.solve(metadataByPartition, null, null, null);

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
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250)),
                List.of(new RangeEntry(1, 3))),
            metadata(
                2,
                List.of(
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150),
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250)),
                List.of(new RangeEntry(1, 3))));

    // when
    final var result = RestoreSolver.solve(metadataByPartition, null, to, null);

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

    final var marker = entry(1, 100, t1, CheckpointType.MARKER, 50);
    final var backup = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var markerAfter = entry(3, 300, t3, CheckpointType.MARKER, 250);

    final var meta =
        metadata(1, List.of(marker, backup, markerAfter), List.of(new RangeEntry(1, 3)));

    // when
    final var result = RestoreSolver.solve(List.of(meta), null, t2, null);

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(2);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(backup);
  }

  @Test
  void shouldUseFromAndToTogether() {
    // given – from filters the range, to picks the checkpoint within that range
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-02-01T00:00:00Z");
    final var t3 = Instant.parse("2025-03-01T00:00:00Z");
    final var t4 = Instant.parse("2025-04-01T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250);
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, 350);

    // from=t2: range [1,2] start=cp1 at t1, t1.isAfter(t2)=false -> qualifies
    //          range [3,4] start=cp3 at t3, t3.isAfter(t2)=true -> filtered out
    // to=t1 picks checkpoint closest to t1 within range [1,2] -> checkpoint 1
    final var meta =
        metadata(
            1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 2), new RangeEntry(3, 4)));

    // when
    final var result = RestoreSolver.solve(List.of(meta), t2, t1, null);

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(1);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(cp1);
  }

  @Test
  void shouldHandleExportedPositionFilteringOutAllRanges() {
    // given – exportedPosition is too small for any range
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);

    final var meta = metadata(1, List.of(cp1), List.of(new RangeEntry(1, 1)));

    // when – exportedPosition=10 < cp1.firstLogPosition=50 -> range filtered out
    assertThatThrownBy(() -> RestoreSolver.solve(List.of(meta), null, null, 10L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No usable range found")
        .hasMessageContaining("partition 1")
        .hasMessageContaining("exportedPosition=10");
  }

  @Test
  void shouldSelectBackupsBasedOnToTimestamp() {
    // given – partition has checkpoints [1,2,3,4], but 'to' limits to checkpoint 2
    final var t1 = Instant.parse("2025-01-01T00:00:00Z");
    final var t2 = Instant.parse("2025-01-02T00:00:00Z");
    final var t3 = Instant.parse("2025-01-03T00:00:00Z");
    final var t4 = Instant.parse("2025-01-04T00:00:00Z");

    final var cp1 = entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50);
    final var cp2 = entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150);
    final var cp3 = entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250);
    final var cp4 = entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, 350);

    final var meta = metadata(1, List.of(cp1, cp2, cp3, cp4), List.of(new RangeEntry(1, 4)));

    // when – 'to' is closest to t2
    final var result = RestoreSolver.solve(List.of(meta), null, t2, null);

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
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150)),
                List.of(new RangeEntry(1, 2))),
            metadata(
                2,
                List.of(
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250),
                    entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, 350)),
                List.of(new RangeEntry(3, 4))));

    // when/then – from=t2 filters out P2's range (t3 > t2)
    assertThatThrownBy(() -> RestoreSolver.solve(metadataByPartition, t2, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No usable range found")
        .hasMessageContaining("partition 2");
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
                    entry(1, 100, t1, CheckpointType.SCHEDULED_BACKUP, 50),
                    entry(2, 200, t2, CheckpointType.SCHEDULED_BACKUP, 150)),
                List.of(new RangeEntry(1, 2))),
            metadata(
                2,
                List.of(
                    entry(3, 300, t3, CheckpointType.SCHEDULED_BACKUP, 250),
                    entry(4, 400, t4, CheckpointType.SCHEDULED_BACKUP, 350)),
                List.of(new RangeEntry(3, 4))));

    // when/then
    assertThatThrownBy(() -> RestoreSolver.solve(metadataByPartition, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Could not find common checkpoint");
  }

  private static CheckpointEntry entry(
      final long id,
      final long position,
      final Instant timestamp,
      final CheckpointType type,
      final long firstLogPosition) {
    return new CheckpointEntry(id, position, timestamp, type, firstLogPosition);
  }

  private static BackupMetadata metadata(
      final int partitionId,
      final List<CheckpointEntry> checkpoints,
      final List<RangeEntry> ranges) {
    return new BackupMetadata(1, partitionId, Instant.EPOCH, checkpoints, ranges);
  }
}
