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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupRangeMarker.Deletion;
import io.camunda.zeebe.backup.api.BackupRangeMarker.End;
import io.camunda.zeebe.backup.api.BackupRangeMarker.Start;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
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

  @Mock BackupStore backupStore;

  @Mock BackupStatus notExistingBackupStatus;

  @Mock LogStreamWriter logStreamWriter;

  private BackupServiceImpl backupService;
  private final TestConcurrencyControl concurrencyControl = new TestConcurrencyControl();

  @BeforeEach
  void setup() {
    backupService = new BackupServiceImpl(backupStore, logStreamWriter);

    lenient()
        .when(notExistingBackupStatus.statusCode())
        .thenReturn(BackupStatusCode.DOES_NOT_EXIST);
    lenient()
        .when(backupStore.getStatus(any()))
        .thenReturn(CompletableFuture.completedFuture(notExistingBackupStatus));
    lenient()
        .when(
            backupStore.list(
                new BackupIdentifierWildcardImpl(
                    Optional.empty(), Optional.of(2), CheckpointPattern.of(3L))))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    lenient()
        .when(logStreamWriter.tryWrite(any(), any(LogAppendEntry.class)))
        .thenReturn(Either.right(-1L));
  }

  @Test
  void shouldTakeBackup() {
    // given
    final ControllableInProgressBackup inProgressBackup = new ControllableInProgressBackup();
    mockSaveBackup();

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    verify(backupStore).save(any());
  }

  @Test
  void shouldCloseAllInProgressBackupsWhenClosing() {
    // given
    final ControllableInProgressBackup backup1 =
        new ControllableInProgressBackup().waitOnFindSegmentFiles();
    final ControllableInProgressBackup backup2 =
        new ControllableInProgressBackup().waitOnFindSegmentFiles();

    backupService.takeBackup(backup1, concurrencyControl);
    backupService.takeBackup(backup2, concurrencyControl);

    // when
    backupService.close();

    // then
    assertThat(backup1.isClosed()).isTrue();
    assertThat(backup2.isClosed()).isTrue();
  }

  @Test
  void shouldCloseInProgressBackupsAfterBackupIsTaken() {
    // given
    final ControllableInProgressBackup inProgressBackup = new ControllableInProgressBackup();
    mockSaveBackup();

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldFailBackupWhenNoValidSnapshotFound() {
    // given
    final ControllableInProgressBackup inProgressBackup =
        new ControllableInProgressBackup().failOnFindValidSnapshot();

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(1000))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Expected");
    verifyInProgressBackupIsCleanedUpAfterFailure(inProgressBackup);
  }

  @Test
  void shouldFailBackupWhenSnapshotCannotBeReserved() {
    // given
    final var inProgressBackup = new ControllableInProgressBackup().failOnReserveSnapshot();

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).failsWithin(Duration.ofMillis(100));
    verifyInProgressBackupIsCleanedUpAfterFailure(inProgressBackup);
  }

  @Test
  void shouldFailBackupWhenSnapshotFilesCannotBeCollected() {
    // given
    final var inProgressBackup = new ControllableInProgressBackup().failOnFindSnapshotFiles();

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Expected");
    verifyInProgressBackupIsCleanedUpAfterFailure(inProgressBackup);
  }

  @Test
  void shouldFailBackupWhenSegmentFilesCannotBeCollected() {
    // given
    final ControllableInProgressBackup inProgressBackup =
        new ControllableInProgressBackup().failOnFindSegmentFiles();

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Expected");
    verifyInProgressBackupIsCleanedUpAfterFailure(inProgressBackup);
  }

  @Test
  void shouldFailBackupIfStoringFailed() {
    // given
    final ControllableInProgressBackup inProgressBackup = new ControllableInProgressBackup();
    when(backupStore.save(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")));

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Expected");
    verifyInProgressBackupIsCleanedUpAfterFailure(inProgressBackup);
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
    when(backupStore.markFailed(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(BackupStatusCode.FAILED));
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
    final ControllableInProgressBackup inProgressBackup = new ControllableInProgressBackup();
    final BackupStatus status = mock(BackupStatus.class);
    when(status.statusCode()).thenReturn(BackupStatusCode.COMPLETED);
    when(backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(),
                Optional.of(inProgressBackup.id.partitionId()),
                CheckpointPattern.of(inProgressBackup.id.checkpointId()))))
        .thenReturn(CompletableFuture.completedFuture(List.of(status)));

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
    final ControllableInProgressBackup inProgressBackup = new ControllableInProgressBackup();
    final BackupStatus status = mock(BackupStatus.class);
    when(status.statusCode()).thenReturn(statusCode);
    when(backupStore.list(any())).thenReturn(CompletableFuture.completedFuture(List.of(status)));

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
    when(backupNode2.statusCode()).thenReturn(BackupStatusCode.COMPLETED);
    when(backupNode2.id()).thenReturn(new BackupIdentifierImpl(2, partitionId, checkpointId));

    when(backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(checkpointId))))
        .thenReturn(CompletableFuture.completedFuture(List.of(backupNode1, backupNode2)));

    when(backupStore.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(backupStore.storeRangeMarker(anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

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
                Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(checkpointId))))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup)));

    when(backupStore.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(backupStore.markFailed(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(BackupStatusCode.FAILED));
    when(backupStore.storeRangeMarker(anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

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
                Optional.empty(), Optional.of(partitionId), CheckpointPattern.any())))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup1, backup2)));

    // when
    final var backups = backupService.listBackups(partitionId, null, concurrencyControl).join();

    // then
    assertThat(backups).containsExactlyInAnyOrder(backup1, backup2);
  }

  @Test
  void confirmsSuccessfulBackup() {
    // given
    final var inProgressBackup = new ControllableInProgressBackup();
    mockSaveBackup();

    // when
    backupService.takeBackup(inProgressBackup, concurrencyControl).join();

    // then
    verify(logStreamWriter)
        .tryWrite(
            eq(WriteContext.internal()),
            assertArg(
                (final LogAppendEntry entry) -> {
                  assertThat(entry.recordMetadata().getRecordType()).isEqualTo(RecordType.COMMAND);
                  assertThat(entry.recordMetadata().getIntent())
                      .isEqualTo(CheckpointIntent.CONFIRM_BACKUP);
                  assertThat(entry.recordValue())
                      .isInstanceOfSatisfying(
                          CheckpointRecord.class,
                          checkpointRecord -> {
                            assertThat(checkpointRecord.getCheckpointId())
                                .isEqualTo(inProgressBackup.id().checkpointId());
                            assertThat(checkpointRecord.getCheckpointPosition())
                                .isEqualTo(
                                    inProgressBackup.backupDescriptor().checkpointPosition());
                          });
                }));
  }

  @Test
  void doesNotConfirmUnsuccessfulBackup() {
    // given
    final var inProgressBackup = new ControllableInProgressBackup();
    when(backupStore.save(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")));

    // when
    final var result = backupService.takeBackup(inProgressBackup, concurrencyControl);

    // then
    assertThat(result).failsWithin(Duration.ofMillis(100));

    verify(logStreamWriter, never()).tryWrite(any(), any(LogAppendEntry.class));
  }

  @Test
  void backupConfirmationWriteErrorIsIgnored() {
    // given
    when(logStreamWriter.tryWrite(any(), any(LogAppendEntry.class)))
        .thenReturn(Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED));
    mockSaveBackup();

    // when
    final var result =
        backupService.takeBackup(new ControllableInProgressBackup(), concurrencyControl);

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    verify(logStreamWriter).tryWrite(any(), any(LogAppendEntry.class));
  }

  @Test
  void shouldStoreDeletionMarkerWhenDeletingCompletedBackup() {
    // given
    final int partitionId = 1;
    final long checkpointId = 5L;
    final BackupStatus backup = mock(BackupStatus.class);
    when(backup.statusCode()).thenReturn(BackupStatusCode.COMPLETED);
    when(backup.id()).thenReturn(new BackupIdentifierImpl(1, partitionId, checkpointId));

    when(backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(checkpointId))))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup)));

    when(backupStore.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(backupStore.storeRangeMarker(anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    backupService.deleteBackup(partitionId, checkpointId, concurrencyControl).join();

    // then - verify storeRangeMarker is called before delete
    final var inOrder = inOrder(backupStore);
    inOrder.verify(backupStore).storeRangeMarker(partitionId, new Deletion(checkpointId));
    inOrder.verify(backupStore).delete(backup.id());
  }

  @Test
  void shouldStoreDeletionMarkerBeforeDeletingInProgressBackup() {
    // given
    final int partitionId = 1;
    final long checkpointId = 5L;
    final BackupStatus backup = mock(BackupStatus.class);
    when(backup.statusCode()).thenReturn(BackupStatusCode.IN_PROGRESS);
    when(backup.id()).thenReturn(new BackupIdentifierImpl(1, partitionId, checkpointId));

    when(backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(checkpointId))))
        .thenReturn(CompletableFuture.completedFuture(List.of(backup)));

    when(backupStore.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(backupStore.markFailed(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(BackupStatusCode.FAILED));
    when(backupStore.storeRangeMarker(anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    backupService.deleteBackup(partitionId, checkpointId, concurrencyControl).join();

    // then - verify markFailed is called first, then storeRangeMarker, then delete
    final var inOrder = inOrder(backupStore);
    inOrder.verify(backupStore).markFailed(eq(backup.id()), anyString());
    inOrder.verify(backupStore).storeRangeMarker(partitionId, new Deletion(checkpointId));
    inOrder.verify(backupStore).delete(backup.id());
  }

  @Test
  void shouldExtendRangeByStoringNewEndMarkerAndDeletingPreviousOne() {
    // given
    final int partitionId = 1;
    final long previousCheckpointId = 5L;
    final long newCheckpointId = 10L;

    when(backupStore.storeRangeMarker(anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(backupStore.deleteRangeMarker(anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    backupService.extendRange(partitionId, previousCheckpointId, newCheckpointId);

    // then - verify storeRangeMarker is called before deleteRangeMarker
    final var inOrder = inOrder(backupStore);
    inOrder
        .verify(backupStore, timeout(1000))
        .storeRangeMarker(partitionId, new End(newCheckpointId));
    inOrder
        .verify(backupStore, timeout(1000))
        .deleteRangeMarker(partitionId, new End(previousCheckpointId));
  }

  @Test
  void shouldNotDeletePreviousEndMarkerIfStoringNewEndMarkerFails() {
    // given
    final int partitionId = 1;
    final long previousCheckpointId = 5L;
    final long newCheckpointId = 10L;

    when(backupStore.storeRangeMarker(anyInt(), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")));

    // when
    backupService.extendRange(partitionId, previousCheckpointId, newCheckpointId);

    // then
    verify(backupStore, timeout(1000)).storeRangeMarker(partitionId, new End(newCheckpointId));
    verify(backupStore, never()).deleteRangeMarker(anyInt(), any());
  }

  @Test
  void shouldStartNewRangeByStoringStartAndEndMarkers() {
    // given
    final int partitionId = 1;
    final long checkpointId = 10L;

    when(backupStore.storeRangeMarker(anyInt(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    backupService.startNewRange(partitionId, checkpointId);

    // then - verify Start marker is stored before End marker
    final var inOrder = inOrder(backupStore);
    inOrder
        .verify(backupStore, timeout(1000))
        .storeRangeMarker(partitionId, new Start(checkpointId));
    inOrder.verify(backupStore, timeout(1000)).storeRangeMarker(partitionId, new End(checkpointId));
  }

  @Test
  void shouldNotStoreEndMarkerIfStoringStartMarkerFails() {
    // given
    final int partitionId = 1;
    final long checkpointId = 10L;

    when(backupStore.storeRangeMarker(eq(partitionId), eq(new Start(checkpointId))))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expected")));

    // when
    backupService.startNewRange(partitionId, checkpointId);

    // then
    verify(backupStore, timeout(1000)).storeRangeMarker(partitionId, new Start(checkpointId));
    verify(backupStore, never()).storeRangeMarker(partitionId, new End(checkpointId));
  }

  private <T> ActorFuture<T> failedFuture() {
    return concurrencyControl.failedFuture(new RuntimeException("Expected"));
  }

  private void verifyInProgressBackupIsCleanedUpAfterFailure(
      final ControllableInProgressBackup inProgressBackup) {
    verify(backupStore).markFailed(any(), any());
    assertThat(inProgressBackup.isClosed()).isTrue();
  }

  private void mockSaveBackup() {
    when(backupStore.save(any())).thenReturn(CompletableFuture.completedFuture(null));
  }

  class ControllableInProgressBackup implements InProgressBackup {

    private final BackupIdentifier id;
    private final BackupDescriptor checkpointDescriptor;
    private ActorFuture<Set<PersistedSnapshot>> findValidSnapshotFuture =
        TestActorFuture.completedFuture(null);
    private ActorFuture<Void> reserveSnapshotFuture = TestActorFuture.completedFuture(null);
    private ActorFuture<Void> findSnapshotFilesFuture = TestActorFuture.completedFuture(null);
    private ActorFuture<Void> findSegmentFilesFuture = TestActorFuture.completedFuture(null);
    private boolean closed;

    ControllableInProgressBackup() {
      id = new BackupIdentifierImpl(1, 2, 3);
      checkpointDescriptor =
          new BackupDescriptorImpl(1L, 2, "1.2.0", Instant.now(), CheckpointType.MANUAL_BACKUP);
    }

    @Override
    public OptionalLong getFirstLogPosition() {
      return OptionalLong.empty();
    }

    @Override
    public BackupDescriptor backupDescriptor() {
      return checkpointDescriptor;
    }

    @Override
    public BackupIdentifier id() {
      return id;
    }

    @Override
    public ActorFuture<Set<PersistedSnapshot>> findValidSnapshot() {
      return findValidSnapshotFuture;
    }

    @Override
    public ActorFuture<Void> reserveSnapshot() {
      return reserveSnapshotFuture;
    }

    @Override
    public ActorFuture<Void> findSnapshotFiles() {
      return findSnapshotFilesFuture;
    }

    @Override
    public ActorFuture<Void> findSegmentFiles() {
      return findSegmentFilesFuture;
    }

    @Override
    public Backup createBackup() {
      return null;
    }

    @Override
    public void close() {
      closed = true;
    }

    boolean isClosed() {
      return closed;
    }

    ControllableInProgressBackup waitOnFindSegmentFiles() {
      findSegmentFilesFuture = concurrencyControl.createFuture();
      return this;
    }

    ControllableInProgressBackup failOnFindSegmentFiles() {
      findSegmentFilesFuture = failedFuture();
      return this;
    }

    ControllableInProgressBackup failOnFindSnapshotFiles() {
      findSnapshotFilesFuture = failedFuture();
      return this;
    }

    ControllableInProgressBackup failOnReserveSnapshot() {
      reserveSnapshotFuture = failedFuture();
      return this;
    }

    ControllableInProgressBackup failOnFindValidSnapshot() {
      findValidSnapshotFuture = failedFuture();
      return this;
    }
  }
}
