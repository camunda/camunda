/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackupServiceImplTest {

  @Mock InProgressBackup inProgressBackup;
  @Mock BackupStore backupStore;

  @Mock BackupStatus notExistingBackupStatus;

  private BackupServiceImpl backupService;
  private final ConcurrencyControl concurrencyControl = new TestConcurrencyControl();

  @BeforeEach
  void setup() {
    backupService = new BackupServiceImpl(backupStore);

    lenient()
        .when(notExistingBackupStatus.statusCode())
        .thenReturn(BackupStatusCode.DOES_NOT_EXIST);
    lenient()
        .when(backupStore.getStatus(any()))
        .thenReturn(CompletableFuture.completedFuture(notExistingBackupStatus));
  }

  @Test
  void shouldTakeBackup() {
    // given
    mockInProgressBackup();

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    verify(backupStore).save(any());
  }

  @Test
  void shouldCloseAllInProgressBackupsWhenClosing() {
    // given
    final InProgressBackup backup1 = mock(InProgressBackup.class);
    final InProgressBackup backup2 = mock(InProgressBackup.class);
    when(backup1.findSegmentFiles()).thenReturn(concurrencyControl.createFuture());
    when(backup2.findSegmentFiles()).thenReturn(concurrencyControl.createFuture());

    backupService.takeBackup(backup1, concurrencyControl);
    backupService.takeBackup(backup2, concurrencyControl);

    // when
    backupService.close();

    // then
    verify(backup1).close();
    verify(backup2).close();
  }

  @Test
  void shouldCloseInProgressBackupsAfterBackupIsTaken() {
    // given
    mockInProgressBackup();

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldFailBackupWhenNoValidSnapshotFound() {
    // given
    mockFindSegmentFiles();
    when(inProgressBackup.findValidSnapshot()).thenReturn(failedFuture());

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(1000))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Expected");
    verifyInProgressBackupIsCleanedUpAfterFailure();
  }

  @Test
  void shouldFailBackupWhenSnapshotCannotBeReserved() {
    // given
    mockFindSegmentFiles();
    mockFindValidSnapshot();
    when(inProgressBackup.reserveSnapshot()).thenReturn(failedFuture());

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).failsWithin(Duration.ofMillis(100));
    verifyInProgressBackupIsCleanedUpAfterFailure();
  }

  @Test
  void shouldFailBackupWhenSnapshotFilesCannotBeCollected() {
    // given
    mockFindSegmentFiles();
    mockFindValidSnapshot();
    mockReserveSnapshot();
    when(inProgressBackup.findSnapshotFiles()).thenReturn(failedFuture());

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).failsWithin(Duration.ofMillis(100));
    verifyInProgressBackupIsCleanedUpAfterFailure();
  }

  @Test
  void shouldFailBackupWhenSegmentFilesCannotBeCollected() {
    // given
    when(inProgressBackup.findSegmentFiles()).thenReturn(failedFuture());

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).failsWithin(Duration.ofMillis(100));
    verifyInProgressBackupIsCleanedUpAfterFailure();
  }

  @Test
  void shouldFailBackupIfStoringFailed() {
    // given
    mockFindValidSnapshot();
    mockReserveSnapshot();
    mockFindSnapshotFiles();
    mockFindSegmentFiles();
    when(backupStore.save(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")));

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).failsWithin(Duration.ofMillis(100));
    verifyInProgressBackupIsCleanedUpAfterFailure();
  }

  @Test
  void shouldGetBackupStatus() {
    // given
    final BackupStatus status = mock(BackupStatus.class);
    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(List.of(status)));

    // when
    final var result = backupService.getBackupStatus(1, 1, concurrencyControl);

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100)).isEqualTo(Optional.of(status));
  }

  @Test
  void shouldCompleteFutureWhenBackupStatusFailed() {
    // given
    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")));

    // when
    final var result = backupService.getBackupStatus(1, 1, concurrencyControl);

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Expected");
  }

  @Test
  void shouldReturnBestStatusCode() {
    // given
    final BackupStatus status1 = mock(BackupStatus.class);
    when(status1.statusCode()).thenReturn(BackupStatusCode.IN_PROGRESS);
    final BackupStatus status2 = mock(BackupStatus.class);
    when(status2.statusCode()).thenReturn(BackupStatusCode.COMPLETED);

    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of(status1, status2)));

    // when
    final var result = backupService.getBackupStatus(1, 1, concurrencyControl);

    // then
    assertThat(result)
        .succeedsWithin(Duration.ofMillis(100))
        .returns(
            Optional.of(BackupStatusCode.COMPLETED),
            backupStatus -> backupStatus.map(BackupStatus::statusCode));
  }

  @Test
  void shouldMarkInProgressBackupsAsFailed() {
    // given
    final var inProgressBackup = new BackupIdentifierImpl(1, 1, 10);
    final var notExistingBackup = new BackupIdentifierImpl(2, 1, 10);
    final var completedBackup = new BackupIdentifierImpl(3, 1, 10);
    final var inProgressStatus =
        new BackupStatusImpl(
            inProgressBackup,
            Optional.empty(),
            BackupStatusCode.IN_PROGRESS,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var notExistingStatus =
        new BackupStatusImpl(
            notExistingBackup,
            Optional.empty(),
            BackupStatusCode.DOES_NOT_EXIST,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var completedStatus =
        new BackupStatusImpl(
            completedBackup,
            Optional.empty(),
            BackupStatusCode.COMPLETED,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    when(backupStore.list(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                List.of(inProgressStatus, notExistingStatus, completedStatus)));

    // when
    backupService.failInProgressBackups(1, 10, concurrencyControl);

    // then
    final var expectedFailureReason = "Backup is cancelled due to leader change.";
    verify(backupStore, timeout(1000)).markFailed(inProgressBackup, expectedFailureReason);
    verify(backupStore, never()).markFailed(notExistingBackup, expectedFailureReason);
    verify(backupStore, never()).markFailed(completedBackup, expectedFailureReason);
  }

  @Test
  void shouldNotTakeNewBackupIfBackupAlreadyCompleted() {
    // given
    final BackupStatus status = mock(BackupStatus.class);
    when(status.statusCode()).thenReturn(BackupStatusCode.COMPLETED);
    when(backupStore.getStatus(any())).thenReturn(CompletableFuture.completedFuture(status));

    // when
    backupService.takeBackup(inProgressBackup, concurrencyControl).join();

    // then
    verify(backupStore, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = BackupStatusCode.class,
      names = {"IN_PROGRESS", "FAILED"})
  void shouldNotTakeNewBackupIfBackupAlreadyExists(final BackupStatusCode statusCode) {
    // given
    final BackupStatus status = mock(BackupStatus.class);
    when(status.statusCode()).thenReturn(statusCode);
    when(backupStore.getStatus(any())).thenReturn(CompletableFuture.completedFuture(status));

    // when
    assertThat(backupService.takeBackup(inProgressBackup, concurrencyControl))
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(BackupAlreadyExistsException.class);

    // then
    verify(backupStore, never()).save(any());
  }

  @Test
  void shouldDeleteAllExistingBackupWithSameCheckpointId() {
    // given
    final int partitionId = 1;
    final long checkpointId = 2L;
    final BackupStatus backupNode1 = mock(BackupStatus.class);
    when(backupNode1.statusCode()).thenReturn(BackupStatusCode.COMPLETED);
    when(backupNode1.id()).thenReturn(new BackupIdentifierImpl(1, partitionId, checkpointId));

    final BackupStatus backupNode2 = mock(BackupStatus.class);
    when(backupNode1.statusCode()).thenReturn(BackupStatusCode.COMPLETED);
    when(backupNode1.id()).thenReturn(new BackupIdentifierImpl(2, partitionId, checkpointId));

    when(backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.of(partitionId), Optional.of(checkpointId))))
        .thenReturn(CompletableFuture.completedFuture(List.of(backupNode1, backupNode2)));

    when(backupStore.delete(any())).thenReturn(CompletableFuture.completedFuture(null));

    // when
    backupService.deleteBackup(partitionId, checkpointId, concurrencyControl).join();

    // then
    verify(backupStore).delete(backupNode1.id());
    verify(backupStore).delete(backupNode2.id());
  }

  @Test
  void shouldDeleteInProgressBackup() {
    // given
    final int partitionId = 1;
    final long checkpointId = 2L;
    final BackupStatus backup = mock(BackupStatus.class);
    when(backup.statusCode()).thenReturn(BackupStatusCode.IN_PROGRESS);
    when(backup.id()).thenReturn(new BackupIdentifierImpl(1, partitionId, checkpointId));

    when(backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.of(partitionId), Optional.of(checkpointId))))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup)));

    when(backupStore.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(backupStore.markFailed(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(BackupStatusCode.FAILED));

    // when
    backupService.deleteBackup(partitionId, checkpointId, concurrencyControl).join();

    // then
    verify(backupStore).markFailed(eq(backup.id()), anyString());
    verify(backupStore).delete(backup.id());
  }

  @Test
  void shouldListAvailableBackups() {
    // given
    final int partitionId = 1;
    final BackupStatus backup1 = mock(BackupStatus.class);
    final BackupStatus backup2 = mock(BackupStatus.class);

    when(backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.of(partitionId), Optional.empty())))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup1, backup2)));

    // when
    final var backups = backupService.listBackups(partitionId, concurrencyControl).join();

    // then
    assertThat(backups).containsExactlyInAnyOrder(backup1, backup2);
  }

  private ActorFuture<Void> failedFuture() {
    final ActorFuture<Void> future = concurrencyControl.createFuture();
    future.completeExceptionally(new RuntimeException("Expected"));
    return future;
  }

  private void mockInProgressBackup() {
    mockFindValidSnapshot();
    mockReserveSnapshot();
    mockFindSnapshotFiles();
    mockFindSegmentFiles();
    mockSaveBackup();
  }

  private void mockFindSnapshotFiles() {
    when(inProgressBackup.findSnapshotFiles())
        .thenReturn(concurrencyControl.createCompletedFuture());
  }

  private void mockReserveSnapshot() {
    when(inProgressBackup.reserveSnapshot()).thenReturn(concurrencyControl.createCompletedFuture());
  }

  private void mockFindValidSnapshot() {
    when(inProgressBackup.findValidSnapshot())
        .thenReturn(concurrencyControl.createCompletedFuture());
  }

  private void mockSaveBackup() {
    when(backupStore.save(any())).thenReturn(CompletableFuture.completedFuture(null));
  }

  private void mockFindSegmentFiles() {
    when(inProgressBackup.findSegmentFiles())
        .thenReturn(concurrencyControl.createCompletedFuture());
  }

  private void verifyInProgressBackupIsCleanedUpAfterFailure() {
    verify(backupStore).markFailed(any(), any());
    verify(inProgressBackup).close();
  }
}
