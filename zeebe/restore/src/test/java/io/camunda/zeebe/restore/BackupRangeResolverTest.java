/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupMetadataManifest;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.RangeEntry;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
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
        Arguments.of(minutesAfterBase(20), null, 400, 2500L), // only from provided
        Arguments.of(null, null, 400, 2500L), // neither bound provided
        // only to provided: to=min30, highest checkpoint ≤ min30 is 200 (at min20),
        // exporter position 1500 → safeStart=100 (pos=1000), filtered=[100, 200]
        Arguments.of(null, minutesAfterBase(30), 200, 1500L));
  }

  @ParameterizedTest
  @MethodSource("optionalTimeBoundsProvider")
  void shouldRestoreCompleteRangeWhenBoundsAreOptional(
      @Nullable final Instant from,
      @Nullable final Instant to,
      final long globalCheckpoint,
      final long exporterPosition) {
    // given
    store
        .forPartition(1)
        .withBackupsInRange(100, 400, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 4);

    // when
    final var result = resolve(1, from, to, Map.of(1, exporterPosition));
    // then - all backups from safe start are returned regardless of optional bounds
    assertThat(result.globalCheckpointId()).isEqualTo(globalCheckpoint);
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
    // given - no manifest exists for partition (store is empty)
    // when/then
    assertThatThrownBy(
            () -> resolve(1, minutesAfterBase(0), minutesAfterBase(60), Map.of(1, 2500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No backup metadata manifest found for partition 1");
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
            "No complete backup range found for partition 1 in interval [2026-01-20T08:00:00Z, 2026-01-20T09:00:00Z], ranges=[RangeEntry[start=100, end=200]]");
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

    // when/then - global target = min(200, 400) = 200
    // P1: safeStart=200 (pos 2000 ≤ 2500), filtered=[200], but last pos 2000 < exporter 2500
    // P2: safeStart=400 (pos 4000 ≤ 4500), but 400 > global 200 → validation fails
    assertThatThrownBy(
            () -> resolve(2, minutesAfterBase(0), minutesAfterBase(30), Map.of(1, 2500L, 2, 4500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot restore to global checkpoint 200")
        .hasMessageContaining("Partition 1")
        .hasMessageContaining("Partition 2");
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
        .hasMessageContaining(
            "No complete backup range found for partition 2 in interval [2026-01-20T10:00:00Z, 2026-01-20T10:30:00Z]");
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
  void shouldFailWhenAPartitionHasDeletedCheckpoint(final int partitionWithDeletion) {
    // given a missing checkpoint for one partition — in the new model, deletion splits the range
    // so the requested interval [30, 60] won't be covered by any single complete range
    for (int i = 1; i <= 2; i++) {
      store
          .forPartition(i)
          .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);
    }

    store.forPartition(partitionWithDeletion).withDeletion(300);

    // when/then - the range [100,300] was split to [100,200] because 300 was deleted
    // so there's no complete range covering [30, 60] (checkpoint 300 at minute 60 is gone)
    assertThatThrownBy(
            () ->
                resolve(2, minutesAfterBase(30), minutesAfterBase(60), Map.of(1, 3500L, 2, 3500L)))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            String.format(
                "No complete backup range found for partition %d in interval [2026-01-20T10:30:00Z, 2026-01-20T11:00:00Z]",
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
  void shouldIncludeCheckpointsBeforeTimeWindowWhenNeededForSafeStart() {
    // given - backups in time interval [min30, min60] start at checkpoint 200,
    // but safe start based on exported position 1500 is checkpoint 100 (pos=1000).
    // The new algorithm uses ALL range checkpoints for safeStart, so checkpoint 100
    // is found even though it's before the time window.
    store
        .forPartition(1)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(60), 3);

    // when - to=min60, global target=300, safeStart=100 (pos 1000 ≤ 1500)
    final var result = resolve(1, minutesAfterBase(30), minutesAfterBase(60), Map.of(1, 1500L));

    // then - all checkpoints from safeStart to globalTarget are included
    assertThat(result.globalCheckpointId()).isEqualTo(300L);
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(100L, 200L, 300L);
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
    // In the manifest-based model, the manifest only stores one entry per checkpoint
    // so duplicates across nodes don't appear. We just set up the manifest normally.
    store
        .forPartition(1)
        .withBackupsInRange(100, 300, 1000, 1000, minutesAfterBase(0), minutesAfterBase(45), 3);

    // when - to=min40, highest checkpoint ≤ min40 is 200 (at min22.5), global target=200
    // exported position 1500 → safeStart=100 (pos=1000), filtered=[100, 200]
    final var result = resolve(1, minutesAfterBase(0), minutesAfterBase(40), Map.of(1, 1500L));

    // then
    assertThat(result.backupsByPartitionId()).containsEntry(1, new long[] {100, 200});
  }

  @Test
  void shouldReturnASingleBackupIfOnlyOneIsPresent() {
    // given - partition with a single backup
    store.forPartition(1).withRange(100, 100).addBackup(100, 1000, 1, minutesAfterBase(0));

    // when the interval is exactly the same as the backup (edge case) w/
    // exporterPosition=checkpointPosition
    final var result = resolve(1, minutesAfterBase(0), minutesAfterBase(0), Map.of(1, 1000L));

    // then
    assertThat(result.backupsByPartitionId()).containsEntry(1, new long[] {100});
  }

  @Test
  void shouldHandleCrossPartitionTimestampSkewInRdbmsPath() {
    // given - 2 partitions with same checkpoint IDs but skewed timestamps
    // P1: checkpoints at min10, min20, min30, min40
    // P2: checkpoints at min11, min21, min32, min42
    // to=min31: P1 sees 300 ≤ min31 (at min30), P2 sees 200 ≤ min31 (300 at min32 > min31)
    // global target = min(300, 200) = 200
    store
        .forPartition(1)
        .withRange(100, 400)
        .addBackup(100, 1000, 1, minutesAfterBase(10))
        .addBackup(200, 2000, 1001, minutesAfterBase(20))
        .addBackup(300, 3000, 2001, minutesAfterBase(30))
        .addBackup(400, 4000, 3001, minutesAfterBase(40));

    store
        .forPartition(2)
        .withRange(100, 400)
        .addBackup(100, 1000, 1, minutesAfterBase(11))
        .addBackup(200, 2000, 1001, minutesAfterBase(21))
        .addBackup(300, 3000, 2001, minutesAfterBase(32))
        .addBackup(400, 4000, 3001, minutesAfterBase(42));

    // when - from=min11 so both ranges cover it, to=min31
    final var result =
        resolve(2, minutesAfterBase(11), minutesAfterBase(31), Map.of(1, 1500L, 2, 1500L));

    // then - global target = 200 (conservative min across partitions)
    assertThat(result.globalCheckpointId()).isEqualTo(200L);
    // Both partitions: safeStart=100 (pos 1000 ≤ 1500), filtered=[100, 200]
    assertThat(result.backupsByPartitionId().get(1)).containsExactly(100L, 200L);
    assertThat(result.backupsByPartitionId().get(2)).containsExactly(100L, 200L);
  }

  private GlobalRestoreInfo resolve(
      final int partitionCount,
      @Nullable final Instant from,
      @Nullable final Instant to,
      final Map<Integer, Long> exportedPositions) {
    return resolver
        .getRestoreInfoForAllPartitions(
            from, to, partitionCount, exportedPositions, DIRECT_EXECUTOR)
        .join();
  }

  private static Instant minutesAfterBase(final int minutes) {
    return BASE_TIME.plus(Duration.ofMinutes(minutes));
  }

  /**
   * Fluent test BackupStore that allows easy setup of partitions with backups and manifest
   * metadata.
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

    private static final ObjectMapper MAPPER =
        new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .disable(WRITE_DATES_AS_TIMESTAMPS);

    private final Map<BackupIdentifier, Backup> backups = new HashMap<>();
    private final Map<Integer, List<RangeEntry>> rangesByPartition = new HashMap<>();
    private final Map<Integer, List<CheckpointEntry>> checkpointsByPartition = new HashMap<>();
    private final Map<String, byte[]> metadataBySlot = new HashMap<>();

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
      addBackup(id, checkpointPosition, firstLogPosition, timestamp);
      // Also add to checkpoints for the manifest
      checkpointsByPartition
          .computeIfAbsent(partitionId, k -> new ArrayList<>())
          .add(
              new CheckpointEntry(
                  checkpointId,
                  checkpointPosition,
                  timestamp,
                  "SCHEDULED_BACKUP",
                  firstLogPosition,
                  3,
                  "8.7.0"));
    }

    private void addRange(final int partitionId, final long start, final long end) {
      rangesByPartition
          .computeIfAbsent(partitionId, k -> new ArrayList<>())
          .add(new RangeEntry(start, end));
    }

    /** Syncs the manifest for this partition to slot "a". */
    private void syncManifest(final int partitionId) {
      final var checkpoints = checkpointsByPartition.getOrDefault(partitionId, List.of());
      final var ranges = rangesByPartition.getOrDefault(partitionId, List.of());
      final var manifest =
          new BackupMetadataManifest(partitionId, 1L, Instant.now(), checkpoints, ranges);
      try {
        final var content = MAPPER.writeValueAsBytes(manifest);
        metadataBySlot.put(partitionId + "/a", content);
      } catch (final JsonProcessingException e) {
        throw new RuntimeException("Failed to serialize manifest", e);
      }
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
    public CompletableFuture<Void> storeBackupMetadata(
        final int partitionId, final String slot, final byte[] content) {
      metadataBySlot.put(partitionId + "/" + slot, content);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<byte[]>> loadBackupMetadata(
        final int partitionId, final String slot) {
      return CompletableFuture.completedFuture(
          Optional.ofNullable(metadataBySlot.get(partitionId + "/" + slot)));
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
        store.addRange(partitionId, startCheckpoint, endCheckpoint);
        store.syncManifest(partitionId);
        return this;
      }

      /**
       * Simulates deletion of a checkpoint by removing it from checkpoints and adjusting ranges. In
       * the new CF-based model, deletion splits/shrinks ranges rather than creating Incomplete
       * ranges.
       */
      PartitionBuilder withDeletion(final long deletedCheckpoint) {
        // Remove the checkpoint entry
        final var checkpoints = store.checkpointsByPartition.get(partitionId);
        if (checkpoints != null) {
          checkpoints.removeIf(e -> e.checkpointId() == deletedCheckpoint);
        }

        // Update ranges: find the range containing the deleted checkpoint and split/shrink it
        final var ranges = store.rangesByPartition.get(partitionId);
        if (ranges != null) {
          final var rangeIter = ranges.iterator();
          final var newRanges = new ArrayList<RangeEntry>();
          while (rangeIter.hasNext()) {
            final var range = rangeIter.next();
            if (deletedCheckpoint >= range.start() && deletedCheckpoint <= range.end()) {
              rangeIter.remove();
              // If deleted is the only entry, range is removed entirely
              if (range.start() == range.end()) {
                // already removed
              } else if (deletedCheckpoint == range.start()) {
                // Find the next checkpoint after the deleted one
                final var nextCheckpoint =
                    checkpoints != null
                        ? checkpoints.stream()
                            .mapToLong(CheckpointEntry::checkpointId)
                            .filter(id -> id > deletedCheckpoint && id <= range.end())
                            .min()
                        : java.util.OptionalLong.empty();
                nextCheckpoint.ifPresent(next -> newRanges.add(new RangeEntry(next, range.end())));
              } else if (deletedCheckpoint == range.end()) {
                // Find the previous checkpoint before the deleted one
                final var prevCheckpoint =
                    checkpoints != null
                        ? checkpoints.stream()
                            .mapToLong(CheckpointEntry::checkpointId)
                            .filter(id -> id < deletedCheckpoint && id >= range.start())
                            .max()
                        : java.util.OptionalLong.empty();
                prevCheckpoint.ifPresent(
                    prev -> newRanges.add(new RangeEntry(range.start(), prev)));
              } else {
                // Split into two ranges
                final var prevCheckpoint =
                    checkpoints != null
                        ? checkpoints.stream()
                            .mapToLong(CheckpointEntry::checkpointId)
                            .filter(id -> id < deletedCheckpoint && id >= range.start())
                            .max()
                        : java.util.OptionalLong.empty();
                final var nextCheckpoint =
                    checkpoints != null
                        ? checkpoints.stream()
                            .mapToLong(CheckpointEntry::checkpointId)
                            .filter(id -> id > deletedCheckpoint && id <= range.end())
                            .min()
                        : java.util.OptionalLong.empty();
                prevCheckpoint.ifPresent(
                    prev -> newRanges.add(new RangeEntry(range.start(), prev)));
                nextCheckpoint.ifPresent(next -> newRanges.add(new RangeEntry(next, range.end())));
              }
              break;
            }
          }
          ranges.addAll(newRanges);
        }

        // Remove the backup too
        store.backups.remove(new BackupIdentifierImpl(NODE_ID, partitionId, deletedCheckpoint));

        store.syncManifest(partitionId);
        return this;
      }

      PartitionBuilder addBackup(
          final BackupIdentifier id,
          final long checkpointPosition,
          final long firstLogPosition,
          final Instant timestamp) {
        store.addBackup(id, checkpointPosition, firstLogPosition, timestamp);
        // Also add checkpoint entry for the manifest
        store
            .checkpointsByPartition
            .computeIfAbsent(id.partitionId(), k -> new ArrayList<>())
            .add(
                new CheckpointEntry(
                    id.checkpointId(),
                    checkpointPosition,
                    timestamp,
                    "SCHEDULED_BACKUP",
                    firstLogPosition,
                    3,
                    "8.7.0"));
        store.syncManifest(id.partitionId());
        return this;
      }

      PartitionBuilder addBackup(
          final long checkpointId,
          final long checkpointPosition,
          final long firstLogPosition,
          final Instant timestamp) {
        store.addBackup(partitionId, checkpointId, checkpointPosition, firstLogPosition, timestamp);
        store.syncManifest(partitionId);
        return this;
      }

      /**
       * Adds a MARKER checkpoint (no backup data) to the partition manifest. MARKERs represent
       * checkpoints that don't have associated backup data.
       */
      PartitionBuilder addMarker(
          final long checkpointId, final long checkpointPosition, final Instant timestamp) {
        store
            .checkpointsByPartition
            .computeIfAbsent(partitionId, k -> new ArrayList<>())
            .add(
                new CheckpointEntry(
                    checkpointId, checkpointPosition, timestamp, "MARKER", 0L, 3, "8.7.0"));
        store.syncManifest(partitionId);
        return this;
      }
    }
  }

  @Nested
  class FindSafeStartCheckpoint {
    @Test
    void shouldReturnEmptyWhenNoCheckpointBelowExportedPosition() {
      // given
      final var checkpoints =
          List.of(new CheckpointEntry(1, 100, Instant.now(), "SCHEDULED_BACKUP", 1, 3, "8.7.0"));

      // when - exported position 50 is below all checkpoints
      final var result = BackupRangeResolver.findSafeStartCheckpoint(50L, checkpoints);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyBackupList() {
      // given
      final List<CheckpointEntry> checkpoints = List.of();

      // when
      final var result = BackupRangeResolver.findSafeStartCheckpoint(100L, checkpoints);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnLargestCheckpointBelowExportedPosition() {
      // given - checkpoints with checkpointPositions [100, 200, 300]
      final var checkpoints =
          List.of(
              new CheckpointEntry(1, 100, Instant.now(), "SCHEDULED_BACKUP", 1, 3, "8.7.0"),
              new CheckpointEntry(2, 200, Instant.now(), "SCHEDULED_BACKUP", 101, 3, "8.7.0"),
              new CheckpointEntry(3, 300, Instant.now(), "SCHEDULED_BACKUP", 201, 3, "8.7.0"));

      // when - exported position 250 means safe start is checkpoint 2 (position 200 <= 250)
      final var result = BackupRangeResolver.findSafeStartCheckpoint(250L, checkpoints);

      // then
      assertThat(result).contains(2L);
    }
  }

  @Nested
  class ResolvePointInTime {

    @Test
    void shouldResolveExactBackupMatch() {
      // given - single partition with 3 backups at minutes 10, 20, 30
      store
          .forPartition(1)
          .withRange(100, 300)
          .addBackup(100, 1000, 1, minutesAfterBase(10))
          .addBackup(200, 2000, 1001, minutesAfterBase(20))
          .addBackup(300, 3000, 2001, minutesAfterBase(30));

      // when - target is exactly at checkpoint 200's timestamp
      final var result =
          resolver.resolvePointInTime(minutesAfterBase(20), 1, DIRECT_EXECUTOR).join();

      // then - should return checkpoint 200
      assertThat(result).isEqualTo(200L);
    }

    @Test
    void shouldResolveTargetBetweenTwoBackups() {
      // given - single partition with 3 backups at minutes 10, 20, 30
      store
          .forPartition(1)
          .withRange(100, 300)
          .addBackup(100, 1000, 1, minutesAfterBase(10))
          .addBackup(200, 2000, 1001, minutesAfterBase(20))
          .addBackup(300, 3000, 2001, minutesAfterBase(30));

      // when - target is between checkpoint 200 (min 20) and 300 (min 30)
      final var result =
          resolver.resolvePointInTime(minutesAfterBase(25), 1, DIRECT_EXECUTOR).join();

      // then - should return checkpoint 200 (highest with timestamp <= target)
      assertThat(result).isEqualTo(200L);
    }

    @Test
    void shouldWalkBackFromMarkerToNearestBackup() {
      // given - partition with: backup at 100, MARKER at 200, backup at 300
      store
          .forPartition(1)
          .withRange(100, 300)
          .addBackup(100, 1000, 1, minutesAfterBase(10))
          .addMarker(200, 2000, minutesAfterBase(20))
          .addBackup(300, 3000, 2001, minutesAfterBase(30));

      // when - target is exactly at MARKER 200
      final var result =
          resolver.resolvePointInTime(minutesAfterBase(20), 1, DIRECT_EXECUTOR).join();

      // then - MARKER 200 is the lower bound, walk back to backup 100
      assertThat(result).isEqualTo(100L);
    }

    @Test
    void shouldHandleCrossPartitionTimestampSkew() {
      // given - 2 partitions with same checkpoint IDs but slightly different timestamps
      // P1: checkpoint 100 at min 10, checkpoint 200 at min 20
      // P2: checkpoint 100 at min 11, checkpoint 200 at min 21
      store
          .forPartition(1)
          .withRange(100, 200)
          .addBackup(100, 1000, 1, minutesAfterBase(10))
          .addBackup(200, 2000, 1001, minutesAfterBase(20));

      store
          .forPartition(2)
          .withRange(100, 200)
          .addBackup(100, 1000, 1, minutesAfterBase(11))
          .addBackup(200, 2000, 1001, minutesAfterBase(21));

      // when - target is minute 20: P1 sees 200 <= target, P2 sees 200 at min 21 > target so P2=100
      final var result =
          resolver.resolvePointInTime(minutesAfterBase(20), 2, DIRECT_EXECUTOR).join();

      // then - global lower bound = min(200, 100) = 100
      assertThat(result).isEqualTo(100L);
    }

    @Test
    void shouldFailWhenNoCheckpointBeforeTarget() {
      // given - partition with checkpoints all after target
      store
          .forPartition(1)
          .withRange(100, 200)
          .addBackup(100, 1000, 1, minutesAfterBase(30))
          .addBackup(200, 2000, 1001, minutesAfterBase(40));

      // when/then - target at minute 10 is before all checkpoints
      assertThatThrownBy(
              () -> resolver.resolvePointInTime(minutesAfterBase(10), 1, DIRECT_EXECUTOR).join())
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No checkpoint found at or before");
    }

    @Test
    void shouldFailWhenAllCheckpointsBeforeTargetAreMarkers() {
      // given - only MARKERs before target, backup only after
      store
          .forPartition(1)
          .withRange(100, 300)
          .addMarker(100, 1000, minutesAfterBase(10))
          .addMarker(200, 2000, minutesAfterBase(20))
          .addBackup(300, 3000, 2001, minutesAfterBase(30));

      // when/then - target at minute 20 finds MARKER 200, walks back but only finds MARKER 100
      assertThatThrownBy(
              () -> resolver.resolvePointInTime(minutesAfterBase(20), 1, DIRECT_EXECUTOR).join())
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(IllegalStateException.class)
          .hasMessageContaining("all checkpoints at or before target are MARKERs");
    }

    @Test
    void shouldResolveWithMultiplePartitionsAndMarkerWalkBack() {
      // given - 2 partitions, global lower bound lands on a MARKER
      // Both partitions have: backup at 100 (min 10), MARKER at 200 (min 20), backup at 300 (min
      // 30)
      for (var p = 1; p <= 2; p++) {
        store
            .forPartition(p)
            .withRange(100, 300)
            .addBackup(100, 1000, 1, minutesAfterBase(10))
            .addMarker(200, 2000, minutesAfterBase(20))
            .addBackup(300, 3000, 2001, minutesAfterBase(30));
      }

      // when - target at minute 25: both partitions find MARKER 200
      final var result =
          resolver.resolvePointInTime(minutesAfterBase(25), 2, DIRECT_EXECUTOR).join();

      // then - global lower bound = 200 (MARKER), walk back to 100
      assertThat(result).isEqualTo(100L);
    }

    @Test
    void shouldResolveLatestBackupWhenTargetIsAfterAll() {
      // given - partition with backups at minutes 10, 20
      store
          .forPartition(1)
          .withRange(100, 200)
          .addBackup(100, 1000, 1, minutesAfterBase(10))
          .addBackup(200, 2000, 1001, minutesAfterBase(20));

      // when - target is well after all backups
      final var result =
          resolver.resolvePointInTime(minutesAfterBase(120), 1, DIRECT_EXECUTOR).join();

      // then - should return the latest backup
      assertThat(result).isEqualTo(200L);
    }

    @Test
    void shouldFailWhenManifestIsMissing() {
      // given - no manifest stored

      // when/then
      assertThatThrownBy(
              () -> resolver.resolvePointInTime(minutesAfterBase(10), 1, DIRECT_EXECUTOR).join())
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No backup metadata manifest found for partition 1");
    }
  }
}
