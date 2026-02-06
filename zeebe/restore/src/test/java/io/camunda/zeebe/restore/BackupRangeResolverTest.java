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

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.BackupRangeMarker;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.BackupRangeResolver.PartitionRestoreInfo;
import io.camunda.zeebe.restore.BackupRangeResolver.ReachabilityValidator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class BackupRangeResolverTest {

  /**
   * Creates a contiguous list of backups from checkpoint specifications. Automatically calculates
   * firstLogPosition to maintain contiguity: - First backup starts at position 1 - Each subsequent
   * backup starts at previousCheckpointPosition + 1
   */
  private List<BackupStatus> createContiguousBackups(final CheckpointSpec... specs) {
    if (specs.length == 0) {
      return List.of();
    }

    final var backups = new java.util.ArrayList<BackupStatus>(specs.length);
    long previousCheckpointPosition = 0; // First backup will start at 1

    for (final var spec : specs) {
      final long firstLogPosition = previousCheckpointPosition + 1;
      backups.add(
          createBackupWithLogPosition(
              spec.checkpointId, spec.checkpointPosition, firstLogPosition, Instant.now()));
      previousCheckpointPosition = spec.checkpointPosition;
    }

    return backups;
  }

  /**
   * Creates a list of backups with an intentional gap in log positions.
   *
   * @param gapAtIndex the index where the gap should occur (gap before this backup)
   * @param gapSize the size of the gap in log positions
   */
  private List<BackupStatus> createBackupsWithGap(
      final int gapAtIndex, final long gapSize, final CheckpointSpec... specs) {
    if (specs.length == 0 || gapAtIndex < 0 || gapAtIndex >= specs.length) {
      return List.of();
    }

    final var backups = new java.util.ArrayList<BackupStatus>(specs.length);
    long previousCheckpointPosition = 0;

    for (int i = 0; i < specs.length; i++) {
      final var spec = specs[i];
      long firstLogPosition = previousCheckpointPosition + 1;

      // Add gap before this backup
      if (i == gapAtIndex) {
        firstLogPosition += gapSize;
      }

      backups.add(
          createBackupWithLogPosition(
              spec.checkpointId, spec.checkpointPosition, firstLogPosition, Instant.now()));
      previousCheckpointPosition = spec.checkpointPosition;
    }

    return backups;
  }

  // Legacy methods kept for compatibility with existing simple tests
  private BackupStatus createBackup(
      final long checkpointId, final long checkpointPosition, final Instant timestamp) {
    final BackupIdentifier id = new BackupIdentifierImpl(1, 1, checkpointId);
    final BackupDescriptor descriptor =
        new BackupDescriptorImpl(
            checkpointPosition, 3, "8.7.0", timestamp, CheckpointType.SCHEDULED_BACKUP);
    return new BackupStatusImpl(
        id,
        Optional.of(descriptor),
        BackupStatusCode.COMPLETED,
        Optional.empty(),
        Optional.of(timestamp),
        Optional.of(timestamp));
  }

  private BackupStatus createBackupWithLogPosition(
      final long checkpointId,
      final long checkpointPosition,
      final long firstLogPosition,
      final Instant timestamp) {
    final BackupIdentifier id = new BackupIdentifierImpl(1, 1, checkpointId);
    final BackupDescriptor descriptor =
        new BackupDescriptorImpl(
            Optional.of("snapshot-" + checkpointId),
            OptionalLong.of(firstLogPosition),
            checkpointPosition,
            3,
            "8.7.0",
            timestamp,
            CheckpointType.SCHEDULED_BACKUP);
    return new BackupStatusImpl(
        id,
        Optional.of(descriptor),
        BackupStatusCode.COMPLETED,
        Optional.empty(),
        Optional.of(timestamp),
        Optional.of(timestamp));
  }

  /** Checkpoint specification: checkpointId and checkpointPosition */
  record CheckpointSpec(long checkpointId, long checkpointPosition) {}

  @Nested
  class ValidateGlobalCheckpointReachability {
    @Test
    void shouldValidateGlobalCheckpointReachabilityWhenAllPartitionsCanReach() {
      // given - global checkpoint 105, all partitions can reach it, 3 nodes expected
      final long globalCheckpoint = 105L;
      final var safeStartByPartition = Map.of(1, 100L, 2, 100L, 3, 100L);

      final var completeBackupList =
          List.of(createBackup(100, 1000, Instant.now()), createBackup(105, 1050, Instant.now()));

      // All nodes have backups for their partitions
      // needed due to lack of covariance
      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(1, completeBackupList, 2, completeBackupList, 3, completeBackupList);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(
              1, new BackupRange.Complete(100, 105),
              2, new BackupRange.Complete(100, 105),
              3, new BackupRange.Complete(100, 105));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldFailWhenPartitionSafeStartBeyondGlobalCheckpoint() {
      // given - partition 3's safe start (110) is beyond global checkpoint (105)
      final long globalCheckpoint = 105L;
      final var safeStartByPartition = Map.of(1, 100L, 2, 100L, 3, 110L); // partition 3 lags

      // needed due to lack of covariance
      final Map<Integer, SequencedCollection<BackupStatus>> backupsByPartition =
          Map.of(
              1, List.of(),
              2, List.of(),
              3, List.of());

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(
              1, new BackupRange.Complete(100, 105),
              2, new BackupRange.Complete(100, 105),
              3, new BackupRange.Complete(110, 115));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByPartition, rangesByPartition);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft())
          .contains("Partition 3")
          .contains("safe start checkpoint 110 is beyond global checkpoint 105");
    }

    @Test
    void shouldFailWhenPartitionHasIncompleteRange() {
      // given - partition 2 has incomplete range with deletions
      final long globalCheckpoint = 105L;
      final var safeStartByPartition = Map.of(1, 100L, 2, 100L, 3, 100L);

      // needed due to lack of covariance
      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(
              1, List.of(),
              2, List.of(),
              3, List.of());

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(
              1, new BackupRange.Complete(100, 105),
              2, new BackupRange.Incomplete(100, 105, Set.of(103L)), // has deletion
              3, new BackupRange.Complete(100, 105));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft())
          .contains("Partition 2")
          .contains("has deletions")
          .contains("[103]");
    }

    @Test
    void shouldFailWhenNodePartitionHasLogPositionGap() {
      // given - Node0P1 and Node1P2 both have gaps in log positions
      final long globalCheckpoint = 105L;
      final var safeStartByPartition = Map.of(1, 100L, 2, 100L);

      // Node0P1 has a gap of 30 positions before backup 103
      final var node0Backups =
          createBackupsWithGap(
              1,
              30, // Gap before index 1
              new CheckpointSpec(100, 1000),
              new CheckpointSpec(103, 1030),
              new CheckpointSpec(105, 1050));

      // Node1P2 has a gap of 99 positions before backup 105
      final var node1Backups =
          createBackupsWithGap(
              1,
              99, // Gap before index 1
              new CheckpointSpec(100, 2000),
              new CheckpointSpec(105, 2050));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(
              1, node0Backups,
              2, node1Backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(
              1, new BackupRange.Complete(100, 105),
              2, new BackupRange.Complete(100, 105));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft())
          .contains("Cannot restore to global checkpoint 10")
          .contains(
              "Partition 1: has gap in log positions - backup 100 ends at position 1000, but backup 103 starts at position 1031 (expected 1001)")
          .contains(
              "Partition 2: has gap in log positions - backup 100 ends at position 2000, but backup 105 starts at position 2100 (expected 2001)");
    }

    @Test
    void shouldFailWhenFirstBackupIsAfterSafeStart() {
      // given - first backup is at 1950, but safeStart is 1900
      final long globalCheckpoint = 2100L;
      final var safeStartByPartition = Map.of(1, 1900L);

      // Backups start at 1950, missing coverage from 1900
      final var backups =
          createContiguousBackups(
              new CheckpointSpec(1950, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2100, 3500));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(1, backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(1, new BackupRange.Complete(1950, 2100));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then - should fail because first backup doesn't cover safeStart
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft())
          .contains("Cannot restore to global checkpoint 2100")
          .contains(
              "Partition 1: backup range [1950, 2100] does not cover required range [1900, 2100]")
          .contains("Partition 1s first backup at checkpoint 1950 is after safe start 1900");
    }

    @Test
    void shouldFailWhenLastBackupIsBeforeGlobalCheckpoint() {
      // given - last backup is at 2050, but globalCheckpoint is 2100
      final long globalCheckpoint = 2100L;
      final var safeStartByPartition = Map.of(1, 1900L);

      // Backups end at 2050, missing coverage to 2100
      final var backups =
          createContiguousBackups(
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2050, 3500));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(1, backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(1, new BackupRange.Complete(1900, 2050));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then - should fail because last backup doesn't reach globalCheckpoint
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft())
          .contains("Partition 1")
          .contains("last backup at checkpoint 2050 is before global checkpoint 2100");
    }

    @Test
    void shouldPassWhenBackupsExactlyCoverRequiredRange() {
      // given - backups exactly cover [safeStart, globalCheckpoint]
      final long globalCheckpoint = 2100L;
      final var safeStartByPartition = Map.of(1, 1900L);

      // Backups exactly cover [1900, 2100]
      final var backups =
          createContiguousBackups(
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2100, 3500));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(1, backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(1, new BackupRange.Complete(1900, 2100));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then - should pass
      assertThat(result.isRight())
          .as("Error: %s", result.isLeft() ? result.getLeft() : "")
          .isTrue();
    }

    @Test
    void shouldPassWhenBackupsExceedRequiredRange() {
      // given - backups cover more than [safeStart, globalCheckpoint]
      final long globalCheckpoint = 2100L;
      final var safeStartByPartition = Map.of(1, 1900L);

      // Backups cover [1800, 2200] which includes [1900, 2100]
      final var backups =
          createContiguousBackups(
              new CheckpointSpec(1800, 2000),
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2100, 3500),
              new CheckpointSpec(2200, 4000));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(1, backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(1, new BackupRange.Complete(1800, 2200));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then - should pass because range is fully covered
      assertThat(result.isRight())
          .as("Error: %s", result.isLeft() ? result.getLeft() : "")
          .isTrue();
    }

    @Test
    void shouldFailWhenDifferentNodesHaveDifferentBackupsForSamePartition() {
      // given - Node0 and Node1 both have partition 1, but different backup sets
      final long globalCheckpoint = 2100L;
      final var safeStartByPartition = Map.of(1, 1900L);

      // Node0 has backups [1900, 2000, 2100]
      final var node0Backups =
          createContiguousBackups(
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2100, 3500));

      // Node1 has backups [1900, 2050, 2100] - different middle checkpoint
      final var node1Backups =
          createContiguousBackups(
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2050, 3200),
              new CheckpointSpec(2100, 3700));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(
              1, node0Backups,
              2, node1Backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(1, new BackupRange.Complete(1900, 2100));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then - should pass as both nodes cover the required range, just with different
      // intermediate backups
      assertThat(result.isRight())
          .as("Error: %s", result.isLeft() ? result.getLeft() : "")
          .isTrue();
    }

    @Test
    void shouldFailWhenSafeStartEqualsGlobalCheckpointButBackupMissing() {
      // given - single checkpoint restore, but that checkpoint is missing
      final long globalCheckpoint = 2000L;
      final var safeStartByPartition = Map.of(1, 2000L); // Single checkpoint

      // Only have backups before and after, not the exact one
      final var backups =
          createContiguousBackups(new CheckpointSpec(1900, 2500), new CheckpointSpec(2100, 3500));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(1, backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(1, new BackupRange.Complete(1900, 2100));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then - should fail because we don't have backup at checkpoint 2000
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft())
          .contains("Cannot restore to global checkpoint 2000.")
          .containsAnyOf("Partition 1 has no backups in range [2000, 2000]");
    }

    @Test
    void shouldPassWhenSafeStartEqualsGlobalCheckpointWithSingleBackup() {
      // given - single checkpoint restore with exactly that backup
      final long globalCheckpoint = 2000L;
      final var safeStartByPartition = Map.of(1, 2000L);

      final var backups = createContiguousBackups(new CheckpointSpec(2000, 3000));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByNodePartition =
          Map.of(1, backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(1, new BackupRange.Complete(2000, 2000));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByNodePartition, rangesByPartition);

      // then - should pass
      assertThat(result.isRight())
          .as("Error: %s", result.isLeft() ? result.getLeft() : "")
          .isTrue();
    }

    @Test
    void shouldFailWhenMixedValidityAcrossPartitions() {
      // given - P1 valid, P2 has gap, P3 valid - should fail overall
      final long globalCheckpoint = 2100L;
      final var safeStartByPartition = Map.of(1, 1900L, 2, 1900L, 3, 1900L);

      // P1: Valid contiguous backups
      final var p1Backups =
          createContiguousBackups(
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2100, 3500));

      // P2: Has a gap (199 positions before the last backup)
      final var p2Backups =
          createBackupsWithGap(
              1,
              199, // Gap before index 1 (backup 2100)
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2100, 3500));

      // P3: Valid contiguous backups
      final var p3Backups =
          createContiguousBackups(
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2100, 3500));

      final Map<Integer, SequencedCollection<BackupStatus>> backupsByPartition =
          Map.of(
              1, p1Backups,
              2, p2Backups,
              3, p3Backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(
              1, new BackupRange.Complete(1900, 2100),
              2, new BackupRange.Complete(1900, 2100),
              3, new BackupRange.Complete(1900, 2100));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByPartition, rangesByPartition);

      // then - should fail because P2 has a gap
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft())
          .contains("Cannot restore to global checkpoint 2100.")
          .contains(
              "Partition 2: has gap in log positions - backup 1900 ends at position 2500, but backup 2100 starts at position 2700 (expected 2501)");
    }

    @Test
    void shouldValidateComplexMultiNodeMultiPartitionScenario() {
      // given - 3 nodes × 3 partitions, each node in replication group for each partition
      // Checkpoint IDs: 1500 + i * 100 (i = 0,1,2,3,4,5,6)
      // Exported positions: P1=2900, P2=3000, P3=4500
      // Checkpoints at positions: P1=[100,500,900,2000,2500,3000,3500]
      //                           P2=[100,500,900,2000,2500,3102,3500]
      //                           P3=[100,500,900,2000,2500,3000,4500]

      final long globalCheckpoint = 2100L; // Checkpoint ID 1500 + 6*100 = 2100

      // Exported positions per partition - safe start at checkpoint 2000
      final var safeStartByPartition = Map.of(1, 2000L, 2, 2000L, 3, 2000L);

      // P1: positions [100, 500, 900, 2000, 2500, 3000, 3500]
      final var p1Backups =
          createContiguousBackups(
              new CheckpointSpec(1500, 100),
              new CheckpointSpec(1600, 500),
              new CheckpointSpec(1700, 900),
              new CheckpointSpec(1800, 2000),
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2100, 3500));

      // P2: positions [100, 500, 900, 2000, 2500, 3102, 3500] - different position for 2000
      final var p2Backups =
          createContiguousBackups(
              new CheckpointSpec(1500, 100),
              new CheckpointSpec(1600, 500),
              new CheckpointSpec(1700, 900),
              new CheckpointSpec(1800, 2000),
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3102),
              new CheckpointSpec(2100, 3500));

      // P3: positions [100, 500, 900, 2000, 2500, 3000, 4500] - different position for 2100
      final var p3Backups =
          createContiguousBackups(
              new CheckpointSpec(1500, 100),
              new CheckpointSpec(1600, 500),
              new CheckpointSpec(1700, 900),
              new CheckpointSpec(1800, 2000),
              new CheckpointSpec(1900, 2500),
              new CheckpointSpec(2000, 3000),
              new CheckpointSpec(2100, 4500));

      // Each node backs up each partition (full replication)
      final Map<Integer, SequencedCollection<BackupStatus>> backupsByPartition =
          Map.of(
              1, p1Backups,
              2, p2Backups,
              3, p3Backups);

      final Map<Integer, BackupRange> rangesByPartition =
          Map.of(
              1, new BackupRange.Complete(1500, 2100),
              2, new BackupRange.Complete(1500, 2100),
              3, new BackupRange.Complete(1500, 2100));

      // when
      final var result =
          BackupRangeResolver.validateGlobalCheckpointReachability(
              globalCheckpoint, safeStartByPartition, backupsByPartition, rangesByPartition);

      // then - validation should succeed
      assertThat(result.isRight())
          .as("Error: %s", result.isLeft() ? result.getLeft() : "")
          .isTrue();
    }
  }

  @Nested
  class FindLatestBackup {
    @Test
    void shouldFindLatestBackupBeforeTimestamp() {
      // given
      final var timestamp1 = Instant.parse("2026-01-20T10:00:00Z");
      final var timestamp2 = Instant.parse("2026-01-20T11:00:00Z");
      final var timestamp3 = Instant.parse("2026-01-20T12:00:00Z");
      final var targetTimestamp = Instant.parse("2026-01-20T11:30:00Z");

      final var backups =
          List.of(
              createBackup(1, 100, timestamp1),
              createBackup(2, 200, timestamp2),
              createBackup(3, 300, timestamp3));

      // when
      final var result = BackupRangeResolver.findLatestBackupBefore(targetTimestamp, backups);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().id().checkpointId()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyWhenNoBackupBeforeTimestamp() {
      // given
      final var timestamp = Instant.parse("2026-01-20T12:00:00Z");
      final var targetTimestamp = Instant.parse("2026-01-20T10:00:00Z");

      final var backups = List.of(createBackup(1, 100, timestamp));

      // when
      final var result = BackupRangeResolver.findLatestBackupBefore(targetTimestamp, backups);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnBackupAtExactTimestamp() {
      // given
      final var timestamp = Instant.parse("2026-01-20T12:00:00Z");
      final var backups = List.of(createBackup(1, 100, timestamp));

      // when
      final var result = BackupRangeResolver.findLatestBackupBefore(timestamp, backups);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().id().checkpointId()).isEqualTo(1);
    }
  }

  @Nested
  class SafeStart {

    @Test
    void shouldFindSafeStartCheckpointBasedOnExportedPosition() {
      // given
      final var exportedPosition = 250L;
      final var backups =
          List.of(
              createBackup(1, 100, Instant.now()),
              createBackup(2, 200, Instant.now()),
              createBackup(3, 300, Instant.now()));

      // when
      final var result = BackupRangeResolver.findSafeStartCheckpoint(exportedPosition, backups);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyWhenNoSafeCheckpoint() {
      // given
      final var exportedPosition = 50L;
      final var backups = List.of(createBackup(1, 100, Instant.now()));

      // when
      final var result = BackupRangeResolver.findSafeStartCheckpoint(exportedPosition, backups);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetInformationPerPartition {

    private static final int PARTITION_ID = 1;
    private static final int NODE_ID = 0;
    private static final int PARTITION_COUNT = 3;

    private TestBackupStore store;
    private BackupRangeResolver resolver;

    private void setupStore() {
      store = new TestBackupStore();
      resolver = new BackupRangeResolver(store);
    }

    @Test
    void shouldReturnPartitionRestoreInfoWhenValidDataExists() {
      // given
      setupStore();

      final var timestamp1 = Instant.parse("2026-01-20T10:00:00Z");
      final var timestamp2 = timestamp1.plus(Duration.ofHours(1));
      final var timestamp3 = timestamp1.plus(Duration.ofHours(2));

      // Add range markers (Start at checkpoint 100, End at checkpoint 300)
      store.storeRangeMarker(PARTITION_ID, new BackupRangeMarker.Start(100L));
      store.storeRangeMarker(PARTITION_ID, new BackupRangeMarker.End(300L));

      // Add backups with timestamps and checkpoint positions
      store.addBackup(PARTITION_ID, 100L, 1000L, timestamp1);
      store.addBackup(PARTITION_ID, 200L, 2000L, timestamp2);
      store.addBackup(PARTITION_ID, 300L, 3000L, timestamp3);

      // Time interval that covers all backups
      final var from = Instant.parse("2026-01-20T10:30:00Z");
      final var to = Instant.parse("2026-01-20T12:00:00Z");

      // Exported position that makes checkpoint 200 the safe start (position <= 2500)
      final var lastExportedPosition = 2500L;

      // when
      final var result =
          resolver.getInformationPerPartition(PARTITION_ID, from, to, lastExportedPosition);

      // then
      assertThat(result.partition()).isEqualTo(PARTITION_ID);
      assertThat(result.safeStart()).isEqualTo(200L);
      assertThat(result.completRange()).isInstanceOf(BackupRange.Complete.class);
      // we should extend the range to make sure to use the first backup as well.
      assertThat(result.backupStatuses()).hasSize(3);
    }

    @Test
    void shouldThrowWhenNoBackupRangeFoundForPartition() {
      // given
      setupStore();

      // No range markers added - empty ranges
      final var from = Instant.parse("2026-01-20T09:00:00Z");
      final var to = Instant.parse("2026-01-20T13:00:00Z");
      final var lastExportedPosition = 2500L;

      // when/then
      assertThatThrownBy(
              () ->
                  resolver.getInformationPerPartition(PARTITION_ID, from, to, lastExportedPosition))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No backup range found for partition " + PARTITION_ID);
    }

    @Test
    void shouldThrowWhenNoSafeStartCheckpointFound() {
      // given
      setupStore();

      final var timestamp1 = Instant.parse("2026-01-20T10:00:00Z");
      final var timestamp2 = Instant.parse("2026-01-20T11:00:00Z");

      // Add range markers
      store.storeRangeMarker(PARTITION_ID, new BackupRangeMarker.Start(100L));
      store.storeRangeMarker(PARTITION_ID, new BackupRangeMarker.End(200L));

      // Add backups
      store.addBackup(PARTITION_ID, 100L, 1000L, timestamp1);
      store.addBackup(PARTITION_ID, 200L, 2000L, timestamp2);

      // Time interval that covers all backups
      final var from = Instant.parse("2026-01-20T09:00:00Z");
      final var to = Instant.parse("2026-01-20T13:00:00Z");

      // Exported position that is too low - no backup has checkpointPosition <= 500
      final var lastExportedPosition = 500L;

      // when/then
      assertThatThrownBy(
              () ->
                  resolver.getInformationPerPartition(PARTITION_ID, from, to, lastExportedPosition))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No safe start checkpoint found for partition " + PARTITION_ID);
    }

    @Test
    void shouldThrowWhenTimeIntervalNotCoveredByRange() {
      // given
      setupStore();

      final var timestamp1 = Instant.parse("2026-01-20T10:00:00Z");
      final var timestamp2 = Instant.parse("2026-01-20T11:00:00Z");

      // Add range markers
      store.storeRangeMarker(PARTITION_ID, new BackupRangeMarker.Start(100L));
      store.storeRangeMarker(PARTITION_ID, new BackupRangeMarker.End(200L));

      // Add backups
      store.addBackup(PARTITION_ID, 100L, 1000L, timestamp1);
      store.addBackup(PARTITION_ID, 200L, 2000L, timestamp2);

      // Time interval that starts before the first backup
      final var from = Instant.parse("2026-01-20T08:00:00Z");
      final var to = Instant.parse("2026-01-20T09:00:00Z");

      final var lastExportedPosition = 2500L;

      // when/then
      assertThatThrownBy(
              () ->
                  resolver.getInformationPerPartition(PARTITION_ID, from, to, lastExportedPosition))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No backup range found for partition " + PARTITION_ID);
    }

    /** Simple test BackupStore implementation for testing getInformationPerPartition */
    private static final class TestBackupStore implements io.camunda.zeebe.backup.api.BackupStore {
      private final Map<BackupIdentifier, Backup> backups =
          new java.util.concurrent.ConcurrentHashMap<>();
      private final Map<Integer, java.util.Collection<BackupRangeMarker>> rangeMarkersByPartition =
          new java.util.concurrent.ConcurrentHashMap<>();

      void addBackup(
          final int partitionId,
          final long checkpointId,
          final long checkpointPosition,
          final Instant timestamp) {
        final BackupIdentifier id = new BackupIdentifierImpl(NODE_ID, partitionId, checkpointId);
        final BackupDescriptor descriptor =
            new BackupDescriptorImpl(
                checkpointPosition,
                PARTITION_COUNT,
                "8.7.0",
                timestamp,
                CheckpointType.SCHEDULED_BACKUP);
        final Backup backup = new TestBackup(id, descriptor);
        backups.put(id, backup);
      }

      @Override
      public CompletableFuture<Void> save(final Backup backup) {
        backups.put(backup.id(), backup);
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
        final var backup = backups.get(id);
        if (backup != null) {
          return CompletableFuture.completedFuture(
              new BackupStatusImpl(
                  id,
                  Optional.of(backup.descriptor()),
                  BackupStatusCode.COMPLETED,
                  Optional.empty(),
                  Optional.of(backup.descriptor().checkpointTimestamp()),
                  Optional.of(backup.descriptor().checkpointTimestamp())));
        }
        return CompletableFuture.completedFuture(
            new BackupStatusImpl(
                id,
                Optional.empty(),
                BackupStatusCode.DOES_NOT_EXIST,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
      }

      @Override
      public CompletableFuture<java.util.Collection<BackupStatus>> list(
          final io.camunda.zeebe.backup.api.BackupIdentifierWildcard wildcard) {
        final var matchingBackups =
            backups.values().stream()
                .filter(backup -> wildcard.matches(backup.id()))
                .map(
                    backup ->
                        (BackupStatus)
                            new BackupStatusImpl(
                                backup.id(),
                                Optional.of(backup.descriptor()),
                                BackupStatusCode.COMPLETED,
                                Optional.empty(),
                                Optional.of(backup.descriptor().checkpointTimestamp()),
                                Optional.of(backup.descriptor().checkpointTimestamp())))
                .toList();
        return CompletableFuture.completedFuture(matchingBackups);
      }

      @Override
      public CompletableFuture<Void> delete(final BackupIdentifier id) {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletableFuture<Backup> restore(
          final BackupIdentifier id, final java.nio.file.Path targetFolder) {
        return CompletableFuture.completedFuture(backups.get(id));
      }

      @Override
      public CompletableFuture<BackupStatusCode> markFailed(
          final BackupIdentifier id, final String failureReason) {
        return CompletableFuture.completedFuture(BackupStatusCode.FAILED);
      }

      @Override
      public CompletableFuture<java.util.Collection<BackupRangeMarker>> rangeMarkers(
          final int partitionId) {
        return CompletableFuture.completedFuture(
            rangeMarkersByPartition.getOrDefault(partitionId, List.of()));
      }

      @Override
      public CompletableFuture<Void> storeRangeMarker(
          final int partitionId, final BackupRangeMarker marker) {
        rangeMarkersByPartition.compute(
            partitionId,
            (k, v) -> {
              if (v == null) {
                return new java.util.ArrayList<>(List.of(marker));
              }
              final var markers = new java.util.ArrayList<>(v);
              markers.add(marker);
              return markers;
            });
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletableFuture<Void> deleteRangeMarker(
          final int partitionId, final BackupRangeMarker marker) {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletableFuture<Void> closeAsync() {
        return CompletableFuture.completedFuture(null);
      }
    }

    /** Simple Backup implementation for testing */
    private record TestBackup(BackupIdentifier id, BackupDescriptor descriptor) implements Backup {
      @Override
      public NamedFileSet snapshot() {
        return new NamedFileSetImpl(Map.of());
      }

      @Override
      public NamedFileSet segments() {
        return new NamedFileSetImpl(Map.of());
      }
    }
  }

  @Nested
  class ComputeGlobalCheckpointId {

    @Test
    void shouldReturnMaxCommonCheckpointWhenAllPartitionsShareCheckpoints() {
      // given - 3 partitions, all have checkpoints [100, 200, 300]
      final var p1Backups =
          List.of(
              createBackupForPartition(1, 100, 1000),
              createBackupForPartition(1, 200, 2000),
              createBackupForPartition(1, 300, 3000));

      final var p2Backups =
          List.of(
              createBackupForPartition(2, 100, 1000),
              createBackupForPartition(2, 200, 2000),
              createBackupForPartition(2, 300, 3000));

      final var p3Backups =
          List.of(
              createBackupForPartition(3, 100, 1000),
              createBackupForPartition(3, 200, 2000),
              createBackupForPartition(3, 300, 3000));

      final var restoreInfos =
          List.of(
              new PartitionRestoreInfo(1, 100L, new BackupRange.Complete(100, 300), p1Backups),
              new PartitionRestoreInfo(2, 100L, new BackupRange.Complete(100, 300), p2Backups),
              new PartitionRestoreInfo(3, 100L, new BackupRange.Complete(100, 300), p3Backups));

      // when
      final var result = ReachabilityValidator.computeGlobalCheckpointId(restoreInfos);

      // then
      assertThat(result).isEqualTo(300L);
    }

    @Test
    void shouldReturnMaxCommonCheckpointWhenPartitionsHaveDifferentCheckpointSets() {
      // given - partitions have overlapping but different checkpoint sets
      // P1: [100, 200, 300, 400]
      // P2: [100, 200, 300]
      // P3: [100, 200, 300, 500]
      // Common: [100, 200, 300], max = 300
      final var p1Backups =
          List.of(
              createBackupForPartition(1, 100, 1000),
              createBackupForPartition(1, 200, 2000),
              createBackupForPartition(1, 300, 3000),
              createBackupForPartition(1, 400, 4000));

      final var p2Backups =
          List.of(
              createBackupForPartition(2, 100, 1000),
              createBackupForPartition(2, 200, 2000),
              createBackupForPartition(2, 300, 3000));

      final var p3Backups =
          List.of(
              createBackupForPartition(3, 100, 1000),
              createBackupForPartition(3, 200, 2000),
              createBackupForPartition(3, 300, 3000),
              createBackupForPartition(3, 500, 5000));

      final var restoreInfos =
          List.of(
              new PartitionRestoreInfo(1, 100L, new BackupRange.Complete(100, 400), p1Backups),
              new PartitionRestoreInfo(2, 100L, new BackupRange.Complete(100, 300), p2Backups),
              new PartitionRestoreInfo(3, 100L, new BackupRange.Complete(100, 500), p3Backups));

      // when
      final var result = ReachabilityValidator.computeGlobalCheckpointId(restoreInfos);

      // then
      assertThat(result).isEqualTo(300L);
    }

    @Test
    void shouldReturnSingleCommonCheckpointWhenOnlyOneIsShared() {
      // given - only checkpoint 200 is common to all partitions
      // P1: [100, 200]
      // P2: [200, 300]
      // Common: [200]
      final var p1Backups =
          List.of(createBackupForPartition(1, 100, 1000), createBackupForPartition(1, 200, 2000));

      final var p2Backups =
          List.of(createBackupForPartition(2, 200, 2000), createBackupForPartition(2, 300, 3000));

      final var restoreInfos =
          List.of(
              new PartitionRestoreInfo(1, 100L, new BackupRange.Complete(100, 200), p1Backups),
              new PartitionRestoreInfo(2, 200L, new BackupRange.Complete(200, 300), p2Backups));

      // when
      final var result = ReachabilityValidator.computeGlobalCheckpointId(restoreInfos);

      // then
      assertThat(result).isEqualTo(200L);
    }

    @Test
    void shouldThrowWhenNoCommonCheckpointExists() {
      // given - no common checkpoints between partitions
      // P1: [100, 200]
      // P2: [300, 400]
      final var p1Backups =
          List.of(createBackupForPartition(1, 100, 1000), createBackupForPartition(1, 200, 2000));

      final var p2Backups =
          List.of(createBackupForPartition(2, 300, 3000), createBackupForPartition(2, 400, 4000));

      final var restoreInfos =
          List.of(
              new PartitionRestoreInfo(1, 100L, new BackupRange.Complete(100, 200), p1Backups),
              new PartitionRestoreInfo(2, 300L, new BackupRange.Complete(300, 400), p2Backups));

      // when/then
      assertThatThrownBy(() -> ReachabilityValidator.computeGlobalCheckpointId(restoreInfos))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No common checkpoint found across all partitions");
    }

    @Test
    void shouldThrowWhenPartitionHasEmptyBackups() {
      // given - one partition has no backups
      final var p1Backups =
          List.of(createBackupForPartition(1, 100, 1000), createBackupForPartition(1, 200, 2000));

      final List<BackupStatus> p2Backups = List.of();

      final var restoreInfos =
          List.of(
              new PartitionRestoreInfo(1, 100L, new BackupRange.Complete(100, 200), p1Backups),
              new PartitionRestoreInfo(2, 100L, new BackupRange.Complete(100, 200), p2Backups));

      // when/then
      assertThatThrownBy(() -> ReachabilityValidator.computeGlobalCheckpointId(restoreInfos))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No common checkpoint found across all partitions");
    }

    @Test
    void shouldWorkWithSinglePartition() {
      // given - single partition with multiple checkpoints
      final var p1Backups =
          List.of(
              createBackupForPartition(1, 100, 1000),
              createBackupForPartition(1, 200, 2000),
              createBackupForPartition(1, 300, 3000));

      final var restoreInfos =
          List.of(new PartitionRestoreInfo(1, 100L, new BackupRange.Complete(100, 300), p1Backups));

      // when
      final var result = ReachabilityValidator.computeGlobalCheckpointId(restoreInfos);

      // then
      assertThat(result).isEqualTo(300L);
    }

    private BackupStatus createBackupForPartition(
        final int partitionId, final long checkpointId, final long checkpointPosition) {
      final BackupIdentifier id = new BackupIdentifierImpl(1, partitionId, checkpointId);
      final BackupDescriptor descriptor =
          new BackupDescriptorImpl(
              checkpointPosition, 3, "8.7.0", Instant.now(), CheckpointType.SCHEDULED_BACKUP);
      return new BackupStatusImpl(
          id,
          Optional.of(descriptor),
          BackupStatusCode.COMPLETED,
          Optional.empty(),
          Optional.of(Instant.now()),
          Optional.of(Instant.now()));
    }
  }
}
