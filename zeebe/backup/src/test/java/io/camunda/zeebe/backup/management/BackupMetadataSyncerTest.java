/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.backup.metrics.BackupMetadataSyncerMetricsDoc;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState.BackupRange;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BackupMetadataSyncerTest {

  @Mock private BackupStore backupStore;

  private BackupMetadataSyncer syncer;

  @BeforeEach
  void setUp() {
    syncer = new BackupMetadataSyncer(backupStore, new SimpleMeterRegistry());
  }

  @Test
  void shouldSerializeCheckpointsAndRanges() throws Exception {
    // given
    final var checkpointEntry =
        new DbCheckpointMetadataState.CheckpointEntry(
            10L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    final var range = new BackupRange(10L, 20L);

    final var contentCaptor = ArgumentCaptor.forClass(byte[].class);
    when(backupStore.storeBackupMetadata(eq(1), contentCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    syncer.store(1, List.of(checkpointEntry), List.of(range)).join();

    // then - verify the content is valid JSON that deserializes correctly
    final var manifest =
        BackupMetadataSyncer.MAPPER.readValue(contentCaptor.getValue(), BackupMetadata.class);
    assertThat(manifest.partitionId()).isOne();
    assertThat(manifest.checkpoints()).hasSize(1);
    assertThat(manifest.checkpoints().getFirst().checkpointId()).isEqualTo(10L);
    assertThat(manifest.checkpoints().getFirst().checkpointPosition()).isEqualTo(100L);
    assertThat(manifest.checkpoints().getFirst().checkpointType())
        .isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(manifest.checkpoints().getFirst().firstLogPosition())
        .isEqualTo(OptionalLong.of(50L));
    assertThat(manifest.ranges()).hasSize(1);
    assertThat(manifest.ranges().getFirst().start()).isEqualTo(10L);
    assertThat(manifest.ranges().getFirst().end()).isEqualTo(20L);
  }

  @Test
  void shouldReturnEmptyWhenMissingFromStore() {
    // given
    when(backupStore.loadBackupMetadata(1))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    // when
    final var result = syncer.load(1).join();

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldLoadManifestWithCheckpointsAndRanges() {
    // given
    final var checkpoints =
        List.of(
            new CheckpointEntry(
                1L,
                100L,
                Instant.ofEpochMilli(1000),
                CheckpointType.SCHEDULED_BACKUP,
                OptionalLong.of(50L)),
            new CheckpointEntry(
                2L,
                200L,
                Instant.ofEpochMilli(2000),
                CheckpointType.MANUAL_BACKUP,
                OptionalLong.of(150L)));
    final var ranges = List.of(new RangeEntry(1L, 2L));
    final var manifest =
        new BackupMetadata(BackupMetadata.VERSION, 1, Instant.now(), checkpoints, ranges);
    final var bytes = serializeManifest(manifest);

    when(backupStore.loadBackupMetadata(1))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(bytes)));

    // when
    final var result = syncer.load(1).join();

    // then
    assertThat(result).isPresent();
    final var loaded = result.get();
    assertThat(loaded.checkpoints()).hasSize(2);
    assertThat(loaded.checkpoints().get(0).checkpointId()).isEqualTo(1L);
    assertThat(loaded.checkpoints().get(1).checkpointId()).isEqualTo(2L);
    assertThat(loaded.ranges()).hasSize(1);
    assertThat(loaded.ranges().getFirst().start()).isEqualTo(1L);
    assertThat(loaded.ranges().getFirst().end()).isEqualTo(2L);
  }

  @Test
  void shouldSerializeMarkerWithoutFirstLogPosition() throws IOException {
    // given
    final var checkpointEntry =
        new DbCheckpointMetadataState.CheckpointEntry(10L, 100L, 1000L, CheckpointType.MARKER, -1L);
    final var range = new BackupRange(10L, 20L);

    final var contentCaptor = ArgumentCaptor.forClass(byte[].class);
    when(backupStore.storeBackupMetadata(eq(1), contentCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    syncer.store(1, List.of(checkpointEntry), List.of(range)).join();

    // then - verify the content is valid JSON that deserializes correctly
    final var manifest =
        BackupMetadataSyncer.MAPPER.readValue(contentCaptor.getValue(), BackupMetadata.class);
    assertThat(manifest.partitionId()).isOne();
    assertThat(manifest.checkpoints()).hasSize(1);
    assertThat(manifest.checkpoints().getFirst().checkpointId()).isEqualTo(10L);
    assertThat(manifest.checkpoints().getFirst().checkpointPosition()).isEqualTo(100L);
    assertThat(manifest.checkpoints().getFirst().checkpointType()).isEqualTo(CheckpointType.MARKER);
    assertThat(manifest.checkpoints().getFirst().firstLogPosition()).isEmpty();

    assertThat(manifest.ranges()).hasSize(1);
    assertThat(manifest.ranges().getFirst().start()).isEqualTo(10L);
    assertThat(manifest.ranges().getFirst().end()).isEqualTo(20L);
  }

  @Test
  void shouldParseMetadataWithoutFirstLogPositionOnMarker() {
    // given
    final var metadata =
        """
        {
            "version": 1,
            "partitionId": 1,
            "lastUpdated": "2026-02-27T06:00:29.349354439Z",
            "checkpoints": [
                {
                    "checkpointId": 1772172018889,
                    "checkpointPosition": 744,
                    "checkpointTimestamp": "2026-02-27T06:00:18.889Z",
                    "checkpointType": "MARKER"
                },
                {
                    "checkpointId": 1772172029318,
                    "checkpointPosition": 752,
                    "checkpointTimestamp": "2026-02-27T06:00:29.346Z",
                    "checkpointType": "SCHEDULED_BACKUP",
                    "firstLogPosition": 1
                }
            ],
            "ranges": [
                {
                    "start": 1772172029318,
                    "end": 1772172029318
                }
            ]
        }
        """;

    final BackupStore store = mock(BackupStore.class);
    final BackupMetadataSyncer syncer = new BackupMetadataSyncer(store);
    when(store.loadBackupMetadata(anyInt()))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(metadata.getBytes())));

    // when
    final var loadedMetadata = syncer.load(1).join();

    // then
    assertThat(loadedMetadata.isPresent());
    assertThat(loadedMetadata.get().checkpoints().size() == 2);
    final var cp =
        loadedMetadata.get().checkpoints().stream()
            .filter(f -> f.checkpointType() == CheckpointType.MARKER)
            .findFirst();
    assertThat(cp).isPresent();
    assertThat(cp.get().firstLogPosition()).isEmpty();

    final var marker =
        loadedMetadata.get().checkpoints().stream()
            .filter(f -> f.checkpointType() == CheckpointType.SCHEDULED_BACKUP)
            .findFirst();
    assertThat(marker).isPresent();
    assertThat(marker.get().firstLogPosition()).isEqualTo(OptionalLong.of(1L));
  }

  private static byte[] serializeManifest(final BackupMetadata manifest) {
    try {
      return BackupMetadataSyncer.MAPPER.writeValueAsBytes(manifest);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  final class MetricsTest {

    private static final int PARTITION_ID = 1;

    @Mock private BackupStore backupStore;

    private SimpleMeterRegistry registry;
    private BackupMetadataSyncer syncer;

    @BeforeEach
    void setUp() {
      registry = new SimpleMeterRegistry();
      syncer = new BackupMetadataSyncer(backupStore, registry);
    }

    @Test
    void shouldIncrementCompletedCounterOnSuccess() {
      // given
      when(backupStore.storeBackupMetadata(anyInt(), any()))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      syncer.store(PARTITION_ID, List.of(sampleCheckpoint()), List.of(sampleRange())).join();

      // then
      final var counter =
          registry
              .find(BackupMetadataSyncerMetricsDoc.METADATA_SYNC_TOTAL.getName())
              .tag("result", "completed")
              .tag("partition", String.valueOf(PARTITION_ID))
              .counter();
      assertThat(counter).isNotNull();
      assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordUploadDurationOnSuccess() {
      // given
      when(backupStore.storeBackupMetadata(anyInt(), any()))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      syncer.store(PARTITION_ID, List.of(sampleCheckpoint()), List.of(sampleRange())).join();

      // then
      final var timer =
          registry
              .find(BackupMetadataSyncerMetricsDoc.METADATA_SYNC_DURATION.getName())
              .tag("partition", String.valueOf(PARTITION_ID))
              .timer();
      assertThat(timer).isNotNull();
      assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordSerializedSizeOnSuccess() {
      // given
      when(backupStore.storeBackupMetadata(anyInt(), any()))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      syncer.store(PARTITION_ID, List.of(sampleCheckpoint()), List.of(sampleRange())).join();

      // then
      final var gauge =
          registry
              .find(BackupMetadataSyncerMetricsDoc.METADATA_SYNC_SERIALIZED_SIZE.getName())
              .tag("partition", String.valueOf(PARTITION_ID))
              .gauge();
      assertThat(gauge).isNotNull();
      assertThat(gauge.value()).isGreaterThan(0);
    }

    @Test
    void shouldIncrementFailedCounterOnUploadFailure() {
      // given
      when(backupStore.storeBackupMetadata(anyInt(), any()))
          .thenReturn(CompletableFuture.failedFuture(new RuntimeException("upload error")));

      // when
      syncer
          .store(PARTITION_ID, List.of(sampleCheckpoint()), List.of(sampleRange()))
          .exceptionally(e -> null)
          .join();

      // then
      final var failedCounter =
          registry
              .find(BackupMetadataSyncerMetricsDoc.METADATA_SYNC_TOTAL.getName())
              .tag("result", "failed")
              .tag("partition", String.valueOf(PARTITION_ID))
              .counter();
      assertThat(failedCounter).isNotNull();
      assertThat(failedCounter.count()).isEqualTo(1);

      // and no completed counter was recorded
      final var completedCounter =
          registry
              .find(BackupMetadataSyncerMetricsDoc.METADATA_SYNC_TOTAL.getName())
              .tag("result", "completed")
              .counter();
      assertThat(completedCounter).isNull();
    }

    @Test
    void shouldRecordDurationOnUploadFailure() {
      // given
      when(backupStore.storeBackupMetadata(anyInt(), any()))
          .thenReturn(CompletableFuture.failedFuture(new RuntimeException("upload error")));

      // when
      syncer
          .store(PARTITION_ID, List.of(sampleCheckpoint()), List.of(sampleRange()))
          .exceptionally(e -> null)
          .join();

      // then
      final var timer =
          registry
              .find(BackupMetadataSyncerMetricsDoc.METADATA_SYNC_DURATION.getName())
              .tag("partition", String.valueOf(PARTITION_ID))
              .timer();
      assertThat(timer).isNotNull();
      assertThat(timer.count()).isEqualTo(1);
    }

    private DbCheckpointMetadataState.CheckpointEntry sampleCheckpoint() {
      return new DbCheckpointMetadataState.CheckpointEntry(
          10L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    }

    private BackupRange sampleRange() {
      return new BackupRange(10L, 20L);
    }
  }
}
