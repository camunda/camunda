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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link RestoreValidator#validate(RestoreRequest)} against a mocked {@link BackupStore}.
 * {@code validate} reports every failure as an {@link Either.Left}. On success, it resolves the
 * request into a {@link RestoreResolvedRequest} carrying the checkpoint ids to restore per
 * partition.
 */
final class RestoreValidatorResolverTest {

  private static final Instant CHECKPOINT_TIMESTAMP = Instant.parse("2026-01-01T10:00:00Z");

  /**
   * Matches how {@code RestoreApp} wires an rdbms secondary storage: an exported-position supplier
   * is only present for real RDBMS setups, which is what allows the null/null "auto lookup the
   * latest common checkpoint" request to be resolved at all - see {@link
   * RestoreValidator#findBackups}.
   */
  private static final IntFunction<Long> EXPORTED_POSITIONS = partitionId -> 50L;

  private final BackupStore backupStore = mock(BackupStore.class);

  @Test
  void shouldRejectWhenNoBackupStoreIsConfigured() {
    // given
    final var validator = new RestoreValidator(1, null, null);

    // when
    final var result = validator.validate(rdbmsRequest());

    // then
    assertThat(assertInvalid(result))
        .isInstanceOf(IllegalStateException.class)
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

  private static RuntimeException assertInvalid(
      final Either<Exception, RestoreResolvedRequest> result) {
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(RuntimeException.class);
    return (RuntimeException) result.getLeft();
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

  private void stubBackupExists(final long backupId) {
    final var status =
        new BackupStatusImpl(
            new BackupIdentifierImpl(1, 1, backupId),
            Optional.empty(),
            BackupStatusCode.COMPLETED,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of(status)));
  }

  private void stubNoBackupExists() {
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
  }

  private void stubBackupExists(final int partitionId, final long backupId) {
    final var pattern =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(backupId));
    final var status =
        new BackupStatusImpl(
            new BackupIdentifierImpl(0, partitionId, backupId),
            Optional.empty(),
            BackupStatusCode.COMPLETED,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    when(backupStore.list(pattern)).thenReturn(CompletableFuture.completedFuture(List.of(status)));
  }

  private void stubBackupMissing(final int partitionId, final long backupId) {
    final var pattern =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(backupId));
    when(backupStore.list(pattern)).thenReturn(CompletableFuture.completedFuture(List.of()));
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
      final var validator = new RestoreValidator(1, backupStore, EXPORTED_POSITIONS);
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
      final var validator = new RestoreValidator(3, backupStore, EXPORTED_POSITIONS);
      final var request = rdbmsRequest();

      // when
      final var result = validator.validate(request);

      // then
      assertValid(
          result, Map.of(1, new long[] {1L}, 2, new long[] {1L}, 3, new long[] {1L}), false);
    }

    @Test
    void shouldResolveWhenFromIsProvidedAloneForContinuousBackups() {
      // given - with a single checkpoint, the exported position must match its checkpoint
      // position exactly: the range lookup requires it not be after the checkpoint, and trimming
      // by `from` requires it not be before
      stubMetadata(1, singleCheckpointMetadata(1));
      final IntFunction<Long> exportedPositionSupplier = partitionId -> 100L;
      final var validator = new RestoreValidator(1, backupStore, exportedPositionSupplier);
      final var request =
          new RestoreRequest(
              "default", List.of(), CHECKPOINT_TIMESTAMP.toString(), null, "rdbms", true, false);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(1, new long[] {1L}), false);
    }

    @Test
    void shouldResolveWhenToIsProvidedAloneForContinuousBackups() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      final IntFunction<Long> exportedPositionSupplier = partitionId -> 100L;
      final var validator = new RestoreValidator(1, backupStore, exportedPositionSupplier);
      final var request =
          new RestoreRequest(
              "default", List.of(), null, CHECKPOINT_TIMESTAMP.toString(), "rdbms", true, false);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(1, new long[] {1L}), false);
    }

    @Test
    void shouldResolveWhenFromAndToAreBothProvided() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      final IntFunction<Long> exportedPositionSupplier = partitionId -> 100L;
      final var validator = new RestoreValidator(1, backupStore, exportedPositionSupplier);
      final var request =
          new RestoreRequest(
              "default",
              List.of(),
              CHECKPOINT_TIMESTAMP.toString(),
              CHECKPOINT_TIMESTAMP.plusSeconds(3600).toString(),
              "rdbms",
              true,
              false);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(1, new long[] {1L}), false);
    }

    @Test
    void shouldValidateForNoneDatabaseType() {
      // given - "none" storage still tracks exported positions in this scenario
      stubMetadata(1, singleCheckpointMetadata(1));
      final var validator = new RestoreValidator(1, backupStore, EXPORTED_POSITIONS);
      final var request = rdbmsRequest("none");

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(1, new long[] {1L}), false);
    }

    @Test
    void shouldFailWhenNoFromIsGivenAndNoExportedPositionsAreTracked() {
      // given - matches RestoreManager: without an exported-position supplier, `from` is required
      // to resolve a restore point; there is no backup-store-wide notion of "the latest backup".
      final var validator = new RestoreValidator(1, backupStore, null);
      final var request = rdbmsRequest("none");

      // when
      final var result = validator.validate(request);

      // then
      assertThat(assertInvalid(result))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "no backupId was specified and no exported-position data is available");
      verifyNoInteractions(backupStore);
    }

    @Test
    void shouldPropagateDryRunFlagToResolvedRequest() {
      // given
      stubMetadata(1, singleCheckpointMetadata(1));
      final var validator = new RestoreValidator(1, backupStore, EXPORTED_POSITIONS);
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
      final var validator = new RestoreValidator(2, backupStore, EXPORTED_POSITIONS);

      // when
      final var result = validator.validate(rdbmsRequest());

      // then
      assertThat(assertInvalid(result))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Could not find common checkpoint across partitions");
    }

    @Test
    void shouldFailWhenBackupMetadataIsMissingForAPartition() {
      // given
      when(backupStore.loadBackupMetadata(1))
          .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
      final var validator = new RestoreValidator(1, backupStore, EXPORTED_POSITIONS);

      // when / then
      final var result = validator.validate(rdbmsRequest());
      assertThat(assertInvalid(result))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No backup metadata found for partition(s): [1]");
    }

    @Test
    void shouldCollectAllPartitionsMissingBackupMetadata() {
      // given - partitions 1 and 3 have no metadata, only partition 2 does; the error should
      // report every missing partition, not just the first one encountered
      when(backupStore.loadBackupMetadata(1))
          .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
      stubMetadata(2, singleCheckpointMetadata(2));
      when(backupStore.loadBackupMetadata(3))
          .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
      final var validator = new RestoreValidator(3, backupStore, EXPORTED_POSITIONS);

      // when
      final var result = validator.validate(rdbmsRequest());

      // then
      assertThat(assertInvalid(result))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("No backup metadata found for partition(s): [1, 3]");
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
          .isInstanceOf(IllegalStateException.class)
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

    @Test
    void shouldResolveSameBackupIdsForEveryPartitionWhenBackupIdsAreExplicit() {
      // given - no range metadata (e.g. continuous backups disabled), only an ad-hoc backup taken
      stubBackupExists(42L);
      final var validator = new RestoreValidator(3, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(42L), null, null, "rdbms", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(
          result, Map.of(1, new long[] {42L}, 2, new long[] {42L}, 3, new long[] {42L}), false);
    }

    @Test
    void shouldFailWhenExplicitBackupIdDoesNotExistOnAPartition() {
      // given
      stubNoBackupExists();
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(42L), null, null, "rdbms", false, false);

      // when / then
      final var result = validator.validate(request);
      assertThat(assertInvalid(result)).isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  final class NonRdbmsDatabaseTypes {

    @Test
    void shouldBroadcastExplicitBackupIdToAllPartitionsForElasticsearch() {
      // given
      stubBackupExists(1, 1L);
      stubBackupExists(2, 1L);
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(1L), null, null, "elasticsearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(1, new long[] {1L}, 2, new long[] {1L}), false);
    }

    @Test
    void shouldBroadcastExplicitBackupIdToAllPartitionsForOpensearch() {
      // given
      stubBackupExists(1, 1L);
      stubBackupExists(2, 1L);
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(1L), null, null, "opensearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertValid(result, Map.of(1, new long[] {1L}, 2, new long[] {1L}), false);
    }

    @Test
    void shouldRejectMultipleBackupIdsForElasticsearch() {
      // given
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(1L, 2L), null, null, "elasticsearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertThat(assertInvalid(result))
          .hasMessage("Cannot restore from multiple backups against database type elasticsearch");
      verifyNoInteractions(backupStore);
    }

    @Test
    void shouldRejectMultipleBackupIdsForOpensearch() {
      // given
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(1L, 2L), null, null, "opensearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertThat(assertInvalid(result))
          .hasMessage("Cannot restore from multiple backups against database type opensearch");
      verifyNoInteractions(backupStore);
    }

    @Test
    void shouldRejectWhenBackupIsMissingForAPartition() {
      // given - partition 2 has no completed backup for the requested id
      stubBackupExists(1, 1L);
      stubBackupMissing(2, 1L);
      final var validator = new RestoreValidator(2, backupStore, null);
      final var request =
          new RestoreRequest("default", List.of(1L), null, null, "elasticsearch", false, false);

      // when
      final var result = validator.validate(request);

      // then
      assertThat(assertInvalid(result))
          .hasMessage("Could not find a completed backup with id 1 for partition 2.");
    }
  }
}
