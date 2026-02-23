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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadataCodec;
import io.camunda.zeebe.backup.common.BackupMetadataManifest;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.RangeEntry;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState.BackupRange;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BackupMetadataSyncerTest {

  @Mock private BackupStore backupStore;
  @Mock private DbCheckpointMetadataState checkpointMetadataState;
  @Mock private DbBackupRangeState backupRangeState;

  private BackupMetadataSyncer syncer;

  @BeforeEach
  void setUp() {
    syncer = new BackupMetadataSyncer(backupStore);
  }

  @Test
  void shouldSyncToStore() {
    // given
    when(checkpointMetadataState.getAllCheckpoints()).thenReturn(List.of());
    when(backupRangeState.getAllRanges()).thenReturn(List.of());
    when(backupStore.storeBackupMetadata(eq(1), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    syncer.sync(1, checkpointMetadataState, backupRangeState).join();

    // then
    verify(backupStore).storeBackupMetadata(eq(1), any());
  }

  @Test
  void shouldSerializeCheckpointsAndRanges() throws Exception {
    // given
    final var checkpointEntry =
        new DbCheckpointMetadataState.CheckpointEntry(
            10L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L, 3, "8.7.0");
    final var range = new BackupRange(10L, 20L);

    when(checkpointMetadataState.getAllCheckpoints()).thenReturn(List.of(checkpointEntry));
    when(backupRangeState.getAllRanges()).thenReturn(List.of(range));

    final var contentCaptor = ArgumentCaptor.forClass(byte[].class);
    when(backupStore.storeBackupMetadata(eq(1), contentCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    syncer.sync(1, checkpointMetadataState, backupRangeState).join();

    // then - verify the content is valid JSON that deserializes correctly
    final var manifest =
        BackupMetadataCodec.MAPPER.readValue(
            contentCaptor.getValue(), BackupMetadataManifest.class);
    assertThat(manifest.partitionId()).isEqualTo(1);
    assertThat(manifest.checkpoints()).hasSize(1);
    assertThat(manifest.checkpoints().getFirst().checkpointId()).isEqualTo(10L);
    assertThat(manifest.checkpoints().getFirst().checkpointPosition()).isEqualTo(100L);
    assertThat(manifest.checkpoints().getFirst().checkpointType())
        .isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(manifest.checkpoints().getFirst().firstLogPosition()).isEqualTo(50L);
    assertThat(manifest.checkpoints().getFirst().numberOfPartitions()).isEqualTo(3);
    assertThat(manifest.checkpoints().getFirst().brokerVersion()).isEqualTo("8.7.0");
    assertThat(manifest.ranges()).hasSize(1);
    assertThat(manifest.ranges().getFirst().start()).isEqualTo(10L);
    assertThat(manifest.ranges().getFirst().end()).isEqualTo(20L);
  }

  @Test
  void shouldHandleStoreFailureGracefully() {
    // given
    when(checkpointMetadataState.getAllCheckpoints()).thenReturn(List.of());
    when(backupRangeState.getAllRanges()).thenReturn(List.of());
    when(backupStore.storeBackupMetadata(eq(1), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("store unavailable")));

    // when - sync completes without throwing (fire-and-forget style, failure logged)
    final var future = syncer.sync(1, checkpointMetadataState, backupRangeState);

    // then - the future completes exceptionally but the syncer does not throw
    assertThat(future).isCompletedExceptionally();
  }

  @Test
  void shouldLoadManifestSuccessfully() {
    // given
    final var manifest = new BackupMetadataManifest(1, Instant.now(), List.of(), List.of());
    final var bytes = serializeManifest(manifest);

    when(backupStore.loadBackupMetadata(1))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(bytes)));

    // when
    final var result = syncer.load(1).join();

    // then
    assertThat(result).isPresent();
    assertThat(result.get().partitionId()).isEqualTo(1);
  }

  @Test
  void shouldReturnEmptyWhenNoFileExists() {
    // given
    when(backupStore.loadBackupMetadata(1))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    // when
    final var result = syncer.load(1).join();

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenFileIsCorrupt() {
    // given
    when(backupStore.loadBackupMetadata(1))
        .thenReturn(CompletableFuture.completedFuture(Optional.of("not-valid-json".getBytes())));

    // when
    final var result = syncer.load(1).join();

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldHandleStoreErrorOnLoadGracefully() {
    // given
    when(backupStore.loadBackupMetadata(1))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("connection error")));

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
                50L,
                3,
                "8.7.0"),
            new CheckpointEntry(
                2L,
                200L,
                Instant.ofEpochMilli(2000),
                CheckpointType.MANUAL_BACKUP,
                150L,
                3,
                "8.7.0"));
    final var ranges = List.of(new RangeEntry(1L, 2L));
    final var manifest = new BackupMetadataManifest(1, Instant.now(), checkpoints, ranges);
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

  private static byte[] serializeManifest(final BackupMetadataManifest manifest) {
    try {
      return BackupMetadataCodec.MAPPER.writeValueAsBytes(manifest);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
