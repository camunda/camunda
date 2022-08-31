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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackupServiceImplTest {

  @Mock InProgressBackup inProgressBackup;
  @Mock BackupStore backupStore;

  private BackupServiceImpl backupService;
  private final ConcurrencyControl concurrencyControl = new TestConcurrencyControl();

  @BeforeEach
  void setup() {
    backupService = new BackupServiceImpl(backupStore);
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
    when(backupStore.getStatus(any())).thenReturn(CompletableFuture.completedFuture(status));

    // when
    final var result =
        backupService.getBackupStatus(new BackupIdentifierImpl(1, 1, 1), concurrencyControl);

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100)).isEqualTo(status);
  }

  @Test
  void shouldCompleteFutureWhenBackupStatusFailed() {
    // given
    when(backupStore.getStatus(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")));

    // when
    final var result =
        backupService.getBackupStatus(new BackupIdentifierImpl(1, 1, 1), concurrencyControl);

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Expected");
  }

  @Test
  void shouldMarkInProgressBackupsAsFailed() {
    // given
    final var inProgressBackup = new BackupIdentifierImpl(1, 1, 10);
    final var notExistingBackup = new BackupIdentifierImpl(2, 1, 10);
    final var completedBackup = new BackupIdentifierImpl(3, 1, 10);
    final var inProgressStatus =
        new BackupStatusImpl(
            inProgressBackup, Optional.empty(), BackupStatusCode.IN_PROGRESS, Optional.empty());
    final var notExistingStatus =
        new BackupStatusImpl(
            notExistingBackup, Optional.empty(), BackupStatusCode.DOES_NOT_EXIST, Optional.empty());
    final var completedStatus =
        new BackupStatusImpl(
            completedBackup, Optional.empty(), BackupStatusCode.COMPLETED, Optional.empty());
    when(backupStore.getStatus(inProgressBackup))
        .thenReturn(CompletableFuture.completedFuture(inProgressStatus));
    when(backupStore.getStatus(notExistingBackup))
        .thenReturn(CompletableFuture.completedFuture(notExistingStatus));
    when(backupStore.getStatus(completedBackup))
        .thenReturn(CompletableFuture.completedFuture(completedStatus));

    // when
    backupService.failInProgressBackups(1, 10, List.of(1, 2, 3), concurrencyControl);

    // then
    verify(backupStore, timeout(1000)).markFailed(inProgressBackup);
    verify(backupStore, never()).markFailed(notExistingBackup);
    verify(backupStore, never()).markFailed(completedBackup);
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
    verify(backupStore).markFailed(any());
    verify(inProgressBackup).close();
  }
}
