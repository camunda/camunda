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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link RestoreValidator#validate(RestoreRequest)} against a mocked {@link BackupStore}.
 * {@code validate} reports invalid requests as an {@link Either.Left} rather than throwing;
 * exceptions raised by the RDBMS backup resolution itself (e.g. {@link IllegalStateException}) are
 * not caught and still propagate. On success, it resolves the request into a {@link
 * RestoreResolvedRequest} carrying the checkpoint ids to restore per partition.
 */
final class RestoreValidatorResolverTest {

  private static final Instant CHECKPOINT_TIMESTAMP = Instant.parse("2026-01-01T10:00:00Z");

  private final BackupStore backupStore = mock(BackupStore.class);

  @Test
  void shouldRejectWhenNoBackupStoreIsConfigured() {
    // given
    final var validator = new RestoreValidator(1, null, null);

    // when
    final var result = validator.validate(rdbmsRequest());

    // then
    assertThat(assertInvalid(result))
        .hasMessage("Cannot restore: no backup store is configured on this broker.");
  }

  private static void assertValid(
      final Either<Exception, RestoreResolvedRequest> result,
      final Map<Integer, long[]> expectedBackups,
      final boolean expectedDryRun) {
    assertThat(result.isRight()).isTrue();
    final var resolved = result.get();
    assertThat(resolved.dryRun()).isEqualTo(expectedDryRun);
    assertThat(toComparable(resolved.backups())).isEqualTo(toComparable(expectedBackups));
  }

  private static InvalidRequest assertInvalid(
      final Either<Exception, RestoreResolvedRequest> result) {
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(InvalidRequest.class);
    return (InvalidRequest) result.getLeft();
  }

  private static Map<Integer, List<Long>> toComparable(final Map<Integer, long[]> backups) {
    return backups.entrySet().stream()
        .collect(
            Collectors.toMap(Entry::getKey, e -> Arrays.stream(e.getValue()).boxed().toList()));
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
    return rdbmsRequest(databaseType, false);
  }

  private static RestoreRequest rdbmsRequest(final String databaseType, final boolean dryRun) {
    return new RestoreRequest("default", List.of(), null, null, databaseType, false, dryRun);
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
      assertValid(result, Map.of(1, new long[] {1L}), false);
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
      assertValid(
          result, Map.of(1, new long[] {1L}, 2, new long[] {1L}, 3, new long[] {1L}), false);
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
      assertValid(result, Map.of(1, new long[] {1L}), false);
    }

    @Test
    void shouldPropagateDryRunFlagToResolvedRequest() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      final var validator = new RestoreValidator(1, backupStore, null);
      final var request = rdbmsRequest("rdbms", true);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(1, new long[] {1L}), true);
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

      // when
      final var result = validator.validate(rdbmsRequest());

      // then
      assertThat(assertInvalid(result))
          .hasMessage("No exported position found for partition 1 in RDBMS");
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
      assertValid(result, Map.of(1, new long[] {1L}), false);
    }
  }

  @Nested
  final class NonRdbmsDatabaseTypes {

    @Test
    void shouldNotResolveRdbmsBackupsForElasticsearch() {
      // given
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(1L), null, null, "elasticsearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(), false);
      verifyNoInteractions(backupStore);
    }

    @Test
    void shouldNotResolveRdbmsBackupsForOpensearch() {
      // given
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(1L), null, null, "opensearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(), false);
      verifyNoInteractions(backupStore);
    }
  }
}
