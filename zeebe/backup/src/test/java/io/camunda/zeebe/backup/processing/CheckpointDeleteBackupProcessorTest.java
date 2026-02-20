/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.processing.MockProcessingResult.Event;
import io.camunda.zeebe.backup.processing.MockProcessingResult.MockProcessingResultBuilder;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckpointDeleteBackupProcessorTest {

  @TempDir Path database;
  private ZeebeDb<ZbColumnFamilies> zeebedb;
  private DbCheckpointMetadataState checkpointMetadataState;
  private DbBackupRangeState backupRangeState;
  private DbCheckpointState checkpointState;

  @BeforeEach
  void before() {
    zeebedb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new)
            .createDb(database.toFile());
    final var context = zeebedb.createContext();
    checkpointMetadataState = new DbCheckpointMetadataState(zeebedb, context);
    backupRangeState = new DbBackupRangeState(zeebedb, context);
    checkpointState = new DbCheckpointState(zeebedb, context);
  }

  @AfterEach
  void closeDb() throws Exception {
    zeebedb.close();
  }

  @Test
  void shouldRejectWhenCheckpointNotFound() {
    // given
    final var processor = createProcessor(null, null);
    final var value = new CheckpointRecord().setCheckpointId(42);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);
    final var resultBuilder = new MockProcessingResultBuilder();

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then
    assertThat(result.records())
        .singleElement()
        .returns(CheckpointIntent.DELETE_BACKUP, Event::intent)
        .returns(RecordType.COMMAND_REJECTION, Event::type)
        .returns(RejectionType.NOT_FOUND, Event::rejectionType);
    assertThat(result.records().getFirst().rejectionReason())
        .isEqualTo("Expected to delete backup for checkpoint 42, but no such checkpoint exists");
  }

  @Test
  void shouldHaveNoPostCommitTasksOnRejection() {
    // given
    final var backupStore = mock(BackupStore.class);
    final var processor = createProcessor(backupStore, null);
    final var value = new CheckpointRecord().setCheckpointId(42);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);
    final var resultBuilder = new MockProcessingResultBuilder();

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then — no post-commit tasks on rejection
    assertThat(result.postCommitTasks()).isEmpty();
  }

  @Test
  void shouldWriteBackupDeletedEventOnSuccess() {
    // given
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    backupRangeState.startNewRange(checkpointId);

    final var processor = createProcessor(null, null);
    final var value = new CheckpointRecord().setCheckpointId(checkpointId);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);
    final var resultBuilder = new MockProcessingResultBuilder();

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then
    assertThat(result.records())
        .singleElement()
        .returns(CheckpointIntent.BACKUP_DELETED, Event::intent)
        .returns(RecordType.EVENT, Event::type)
        .returns(value, Event::value);
  }

  @Test
  void shouldSchedulePostCommitTasksWithBackupStore() {
    // given
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    backupRangeState.startNewRange(checkpointId);

    final var backupStore = mock(BackupStore.class);
    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(backupStore.storeBackupMetadata(any(int.class), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var processor = createProcessor(backupStore, backupStore);
    final var value = new CheckpointRecord().setCheckpointId(checkpointId);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);
    final var resultBuilder = new MockProcessingResultBuilder();

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then — 2 post-commit tasks: backup store delete + JSON sync
    assertThat(result.postCommitTasks()).hasSize(2);
  }

  @Test
  void shouldDeleteMixedStatusBackupsFromStore() {
    // given
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    backupRangeState.startNewRange(checkpointId);

    final var backupStore = mock(BackupStore.class);
    final var inProgressId = new BackupIdentifierImpl(1, 1, checkpointId);
    final var completedId = new BackupIdentifierImpl(2, 1, checkpointId);
    final BackupStatus inProgressStatus =
        new BackupStatusImpl(
            inProgressId,
            Optional.empty(),
            BackupStatusCode.IN_PROGRESS,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final BackupStatus completedStatus =
        new BackupStatusImpl(
            completedId,
            Optional.empty(),
            BackupStatusCode.COMPLETED,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of(inProgressStatus, completedStatus)));
    when(backupStore.markFailed(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(BackupStatusCode.FAILED));
    when(backupStore.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(backupStore.storeBackupMetadata(any(int.class), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var processor = createProcessor(backupStore, backupStore);
    final var value = new CheckpointRecord().setCheckpointId(checkpointId);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);
    final var resultBuilder = new MockProcessingResultBuilder();

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);
    result.executePostCommitTasks();

    // then — IN_PROGRESS backup is marked failed before delete
    verify(backupStore).markFailed(inProgressId, "The backup is being deleted.");
    verify(backupStore).delete(inProgressId);
    // COMPLETED backup is deleted directly
    verify(backupStore).delete(completedId);
  }

  @Test
  void shouldHandleBackupStoreListFailureGracefully() {
    // given
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    backupRangeState.startNewRange(checkpointId);

    final var backupStore = mock(BackupStore.class);
    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Store unavailable")));
    when(backupStore.storeBackupMetadata(any(int.class), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var processor = createProcessor(backupStore, backupStore);
    final var value = new CheckpointRecord().setCheckpointId(checkpointId);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);
    final var resultBuilder = new MockProcessingResultBuilder();

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then — post-commit tasks succeed (failure is swallowed by exceptionally handler)
    final var success = result.executePostCommitTasks();
    assertThat(success).isTrue();
    // No delete called since list failed
    verify(backupStore, never()).delete(any());
  }

  @Test
  void shouldHaveNoPostCommitTasksWithoutBackupStore() {
    // given
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    backupRangeState.startNewRange(checkpointId);

    final var processor = createProcessor(null, null);
    final var value = new CheckpointRecord().setCheckpointId(checkpointId);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);
    final var resultBuilder = new MockProcessingResultBuilder();

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then
    assertThat(result.postCommitTasks()).isEmpty();
  }

  /**
   * Creates a CheckpointDeleteBackupProcessor with the given backup store. Pass null for no backup
   * store.
   */
  private CheckpointDeleteBackupProcessor createProcessor(
      final BackupStore backupStore, final BackupStore syncStore) {
    final var syncer =
        syncStore != null
            ? new io.camunda.zeebe.backup.management.BackupMetadataSyncer(syncStore)
            : null;
    return new CheckpointDeleteBackupProcessor(
        checkpointMetadataState, backupRangeState, checkpointState, backupStore, syncer, 1);
  }
}
