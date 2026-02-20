/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
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

  private BackupMetadataSyncer syncer;

  @BeforeEach
  void setUp() {
    syncer = new BackupMetadataSyncer(1, backupStore);
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
    syncer.store(List.of(checkpointEntry), List.of(range)).join();

    // then - verify the content is valid JSON that deserializes correctly
    final var manifest =
        BackupMetadataSyncer.MAPPER.readValue(contentCaptor.getValue(), BackupMetadata.class);
    assertThat(manifest.partitionId()).isEqualTo(1);
    assertThat(manifest.checkpoints()).hasSize(1);
    assertThat(manifest.checkpoints().getFirst().checkpointId()).isEqualTo(10L);
    assertThat(manifest.checkpoints().getFirst().checkpointPosition()).isEqualTo(100L);
    assertThat(manifest.checkpoints().getFirst().checkpointType())
        .isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(manifest.checkpoints().getFirst().firstLogPosition()).isEqualTo(50L);
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
                1L, 100L, Instant.ofEpochMilli(1000), CheckpointType.SCHEDULED_BACKUP, 50L),
            new CheckpointEntry(
                2L, 200L, Instant.ofEpochMilli(2000), CheckpointType.MANUAL_BACKUP, 150L));
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

  private static byte[] serializeManifest(final BackupMetadata manifest) {
    try {
      return BackupMetadataSyncer.MAPPER.writeValueAsBytes(manifest);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
