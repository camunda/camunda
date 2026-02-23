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
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupRangeMarker;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.restore.BackupRangeResolver.GlobalRestoreInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@NullMarked
final class BackupRangeResolverTest {

  private static final int NODE_ID = 0;
  private static final Instant BASE_TIME = Instant.parse("2026-01-20T10:00:00Z");

  /** Direct executor for synchronous test execution */
  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  private TestBackupStore store;
  private BackupRangeResolver resolver;
  private final CheckpointIdGenerator checkIdGenerator = new CheckpointIdGenerator();

  @BeforeEach
  void setUp() {
    store = new TestBackupStore();
    resolver = new BackupRangeResolver(store);
  }

  @Test
  void shouldRestoreSinglePartitionWithContiguousBackups() {
    // given - single partition with 3 contiguous backups
    // Time interval covers all backups
    store
        .forPartition(1)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);

    // when - time interval covers all backups [0, 60]
    final var result = resolve(1, minutesAfterBase(0), minutesAfterBase(60), Map.of(1, 2500L));

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(300L);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(200L, 300L);
  }

  @Test
  void shouldRestoreMultiplePartitionsWithSharedCheckpoints() {
    // given - 3 partitions, all have checkpoints [100, 200, 300]
    for (int p = 1; p <= 3; p++) {
      store
          .forPartition(p)
          .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);
    }

    // when - time interval covers all backups
    final var result =
        resolve(3, minutesAfterBase(0), minutesAfterBase(60), Map.of(1, 2500L, 2, 2500L, 3, 2500L));

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(300L);
    for (int p = 1; p <= 3; p++) {
      assertThat(result.backupsByPartitionId().get(p)).containsExactly(200L, 300L);
    }
  }

  @Test
  void shouldFindGlobalCheckpointAsMaxCommonAcrossPartitions() {
    // given - partitions have different checkpoint sets
    // P1: [100, 200, 300] - all within time window
    // P2: [100, 200, 300] - same
    // P3: [100, 200, 300] - same
    // All share [100, 200, 300], max common = 300
    store
        .forPartition(1)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(40), 3);

    store
        .forPartition(2)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(40), 3);

    store
        .forPartition(3)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(40), 3);

    // when
    final var result =
        resolve(3, minutesAfterBase(0), minutesAfterBase(40), Map.of(1, 2500L, 2, 2500L, 3, 2500L));

    // then - global checkpoint is 300 (max common across all partitions)
    assertThat(result.globalCheckpointId()).isEqualTo(300L);
  }

  @Test
  void shouldSelectBackupsBasedOnTimeInterval() {
    // given - partition has backups [100, 200, 300, 400]
    // But time interval only covers [200, 300]
    store
        .forPartition(1)
        .withBackupsInRange(100, 400, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 4);

    // when - time interval [20, 40] covers only checkpoints 200 and 300
    final var result = resolve(1, minutesAfterBase(20), minutesAfterBase(40), Map.of(1, 2500L));

    // then - only backups within time interval are returned
    assertThat(result.globalCheckpointId()).isEqualTo(300L);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(200L, 300L);
  }

  static Stream<Arguments> optionalTimeBoundsProvider() {
    return Stream.of(
        Arguments.of(minutesAfterBase(20), null, 400), // only from provided
        Arguments.of(null, null, 400), // neither bound provided
        Arguments.of(null, minutesAfterBase(30), 300) // only to provided
        );
  }

  @ParameterizedTest
  @MethodSource("optionalTimeBoundsProvider")
  void shouldRestoreCompleteRangeWhenBoundsAreOptional(
      @Nullable final Instant from, @Nullable final Instant to, final long globalCheckpoint) {
    // given
    store
        .forPartition(1)
        .withBackupsInRange(100, 400, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 4);

    // when
    final var result = resolve(1, from, to, Map.of(1, 2500L));
    // then - all backups from safe start are returned regardless of optional bounds
    assertThat(result.globalCheckpointId()).isEqualTo(globalCheckpoint);
    final var expectedCheckpoints =
        LongStream.of(200L, 300L, 400L).filter(idx -> idx <= globalCheckpoint).toArray();
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(expectedCheckpoints);
  }

  @Test
  void shouldHandleDifferentSafeStartsPerPartition() {
    // given - partitions have different exported positions, so different safe starts
    store
        .forPartition(1)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);

    store
        .forPartition(2)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);

    // when - P1 has higher exported position (safe start = 200), P2 lower (safe start = 100)
    final var result =
        resolve(2, minutesAfterBase(0), minutesAfterBase(60), Map.of(1, 2500L, 2, 1500L));

    // then
    assertThat(result.globalCheckpointId()).isEqualTo(300L);
    // Both partitions return all backups in the time interval
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(200L, 300L);
    assertThat(result.backupsByPartitionId().get(2)).containsExactly(100L, 200L, 300L);
  }

  @Test
  void shouldFailWhenNoBackupRangeExists() {
    // given - no range markers added for partition
    // (store is empty)

    // when/then
    assertThatThrownBy(
            () -> resolve(1, minutesAfterBase(0), minutesAfterBase(60), Map.of(1, 2500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
            "No complete backup range found for partition 1 in interval [2026-01-20T10:00:00Z, 2026-01-20T11:00:00Z], ranges=[], timeInterval=[]");
  }

  @Test
  void shouldFailWhenTimeIntervalNotCoveredByBackupRange() {
    // given - backup range exists but doesn't cover requested time interval
    store
        .forPartition(1)
        .withBackupsInRange(100, 200, 1000, 1000, minutesAfterBase(0), minutesAfterBase(30), 2);

    // when/then - requesting interval before backups exist
    assertThatThrownBy(
            () ->
                resolve(
                    1,
                    BASE_TIME.minus(Duration.ofHours(2)),
                    BASE_TIME.minus(Duration.ofHours(1)),
                    Map.of(1, 2500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "No complete backup range found for partition 1 in interval [2026-01-20T08:00:00Z, 2026-01-20T09:00:00Z], ranges=[Complete[checkpointInterval=[100, 200]]]");
  }

  @Test
  void shouldFailWhenNoSafeStartCheckpointExists() {
    // given - backup exists but exported position is too low
    store
        .forPartition(1)
        .withBackupsInRange(100, 200, 1000, 1000, minutesAfterBase(0), minutesAfterBase(30), 2);

    // when/then - exported position 500 is below all checkpoint positions
    assertThatThrownBy(() -> resolve(1, minutesAfterBase(0), minutesAfterBase(30), Map.of(1, 500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No safe start checkpoint found for partition 1");
  }

  // ============================================================================
  // Error cases - No common checkpoint across partitions
  // ============================================================================

  @Test
  void shouldFailWhenNoCommonCheckpointExistsAcrossPartitions() {
    // given - partitions have non-overlapping checkpoint sets
    // P1: [100, 200] at times [0, 30]
    // P2: [300, 400] at times [0, 30]
    store
        .forPartition(1)
        .withBackupsInRange(100, 200, 1000, 1000, minutesAfterBase(0), minutesAfterBase(30), 2);

    store
        .forPartition(2)
        .withBackupsInRange(300, 400, 3000, 1000, minutesAfterBase(0), minutesAfterBase(30), 2);

    // when/then
    assertThatThrownBy(
            () -> resolve(2, minutesAfterBase(0), minutesAfterBase(30), Map.of(1, 2500L, 2, 4500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No common checkpoint found across all partitions");
  }

  @Test
  void shouldFailWhenOnePartitionHasNoBackupsInTimeInterval() {
    // given - P1 has backups in interval, P2 has backups outside interval
    store
        .forPartition(1)
        .withBackupsInRange(100, 200, 1000, 1000, minutesAfterBase(0), minutesAfterBase(30), 2);

    store
        .forPartition(2)
        .withBackupsInRange(100, 200, 1000, 1000, minutesAfterBase(120), minutesAfterBase(150), 2);

    // when/then
    assertThatThrownBy(
            () -> resolve(2, minutesAfterBase(0), minutesAfterBase(30), Map.of(1, 2500L, 2, 2500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
            "No complete backup range found for partition 2 in interval [2026-01-20T10:00:00Z, 2026-01-20T10:30:00Z], ranges=[Complete[checkpointInterval=[100, 200]]], timeInterval=[[1970-01-01T00:00:00.100Z, 1970-01-01T00:00:00.200Z]]");
  }

  @Test
  void shouldFailWhenPartitionHasLogPositionGap() {
    // given - backup chain has gap in log positions
    // checkpoint 100: [1, 1000], checkpoint 200 should start at 1001 but starts at 1100
    store
        .forPartition(1)
        .withRange(100, 200)
        .addBackup(100, 1000, 1, minutesAfterBase(0))
        .addBackup(200, 2000, 1100, minutesAfterBase(30)); // gap: expected <= 1001, got 1100

    // when/then
    assertThatThrownBy(
            () -> resolve(1, minutesAfterBase(0), minutesAfterBase(30), Map.of(1, 1500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Partition 1")
        .hasMessageContaining("has gap in log positions");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void shouldFailWhenAPartitionHasIncompleteBackupRange(final int partitionWithDeletion) {
    // given a missing checkpoint for one partition
    for (int i = 1; i <= 2; i++) {
      store
          .forPartition(i)
          .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);
    }

    store.forPartition(partitionWithDeletion).withDeletion(300);

    // P1 exported position 3500 means safe start = 300 (beyond global checkpoint 200)
    // when/then
    assertThatThrownBy(
            () ->
                resolve(2, minutesAfterBase(30), minutesAfterBase(60), Map.of(1, 3500L, 2, 3500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            String.format(
                "No complete backup range found for partition %d in interval [2026-01-20T10:30:00Z, 2026-01-20T11:00:00Z], ranges=[Incomplete[checkpointInterval=[100, 300], deletedCheckpointIds=[300]]]",
                partitionWithDeletion));
  }

  @Test
  void shouldFailWhenPartitionExporterIsLagging() {
    // given a missing checkpoint for one partition
    for (int i = 1; i <= 2; i++) {
      store
          .forPartition(i)
          .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);
    }

    // P1 exported position 3500 means safe start = 300 (beyond global checkpoint 200)
    // when/then
    assertThatThrownBy(
            () -> resolve(2, minutesAfterBase(30), minutesAfterBase(60), Map.of(1, 3500L, 2, 500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "No safe start checkpoint found for partition 2 with exported position 500");
  }

  @Test
  void shouldFailWhenFirstBackupInRangeIsAfterSafeStart() {
    // given - backups in time interval start at 200, but safe start is 100
    store
        .forPartition(1)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);

    // when - time interval [30, 60] returns backups [200, 300]
    // but safe start based on exported position 1500 is checkpoint 100
    // first backup in interval (200) > safe start (100) -> validation fails
    assertThatThrownBy(
            () -> resolve(1, minutesAfterBase(30), minutesAfterBase(60), Map.of(1, 1500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "No safe start checkpoint found for partition 1 with exported position 1500");
  }

  @Test
  void shouldReportAllPartitionFailuresInSingleException() {
    // given - multiple partitions with gaps in log positions
    // P1: has gap
    // P2: valid
    // P3: has gap
    store
        .forPartition(1)
        .withRange(100, 200)
        .addBackup(100, 1000, 1, minutesAfterBase(0))
        .addBackup(200, 2000, 1100, minutesAfterBase(30)); // gap

    store
        .forPartition(2)
        .withBackupsInRange(100, 200, 1000, 1000, minutesAfterBase(0), minutesAfterBase(30), 2);

    store
        .forPartition(3)
        .withRange(100, 200)
        .addBackup(100, 1000, 1, minutesAfterBase(0))
        .addBackup(200, 2000, 1200, minutesAfterBase(30)); // gap

    // when/then - should report failures for P1 and P3
    assertThatThrownBy(
            () ->
                resolve(
                    3,
                    minutesAfterBase(0),
                    minutesAfterBase(30),
                    Map.of(1, 1500L, 2, 1500L, 3, 1500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Partition 1")
        .hasMessageContaining("Partition 3")
        .hasMessageContaining("has gap in log positions");
  }

  @Test
  void shouldNotReturnDuplicatedBackupsWhenMultipleNodes() {
    // given - partition with multiple backups for the same checkpoint (different nodes)
    store
        .forPartition(1)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(45), 3)
        .addBackup(new BackupIdentifierImpl(NODE_ID + 1, 1, 100L), 1000, 1, minutesAfterBase(0));

    // when - exported position 250 means safe start is checkpoint 2 (position 200 <= 250)
    final var result = resolve(1, minutesAfterBase(0), minutesAfterBase(40), Map.of(1, 2500L));

    // then
    // backup 100 is not included as 200 is already before safeStart
    assertThat(result.backupsByPartitionId()).containsEntry(1, new long[] {200, 300});
  }

  @Test
  void shouldReturnASingleBackupIfOnlyOneIsPresent() {
    // given - partition with multiple backups for the same checkpoint (different nodes)
    store.forPartition(1).withRange(100, 100).addBackup(100, 1000, 1, minutesAfterBase(0));

    // when the interval is exactly the same as the backup (edge case) w/
    // exporterPosition=checkpointPosition
    final var result = resolve(1, minutesAfterBase(0), minutesAfterBase(0), Map.of(1, 1000L));

    // then
    // backup 100 is not included as 200 is already before safeStart
    assertThat(result.backupsByPartitionId()).containsEntry(1, new long[] {100});
  }

  private GlobalRestoreInfo resolve(
      final int partitionCount,
      @Nullable final Instant from,
      @Nullable final Instant to,
      final Map<Integer, Long> exportedPositions) {
    return resolver
        .getRestoreInfoForAllPartitions(
            from, to, partitionCount, exportedPositions, checkIdGenerator, DIRECT_EXECUTOR)
        .join();
  }

  private static Instant minutesAfterBase(final int minutes) {
    return BASE_TIME.plus(Duration.ofMinutes(minutes));
  }

  private static BackupStatus createBackupStatus(
      final BackupIdentifier id, final long checkpointPosition, final long firstLogPosition) {
    final BackupDescriptor descriptor =
        new BackupDescriptorImpl(
            Optional.of("snapshot-" + id.checkpointId()),
            OptionalLong.of(firstLogPosition),
            checkpointPosition,
            3,
            "8.7.0",
            Instant.now(),
            CheckpointType.SCHEDULED_BACKUP);
    return new BackupStatusImpl(
        id,
        Optional.of(descriptor),
        BackupStatusCode.COMPLETED,
        Optional.empty(),
        Optional.of(Instant.now()),
        Optional.of(Instant.now()));
  }

  private static BackupStatus createBackupStatus(
      final int partitionId,
      final long checkpointId,
      final long checkpointPosition,
      final long firstLogPosition) {
    final BackupIdentifier id = new BackupIdentifierImpl(NODE_ID, partitionId, checkpointId);
    return createBackupStatus(id, checkpointPosition, firstLogPosition);
  }

  /**
   * Fluent test BackupStore that allows easy setup of partitions with backups and range markers.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * store
   *     .forPartition(1)
   *     .withRange(100, 300)
   *     .addBackup(100, 1000, 1, timestamp1)
   *     .addBackup(200, 2000, 1001, timestamp2)
   *     .addBackup(300, 3000, 2001, timestamp3);
   * }</pre>
   */
  private static final class TestBackupStore implements BackupStore {
    private final Map<BackupIdentifier, Backup> backups = new HashMap<>();
    private final Map<Integer, List<BackupRangeMarker>> rangeMarkersByPartition = new HashMap<>();

    PartitionBuilder forPartition(final int partitionId) {
      return new PartitionBuilder(this, partitionId);
    }

    private void addBackup(
        final BackupIdentifier id,
        final long checkpointPosition,
        final long firstLogPosition,
        final Instant timestamp) {
      final BackupDescriptor descriptor =
          new BackupDescriptorImpl(
              Optional.of("snapshot-" + id.checkpointId()),
              OptionalLong.of(firstLogPosition),
              checkpointPosition,
              3,
              "8.7.0",
              timestamp,
              CheckpointType.SCHEDULED_BACKUP);
      backups.put(
          id,
          new BackupImpl(
              id, descriptor, new NamedFileSetImpl(Map.of()), new NamedFileSetImpl(Map.of())));
    }

    private void addBackup(
        final int partitionId,
        final long checkpointId,
        final long checkpointPosition,
        final long firstLogPosition,
        final Instant timestamp) {
      final BackupIdentifier id = new BackupIdentifierImpl(NODE_ID, partitionId, checkpointId);
      final BackupDescriptor descriptor =
          new BackupDescriptorImpl(
              Optional.of("snapshot-" + checkpointId),
              OptionalLong.of(firstLogPosition),
              checkpointPosition,
              3,
              "8.7.0",
              timestamp,
              CheckpointType.SCHEDULED_BACKUP);
      backups.put(
          id,
          new BackupImpl(
              id, descriptor, new NamedFileSetImpl(Map.of()), new NamedFileSetImpl(Map.of())));
    }

    private void addRangeMarker(final int partitionId, final BackupRangeMarker marker) {
      rangeMarkersByPartition.computeIfAbsent(partitionId, k -> new ArrayList<>()).add(marker);
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
        return CompletableFuture.completedFuture(toStatus(backup));
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
    public CompletableFuture<Collection<BackupStatus>> list(
        final BackupIdentifierWildcard wildcard) {
      final var matchingBackups =
          backups.values().stream()
              .filter(backup -> wildcard.matches(backup.id()))
              .map(this::toStatus)
              .map(BackupStatus.class::cast)
              .toList();
      return CompletableFuture.completedFuture(matchingBackups);
    }

    @Override
    public CompletableFuture<Void> delete(final BackupIdentifier id) {
      backups.remove(id);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
      return CompletableFuture.completedFuture(backups.get(id));
    }

    @Override
    public CompletableFuture<BackupStatusCode> markFailed(
        final BackupIdentifier id, final String failureReason) {
      return CompletableFuture.completedFuture(BackupStatusCode.FAILED);
    }

    @Override
    public CompletableFuture<Collection<BackupRangeMarker>> rangeMarkers(final int partitionId) {
      return CompletableFuture.completedFuture(
          rangeMarkersByPartition.getOrDefault(partitionId, List.of()));
    }

    @Override
    public CompletableFuture<Void> storeRangeMarker(
        final int partitionId, final BackupRangeMarker marker) {
      addRangeMarker(partitionId, marker);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteRangeMarker(
        final int partitionId, final BackupRangeMarker marker) {
      final var markers = rangeMarkersByPartition.get(partitionId);
      if (markers != null) {
        markers.remove(marker);
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
      return CompletableFuture.completedFuture(null);
    }

    private BackupStatusImpl toStatus(final Backup backup) {
      return new BackupStatusImpl(
          backup.id(),
          Optional.of(backup.descriptor()),
          BackupStatusCode.COMPLETED,
          Optional.empty(),
          Optional.of(backup.descriptor().checkpointTimestamp()),
          Optional.of(backup.descriptor().checkpointTimestamp()));
    }

    /** Fluent builder for setting up partition data */
    private static final class PartitionBuilder {
      private final TestBackupStore store;
      private final int partitionId;

      PartitionBuilder(final TestBackupStore store, final int partitionId) {
        this.store = store;
        this.partitionId = partitionId;
      }

      PartitionBuilder withBackupsInRange(
          final long startCheckpoint,
          final long endCheckpoint,
          final long startCheckpointPosition,
          final long eventsPerBackup,
          final Instant startTimestamp,
          final Instant endTimestamp,
          final int count) {
        if (count < 2) {
          throw new IllegalArgumentException("Use withBackup & withRange when count < 2");
        }
        withRange(startCheckpoint, endCheckpoint);
        final long checkpointIdDelta = (endCheckpoint - startCheckpoint) / (count - 1);
        final long timeDelta =
            (endTimestamp.toEpochMilli() - startTimestamp.toEpochMilli()) / (count - 1);
        for (int i = 0; i < count; i++) {
          final long checkpointId = startCheckpoint + i * checkpointIdDelta;
          final long logPosition = startCheckpointPosition + i * eventsPerBackup;
          final Instant timestamp = startTimestamp.plusMillis(i * timeDelta);
          addBackup(checkpointId, logPosition, logPosition - eventsPerBackup, timestamp);
        }
        return this;
      }

      PartitionBuilder withRange(final long startCheckpoint, final long endCheckpoint) {
        store.addRangeMarker(partitionId, new BackupRangeMarker.Start(startCheckpoint));
        store.addRangeMarker(partitionId, new BackupRangeMarker.End(endCheckpoint));
        return this;
      }

      PartitionBuilder withDeletion(final long deletedCheckpoint) {
        store.addRangeMarker(partitionId, new BackupRangeMarker.Deletion(deletedCheckpoint));
        return this;
      }

      PartitionBuilder addBackup(
          final BackupIdentifier id,
          final long checkpointPosition,
          final long firstLogPosition,
          final Instant timestamp) {
        store.addBackup(id, checkpointPosition, firstLogPosition, timestamp);
        return this;
      }

      PartitionBuilder addBackup(
          final long checkpointId,
          final long checkpointPosition,
          final long firstLogPosition,
          final Instant timestamp) {
        store.addBackup(partitionId, checkpointId, checkpointPosition, firstLogPosition, timestamp);
        return this;
      }
    }
  }

  @Nested
  class FindSafeStartCheckpoint {
    @Test
    void shouldReturnEmptyWhenNoCheckpointBelowExportedPosition() {
      // given
      final var backups = List.of(createBackupStatus(1, 1, 100, 1));

      // when - exported position 50 is below all checkpoints
      final var result = BackupRangeResolver.findSafeStartCheckpoint(50L, backups);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyBackupList() {
      // given
      final List<BackupStatus> backups = List.of();

      // when
      final var result = BackupRangeResolver.findSafeStartCheckpoint(100L, backups);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnLargestCheckpointBelowExportedPosition() {
      // given - backups with checkpointPositions [100, 200, 300]
      final var backups =
          List.of(
              createBackupStatus(1, 1, 100, 1), // checkpointId=1, checkpointPosition=100
              createBackupStatus(1, 2, 200, 101), // checkpointId=2, checkpointPosition=200
              createBackupStatus(1, 3, 300, 201)); // checkpointId=3, checkpointPosition=300

      // when - exported position 250 means safe start is checkpoint 2 (position 200 <= 250)
      final var result = BackupRangeResolver.findSafeStartCheckpoint(250L, backups);

      // then
      assertThat(result).contains(2L);
    }
  }
}
