/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.validation;

import static io.camunda.zeebe.backup.management.BackupMetadataSyncer.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link RestoreValidator#validate(RestoreRequest)} against a mocked {@link BackupStore}.
 * {@code validate} does not return the resolved backup set, so the only observable effects of RDBMS
 * backup resolution are whether it throws and which store partitions it touches.
 */
final class RestoreValidatorIT {

  private static final Instant CHECKPOINT_TIMESTAMP = Instant.parse("2026-01-01T10:00:00Z");

  private final BackupStore backupStore = mock(BackupStore.class);

  @Nested
  final class RdbmsBackupResolution {

    @Test
    void shouldValidateWhenSinglePartitionHasBackup() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      final var validator = new RestoreValidator(1, backupStore, null);
      final var request = rdbmsRequest();

      // when
      final var result = validator.validate(request);

      // then
      assertThat(result).isSameAs(request);
    }

    @Test
    void shouldValidateWhenAllPartitionsShareCommonCheckpoint() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      stubMetadata(2, singleCheckpointMetadata(2));
      stubMetadata(3, singleCheckpointMetadata(3));
      final var validator = new RestoreValidator(3, backupStore, null);
      final var request = rdbmsRequest();

      // when
      final var result = validator.validate(request);

      // then
      assertThat(result).isSameAs(request);
    }

    @Test
    void shouldValidateForNoneDatabaseType() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      final var validator = new RestoreValidator(1, backupStore, null);
      final var request = rdbmsRequest("none");

      // when
      final var result = validator.validate(request);

      // then
      assertThat(result).isSameAs(request);
    }

    @Test
    void shouldFailWhenNoCommonCheckpointExistsAcrossPartitions() {
      // given - partition 2 only has a checkpoint that partition 1 does not have
      stubMetadata(1, singleCheckpointMetadata(1, 1L));
      stubMetadata(2, singleCheckpointMetadata(2, 2L));
      final var validator = new RestoreValidator(2, backupStore, null);

      // when / then
      assertThatExceptionOfType(IllegalStateException.class)
          .isThrownBy(() -> validator.validate(rdbmsRequest()))
          .withMessageContaining("Could not find common checkpoint across partitions");
    }

    @Test
    void shouldFailWhenBackupMetadataIsMissingForAPartition() {
      // given
      when(backupStore.loadBackupMetadata(1))
          .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
      final var validator = new RestoreValidator(1, backupStore, null);

      // when / then
      assertThatExceptionOfType(IllegalStateException.class)
          .isThrownBy(() -> validator.validate(rdbmsRequest()))
          .withMessage("No backup metadata found for partition 1");
    }

    @Test
    void shouldFailWhenExportedPositionIsMissingForAPartition() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      final IntFunction<Long> exportedPositionSupplier = partitionId -> null;
      final var validator = new RestoreValidator(1, backupStore, exportedPositionSupplier);

      // when / then
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> validator.validate(rdbmsRequest()))
          .withMessage("No exported position found for partition 1 in RDBMS");
    }

    @Test
    void shouldValidateWhenExportedPositionIsCoveredByBackup() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      final IntFunction<Long> exportedPositionSupplier = partitionId -> 50L;
      final var validator = new RestoreValidator(1, backupStore, exportedPositionSupplier);
      final var request = rdbmsRequest();

      // when
      final var result = validator.validate(request);

      // then
      assertThat(result).isSameAs(request);
    }
  }

  @Nested
  final class NonRdbmsDatabaseTypes {

    @Test
    void shouldNotResolveRdbmsBackupsForElasticsearch() {
      // given
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest(List.of(1L), null, null, "elasticsearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertThat(result).isSameAs(request);
      verifyNoInteractions(backupStore);
    }

    @Test
    void shouldNotResolveRdbmsBackupsForOpensearch() {
      // given
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request = new RestoreRequest(List.of(1L), null, null, "opensearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertThat(result).isSameAs(request);
      verifyNoInteractions(backupStore);
    }
  }

  @Test
  void shouldRejectWhenNoBackupStoreIsConfigured() {
    // given
    final var validator = new RestoreValidator(1, null, null);

    // when / then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> validator.validate(rdbmsRequest()))
        .withMessage("Cannot restore: no backup store is configured on this broker.");
  }

  private void stubMetadata(final int partitionId, final BackupMetadata metadata) {
    when(backupStore.loadBackupMetadata(partitionId))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(serialize(metadata))));
  }

  private static byte[] serialize(final BackupMetadata metadata) {
    try {
      return MAPPER.writeValueAsBytes(metadata);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static RestoreRequest rdbmsRequest() {
    return rdbmsRequest("rdbms");
  }

  private static RestoreRequest rdbmsRequest(final String databaseType) {
    return new RestoreRequest(List.of(), null, null, databaseType, false, false);
  }

  private static BackupMetadata singleCheckpointMetadata(final int partitionId) {
    return singleCheckpointMetadata(partitionId, 1L);
  }

  private static BackupMetadata singleCheckpointMetadata(
      final int partitionId, final long checkpointId) {
    final var checkpoint =
        new CheckpointEntry(
            checkpointId,
            100L,
            CHECKPOINT_TIMESTAMP,
            CheckpointType.SCHEDULED_BACKUP,
            OptionalLong.of(1L));
    return new BackupMetadata(
        BackupMetadata.VERSION,
        partitionId,
        CHECKPOINT_TIMESTAMP,
        List.of(checkpoint),
        List.of(new RangeEntry(checkpointId, checkpointId)));
  }
}
